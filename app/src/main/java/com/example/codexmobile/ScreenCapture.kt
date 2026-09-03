package com.example.codexmobile

import android.content.Context
import android.net.Uri
import com.example.codexmobile.api.MessageAttachment
import com.example.codexmobile.data.AttachmentStore
import java.io.File
import java.util.UUID

object ScreenCapture {
    /** Uses the current display; never unlocks the device or bypasses protected windows. */
    suspend fun capture(context: Context): List<MessageAttachment> {
        val capture = File(context.cacheDir, "screenshot-${UUID.randomUUID()}.png")
        try {
            val path = ShellManager.quote(capture.path)
            val uid = android.os.Process.myUid()
            val result = ShellManager.executeRootCommand("screencap -p $path && chown $uid:$uid $path && chmod 600 $path")
            require(result.exitCode == 0) { "Screen capture failed: ${result.stderr}" }
            return AttachmentStore(context).import(Uri.fromFile(capture)).map { it.copy(name = "Screen capture") }
        } finally { capture.delete() }
    }
}
