<p align="center">
  <img src="app/src/main/res/drawable-nodpi/codexr_logo.png" width="128" alt="CodexR logo" />
</p>

<h1 align="center">CodexR</h1>

<p align="center">
  An unofficial Android client for running Codex with a controlled root shell.
</p>

CodexR is a personal, open-source Android app for using Codex directly on a rooted device. It supports ChatGPT OAuth and OpenAI Platform API keys, persistent chat sessions, live model selection, reasoning controls, usage visibility, and root command execution with an approval boundary.

> [!WARNING]
> CodexR can execute commands as `root`. A command can modify or erase system data, weaken device security, or make the device unbootable. Keep approval-required mode enabled unless you fully understand and accept the consequences of unattended root execution.

CodexR is an independent project. It is not an official OpenAI product and is not affiliated with or endorsed by OpenAI.

## Features

- ChatGPT OAuth with PKCE, state validation, token refresh, and encrypted session storage
- OpenAI Platform API-key authentication through the Responses API
- API keys encrypted using Android Keystore
- Live model catalog with model-specific reasoning-effort controls
- Multiple persistent chat sessions with per-chat model settings
- Root shell execution through [libsu](https://github.com/topjohnwu/libsu)
- Captured `stdout`, `stderr`, and exit codes returned to the model
- Approval-required root mode for reviewing each requested command
- Optional full-access mode for automatic root command execution
- A per-turn automatic-command limit to reduce runaway command loops
- ChatGPT Codex usage windows, remaining percentages, and reset times
- System, light, and dark themes

## Authentication

CodexR supports two separate authentication paths.

### ChatGPT

Sign in through the browser using ChatGPT OAuth. This mode uses the Codex access available to the signed-in ChatGPT account and can display Codex usage windows and remaining capacity.

The ChatGPT path communicates with ChatGPT's Codex backend. It is intended for personal use and may require maintenance if that backend changes.

### OpenAI API key

Enter an OpenAI Platform API key on the sign-in screen. CodexR validates the key, stores it encrypted, loads available GPT-5 model aliases, and sends requests through the public Responses API.

ChatGPT subscriptions and OpenAI API billing are separate. API-key spend and limits are shown in the [OpenAI Platform usage dashboard](https://platform.openai.com/usage), not the ChatGPT Codex usage panel.

Never commit an API key to this repository or paste one into an issue.

## Root access modes

### Approval required

This is the default and recommended mode. CodexR displays the requested shell command and waits for explicit approval before running it as root. A denied command and optional reason are returned to the model.

### Full access

Full-access mode runs model-requested commands immediately as root. The setting persists until disabled. CodexR limits automatic execution to 20 commands per user turn, but this is only a runaway-loop guard; it is not a security sandbox.

## Requirements

- Android 7.0 or newer (`minSdk 24`)
- A rooted device with a working `su` implementation
- Android SDK 36 for building
- JDK 17
- Internet access for authentication and model requests
- A ChatGPT account with Codex access or an OpenAI Platform API key

## Build from source

Clone the repository:

```bash
git clone https://github.com/YujieSang/CodexR.git
cd CodexR
```

Build the debug APK on macOS or Linux:

```bash
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install

With Android Debug Bridge connected to the device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch CodexR, grant its root request when prompted, and select either ChatGPT sign-in or API-key access.

## Development checks

Run unit tests:

```bash
./gradlew testDebugUnitTest
```

Run Android lint:

```bash
./gradlew lintDebug
```

Build and verify the debug APK:

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Instrumented tests require a connected Android device. Authentication-related instrumented tests may interact with app credential state, so use a test account or back up the app data first.

## Project structure

```text
app/src/main/java/com/example/codexmobile/
|-- api/       OAuth, API-key storage, model requests, and usage requests
|-- data/      Persistent chat sessions and cached model catalog
|-- theme/     Theme configuration and persisted appearance preference
|-- ui/        Sign-in, chat, session drawer, model, usage, and root controls
|-- ShellManager.kt
`-- MainActivity.kt
```

Important components:

- `AIClient` routes requests to the ChatGPT Codex backend or public Responses API.
- `OAuthManager` implements the browser-based PKCE flow and token refresh.
- `AuthManager` selects the active ChatGPT or API-key credential.
- `ShellManager` executes root commands and captures all process output.
- `ChatViewModel` manages sessions, command approval, model settings, and usage state.

## Security notes

- OAuth tokens and API keys are encrypted at rest with AES-GCM keys held by Android Keystore.
- Chat history and model catalog data are stored in the app's private internal storage.
- Root access can bypass normal Android application isolation. Device compromise can weaken any app-level credential protection.
- Shell output is treated as untrusted data when returned to the model.
- Review generated commands carefully, especially commands that change partitions, permissions, networking, packages, boot configuration, or user data.
- Do not use CodexR on a device containing data you cannot restore.

## Known limitations

- The app currently processes one model response at a time.
- ChatGPT OAuth depends on Codex backend behavior that may change.
- API keys do not expose a simple "subscription remaining" value; API usage is managed through OpenAI Platform.
- Root availability and behavior vary across Magisk, KernelSU, APatch, and device ROMs.
- The app does not provide a security sandbox around full-access commands.

## License

CodexR is available under the [MIT License](LICENSE).
