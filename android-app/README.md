# MobileClaw Android Host App

This module hosts the MobileClaw app surface (ZeroClaw Rust core) on Android and provides a native UI for:

- Chat with the agent
- LLM and integration settings
- Device capability controls (app launch, sensors)
- Optional telephony/SMS flows for enterprise/full distribution

## Flavors

- `play`: Google Play-compatible baseline, SMS/call features disabled
- `full`: Enterprise/sideload distribution with optional SMS/call permissions

## Build

```bash
cd android-app
./gradlew assemblePlayDebug || gradle assemblePlayDebug
./gradlew assembleFullDebug || gradle assembleFullDebug
```

## Instrumentation tests

```bash
cd android-app
./gradlew connectedPlayDebugAndroidTest || gradle connectedPlayDebugAndroidTest
```

## Ollama local setup (emulator)

The app chat screen targets Ollama at `http://10.0.2.2:11434` by default and uses model `gpt-oss:20b`.

- Start Ollama on your host: `ollama serve`
- Ensure model exists: `ollama pull gpt-oss:20b`
- Send a chat message from emulator; status shows `Ollama live connection OK` on success.

## OpenRouter setup from app UI

1. Open `LLM` tab.
2. Set `Provider` to `openrouter`.
3. Set `Model` (for example `openai/gpt-5-mini` or your preferred OpenRouter model id).
4. Set `Endpoint` to `https://openrouter.ai/api/v1`.
5. Paste your token into `API token` and press `Save`.

## Provider credential modes

- OpenAI: API key mode, plus experimental ChatGPT Codex OAuth-token mode.
- OpenRouter: API key mode.
- Anthropic: API key mode and setup-token OAuth mode.
- Gemini: API key mode, plus OAuth access-token mode for enterprise/GCP-style deployments.
- GitHub Copilot: bearer token mode (GitHub token from Copilot-enabled account/org policy).

Important: consumer subscriptions (ChatGPT Plus / Gemini Advanced / Claude Pro) do not directly authenticate API calls. Provider APIs still require API credentials or supported OAuth tokens.

OpenCode compatibility note:
- OpenCode implements dedicated OAuth plugin flows for ChatGPT/Codex and GitHub Copilot. MobileClaw now implements equivalent in-app initiated browser/device OAuth flows for those providers.

## In-app OAuth flow

MobileClaw now supports in-app initiated OAuth device flows for:

- OpenAI subscription/Codex path (`openai` + `OAuth access token`)
- GitHub Copilot (`copilot` + `OAuth access token`)

From `LLM` screen:
1. Select provider.
2. Select `Credential type = OAuth Access Token`.
3. Press `Connect OAuth in browser`.
4. Enter displayed code on opened verification page.
5. App polls and stores token in encrypted storage on success.

## Rust bridge

The app uses a single primary bridge path (`NativeZeroClawBridge`) with no fallback chain.
