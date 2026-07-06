package com.example.codexmobile

import android.content.Context

enum class ShellAccessMode {
    APPROVAL_REQUIRED,
    FULL_ACCESS,
}

class ShellAccessPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "shell_access_control",
        Context.MODE_PRIVATE,
    )

    fun load(): ShellAccessMode = runCatching {
        ShellAccessMode.valueOf(
            preferences.getString(KEY_MODE, ShellAccessMode.APPROVAL_REQUIRED.name)!!,
        )
    }.getOrDefault(ShellAccessMode.APPROVAL_REQUIRED)

    fun save(mode: ShellAccessMode) {
        preferences.edit().putString(KEY_MODE, mode.name).apply()
    }

    private companion object {
        const val KEY_MODE = "mode"
    }
}
