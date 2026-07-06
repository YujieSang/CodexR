package com.example.codexmobile

import android.app.Application
import com.example.codexmobile.api.AIClient
import com.topjohnwu.superuser.Shell

class CodexApplication : Application() {
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
