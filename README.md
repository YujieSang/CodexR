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

## Download and quick start

**Current preview: [v1.1.0-beta.1](https://github.com/YujieSang/CodexR/releases/tag/v1.1.0-beta.1)** · [Download APK](https://github.com/YujieSang/CodexR/releases/download/v1.1.0-beta.1/CodexR-v1.1.0-beta.1.apk) · [Release notes](docs/releases/v1.1.0-beta.1.md)

1. Download the APK from the release page and install it on a rooted Android 7.0+ device. Allow installation from your browser or file manager when Android asks.
2. Open CodexR and sign in with ChatGPT or enter an OpenAI Platform API key.
3. Start a chat, choose a model and reasoning level, and review each requested root action. Grant CodexR access in your root manager when prompted.
4. Allow notifications for the background-work indicator and Stop action. For long screen-off tasks, open **Background execution / battery settings** in the sidebar and choose **Unrestricted** / **Don't optimize**.

> [!IMPORTANT]
> This is an experimental, non-debuggable preview APK signed with the maintainer's existing **development certificate**, not a dedicated production certificate. It can update earlier CodexR builds signed with that same certificate. A locally built APK usually has a different certificate and cannot update it in place. Do not uninstall just to resolve a signature mismatch: uninstalling removes local chats, attachments, and saved sign-in. The signing key is not included in this repository or the release.

The release includes `SHA256SUMS.txt` for download integrity checks. Android SDK and JDK are needed only to build from source, not to install the APK.

## Features

- ChatGPT OAuth with PKCE, state validation, token refresh, and encrypted session storage
- OpenAI Platform API-key authentication through the Responses API
- API keys encrypted using Android Keystore
- Live model catalog with model-specific reasoning-effort controls
- Multiple persistent chat sessions with per-chat model settings
- Isolated root shell execution with cancellable process groups
- Captured `stdout`, `stderr`, and exit codes returned to the model
- Approval-required root mode for reviewing each requested command
- Optional full-access mode for automatic root command execution
- Official-style structured `exec_command` calls with paired outputs and durable interruption state
- A per-turn automatic-command limit to reduce runaway command loops
- ChatGPT Codex usage windows, remaining percentages, and reset times
- System, light, and dark themes
- Selectable Markdown responses with tables, task lists, code blocks, and offline LaTeX rendering
- Streaming responses, Stop, error retry, and long-press editing of user messages
- Follow-ups queued during processing and delivered with the next tool result
- Background execution with a work notification, floating live-status overlay, Stop and follow-up controls, and screen-awake behavior
- Image, text/code, and PDF attachments with local previews
- An AI-callable `capture_screen` tool that returns the current display as an image
- Manual screenshot attachment with a countdown

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

Choose **Allow for this chat** in an approval dialog to approve subsequent root commands and AI screen captures for that chat. Use **Revoke** in the chat to restore individual approvals. This grant does not apply to other chats and is cleared when the app process restarts.

### Full access

Full-access mode runs model-requested commands immediately as root. The setting persists until disabled. CodexR limits automatic execution to 20 commands per user turn, but this is only a runaway-loop guard; it is not a security sandbox.

## Requirements

- Android 7.0 or newer (`minSdk 24`)
- A rooted device with a working `su` implementation
- Android's `setsid` utility (present on standard modern Android builds)
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

For the downloaded preview, with Android Debug Bridge connected to the device:

```bash
adb install -r CodexR-v1.1.0-beta.1.apk
```

For a local debug build:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch CodexR, grant its root request when prompted, and select either ChatGPT sign-in or API-key access.

### Release builds

The release build is non-debuggable and unsigned by default. Build it with:

```bash
./gradlew testDebugUnitTest lintRelease assembleRelease
```

On Windows, `scripts/package-preview.ps1` runs the checks, builds both variants, aligns the release APK, signs it with the existing local Android development key, verifies the result, and writes a SHA-256 checksum into the ignored `dist/` folder. It never uploads the key. Use a dedicated, securely backed-up signing key for production distribution; changing certificates affects upgrade compatibility.

```powershell
./scripts/package-preview.ps1
```

## Chat controls

- **Edit:** long-press one of your messages, or tap its pencil. Editing stops the current turn and replaces the conversation from that prompt onward after confirmation. Commands already executed are not undone.
- **Copy:** select response text directly, or use the copy icon to copy the original Markdown, including LaTeX source.
- **Stop:** the square button cancels the network request and signals the active root process group. The work notification also has a Stop action. You can then edit the last prompt or retry.
- **Retry:** after an error or interruption, Retry response continues from the saved conversation. It does not directly replay completed commands; the model may request additional commands under the selected approval policy.
- **Follow-up:** type and send while CodexR is processing. The message is queued until the next tool result, or the end of the current response if no tool is used. Queued messages can be edited or removed. They remain queued if you stop or encounter an error.

Shell actions are stored as Responses API function-call/function-output pairs, rather than inferred from ordinary Markdown. Stopping during approval records that the command did not run; stopping during execution records an interrupted result and warns the model to inspect device state before retrying. This keeps stop, retry, and follow-up behavior stable in long conversations.

### Markdown and math

Responses render headings, lists, tables, links, emphasis, code fences, and task lists. Math supports `$…$`, `$$…$$`, `\(…\)`, and `\[…\]`. Rendering is local; no external math-rendering service receives your messages.

### Attachments and screen capture

The paperclip menu accepts images, PDFs, and UTF-8 text/code files. Images are resized to a maximum edge of 1,600 pixels. PDFs are rendered into page images for compatibility with both authentication paths. Limits are 10 MB per imported file, 256 KB per text/code file, 8 attachments/PDF pages per message, and 24 MB of prepared attachments per conversation. Other binary formats are rejected with an explanation; export them to PDF or text first.

The AI can call **`capture_screen`** when it needs to inspect the device UI. The image is returned to its next response and shown in the chat. AI captures use the same approval policy as root commands; session/full access enables automatic capture. Screenshots may contain private information, so review what is visible before granting access. Protected windows may appear blank; the tool does not unlock the device.

Manual capture is also available in the paperclip menu. It waits three seconds so you can switch screens, then puts the screenshot in your draft. Nothing from a manual capture is sent until you press Send.

### Background execution

Active turns run independently of the activity, with a foreground-service notification and a partial wake lock. CodexR keeps its screen awake while processing, but does not prevent you from locking the device yourself. Screen-awake and CPU wake locks are released when work stops.

Enable **Floating work overlay** in the sidebar and grant Android's **Display over other apps** permission to see the latest partial response or execution status while another app is open. The draggable overlay has an immediate **Stop** button and an inline **Follow up** composer. Follow-ups use the same queue as the main chat and are delivered at the next tool boundary. The overlay is shown only during active background work, hides when CodexR returns to the foreground, and does not intentionally display over the lock screen.

Allow notification permission so Stop is readily accessible. Open **Background execution / battery settings** in the sidebar and set CodexR to **Unrestricted** / **Don't optimize** for reliable screen-off networking. Vendor power-saving settings may also need adjustment. Android deep sleep can restrict networking without an exemption, and Android 15+ limits `dataSync` foreground-service background time. See [Android's Doze guidance](https://developer.android.com/training/monitoring-device-state/doze-standby) and [foreground-service timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout).

If Android kills the process, CodexR records the interrupted turn on next launch. It never silently restarts a root command.

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

The feature update was verified with 29 local tests, three targeted device tests on a rooted Lenovo TB-J716F running Android 16, and live checks for Markdown/LaTeX selection, AI-requested screenshots, background completion, floating overlay controls, and queued follow-ups with the screen off. This does not establish compatibility with every device or root manager.

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
- `ChatRuntime` separates real network/shell effects from conversation logic for offline regression tests.
- `ExecutionService` maintains the active-work notification and wake lock.
- `ScreenCapture` and `AttachmentStore` prepare private, model-readable attachments.

## Security notes

- OAuth tokens and API keys are encrypted at rest with AES-GCM keys held by Android Keystore.
- Chat history and model catalog data are stored in the app's private internal storage.
- Attachments and screenshots are copied to private internal storage and sent to the selected model provider when included in a request. They are not encrypted separately from Android's device storage. Unreferenced attachment files may remain until app storage is cleared.
- Root access can bypass normal Android application isolation. Device compromise can weaken any app-level credential protection.
- Shell output is treated as untrusted data when returned to the model.
- Review generated commands carefully, especially commands that change partitions, permissions, networking, packages, boot configuration, or user data.
- Do not use CodexR on a device containing data you cannot restore.

## Known limitations

- The app currently processes one model response at a time.
- The published preview uses a development signing certificate; a production-signing migration may require extra upgrade steps.
- Stopping cannot undo changes already made. Processes that deliberately detach into a new session may escape process-group cancellation.
- Background execution cannot survive force-stop, revoked root/network access, or every vendor's power management policy.
- ChatGPT OAuth depends on Codex backend behavior that may change.
- API keys do not expose a simple "subscription remaining" value; API usage is managed through OpenAI Platform.
- Root availability and behavior vary across Magisk, KernelSU, APatch, and device ROMs.
- The app does not provide a security sandbox around full-access commands.

## License

CodexR is available under the [MIT License](LICENSE).
