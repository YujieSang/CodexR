package com.example.codexmobile.api

import android.content.Context
import com.example.codexmobile.data.CodexModelOption
import com.example.codexmobile.data.DEFAULT_REASONING_LEVELS
import com.example.codexmobile.data.DEFAULT_CODEX_MODEL_ID
import com.example.codexmobile.data.ReasoningLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.util.UUID
import okio.ByteString.Companion.toByteString
import java.io.File
import java.util.concurrent.TimeUnit

object AIClient {
    private const val CODEX_RESPONSES_URL = "https://chatgpt.com/backend-api/codex/responses"
    private const val CODEX_MODELS_URL =
        "https://chatgpt.com/backend-api/codex/models?client_version=1.0.0"
    private const val CODEX_USAGE_URL = "https://chatgpt.com/backend-api/wham/usage"
    private const val API_RESPONSES_URL = "https://api.openai.com/v1/responses"
    private const val API_MODELS_URL = "https://api.openai.com/v1/models"
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .build()

    @Volatile
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    suspend fun generateResponse(
        messages: List<ChatMessage>,
        modelId: String = DEFAULT_CODEX_MODEL_ID,
        reasoningLevel: ReasoningLevel = ReasoningLevel.MEDIUM,
        onPartial: (String) -> Unit = {},
        onCaptureRequest: (String) -> Unit = {},
        onReasoningItem: (String) -> Unit = {},
    ): String {
        val context = applicationContext ?: error("AIClient is not initialized")
        var credential = AuthManager.credential(context)
        return try {
            sendRequest(credential, messages, modelId, reasoningLevel, onPartial, onCaptureRequest, onReasoningItem)
        } catch (failure: CodexHttpException) {
            if (failure.statusCode != 401 || credential is AuthCredential.ApiKey) throw failure
            credential = AuthManager.credential(context, forceRefresh = true)
            sendRequest(credential, messages, modelId, reasoningLevel, onPartial, onCaptureRequest, onReasoningItem)
        }
    }

    suspend fun fetchModelCatalog(): List<CodexModelOption> {
        val context = applicationContext ?: error("AIClient is not initialized")
        var credential = AuthManager.credential(context)
        return try {
            sendModelCatalogRequest(credential)
        } catch (failure: CodexHttpException) {
            if (failure.statusCode != 401 || credential is AuthCredential.ApiKey) throw failure
            credential = AuthManager.credential(context, forceRefresh = true)
            sendModelCatalogRequest(credential)
        }
    }

    suspend fun validateApiKey(apiKey: String) {
        sendModelCatalogRequest(AuthCredential.ApiKey(apiKey.trim()))
    }

    suspend fun fetchUsage(): UsageSnapshot {
        val context = applicationContext ?: error("AIClient is not initialized")
        var credential = AuthManager.credential(context)
        if (credential is AuthCredential.ApiKey) {
            throw CodexProtocolException(
                "API-key spend and limits are managed in the OpenAI Platform usage dashboard.",
            )
        }
        return try {
            sendUsageRequest((credential as AuthCredential.ChatGpt).session)
        } catch (failure: CodexHttpException) {
            if (failure.statusCode != 401) throw failure
            credential = AuthManager.credential(context, forceRefresh = true)
            sendUsageRequest((credential as AuthCredential.ChatGpt).session)
        }
    }

