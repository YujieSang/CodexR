package com.example.codexmobile.theme

import android.content.Context

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

class ThemePreferences(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): ThemeMode = preferences.getString(KEY_MODE, null)
        ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
        ?: ThemeMode.SYSTEM

    fun save(mode: ThemeMode) {
        preferences.edit().putString(KEY_MODE, mode.name).apply()
    }

    private companion object {
        const val FILE_NAME = "codexr_appearance"
        const val KEY_MODE = "theme_mode"
    }
}
