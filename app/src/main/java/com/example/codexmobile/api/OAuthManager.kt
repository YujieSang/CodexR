package com.example.codexmobile.api

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

object OAuthManager {
    private const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
    private const val AUTHORIZE_URL = "https://auth.openai.com/oauth/authorize"
    private const val TOKEN_URL = "https://auth.openai.com/oauth/token"
    private const val REDIRECT_URI = "http://localhost:1455/auth/callback"
    private const val CALLBACK_PATH = "/auth/callback"
    private const val CALLBACK_PORT = 1455
    private const val LOGIN_TIMEOUT_MS = 5 * 60 * 1000L
    private const val SCOPE = "openid profile email offline_access"

    private val json = Json { ignoreUnknownKeys = true }
    private val secureRandom = SecureRandom()
    private val refreshMutex = Mutex()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var manualRedirectInput: CompletableDeferred<String>? = null

    @Volatile
    private var isAppForeground = false

    private val foregroundLock = Any()
    private var foregroundWaiter: CompletableDeferred<Unit>? = null

    suspend fun login(context: Context): OAuthSession = coroutineScope {
        val verifier = randomBase64Url(32)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val expectedState = randomBase64Url(24)
        val server = runCatching(::openLoopbackServer).getOrNull()
        val manualInput = CompletableDeferred<String>()
        manualRedirectInput = manualInput

        val authorizationUrl = buildAuthorizationUrl(challenge, expectedState)
        val callbackJob = server?.let {
            async(Dispatchers.IO) { acceptBrowserCallback(it, expectedState) }
        }

        try {
            withContext(Dispatchers.Main) {
                CustomTabsIntent.Builder().build().launchUrl(context, authorizationUrl)
            }

            val input = withTimeout(LOGIN_TIMEOUT_MS) {
                select {
                    callbackJob?.onAwait { it }
                    manualInput.onAwait { it }
                }
            }
            val code = parseAuthorizationInput(input, expectedState)
            awaitAppForeground()
            exchangeAuthorizationCode(code, verifier).also { session ->
                OAuthSessionStore(context).save(session)
            }
        } finally {
            manualRedirectInput = null
            server?.close()
            callbackJob?.cancel()
        }
    }

    fun submitManualRedirect(value: String): Boolean {
        val target = manualRedirectInput ?: return false
        return target.complete(value.trim())
    }

    fun onAppForegroundChanged(isForeground: Boolean) {
        isAppForeground = isForeground
        if (isForeground) {
            synchronized(foregroundLock) {
                foregroundWaiter?.complete(Unit)
                foregroundWaiter = null
            }
        }
    }

    fun loadSession(context: Context): OAuthSession? = OAuthSessionStore(context).load()

    fun clearSession(context: Context) {
        OAuthSessionStore(context).clear()
    }

    suspend fun validSession(context: Context, forceRefresh: Boolean = false): OAuthSession =
        refreshMutex.withLock {
            val stored = OAuthSessionStore(context).load()
                ?: throw OAuthException("No saved ChatGPT session. Sign in again.")
            if (!forceRefresh && !stored.needsRefresh()) {
                return@withLock stored
            }
            refreshAccessToken(stored.refreshToken).also { refreshed ->
                OAuthSessionStore(context).save(refreshed)
            }
        }

    internal fun buildAuthorizationUrl(challenge: String, state: String): Uri =
        AUTHORIZE_URL.toUri().buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("id_token_add_organizations", "true")
            .appendQueryParameter("codex_cli_simplified_flow", "true")
            .appendQueryParameter("originator", "openclaw")
            .build()

