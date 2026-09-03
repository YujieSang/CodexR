package com.example.codexmobile

import android.app.Application
import com.example.codexmobile.api.AIClient
import com.topjohnwu.superuser.Shell
import com.example.codexmobile.ui.ChatViewModel

class CodexApplication : Application() {
    private var controller: ChatViewModel? = null
    val chatController: ChatViewModel
        get() = controller ?: ChatViewModel(this).also { controller = it }

    fun resetChatController() {
        controller?.close()
        controller = null
    }

    companion object {
        init {
            Shell.enableVerboseLogging = true
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setTimeout(10)
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        AIClient.initialize(this)
    }
}
