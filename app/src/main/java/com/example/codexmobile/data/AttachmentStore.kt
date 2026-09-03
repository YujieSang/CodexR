package com.example.codexmobile.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.example.codexmobile.api.MessageAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/** Copies selected content into private storage, so history survives revoked picker permissions. */
class AttachmentStore(private val context: Context) {
    val directory = File(context.filesDir, "attachments").apply { mkdirs() }

    suspend fun import(uri: Uri): List<MessageAttachment> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "attachment"
        val mime = resolver.getType(uri).orEmpty()
        val source = File(directory, UUID.randomUUID().toString())
        val created = mutableListOf<File>()
        try {
            resolver.openInputStream(uri)?.use { input ->
                source.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_IMPORT_BYTES) { "Files must be 10 MB or smaller." }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Cannot open $name")
            require(source.length() > 0) { "$name is empty." }
            when {
                mime == "application/pdf" || name.endsWith(".pdf", true) -> {
                    ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { pdf ->
                            require(pdf.pageCount in 1..MAX_ATTACHMENTS) {
                                "PDFs may contain at most $MAX_ATTACHMENTS pages. Split this PDF first."
                            }
                            (0 until pdf.pageCount).map { index ->
                                coroutineContext.ensureActive()
                                pdf.openPage(index).use { page ->
                                    val scale = IMAGE_EDGE.toFloat() / maxOf(page.width, page.height)
                                    val bitmap = Bitmap.createBitmap(
                                        (page.width * scale).roundToInt().coerceAtLeast(1),
                                        (page.height * scale).roundToInt().coerceAtLeast(1),
                                        Bitmap.Config.ARGB_8888,
                                    )
                                    try {
                                        bitmap.eraseColor(Color.WHITE)
                                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        saveImage(bitmap, "$name — page ${index + 1}", created)
                                    } finally { bitmap.recycle() }
                                }
                            }
                        }
                    }
                }
                mime.startsWith("image/") || name.substringAfterLast('.', "").lowercase() in
                    setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp") -> {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(source.path, bounds)
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image: $name" }
                    val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                    while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > IMAGE_EDGE * 2) {
                        options.inSampleSize *= 2
                    }
                    val bitmap = BitmapFactory.decodeFile(source.path, options) ?: error("Cannot decode $name")
                    try {
                        val scale = (IMAGE_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
                        val scaled = Bitmap.createScaledBitmap(bitmap,
                            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                            (bitmap.height * scale).roundToInt().coerceAtLeast(1), true)
                        try { listOf(saveImage(scaled, name, created)) }
                        finally { if (scaled !== bitmap) scaled.recycle() }
                    } finally { bitmap.recycle() }
                }
                else -> {
                    require(source.length() <= MAX_TEXT_BYTES) {
                        "Text/code files must be 256 KB or smaller. Binary formats other than images/PDF are not supported."
                    }
                    val bytes = source.readBytes()
                    require(bytes.none { it == 0.toByte() }) { "Unsupported binary file. Attach a PDF, image, or UTF-8 text/code file." }
                    runCatching {
                        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes))
                    }.getOrElse { error("$name is not UTF-8 text. Export it as text or PDF first.") }
                    val target = File(directory, "${UUID.randomUUID()}.txt")
                    created += target
                    check(source.renameTo(target)) { "Cannot save attachment" }
                    listOf(MessageAttachment(name = name, mimeType = "text/plain", localPath = target.path, sizeBytes = target.length()))
                }
            }
        } catch (error: Exception) {
            created.forEach { it.delete() }
            throw error
        } finally { source.delete() }
    }

    private fun saveImage(bitmap: Bitmap, name: String, created: MutableList<File>): MessageAttachment {
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        created += file
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        return MessageAttachment(name = name, mimeType = "image/jpeg", localPath = file.path, sizeBytes = file.length())
    }

    fun delete(attachment: MessageAttachment) {
        val file = File(attachment.localPath)
        if (file.canonicalFile.parentFile == directory.canonicalFile) file.delete()
    }

    companion object {
        const val MAX_ATTACHMENTS = 8
        const val MAX_IMPORT_BYTES = 10L * 1024 * 1024
        const val MAX_TEXT_BYTES = 256L * 1024
        private const val IMAGE_EDGE = 1600
    }
}
