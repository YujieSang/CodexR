package com.example.codexmobile.ui

import android.app.Application
import com.example.codexmobile.ShellCommandResult
import com.example.codexmobile.api.ChatMessage
import com.example.codexmobile.api.CodexResponse
import com.example.codexmobile.api.CodexToolCall
import com.example.codexmobile.api.MessageAttachment
import com.example.codexmobile.data.FALLBACK_CODEX_MODEL_OPTIONS
import com.example.codexmobile.data.ReasoningLevel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ConversationBehaviorTest {
    private lateinit var runtime: FakeRuntime
    private lateinit var controller: ChatViewModel

    @Before fun setup() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runtime = FakeRuntime()
        controller = ChatViewModel(RuntimeEnvironment.getApplication(), runtime)
        withTimeout(5_000) { controller.isReady.first { it } }
        Unit
    }

    @After fun teardown() { controller.close(); Dispatchers.resetMain() }

    @Test fun `queued followup goes after tool output and before next request`() = runBlocking {
        val rootGate = CompletableDeferred<ShellCommandResult>()
        runtime.replies += CodexResponse("", listOf(CodexToolCall("call_1", "exec_command", "{\"cmd\":\"printf hello\"}")))
        runtime.replies += CodexResponse("Done")
        runtime.root = { rootGate.await() }
        controller.sendMessage("Run it")
        assertNotNull(controller.pendingCommand.value)
        controller.approveCommand()
        assertTrue(controller.isTyping.value)
        controller.sendMessage("Now explain the output")
        assertEquals(1, controller.queuedMessages.value.size)
        rootGate.complete(ShellCommandResult(0, "hello", ""))
        withTimeout(5_000) { controller.isTyping.first { !it } }
        val next = runtime.requests.last()
        assertTrue(next[next.lastIndex - 1].content.contains("stdout:"))
        assertEquals("Now explain the output", next.last().content)
        assertTrue(controller.queuedMessages.value.isEmpty())
        assertEquals(1, runtime.executions)
    }

    @Test fun `stop cancels root work and retry does not directly replay it`() = runBlocking {
        runtime.replies += CodexResponse("", listOf(CodexToolCall("call_1", "exec_command", "{\"cmd\":\"sleep 100\"}")))
        runtime.replies += CodexResponse("Stopped safely")
        var cancelled = false
        runtime.root = { try { awaitCancellation() } finally { cancelled = true } }
        controller.sendMessage("Run a long command")
        controller.approveCommand()
        controller.stopExecution()
        withTimeout(5_000) { controller.isTyping.first { !it } }
        assertTrue(cancelled)
        assertTrue(controller.canRetry.value)
        val interrupted = controller.messages.value.single { it.kind == "tool_result" }
        assertEquals("call_1", interrupted.toolCallId)
        assertTrue(interrupted.content.contains("stopped", ignoreCase = true))
        controller.retryResponse()
        assertEquals(1, runtime.executions)
        assertTrue(runtime.requests.last().any { it.kind == "tool_result" && it.toolCallId == "call_1" })
        assertEquals("Stopped safely", controller.messages.value.last().content)
    }

    @Test fun `stop at approval never executes and prompt is editable`() {
        runtime.replies += CodexResponse("", listOf(CodexToolCall("call_1", "exec_command", "{\"cmd\":\"echo original\"}")))
        runtime.replies += CodexResponse("Revised response")
        controller.sendMessage("Original")
        val id = controller.messages.value.first().id
        controller.stopExecution()
        assertEquals(0, runtime.executions)
        assertNull(controller.pendingCommand.value)
        controller.editMessage(id, "Revised", emptyList())
        assertEquals(listOf("Revised", "Revised response"), controller.messages.value.map { it.content })
    }

    @Test fun `session permission is isolated and revocable`() {
        runtime.replies += listOf(
            CodexResponse("", listOf(CodexToolCall("call_1", "exec_command", "{\"cmd\":\"echo one\"}"))),
            CodexResponse("", listOf(CodexToolCall("call_2", "exec_command", "{\"cmd\":\"echo two\"}"))),
            CodexResponse("Done"),
        )
        controller.sendMessage("Inspect")
        val firstSession = controller.activeSessionId.value!!
        controller.approveCommand(forSession = true)
        assertEquals(2, runtime.executions)
        assertTrue(controller.sessionRootAllowed.value)
        controller.createSession()
        assertFalse(controller.sessionRootAllowed.value)
        runtime.replies += CodexResponse("", listOf(CodexToolCall("call_3", "exec_command", "{\"cmd\":\"echo third\"}")))
        controller.sendMessage("Inspect again")
        assertNotNull(controller.pendingCommand.value)
        assertEquals(2, runtime.executions)
        controller.stopExecution()
        controller.selectSession(firstSession)
        assertTrue(controller.sessionRootAllowed.value)
        controller.revokeSessionRoot()
        assertFalse(controller.sessionRootAllowed.value)
    }

    @Test fun `failed response can retry without duplicating the user prompt`() {
        runtime.failure = IllegalStateException("Connection dropped")
        controller.sendMessage("Hello")
        assertTrue(controller.canRetry.value)
        assertEquals("Connection dropped", controller.errorMessage.value)
        runtime.failure = null
        runtime.replies += CodexResponse("Hello back")
        controller.retryResponse()
        assertEquals(1, controller.messages.value.count { it.role == "user" })
        assertEquals("Hello back", controller.messages.value.last().content)
        assertFalse(controller.canRetry.value)
    }

    @Test fun `AI screen capture returns an attachment and uses root approval`() = runBlocking {
        runtime.replies += CodexResponse("", listOf(CodexToolCall("call_capture", "capture_screen", "{}")))
        runtime.replies += CodexResponse("I can see the screen")
        controller.sendMessage("What is on the screen?")
        assertTrue(controller.pendingCommand.value!!.contains("capture_screen"))
        assertEquals(0, runtime.captures)
        controller.approveCommand()
        withTimeout(5_000) { controller.isTyping.first { !it } }
        assertEquals(1, runtime.captures)
        val output = runtime.requests.last().single { it.kind == "tool_result" }
        assertEquals("call_capture", output.toolCallId)
        assertEquals(1, output.attachments.size)
    }

    @Test fun `followup without a tool starts the next response after completion`() = runBlocking {
        val gate = CompletableDeferred<CodexResponse>()
        runtime.responseGate = gate
        controller.sendMessage("First")
        controller.sendMessage("Second")
        runtime.responseGate = null
        runtime.replies += CodexResponse("Second answer")
        gate.complete(CodexResponse("First answer"))
        withTimeout(5_000) { controller.isTyping.first { !it } }
        assertEquals(listOf("First", "First answer", "Second", "Second answer"), controller.messages.value.map { it.content })
    }

    private class FakeRuntime : ChatRuntime {
        val replies = ArrayDeque<CodexResponse>()
        val requests = mutableListOf<List<ChatMessage>>()
        var executions = 0
        var captures = 0
        var failure: Exception? = null
        var responseGate: CompletableDeferred<CodexResponse>? = null
        var root: suspend () -> ShellCommandResult = { ShellCommandResult(0, "ok", "") }
        override suspend fun respond(messages: List<ChatMessage>, modelId: String, reasoningLevel: ReasoningLevel,
            onPartial: (String) -> Unit): CodexResponse {
            requests += messages
            failure?.let { throw it }
            return responseGate?.await() ?: replies.removeFirst()
        }
        override suspend fun execute(command: String): ShellCommandResult { executions++; return root() }
        override suspend fun capture(): List<MessageAttachment> { captures++; return listOf(MessageAttachment(name = "Screen", mimeType = "image/jpeg", localPath = "fake", sizeBytes = 1)) }
        override suspend fun models() = FALLBACK_CODEX_MODEL_OPTIONS
        override fun startBackground() {}
        override fun stopBackground() {}
    }
}
