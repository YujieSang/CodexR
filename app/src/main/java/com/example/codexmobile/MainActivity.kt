package com.example.codexmobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.codexmobile.api.AuthManager
import com.example.codexmobile.api.OAuthManager
import com.example.codexmobile.ui.ChatScreen
import com.example.codexmobile.ui.LoginScreen
import com.example.codexmobile.theme.CodexMobileTheme
import com.example.codexmobile.theme.ThemePreferences

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeMode by remember {
                mutableStateOf(ThemePreferences(this@MainActivity).load())
            }
            CodexMobileTheme(themeMode = themeMode) {
                var isLoggedIn by remember {
                    mutableStateOf(AuthManager.isAuthenticated(this@MainActivity))
                }

                if (isLoggedIn) {
                    ChatScreen(
                        onLogout = {
                            AuthManager.clearActive(this@MainActivity)
                            isLoggedIn = false
                        },
                        themeMode = themeMode,
                        onThemeModeChanged = { mode ->
                            ThemePreferences(this@MainActivity).save(mode)
                            themeMode = mode
                        },
                    )
                } else {
                    LoginScreen(
                        onLoginSuccess = { isLoggedIn = true },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        OAuthManager.onAppForegroundChanged(true)
    }

    override fun onPause() {
        OAuthManager.onAppForegroundChanged(false)
        super.onPause()
    }
}
