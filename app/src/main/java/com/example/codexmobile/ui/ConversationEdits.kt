package com.example.codexmobile.ui

import com.example.codexmobile.api.ChatMessage
import com.example.codexmobile.api.MessageAttachment

internal object ConversationEdits {
    /** Editing branches at this prompt; downstream responses/tool outputs no longer apply. */
    fun edit(messages: List<ChatMessage>, id: String, text: String, attachments: List<MessageAttachment>): List<ChatMessage>? {
        val index = messages.indexOfFirst { it.id == id && it.role == "user" && it.kind == "message" }
        if (index < 0 || (text.isBlank() && attachments.isEmpty())) return null
        return messages.take(index) + messages[index].copy(content = text.trim(), attachments = attachments)
    }
}
