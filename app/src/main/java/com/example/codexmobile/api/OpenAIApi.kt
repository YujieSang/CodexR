package com.example.codexmobile.api

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val id: String = UUID.randomUUID().toString(),
    val attachments: List<MessageAttachment> = emptyList(),
    // Tool results remain user-role inputs for the existing command protocol.
    val kind: String = "message",
    val interrupted: Boolean = false,
    val toolCallId: String? = null,
)

@Serializable
data class MessageAttachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mimeType: String,
    val localPath: String,
    val sizeBytes: Long,
)
