package com.example.codexmobile.data

import com.example.codexmobile.api.ChatMessage
import java.util.UUID
import kotlinx.serialization.Serializable

const val DEFAULT_CODEX_MODEL_ID = "gpt-5.5"

@Serializable
data class CodexModelOption(
    val id: String,
    val label: String,
    val description: String = "",
    val supportedReasoningLevels: List<ReasoningLevel> = DEFAULT_REASONING_LEVELS,
    val defaultReasoningLevel: ReasoningLevel = ReasoningLevel.MEDIUM,
    val priority: Int = Int.MAX_VALUE,
)

@Serializable
enum class ReasoningLevel(val wireValue: String, val label: String) {
    NONE("none", "None"),
    MINIMAL("minimal", "Minimal"),
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High"),
    XHIGH("xhigh", "Extra high"),

    ;

    companion object {
        fun fromWireValue(value: String?): ReasoningLevel? =
            entries.firstOrNull { it.wireValue == value }
    }
}

val DEFAULT_REASONING_LEVELS = listOf(
    ReasoningLevel.LOW,
    ReasoningLevel.MEDIUM,
    ReasoningLevel.HIGH,
    ReasoningLevel.XHIGH,
)

val FALLBACK_CODEX_MODEL_OPTIONS = listOf(
    CodexModelOption("gpt-5.5", "GPT-5.5", priority = 0),
    CodexModelOption("gpt-5.4", "GPT-5.4", priority = 2),
    CodexModelOption("gpt-5.4-mini", "GPT-5.4 Mini", priority = 4),
    CodexModelOption("gpt-5.3-codex", "GPT-5.3 Codex", priority = 6),
    CodexModelOption("gpt-5.2", "GPT-5.2", priority = 10),
)

@Serializable
data class ModelCatalogState(
    val models: List<CodexModelOption>,
    val fetchedAt: Long,
    val source: String = "chatgpt",
)

@Serializable
data class ChatSession(
    val id: String,
    val title: String,
    val modelId: String = DEFAULT_CODEX_MODEL_ID,
    val reasoningLevel: ReasoningLevel = ReasoningLevel.MEDIUM,
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        fun create(
            modelId: String = DEFAULT_CODEX_MODEL_ID,
            reasoningLevel: ReasoningLevel = ReasoningLevel.MEDIUM,
            now: Long = System.currentTimeMillis(),
        ): ChatSession = ChatSession(
            id = UUID.randomUUID().toString(),
            title = "New chat",
            modelId = modelId,
            reasoningLevel = reasoningLevel,
            createdAt = now,
            updatedAt = now,
        )
    }
}

@Serializable
data class ChatSessionState(
    val activeSessionId: String,
    val sessions: List<ChatSession>,
)