    private suspend fun sendModelCatalogRequest(credential: AuthCredential): List<CodexModelOption> =
        withContext(Dispatchers.IO) {
            val requestBuilder = Request.Builder()
                .url(if (credential is AuthCredential.ChatGpt) CODEX_MODELS_URL else API_MODELS_URL)
                .header("Accept", "application/json")
                .header("User-Agent", "CodexR/1.0 (Android)")
                .get()
            addAuthenticationHeaders(requestBuilder, credential)

            client.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw CodexHttpException(response.code, extractError(body))
                }
                val models = if (credential is AuthCredential.ChatGpt) {
                    parseModelCatalog(body)
                } else {
                    parseApiModelCatalog(body)
                }
                models.ifEmpty {
                    throw CodexProtocolException("Codex returned an empty model catalog")
                }
            }
        }

    private suspend fun sendUsageRequest(session: OAuthSession): UsageSnapshot =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(CODEX_USAGE_URL)
                .header("Authorization", "Bearer ${session.accessToken}")
                .header("chatgpt-account-id", session.accountId)
                .header("Accept", "application/json")
                .header("User-Agent", "CodexR/1.0 (Android)")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw CodexHttpException(response.code, extractError(body))
                }
                parseUsage(body)
            }
        }

    internal fun parseModelCatalog(body: String): List<CodexModelOption> {
        val root = json.parseToJsonElement(body).jsonObject
        val models = root["models"]?.jsonArray
            ?: throw CodexProtocolException("Codex model catalog has no models list")

        return models.asSequence()
            .mapNotNull { it as? JsonObject }
            .filter { model ->
                model["visibility"]?.jsonPrimitive?.contentOrNull != "hide" &&
                    model["visibility"]?.jsonPrimitive?.contentOrNull != "hidden" &&
                    model["supported_in_api"]?.jsonPrimitive?.booleanOrNull != false
            }
            .mapNotNull { model ->
                val id = model["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val supportedLevels = (model["supported_reasoning_levels"] as? JsonArray)
                    .orEmpty()
                    .mapNotNull { effort ->
                        val value = (effort as? JsonObject)
                            ?.get("effort")
                            ?.jsonPrimitive
                            ?.contentOrNull
                        ReasoningLevel.fromWireValue(value)
                    }
                    .distinct()
                    .ifEmpty { DEFAULT_REASONING_LEVELS }
                val advertisedDefault = ReasoningLevel.fromWireValue(
                    model["default_reasoning_level"]?.jsonPrimitive?.contentOrNull,
                )
                CodexModelOption(
                    id = id,
                    label = model["display_name"]?.jsonPrimitive?.contentOrNull ?: id,
                    description = model["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    supportedReasoningLevels = supportedLevels,
                    defaultReasoningLevel = advertisedDefault
                        ?.takeIf { it in supportedLevels }
                        ?: supportedLevels.firstOrNull { it == ReasoningLevel.MEDIUM }
                        ?: supportedLevels.first(),
                    priority = model["priority"]?.jsonPrimitive?.intOrNull ?: Int.MAX_VALUE,
                )
            }
            .sortedWith(compareBy<CodexModelOption> { it.priority }.thenBy { it.label })
            .toList()
    }

    internal fun parseApiModelCatalog(body: String): List<CodexModelOption> {
        val root = json.parseToJsonElement(body).jsonObject
        val snapshotSuffix = Regex("-\\d{4}-\\d{2}-\\d{2}$")
        val ids = root["data"]?.jsonArray.orEmpty()
            .mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull }
            .filter { it == "gpt-5" || it.startsWith("gpt-5.") || it.startsWith("gpt-5-") }
            .filterNot { snapshotSuffix.containsMatchIn(it) }
            .distinct()
        return ids.map { id ->
            CodexModelOption(
                id = id,
                label = id.replaceFirst("gpt", "GPT"),
                description = "OpenAI Platform model",
                priority = apiModelPriority(id),
            )
        }.sortedWith(compareBy<CodexModelOption> { it.priority }.thenByDescending { it.id })
    }

    internal fun parseUsage(body: String): UsageSnapshot {
        val root = json.parseToJsonElement(body).jsonObject
        val rateLimit = root["rate_limit"] as? JsonObject
        val windows = listOfNotNull(
            parseUsageWindow("Short window", rateLimit?.get("primary_window")),
            parseUsageWindow("Weekly window", rateLimit?.get("secondary_window")),
        )
        val credits = root["credits"] as? JsonObject
        return UsageSnapshot(
            planType = root["plan_type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            windows = windows,
            creditBalance = credits?.get("balance")?.jsonPrimitive?.contentOrNull,
            unlimitedCredits = credits?.get("unlimited")?.jsonPrimitive?.booleanOrNull == true,
        )
    }

    private fun parseUsageWindow(label: String, element: JsonElement?): UsageWindow? {
        val value = element as? JsonObject ?: return null
        val used = value["used_percent"]?.jsonPrimitive?.intOrNull ?: return null
        return UsageWindow(
            label = label,
            usedPercent = used.coerceIn(0, 100),
            resetsAtEpochSeconds = value["reset_at"]?.jsonPrimitive?.longOrNull,
        )
    }

    private suspend fun sendRequest(
        credential: AuthCredential,
        messages: List<ChatMessage>,
        modelId: String,
        reasoningLevel: ReasoningLevel,
        onPartial: (String) -> Unit,
        onCaptureRequest: (String) -> Unit,
        onReasoningItem: (String) -> Unit,
    ): String =
        withContext(Dispatchers.IO) {
            val requestId = UUID.randomUUID().toString()
            val body = buildRequestBody(messages, modelId, reasoningLevel).toString()
                .toRequestBody("application/json".toMediaType())
            val requestBuilder = Request.Builder()
                .url(if (credential is AuthCredential.ChatGpt) CODEX_RESPONSES_URL else API_RESPONSES_URL)
                .header("OpenAI-Beta", "responses=experimental")
                .header("Accept", "text/event-stream")
                .header("session_id", requestId)
                .header("x-client-request-id", requestId)
                .header("User-Agent", "CodexR/1.0 (Android)")
                .post(body)
            addAuthenticationHeaders(requestBuilder, credential)

            client.newCall(requestBuilder.build()).consumeCancellable { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    throw CodexHttpException(response.code, extractError(errorBody))
                }
                val responseBody = response.body
                    ?: throw CodexProtocolException("Codex returned an empty response")
                parseEventStream(responseBody.charStream().buffered(), onPartial, onCaptureRequest, onReasoningItem)
            }
        }

    private fun addAuthenticationHeaders(builder: Request.Builder, credential: AuthCredential) {
        when (credential) {
            is AuthCredential.ChatGpt -> builder
                .header("Authorization", "Bearer ${credential.session.accessToken}")
                .header("chatgpt-account-id", credential.session.accountId)
                .header("originator", "codexr")
            is AuthCredential.ApiKey -> builder
                .header("Authorization", "Bearer ${credential.key}")
        }
    }

    private fun apiModelPriority(id: String): Int = when {
        id == "gpt-5.5" -> 0
        id == "gpt-5.4" -> 10
        id.startsWith("gpt-5.4") -> 20
        id == "gpt-5" -> 30
        else -> 100
    }

    internal fun buildRequestBody(
        messages: List<ChatMessage>,
        modelId: String = DEFAULT_CODEX_MODEL_ID,
        reasoningLevel: ReasoningLevel = ReasoningLevel.MEDIUM,
    ): JsonObject {
        val attachmentBytes = messages.sumOf { message -> message.attachments.sumOf { File(it.localPath).length() } }
        require(attachmentBytes <= 24L * 1024 * 1024) {
            "This conversation has more than 24 MB of attachments. Start a new chat or edit earlier messages to remove attachments."
        }
        val instructions = messages.firstOrNull { it.role == "system" }?.content
            ?: "You are a helpful assistant."
        val input = buildJsonArray {
            messages.asSequence()
                .filter { it.role == "user" || it.role == "assistant" }
                .forEach { message ->
                    if (message.kind == "reasoning") {
                        val item = json.parseToJsonElement(message.content).jsonObject
                        if (item["type"]?.jsonPrimitive?.content == "reasoning") add(item)
                        return@forEach
                    }
                    if (message.kind == "capture_call") {
                        add(buildJsonObject {
                            put("type", "function_call")
                            put("call_id", message.toolCallId)
                            put("name", "capture_screen")
                            put("arguments", "{}")
                        })
                        // Process death may leave a tool call without a result. Never replay it silently.
                        if (messages.none { it.kind == "capture_result" && it.toolCallId == message.toolCallId }) {
                            add(buildJsonObject {
                                put("type", "function_call_output")
                                put("call_id", message.toolCallId)
                                put("output", "Capture was interrupted; no screenshot is available.")
                            })
                        }
                        return@forEach
                    }
                    if (message.kind == "capture_result") {
                        add(buildJsonObject {
                            put("type", "function_call_output")
                            put("call_id", message.toolCallId)
                            put("output", message.content)
                        })
                        if (message.attachments.isEmpty()) return@forEach
                        // Images are sent as an adjacent user input for compatibility with both backends.
                    }
                    add(buildJsonObject {
                        put("role", message.role)
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", if (message.role == "assistant") "output_text" else "input_text")
                                put("text", message.content)
                            })
                            if (message.role == "user") message.attachments.forEach { attachment ->
                                val file = File(attachment.localPath)
                                applicationContext?.let { context ->
                                    require(file.canonicalFile.parentFile == File(context.filesDir, "attachments").canonicalFile) {
                                        "Attachment is outside CodexR's private storage."
                                    }
                                }
                                require(file.isFile) { "Attachment is missing: ${attachment.name}. Edit this message to remove or reattach it." }
                                require(file.length() <= 10L * 1024 * 1024) { "Attachment is too large: ${attachment.name}" }
                                add(buildJsonObject {
                                    if (attachment.mimeType.startsWith("image/")) {
                                        put("type", "input_image")
                                        put("image_url", "data:${attachment.mimeType};base64," + file.readBytes().toByteString().base64())
                                    } else {
                                        put("type", "input_text")
                                        put("text", "Attached file: ${attachment.name}\n<file_content>\n${file.readText()}\n</file_content>")
                                    }
                                })
                            }
                        })
                    })
                }
        }
        return buildJsonObject {
            put("model", modelId)
            put("store", false)
            put("stream", true)
            put("instructions", instructions)
            put("input", input)
            put("parallel_tool_calls", false)
            put("tools", buildJsonArray {
                add(buildJsonObject {
                    put("type", "function")
                    put("name", "capture_screen")
                    put("description", "Capture the Android device's currently visible screen to inspect UI, errors, or the result of an action. Returns an image. The app enforces root approval. Protected screens may be blank. Do not use a shell screencap command; call this tool to receive the image.")
                    put("strict", true)
                    put("parameters", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {})
                        put("required", buildJsonArray {})
                        put("additionalProperties", false)
                    })
                })
            })
            put("text", buildJsonObject { put("verbosity", "low") })
            put("reasoning", buildJsonObject {
                put("effort", reasoningLevel.wireValue)
                put("summary", "auto")
            })
            put("include", buildJsonArray { add(JsonPrimitive("reasoning.encrypted_content")) })
        }
    }

    internal fun parseEventStream(reader: BufferedReader, onPartial: (String) -> Unit = {},
        onCaptureRequest: (String) -> Unit = {}, onReasoningItem: (String) -> Unit = {}): String {
        val output = StringBuilder()
        val data = StringBuilder()
        var completed = false
        val captures = linkedSetOf<String>()
        val reasoningItems = linkedMapOf<String, String>()

        fun consumeItem(item: JsonObject) {
            when (item["type"]?.jsonPrimitive?.contentOrNull) {
                "function_call" -> {
                    if (item["name"]?.jsonPrimitive?.contentOrNull != "capture_screen")
                        throw CodexProtocolException("Unsupported tool requested")
                    val arguments = item["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
                    if (json.parseToJsonElement(arguments).jsonObject.isNotEmpty())
                        throw CodexProtocolException("Screen capture received unexpected arguments")
                    captures += item["call_id"]?.jsonPrimitive?.contentOrNull
                        ?: throw CodexProtocolException("Capture request has no call ID")
                }
                "reasoning" -> item["id"]?.jsonPrimitive?.contentOrNull?.let { reasoningItems[it] = item.toString() }
            }
        }

        fun consumeEvent() {
            if (data.isEmpty()) return
            val raw = data.toString()
            data.clear()
            if (raw == "[DONE]") return
            val event = runCatching { json.parseToJsonElement(raw).jsonObject }
                .getOrElse { throw CodexProtocolException("Codex returned invalid stream data", it) }
            when (event["type"]?.jsonPrimitive?.contentOrNull) {
                "response.output_text.delta" -> {
                    event["delta"]?.jsonPrimitive?.contentOrNull?.let(output::append)
                    onPartial(output.toString())
                }
                "error" -> throw CodexProtocolException(extractEventError(event))
                "response.failed" -> throw CodexProtocolException(extractFailedResponseError(event))
                "response.incomplete" -> throw CodexProtocolException("Response was incomplete. Retry to continue.")
                "response.output_item.done" -> (event["item"] as? JsonObject)?.let(::consumeItem)
                "response.completed", "response.done" -> {
                    completed = true
                    if (output.isEmpty()) extractCompletedText(event)?.let(output::append)
                    onPartial(output.toString())
                    ((event["response"] as? JsonObject)?.get("output") as? JsonArray)
                        ?.mapNotNull { it as? JsonObject }?.forEach(::consumeItem)
                }
            }
        }

        reader.use {
            while (!completed) {
                val line = it.readLine() ?: break
                if (line.isBlank()) {
                    consumeEvent()
                } else if (line.startsWith("data:")) {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.removePrefix("data:").trimStart())
                }
            }
        }
        consumeEvent()
        if (!completed) throw CodexProtocolException("Connection ended before the response completed. Retry to continue.")
        if (captures.size > 1) throw CodexProtocolException("Only one screen capture is supported per response")
        reasoningItems.values.forEach(onReasoningItem)
        captures.forEach(onCaptureRequest)
        if (captures.isNotEmpty()) return output.toString()
        return output.toString().ifBlank {
            throw CodexProtocolException("Codex completed without returning text")
        }
    }

    private fun extractCompletedText(event: JsonObject): String? {
        val response = event["response"] as? JsonObject ?: return null
        val outputItems = response["output"] as? JsonArray ?: return null
        return outputItems.asSequence()
            .mapNotNull { it as? JsonObject }
            .flatMap { item -> (item["content"] as? JsonArray).orEmpty().asSequence() }
            .mapNotNull { it as? JsonObject }
            .mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("")
            .ifBlank { null }
    }

    private fun extractEventError(event: JsonObject): String {
        val nested = event["error"] as? JsonObject
        return event["message"]?.jsonPrimitive?.contentOrNull
            ?: nested?.get("message")?.jsonPrimitive?.contentOrNull
            ?: event["code"]?.jsonPrimitive?.contentOrNull
            ?: nested?.get("code")?.jsonPrimitive?.contentOrNull
            ?: "Codex returned an unknown error"
    }

    private fun extractFailedResponseError(event: JsonObject): String {
        val response = event["response"] as? JsonObject
        val error = response?.get("error") as? JsonObject
        return error?.get("message")?.jsonPrimitive?.contentOrNull
            ?: error?.get("code")?.jsonPrimitive?.contentOrNull
            ?: "Codex response failed"
    }

    private fun extractError(body: String): String = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val error = root["error"]
        when (error) {
            is JsonObject -> error["message"]?.jsonPrimitive?.contentOrNull
                ?: error["code"]?.jsonPrimitive?.contentOrNull
            else -> error?.jsonPrimitive?.contentOrNull
        } ?: body.take(1_000)
    }.getOrElse { body.take(1_000).ifBlank { "Unknown Codex error" } }
}

class CodexHttpException(val statusCode: Int, message: String) : Exception(
    "Codex request failed ($statusCode): $message",
)

class CodexProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class UsageWindow(
    val label: String,
    val usedPercent: Int,
    val resetsAtEpochSeconds: Long?,
) {
    val remainingPercent: Int get() = 100 - usedPercent
}

data class UsageSnapshot(
    val planType: String,
    val windows: List<UsageWindow>,
    val creditBalance: String?,
    val unlimitedCredits: Boolean,
)
