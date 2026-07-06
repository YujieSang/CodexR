# CodexR

CodexR is a personal Android client for using Codex on a rooted device. It combines ChatGPT OAuth or OpenAI API-key access with a root shell, explicit command approval, persistent chat sessions, model selection, reasoning controls, usage visibility, and full-access mode.

## Features

- ChatGPT OAuth with PKCE and encrypted token storage
- Optional OpenAI API key stored through Android Keystore
- Live Codex model catalog and model-specific reasoning controls
- Multiple persistent chat sessions
- Root command execution with stdout, stderr, and exit-code reporting
- Approval-required and full-access root modes
- ChatGPT Codex usage windows and reset times
- System, light, and dark themes

## Build

Requirements:

- Android SDK 36
- JDK 17
- A rooted Android device for shell execution

Build the debug APK:

```powershell
.\gradlew.bat assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Security

Full-access mode allows model-requested commands to run immediately as root. Keep approval-required mode enabled unless unrestricted automated root execution is intentional. OAuth credentials and API keys are encrypted with keys held by Android Keystore.
