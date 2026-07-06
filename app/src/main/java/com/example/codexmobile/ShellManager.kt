package com.example.codexmobile

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    suspend fun executeRootCommand(command: String): ShellCommandResult = withContext(Dispatchers.IO) {
        require(command.isNotBlank()) { "Refusing to execute an empty command" }
        val shell = Shell.getShell()
        if (!shell.isRoot) {
            return@withContext ShellCommandResult(
                exitCode = -1,
                stdout = "",
                stderr = "Root access was not granted.",
            )
        }

        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()
        val result = shell.newJob()
            .add(command)
            .to(stdout, stderr)
            .exec()

        ShellCommandResult(
            exitCode = result.code,
            stdout = truncate(stdout.joinToString("\n")),
            stderr = truncate(stderr.joinToString("\n")),
        )
    }

    private fun truncate(value: String): String =
        if (value.length <= MAX_STREAM_CHARS) value
        else value.take(MAX_STREAM_CHARS) + "\n[output truncated]"
}
