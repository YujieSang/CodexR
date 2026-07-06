package com.example.codexmobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShellManagerTest {
    @Test
    fun capturesStdoutStderrAndExitCodeFromRootShell() = runBlocking {
        val result = ShellManager.executeRootCommand(
            "(printf 'stdout-line\\n'; printf 'stderr-line\\n' >&2; exit 7)",
        )

        assertTrue(result.stdout.contains("stdout-line"))
        assertTrue(result.stderr.contains("stderr-line"))
        assertEquals(7, result.exitCode)
    }
}
