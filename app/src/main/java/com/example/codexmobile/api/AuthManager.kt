package com.example.codexmobile.api

import android.content.Context

enum class AuthMethod(val storageKey: String) {
    CHATGPT("chatgpt"),
    API_KEY("api_key"),
}

sealed interface AuthCredential {
    data class ChatGpt(val session: OAuthSession) : AuthCredential
    data class ApiKey(val key: String) : AuthCredential
}

object AuthManager {
    fun activeMethod(context: Context): AuthMethod? {
        val applicationContext = context.applicationContext
        val preferred = applicationContext
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_METHOD, null)
            ?.let { value -> AuthMethod.entries.firstOrNull { it.storageKey == value } }
        if (preferred == AuthMethod.API_KEY && ApiKeyStore(applicationContext).load() != null) {
            return AuthMethod.API_KEY
        }
        if (preferred == AuthMethod.CHATGPT && OAuthManager.loadSession(applicationContext) != null) {
            return AuthMethod.CHATGPT
        }
        return when {
            OAuthManager.loadSession(applicationContext) != null -> AuthMethod.CHATGPT
            ApiKeyStore(applicationContext).load() != null -> AuthMethod.API_KEY
            else -> null
        }
    }

    fun isAuthenticated(context: Context): Boolean = activeMethod(context) != null

    fun selectChatGpt(context: Context) = select(context, AuthMethod.CHATGPT)

    fun saveApiKey(context: Context, apiKey: String) {
        ApiKeyStore(context).save(apiKey)
        select(context, AuthMethod.API_KEY)
    }

    suspend fun credential(context: Context, forceRefresh: Boolean = false): AuthCredential =
        when (activeMethod(context)) {
            AuthMethod.CHATGPT -> AuthCredential.ChatGpt(
                OAuthManager.validSession(context, forceRefresh),
            )
            AuthMethod.API_KEY -> AuthCredential.ApiKey(
                ApiKeyStore(context).load() ?: error("API key is not available"),
            )
            null -> error("Authentication is required")
        }

    fun clearActive(context: Context) {
        when (activeMethod(context)) {
            AuthMethod.CHATGPT -> OAuthManager.clearSession(context)
            AuthMethod.API_KEY -> ApiKeyStore(context).clear()
            null -> Unit
        }
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACTIVE_METHOD)
            .apply()
    }

    private fun select(context: Context, method: AuthMethod) {
        check(
            context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACTIVE_METHOD, method.storageKey)
                .commit(),
        ) { "Failed to select authentication method" }
    }

    private const val FILE_NAME = "codexr_auth"
    private const val KEY_ACTIVE_METHOD = "active_method"
}
