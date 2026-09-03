package com.example.codexmobile

import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

class ShellManagerTest {
    @Test fun `drains both small and capped output without losing exit data`() {
        assertEquals("hello\nworld", ShellManager.drain(StringReader("hello\nworld")))
        val output = ShellManager.drain(StringReader("a".repeat(100_000)))
        assertTrue(output.startsWith("a".repeat(65_536)))
        assertTrue(output.contains("truncated"))
        assertTrue(output.length < 66_000)
    }
    @Test fun `shell quotes preserve literal apostrophes and substitutions`() {
        assertEquals("'echo '\"'\"'hello'\"'\"'; \$(id)'", ShellManager.quote("echo 'hello'; \$(id)"))
    }
}
