package com.example.codexmobile.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.StringReader
import java.util.concurrent.TimeUnit

class StreamingAndAttachmentsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `cancel closes an active HTTP stream promptly`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ab").throttleBody(1, 2, TimeUnit.SECONDS))
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/")).build())
            val firstByte = CompletableDeferred<Unit>()
            val job = launch {
                call.consumeCancellable { response ->
                    val source = response.body!!.source()
                    source.readByte()
                    firstByte.complete(Unit)
                    source.readByte()
                }
            }
            withTimeout(3_000) { firstByte.await() }
            withTimeout(500) { job.cancelAndJoin() }
            assertTrue(call.isCanceled())
        }
    }

    @Test(expected = CodexProtocolException::class)
    fun `truncated stream is not treated as a completed command`() {
        AIClient.parseEventStream(StringReader("data: {\"type\":\"response.output_text.delta\",\"delta\":\"```bash\\necho x\\n```\"}\n\n").buffered())
    }

    @Test fun `capture function deduplicates stream and completion items`() {
        val item = """{"type":"function_call","name":"capture_screen","call_id":"call_1","arguments":"{}"}"""
        val stream = "data: {\"type\":\"response.output_item.done\",\"item\":$item}\n\n" +
            "data: {\"type\":\"response.completed\",\"response\":{\"output\":[$item]}}\n\n"
        val result = AIClient.parseEventStream(StringReader(stream).buffered())
        assertEquals("", result.text)
        assertEquals(listOf("call_1"), result.toolCalls.map { it.callId })
    }

    @Test fun `exec command is a structured tool with durable call id and arguments`() {
        val item = """{"type":"function_call","name":"exec_command","call_id":"call_shell","arguments":"{\"cmd\":\"id\"}"}"""
        val stream = "data: {\"type\":\"response.completed\",\"response\":{\"output\":[$item]}}\n\n"
        val result = AIClient.parseEventStream(StringReader(stream).buffered())
        assertEquals("exec_command", result.toolCalls.single().name)
        assertEquals("{\"cmd\":\"id\"}", result.toolCalls.single().arguments)
    }

    @Test fun `request contains image bytes text file content and proper tool result pairing`() {
        val image = temporary.newFile("image.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val text = temporary.newFile("code.txt").apply { writeText("println(42)") }
        val attachments = listOf(
            MessageAttachment(name = "Image", mimeType = "image/jpeg", localPath = image.path, sizeBytes = image.length()),
            MessageAttachment(name = "code.kt", mimeType = "text/plain", localPath = text.path, sizeBytes = text.length()),
        )
        val body = AIClient.buildRequestBody(listOf(
            ChatMessage("assistant", "Capture", kind = "capture_call", toolCallId = "call_1"),
            ChatMessage("user", "Captured", kind = "capture_result", toolCallId = "call_1", attachments = attachments),
        ))
        val input = body["input"]!!.jsonArray
        assertEquals("function_call", input[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("function_call_output", input[1].jsonObject["type"]!!.jsonPrimitive.content)
        val content = input[2].jsonObject["content"]!!.jsonArray
        assertEquals("data:image/jpeg;base64,AQID", content[1].jsonObject["image_url"]!!.jsonPrimitive.content)
        assertTrue(content[2].jsonObject["text"]!!.jsonPrimitive.content.contains("println(42)"))
        val tools = body["tools"]!!.jsonArray
        assertTrue(tools.any { it.jsonObject["name"]!!.jsonPrimitive.content == "exec_command" })
    }
}
