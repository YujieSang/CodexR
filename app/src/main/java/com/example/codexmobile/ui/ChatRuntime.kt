package com.example.codexmobile.ui

import android.app.Application
import com.example.codexmobile.ExecutionService
import com.example.codexmobile.ScreenCapture
import com.example.codexmobile.ShellCommandResult
import com.example.codexmobile.ShellManager
import com.example.codexmobile.api.AIClient
import com.example.codexmobile.api.ChatMessage
import com.example.codexmobile.api.CodexResponse
import com.example.codexmobile.api.MessageAttachment
import com.example.codexmobile.data.CodexModelOption
import com.example.codexmobile.data.ReasoningLevel

/** The side effects of a turn, separated so conversation transitions can be tested offline. */
interface ChatRuntime {
    suspend fun respond(messages: List<ChatMessage>, modelId: String, reasoningLevel: ReasoningLevel,
        onPartial: (String) -> Unit): CodexResponse
    suspend fun execute(command: String): ShellCommandResult
    suspend fun capture(): List<MessageAttachment>
    suspend fun models(): List<CodexModelOption>
    fun startBackground()
    fun stopBackground()
}

class AndroidChatRuntime(private val application: Application) : ChatRuntime {
    override suspend fun respond(messages: List<ChatMessage>, modelId: String, reasoningLevel: ReasoningLevel,
        onPartial: (String) -> Unit) =
        AIClient.generateResponse(messages, modelId, reasoningLevel, onPartial)
    override suspend fun execute(command: String) = ShellManager.executeRootCommand(command)
    override suspend fun capture() = ScreenCapture.capture(application)
    override suspend fun models() = AIClient.fetchModelCatalog()
    override fun startBackground() { ExecutionService.start(application) }
    override fun stopBackground() = ExecutionService.stop(application)
}
