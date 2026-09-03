package com.example.codexmobile.api

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val id: String = UUID.randomUUID().toString(),
    val attachments: List<MessageAttachment> = emptyList(),
    // Tool calls/results are persisted as Responses API items so their meaning does not
    // decay into ordinary chat text as a conversation grows.
    val kind: String = "message",
    val interrupted: Boolean = false,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolArguments: String? = null,
)

data class CodexToolCall(
    val callId: String,
    val name: String,
    val arguments: String,
)

data class CodexResponse(
    val text: String,
    val toolCalls: List<CodexToolCall> = emptyList(),
    val reasoningItems: List<String> = emptyList(),
)

@Serializable
data class MessageAttachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mimeType: String,
    val localPath: String,
    val sizeBytes: Long,
)