    private fun openLoopbackServer(): ServerSocket = ServerSocket().apply {
        reuseAddress = false
        bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), CALLBACK_PORT), 1)
    }

    private fun acceptBrowserCallback(server: ServerSocket, expectedState: String): String {
        val socket = server.accept()
        return socket.use {
            val requestTarget = readRequestTarget(it)
            val callbackUri = "http://localhost$requestTarget".toUri()
            val validationError = validateCallback(callbackUri, expectedState)
            writeBrowserResponse(it, validationError)
            if (validationError != null) {
                throw OAuthException(validationError)
            }
            callbackUri.toString()
        }
    }

    private fun readRequestTarget(socket: Socket): String {
        val requestLine = BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
            ?: throw OAuthException("OAuth callback was empty")
        val parts = requestLine.split(' ')
        if (parts.size < 2 || parts[0] != "GET") {
            throw OAuthException("OAuth callback was not a valid GET request")
        }
        return parts[1]
    }

    private fun validateCallback(uri: Uri, expectedState: String): String? {
        if (uri.path != CALLBACK_PATH) return "Unexpected OAuth callback path"
        uri.getQueryParameter("error")?.let { error ->
            val description = uri.getQueryParameter("error_description")
            return description?.takeIf { it.isNotBlank() } ?: error
        }
        if (uri.getQueryParameter("state") != expectedState) return "OAuth state mismatch"
        if (uri.getQueryParameter("code").isNullOrBlank()) return "OAuth callback did not contain a code"
        return null
    }

    private fun writeBrowserResponse(socket: Socket, error: String?) {
        val title = if (error == null) "Authentication successful" else "Authentication failed"
        val message = if (error == null) {
            "You can close this tab and return to CodexR."
        } else {
            "Return to CodexR and try again."
        }
        val body = """<!doctype html><html><head><meta name="viewport" content="width=device-width"></head><body><h2>$title</h2><p>$message</p></body></html>"""
        val bytes = body.toByteArray(Charsets.UTF_8)
        socket.getOutputStream().use { output ->
            output.write("HTTP/1.1 ${if (error == null) "302 Found" else "400 Bad Request"}\r\n".toByteArray())
            if (error == null) {
                output.write("Location: codexmobile://oauth-complete\r\n".toByteArray())
            }
            output.write("Content-Type: text/html; charset=utf-8\r\n".toByteArray())
            output.write("Content-Length: ${bytes.size}\r\n".toByteArray())
            output.write("Connection: close\r\n\r\n".toByteArray())
            output.write(bytes)
            output.flush()
        }
    }

    private fun parseAuthorizationInput(input: String, expectedState: String): String {
        if (input.isBlank()) throw OAuthException("No OAuth callback was provided")
        val uri = runCatching { input.toUri() }.getOrNull()
        if (uri?.scheme == "http" || uri?.scheme == "https") {
            validateCallback(uri, expectedState)?.let { throw OAuthException(it) }
            return uri.getQueryParameter("code")!!
        }
        if (input.contains("code=")) {
            val query = "http://localhost/?${input.substringAfter('?')}".toUri()
            query.getQueryParameter("state")?.let { state ->
                if (state != expectedState) throw OAuthException("OAuth state mismatch")
            }
            return query.getQueryParameter("code")
                ?: throw OAuthException("OAuth callback did not contain a code")
        }
        return input
    }

    private suspend fun exchangeAuthorizationCode(code: String, verifier: String): OAuthSession =
        requestTokens(
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", CLIENT_ID)
                .add("code", code)
                .add("code_verifier", verifier)
                .add("redirect_uri", REDIRECT_URI)
                .build(),
            "exchange",
        )

    private suspend fun refreshAccessToken(refreshToken: String): OAuthSession =
        requestTokens(
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", CLIENT_ID)
                .build(),
            "refresh",
        )

    private suspend fun requestTokens(body: FormBody, operation: String): OAuthSession =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(TOKEN_URL).post(body).build()
            var lastDnsFailure: UnknownHostException? = null
            repeat(3) { attempt ->
                try {
                    httpClient.newCall(request).execute().use { response ->
                        val responseText = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            throw OAuthException("OpenAI token $operation failed (${response.code}): ${extractError(responseText)}")
                        }
                        val payload = runCatching { json.parseToJsonElement(responseText).jsonObject }
                            .getOrElse { throw OAuthException("OpenAI token response was not valid JSON", it) }
                        val access = payload["access_token"]?.jsonPrimitive?.contentOrNull
                            ?: throw OAuthException("OpenAI token response did not contain an access token")
                        val refresh = payload["refresh_token"]?.jsonPrimitive?.contentOrNull
                            ?: throw OAuthException("OpenAI token response did not contain a refresh token")
                        val expiresIn = payload["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                            ?: throw OAuthException("OpenAI token response did not contain a valid expiry")
                        val accountId = extractAccountId(access)
                            ?: throw OAuthException("OpenAI access token did not contain a ChatGPT account ID")
                        return@withContext OAuthSession(
                            accessToken = access,
                            refreshToken = refresh,
                            expiresAt = System.currentTimeMillis() + expiresIn * 1000L,
                            accountId = accountId,
                        )
                    }
                } catch (failure: UnknownHostException) {
                    lastDnsFailure = failure
                    if (attempt < 2) delay(500L * (attempt + 1))
                }
            }
            throw OAuthException(
                "Android could not resolve auth.openai.com. Check per-app firewall/DNS settings and retry.",
                lastDnsFailure,
            )
        }

    private suspend fun awaitAppForeground() {
        if (isAppForeground) return
        val waiter = synchronized(foregroundLock) {
            if (isAppForeground) return
            foregroundWaiter ?: CompletableDeferred<Unit>().also { foregroundWaiter = it }
        }
        withTimeout(60_000L) { waiter.await() }
    }

    private fun extractError(responseText: String): String = runCatching {
        val root = json.parseToJsonElement(responseText).jsonObject
        val error = root["error"]
        if (error == null) return@runCatching responseText.take(500)
        if (error is kotlinx.serialization.json.JsonObject) {
            error["message"]?.jsonPrimitive?.contentOrNull
                ?: error["code"]?.jsonPrimitive?.contentOrNull
                ?: responseText.take(500)
        } else {
            error.jsonPrimitive.contentOrNull ?: responseText.take(500)
        }
    }.getOrElse { responseText.take(500).ifBlank { "Unknown error" } }

    internal fun extractAccountId(accessToken: String): String? = runCatching {
        val payloadPart = accessToken.split('.').getOrNull(1) ?: return null
        val decoded = Base64.decode(payloadPart, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val payload = json.parseToJsonElement(decoded.toString(Charsets.UTF_8)).jsonObject
        payload["https://api.openai.com/auth"]
            ?.jsonObject
            ?.get("chatgpt_account_id")
            ?.jsonPrimitive
            ?.contentOrNull
    }.getOrNull()

    private fun randomBase64Url(byteCount: Int): String =
        ByteArray(byteCount).also(secureRandom::nextBytes).let(::base64Url)

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

class OAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)
