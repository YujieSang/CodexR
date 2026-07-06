package com.example.codexmobile.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ChatSessionStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir, "chat_sessions.json"))
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun load(): ChatSessionState? = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists()) return@withContext null
        runCatching {
            file.openRead().bufferedReader().use { reader ->
                json.decodeFromString<ChatSessionState>(reader.readText())
            }
        }.getOrNull()
    }

    suspend fun save(state: ChatSessionState) = withContext(Dispatchers.IO) {
        val stream = file.startWrite()
        try {
            stream.write(json.encodeToString(state).toByteArray(Charsets.UTF_8))
            stream.flush()
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
