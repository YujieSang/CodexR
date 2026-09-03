package com.example.codexmobile.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.codexmobile.api.MessageAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AttachmentStrip(attachments: List<MessageAttachment>, onRemove: ((String) -> Unit)? = null) {
    if (attachments.isEmpty()) return
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        attachments.forEach { attachment ->
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.padding(6.dp)) {
                    Column(Modifier.widthIn(max = 160.dp)) {
                        if (attachment.mimeType.startsWith("image/")) {
                            val thumbnail by produceState<ImageBitmap?>(null, attachment.localPath) {
                                value = withContext(Dispatchers.IO) {
                                    runCatching { BitmapFactory.decodeFile(attachment.localPath,
                                        BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() }.getOrNull()
                                }
                            }
                            thumbnail?.let { Image(it, "Preview: ${attachment.name}", Modifier.size(110.dp, 72.dp), contentScale = ContentScale.Fit) }
                        }
                        Text(attachment.name, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                    }
                    onRemove?.let { remove ->
                        IconButton(onClick = { remove(attachment.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, "Remove ${attachment.name}", Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
