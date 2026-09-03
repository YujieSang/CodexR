package com.example.codexmobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import androidx.test.platform.app.InstrumentationRegistry
import com.example.codexmobile.data.AttachmentStore
import java.io.File
import java.util.UUID
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

        assertTrue("stdout capture: $result", result.stdout.contains("stdout-line"))
        assertTrue("stderr capture: $result", result.stderr.contains("stderr-line"))
        assertEquals(7, result.exitCode)
    }

    @Test
    fun stopKillsCommandChildrenWithoutKillingIndependentShells() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val marker = File(context.cacheDir, "cancel-check-${UUID.randomUUID()}")
        val ready = File(context.cacheDir, "ready-check-${UUID.randomUUID()}")
        try {
            val job = launch {
                ShellManager.executeRootCommand("echo ready > ${ShellManager.quote(ready.path)}; (sleep 2; echo escaped > ${ShellManager.quote(marker.path)}) & wait")
            }
            withTimeout(5_000) { while (!ready.exists()) delay(25) }
            withTimeout(1_000) { job.cancelAndJoin() }
            assertEquals("independent", ShellManager.executeRootCommand("printf independent").stdout)
            delay(2_500)
            assertTrue("Stopped child must not produce delayed output", !marker.exists())
        } finally { marker.delete(); ready.delete() }
    }

    @Test
    fun capturedScreenshotIsReadableByTheApp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val attachments = ScreenCapture.capture(context)
        try {
            assertEquals(1, attachments.size)
            assertTrue(attachments.single().mimeType.startsWith("image/"))
            assertTrue(File(attachments.single().localPath).length() > 0)
        } finally { attachments.forEach(AttachmentStore(context)::delete) }
    }
}
