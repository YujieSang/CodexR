package com.example.codexmobile.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthSessionTest {
    @Test
    fun `refreshes before the access token expires`() {
        val now = 1_000_000L
        val fresh = OAuthSession("access", "refresh", now + 120_000L, "account")
        val nearExpiry = fresh.copy(expiresAt = now + 30_000L)

        assertFalse(fresh.needsRefresh(now))
        assertTrue(nearExpiry.needsRefresh(now))
    }
}
