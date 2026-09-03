package com.example.codexmobile

import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Reader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ShellCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    fun toModelMessage(): String = buildString {
        appendLine("Command execution result:")
        appendLine("exit_code: $exitCode")
        appendLine("stdout:")
        appendLine("```")
        appendLine(stdout)
        appendLine("```")
        appendLine("stderr:")
        appendLine("```")
        appendLine(stderr)
        append("```")
    }
}

object ShellManager {
    private const val MAX_STREAM_CHARS = 64 * 1024
    private val workers = Executors.newCachedThreadPool()

    /** Each command owns a root process group; stopping never kills unrelated root shells. */
    suspend fun executeRootCommand(command: String): ShellCommandResult {
        require(command.isNotBlank()) { "Command is empty" }
        return suspendCancellableCoroutine { continuation ->
            val lock = Any()
            var process: Process? = null
            var groupId: Int? = null
            var cancelled = false
            continuation.invokeOnCancellation {
                val target = synchronized(lock) {
                    cancelled = true
                    process to groupId
                }
                workers.execute {
                    target.second?.let { pid ->
                        runCatching {
                            val killer = ProcessBuilder("su", "-c", "kill -KILL -- -$pid").start()
                            try { workers.submit<Int> { killer.waitFor() }.get(2, TimeUnit.SECONDS) }
                            finally { killer.destroy() }
                        }
                    }
                    target.first?.destroy()
                }
            }
            workers.execute {
                var running: Process? = null
                try {
                    // Execution cannot begin until the app receives the isolated process-group ID.
                    val wrapper = "echo CODEXR_PID:\$\$; IFS= read -r go; " +
                        "[ \"\$go\" = RUN ] || exit 130; exec sh -c ${quote(command)}"
                    synchronized(lock) {
                        if (!cancelled) {
                            // Android su may already be a session leader. -w keeps setsid's
                            // parent alive after a fork, preserving stdout and the real exit status.
                            running = ProcessBuilder("su", "-c", "exec setsid -w sh -c ${quote(wrapper)}").start()
                            process = running
                        }
                    }
                    val child = running ?: return@execute
                    val stderr = workers.submit<String> { drain(child.errorStream.reader()) }
                    val stdoutReader = child.inputStream.bufferedReader()
                    val greeting = stdoutReader.readLine().orEmpty()
                    val pid = greeting.removePrefix("CODEXR_PID:").toIntOrNull()
                    if (!greeting.startsWith("CODEXR_PID:") || pid == null || pid <= 1) {
                        child.outputStream.close()
                        val code = child.waitFor()
                        error("Could not start an isolated root shell (exit $code). " +
                            "Grant root access and ensure Android setsid is available. ${stderr.get()}")
                    }
                    synchronized(lock) {
                        if (!cancelled) {
                            groupId = pid
                            child.outputStream.write("RUN\n".toByteArray())
                            child.outputStream.flush()
                        }
                        child.outputStream.close()
                    }
                    val stdout = drain(stdoutReader)
                    val result = ShellCommandResult(child.waitFor(), stdout, stderr.get())
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                } finally {
                    running?.let {
                        runCatching { it.outputStream.close() }
                        runCatching { it.inputStream.close() }
                        runCatching { it.errorStream.close() }
                        it.destroy()
                    }
                }
            }
        }
    }

    internal fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    internal fun drain(reader: Reader): String = reader.use {
        val output = StringBuilder()
        val buffer = CharArray(8192)
        var truncated = false
        while (true) {
            val count = it.read(buffer)
            if (count < 0) break
            val remaining = MAX_STREAM_CHARS - output.length
            output.append(buffer, 0, count.coerceAtMost(remaining))
            if (count > remaining) truncated = true
        }
        if (truncated) output.append("\n[Output truncated at 64 KiB; remaining output was drained.]")
        output.toString()
    }
}
