package com.example.codexmobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionOverlayTest {
    @Test fun `overlay message is compact and whitespace normalized`() {
        assertEquals("Latest partial response", compactOverlayMessage("  Latest\n partial   response  "))
        assertEquals("Processing…", compactOverlayMessage(" \n "))
    }

    @Test fun `overlay message is bounded`() {
        val value = compactOverlayMessage("a".repeat(1_000), limit = 20)
        assertEquals(20, value.length)
        assertTrue(value.startsWith("…"))
    }
}
