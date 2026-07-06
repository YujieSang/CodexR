package com.example.codexmobile.api

import kotlinx.serialization.Serializable

private const val REFRESH_SKEW_MS = 60_000L

@Serializable
data class OAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val accountId: String,
) {
    fun needsRefresh(now: Long = System.currentTimeMillis()): Boolean =
        expiresAt <= now + REFRESH_SKEW_MS
}
