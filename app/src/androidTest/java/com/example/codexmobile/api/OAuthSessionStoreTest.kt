package com.example.codexmobile.api

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OAuthSessionStoreTest {
    private val store = OAuthSessionStore(ApplicationProvider.getApplicationContext())
    private val existingSession = store.load()

    @After
    fun cleanUp() {
        existingSession?.let(store::save) ?: store.clear()
    }

    @Test
    fun encryptedSessionRoundTripsOnDevice() {
        val session = OAuthSession(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAt = 123_456_789L,
            accountId = "account-id",
        )

        store.save(session)

        assertEquals(session, store.load())
    }
}
