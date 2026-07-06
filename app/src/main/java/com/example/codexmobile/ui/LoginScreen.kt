package com.example.codexmobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.codexmobile.R
import com.example.codexmobile.api.AIClient
import com.example.codexmobile.api.AuthManager
import com.example.codexmobile.api.OAuthManager
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoggingIn by remember { mutableStateOf(false) }
    var isApiKeyLogin by remember { mutableStateOf(false) }
    var manualRedirect by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.codexr_logo),
            contentDescription = "CodexR logo",
            modifier = Modifier.height(104.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("CodexR", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Use your ChatGPT Codex subscription or an OpenAI Platform API key.",
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (isLoggingIn) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (isApiKeyLogin) "Validating API key..." else "Complete sign-in in the browser.",
                textAlign = TextAlign.Center,
            )
            if (!isApiKeyLogin) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = manualRedirect,
                    onValueChange = { manualRedirect = it },
                    label = { Text("Callback URL (fallback)") },
                    supportingText = {
                        Text("If localhost does not return automatically, paste the complete final URL here.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (OAuthManager.submitManualRedirect(manualRedirect)) {
                            manualRedirect = ""
                        }
                    },
                    enabled = manualRedirect.isNotBlank(),
                ) {
                    Text("Submit callback URL")
                }
            }
        } else {
            Button(onClick = {
                isApiKeyLogin = false
                isLoggingIn = true
                errorMessage = null
                coroutineScope.launch {
                    runCatching { OAuthManager.login(context) }
                        .onSuccess {
                            AuthManager.selectChatGpt(context)
                            onLoginSuccess()
                        }
                        .onFailure { errorMessage = it.message ?: "Authentication failed" }
                    isLoggingIn = false
                }
            }) {
                Text("Sign in with ChatGPT")
            }
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("OpenAI API key") },
                supportingText = { Text("Stored encrypted in Android Keystore") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    isApiKeyLogin = true
                    isLoggingIn = true
                    errorMessage = null
                    coroutineScope.launch {
                        runCatching {
                            AIClient.validateApiKey(apiKey)
                            AuthManager.saveApiKey(context, apiKey)
                        }
                            .onSuccess {
                                apiKey = ""
                                onLoginSuccess()
                            }
                            .onFailure { errorMessage = it.message ?: "API key validation failed" }
                        isLoggingIn = false
                    }
                },
                enabled = apiKey.isNotBlank(),
            ) {
                Text("Use API key")
            }
        }

        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}
