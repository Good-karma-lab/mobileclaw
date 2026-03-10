# Guappa Implementation Checklist — Plan vs Reality

**Date**: 2026-03-09
**Purpose**: Map every planned feature to actual implementation status, identify gaps, and define E2E test coverage.

Legend:
- ✅ Implemented — code exists and appears functional
- ⚠️ Partial — code exists but incomplete or missing key features
- ❌ Missing — no implementation found
- 🔧 Stub — file exists but minimal/skeleton implementation

---

## Phase 1: Foundation — Agent Core

| # | Feature | Status | Evidence | Gap |
|---|---------|--------|----------|-----|
| 1.1 | GuappaOrchestrator (ReAct loop) | ✅ | `agent/GuappaOrchestrator.kt` (516 lines) | — |
| 1.2 | GuappaSession (conversation state) | ⚠️ | `agent/GuappaSession.kt` (67 lines) | Very thin — no session type enum (CHAT/BACKGROUND_TASK/TRIGGER/SYSTEM), no TTL, no checkpoint/recovery |
| 1.3 | GuappaPlanner (ReAct, task decomposition) | ✅ | `agent/GuappaPlanner.kt` (356 lines) | — |
| 1.4 | MessageBus (SharedFlow pub/sub) | ✅ | `agent/MessageBus.kt` (74 lines) | No priority queue for urgent events as planned |
| 1.5 | TaskManager | ✅ | `agent/TaskManager.kt` (262 lines) | — |
| 1.6 | GuappaConfig | ✅ | `agent/GuappaConfig.kt` | — |
| 1.7 | GuappaPersona (system prompt, personality) | ✅ | `agent/GuappaPersona.kt` | — |
| 1.8 | Foreground service (DATA_SYNC) | ✅ | `RuntimeAlwaysOnService.kt` (48 lines) + `GuappaAgentService.kt` (129 lines) | Both exist — verify no duplication |
| 1.9 | Boot receiver (auto-start) | ✅ | `RuntimeBootReceiver.kt` (24 lines), manifest registered | — |
| 1.10 | Room database (sessions, messages, tasks) | ✅ | `memory/GuappaDatabase.kt`, `Entities.kt`, `Daos.kt` | Full schema with sessions, messages, tasks, facts, episodes, embeddings |
| 1.11 | Context Manager / budget allocation | ✅ | `memory/ContextCompactor.kt` (343 lines) | — |
| 1.12 | Streaming responses | ✅ | All providers implement `streamChat()` | — |
| 1.13 | Retry with exponential backoff | ⚠️ | Logic in orchestrator | Verify fallback provider chain works |
| 1.14 | Multi-session concurrency | ⚠️ | Session entity exists | Unclear if concurrent CHAT + BACKGROUND_TASK tested |
| 1.15 | Session encryption (SQLCipher / Keystore) | ❌ | No encryption layer found | Plain Room database, no SQLCipher |
| 1.16 | Dependency injection (Hilt/Koin) | ❌ | No DI framework | Manual wiring via service classes |

---

## Phase 2: Provider Router — Dynamic Model Discovery

| # | Feature | Status | Evidence | Gap |
|---|---------|--------|----------|-----|
| 2.1 | ProviderRouter (capability-based routing) | ✅ | `providers/ProviderRouter.kt` (114 lines) | — |
| 2.2 | Provider interface | ✅ | `providers/Provider.kt` | `chat()`, `streamChat()`, `listModels()` |
| 2.3 | AnthropicProvider | ✅ | `providers/AnthropicProvider.kt` (310 lines) | Full implementation |
| 2.4 | OpenAI-compatible provider (base) | ✅ | `providers/OpenAICompatibleProvider.kt` (307 lines) | Covers OpenAI, OpenRouter, DeepSeek, Mistral, xAI, Groq, Together, etc. |
| 2.5 | Google Gemini provider | ✅ | `providers/GoogleGeminiProvider.kt` | — |
| 2.6 | Dynamic model fetching (list models API) | ✅ | `listModels()` in all providers | Verified in Anthropic, OpenAI-compat, Gemini |
| 2.7 | CapabilityInferrer (detect model capabilities) | ✅ | `providers/CapabilityInferrer.kt` | Infers from model ID patterns |
| 2.8 | CapabilityType enum | ✅ | `providers/CapabilityType.kt` | TEXT, VISION, IMAGE_GEN, etc. |
| 2.9 | CostTracker | ✅ | `providers/CostTracker.kt` | — |
| 2.10 | ProviderFactory (create from name) | ✅ | `providers/ProviderFactory.kt` | Covers 20+ provider endpoints via defaults |
| 2.11 | Local inference — text (llama.rn via GGUF) | ✅ | `localLlmServer.ts` uses `llama.rn` (initLlama) + NanoHTTPD proxy server | Full flow: download GGUF → load via llama.rn → expose as OpenAI-compat `/v1/chat/completions` on localhost:8888 |
| 2.12 | LiteRT-LM (Gemini Nano) | ❌ | Not found | — |
| 2.13 | Qualcomm GENIE (NPU) | ❌ | Not found | — |
| 2.14 | ONNX Runtime Mobile | ❌ | Not found | Only referenced in EmbeddingService |
| 2.15 | HardwareProbe (SoC/NPU detection) | ❌ | Not found | — |
| 2.16 | ModelDownloadManager | ✅ | `ModelDownloaderModule.kt` (91 lines) + `whisperModelManager.ts` + `modelDownloader.ts` | Downloads GGUF + Whisper models |
| 2.17 | Token counter (tiktoken) | ❌ | Not found | Token counting may be approximate |
| 2.18 | Separate model per capability (text/vision/image) | ⚠️ | ConfigStore has fields for this | UI supports it but routing needs verification |
| 2.19 | **OAuth — OpenAI Codex (ChatGPT Plus/Pro)** | ❌ | Only API key auth | Pi implements full OAuth: PKCE authorization code flow → local callback server on `:1455` → exchange code → JWT decode for `accountId` → auto-refresh. Client ID: `app_EMoamEEZ73f0CkXaXp7hrann`. Allows ChatGPT subscribers to use models without API key billing. |
| 2.20 | **OAuth — Anthropic (Claude Pro/Max)** | ❌ | Only API key auth | Pi implements PKCE flow: authorize at `claude.ai/oauth/authorize` → user pastes `code#state` → exchange at `console.anthropic.com/v1/oauth/token` → auto-refresh. Scopes: `org:create_api_key user:profile user:inference`. Allows Claude Pro/Max subscribers to use without API key. |
| 2.21 | **OAuth — GitHub Copilot** | ❌ | `copilot` endpoint exists in ProviderFactory but uses API key (won't work) | Pi implements GitHub device code flow (RFC 8628): `POST /login/device/code` → user enters code at `github.com/login/device` → poll `access_token` → exchange for Copilot token at `/copilot_internal/v2/token` → auto-refresh. Supports GitHub Enterprise (`company.ghe.com`). After login, enables all Copilot models via policy API. Dynamic `baseUrl` from proxy-ep in token. |
| 2.22 | **OAuth — Google Gemini CLI (Cloud Code Assist)** | ❌ | Only API key auth | Pi implements Google OAuth: PKCE + local callback server on `:8085` → exchange code → discover/provision Cloud project → auto-refresh. Supports free/standard/enterprise tiers. |
| 2.23 | **OAuth infrastructure (PKCE, token refresh, credential storage)** | ❌ | No OAuth code anywhere | **Required components** (from pi reference implementation): |

#### OAuth Implementation Plan (Based on Pi Architecture)

Pi's OAuth system (`@mariozechner/pi-ai/oauth`) provides a clean reference. The Guappa Android adaptation needs:

**1. Core OAuth Types** (`providers/oauth/OAuthTypes.kt`)
```
OAuthCredentials { refresh, access, expires, [extra fields] }
OAuthProviderInterface { id, name, login(), refreshToken(), getApiKey(), modifyModels?() }
OAuthLoginCallbacks { onAuth(url, instructions), onPrompt(message) → String, onProgress(msg) }
```

**2. PKCE Utility** (`providers/oauth/PKCE.kt`)
- Generate code verifier (32 random bytes → base64url)
- Compute SHA-256 challenge
- Use `java.security.MessageDigest` + `SecureRandom`

**3. Provider Implementations** (each ~100-300 lines):

| Provider | Flow Type | Key Details |
|----------|-----------|-------------|
| **OpenAI Codex** | Authorization Code + PKCE | Local HTTP server on device for callback; fallback to manual code paste. Extracts `accountId` from JWT. |
| **Anthropic** | Authorization Code + PKCE | User opens URL in browser, pastes `code#state` back. No local server needed. |
| **GitHub Copilot** | Device Code (RFC 8628) | Show user code → poll for token → exchange for Copilot token. Supports GH Enterprise. Auto-enables models via policy API. |
| **Google Gemini CLI** | Authorization Code + PKCE | Local HTTP server for callback. Discovers/provisions Cloud project. Supports free/enterprise tiers. |

**4. Android-Specific Adaptations:**
- Replace `http.createServer` with Android's NanoHTTPD (already in deps) for local OAuth callback
- Use Chrome Custom Tabs (`androidx.browser`) to open auth URLs (better UX than WebView)
- Store credentials encrypted via `SecurePrefs.kt` (Android Keystore)
- Background token refresh via `CoroutineScope` (not file locking as in CLI)
- In-app login UI: show URL + code, open browser, handle callback

**5. Credential Storage** (`providers/oauth/OAuthCredentialStore.kt`)
- Persist `OAuthCredentials` per provider in encrypted SharedPreferences
- Priority chain: OAuth token > API key > env var
- Auto-refresh on expiry with 5-min buffer
- Thread-safe refresh (mutex, not file lock)

**6. UI Integration:**
- Config screen: "Login with ChatGPT" / "Login with Claude" / "Login with Copilot" / "Login with Google" buttons
- Login flow: Chrome Custom Tab → callback → store credentials → show success
- Status indicator: "Authenticated via OAuth" vs "API Key"

---

## Phase 3: Tool Engine — 65+ Tools

| # | Feature | Status | Evidence | Gap |
|---|---------|--------|----------|-----|
| 3.1 | ToolEngine (registry, dispatch) | ✅ | `tools/ToolEngine.kt` (62 lines) | Thin dispatcher |
| 3.2 | ToolRegistry | ✅ | `tools/ToolRegistry.kt` (153 lines) | — |
| 3.3 | ToolResult (structured result) | ✅ | `tools/ToolResult.kt` | — |
| 3.4 | ToolPermissions (Android permission checking) | ✅ | `tools/ToolPermissions.kt` | — |
| 3.5 | ToolRateLimiter | ✅ | `tools/ToolRateLimiter.kt` | — |
| 3.6 | ToolAuditLog | ✅ | `tools/ToolAuditLog.kt` | — |
| 3.7 | Tool implementations | ✅ | **78 tools** in `tools/impl/` | Exceeds plan target of 65 |
| 3.8 | Device tools (SMS, call, contacts, calendar, camera, location, sensors) | ✅ | SendSmsTool, ReadSmsTool, PlaceCallTool, ReadCallLogTool, GetContactsTool, AddContactTool, CalendarTool, CameraTool, LocationTool, SensorTool, BatteryInfoTool | — |
| 3.9 | App tools (launch, list, alarm, timer, reminder, email, browser, maps, music) | ✅ | LaunchAppTool, ListAppsTool, SetAlarmTool, SetTimerTool, ReminderTool, EmailComposeTool, OpenBrowserTool, MapsTool, MusicControlTool | — |
| 3.10 | Web tools (web_fetch, web_search, web_scrape) | ✅ | WebFetchTool, WebSearchTool, WebScrapeTool, WebApiTool | — |
| 3.11 | File tools (read, write, search, list, delete) | ✅ | ReadFileTool, WriteFileTool, FileSearchTool, ListFilesTool, DeleteFileTool | — |
| 3.12 | Social tools (Twitter, Instagram, Telegram, WhatsApp) | ✅ | TwitterPostTool, InstagramShareTool, TelegramSendTool, WhatsAppSendTool, SocialShareTool | — |
| 3.13 | AI tools (image analyze, OCR, code interpreter, calculator, translation) | ✅ | ImageAnalyzeTool, OCRTool, CodeInterpreterTool, CalculatorTool, TranslationTool | — |
| 3.14 | System tools (shell, package info, system info) | ✅ | ShellTool, PackageInfoTool, SystemInfoTool, ProcessListTool | — |
| 3.15 | **Android AppFunctions API** | ❌ | No implementation | **NOW AVAILABLE** — `androidx.appfunctions:appfunctions-common:0.1.0-alpha01`. See [developer.android.com/ai/appfunctions](https://developer.android.com/ai/appfunctions), [AppFunctionsPilot](https://github.com/FilipFan/AppFunctionsPilot). Allows structured app-to-agent communication. Requires `AppFunctionManagerCompat` + declaring `AppFunctionService`. |
| 3.15b | Android UI Automation Framework | ❌ | `AgentAccessibilityService.kt` exists but uses accessibility, not new framework | Google-sanctioned agent automation API — still in preview |
| 3.16 | ScreenshotTool (MediaProjection) | ✅ | `ScreenshotTool.kt` | — |
| 3.17 | CronJobTool (scheduled execution) | ✅ | `CronJobTool.kt` (241 lines) | — |
| 3.18 | GeofenceTool | ✅ | `GeofenceTool.kt` (230 lines) | — |
| 3.19 | Additional tools (NFC, Bluetooth, QR, PDF, RSS, barcode, etc.) | ✅ | NFCReadTool, BluetoothTool, QRCodeTool, PdfReaderTool, RssReaderTool, BarcodeScanTool | — |
| 3.20 | Hook incoming call tool | ✅ | `RuntimeBridge.kt` → `hook_incoming_call`, `AndroidAgentToolsModule.kt` | Agent can register call hooks |
| 3.21 | Browser session tool (headless WebView) | ⚠️ | `AgentBrowserActivity.kt` exists | Not a headless browser session as planned — uses activity |

---

## Phase 4: Proactive Agent & Push Notifications

| # | Feature | Status | Evidence | Gap |
|---|---------|--------|----------|-----|
| 4.1 | ProactiveEngine (decision engine) | ✅ | `proactive/ProactiveEngine.kt` (118 lines) | — |
| 4.2 | GuappaNotificationManager | ✅ | `proactive/GuappaNotificationManager.kt` | — |
| 4.3 | NotificationChannels (IDs, importance) | ✅ | `proactive/NotificationChannels.kt` | — |
| 4.4 | TriggerManager (register trigger sources) | ✅ | `proactive/TriggerManager.kt` (274 lines) | — |
| 4.5 | IncomingCallReceiver | ✅ | `IncomingCallReceiver.kt` | Full: emits to RN + publishes to MessageBus |
| 4.6 | IncomingSmsReceiver | ✅ | `IncomingSmsReceiver.kt` | — |
| 4.7 | BatteryReceiver | ✅ | `proactive/BatteryReceiver.kt` | — |
| 4.8 | CalendarObserver | ✅ | `proactive/CalendarObserver.kt` | — |
| 4.9 | EventReactor | ✅ | `proactive/EventReactor.kt` | — |
| 4.10 | SmartTiming (DND, night) | ✅ | `proactive/SmartTiming.kt` | — |
| 4.11 | ProactiveRules (configurable rules) | ✅ | `proactive/ProactiveRules.kt` | — |
| 4.12 | TaskCompletionReporter | ✅ | `proactive/TaskCompletionReporter.kt` | — |
| 4.13 | NotificationActionReceiver (button clicks) | ✅ | `proactive/NotificationActionReceiver.kt` | — |
| 4.14 | NotificationDeduplicator | ✅ | `proactive/NotificationDeduplicator.kt` | — |
| 4.15 | NotificationHistory | ✅ | `proactive/NotificationHistory.kt` | — |
| 4.16 | MorningBriefingWorker | ✅ | `proactive/MorningBriefingWorker.kt` | — |
| 4.17 | DailySummaryWorker | ✅ | `proactive/DailySummaryWorker.kt` | — |
| 4.18 | MessagingStyle notifications (conversation bubbles) | ⚠️ | NotificationManager exists | Need to verify MessagingStyle is used |
| 4.19 | Inline reply from notification | ⚠️ | NotificationActionReceiver exists | Need to verify direct reply input |
| 4.20 | LocationGeofenceReceiver | ⚠️ | GeofenceTool exists | No dedicated broadcast receiver for geofence transitions |
| 4.21 | Network state receiver | ❌ | Not found | — |
| 4.22 | Screen state receiver | ❌ | Not found | — |

---

## Phase 5: Channel Hub — Messenger Integrations

| # | Feature | Status | Evidence | Gap |
|---|---------|--------|----------|-----|
| 5.1 | Channel interface | ✅ | `channels/Channel.kt` | `send()`, `healthCheck()`, `configure()`, `reset()` |
| 5.2 | ChannelHub (manage all channels) | ✅ | `channels/ChannelHub.kt` (153 lines) | — |
| 5.3 | ChannelFactory | ✅ | `channels/ChannelFactory.kt` (52 lines) | — |
| 5.4 | Telegram (send via Bot API) | ✅ | `channels/TelegramChannel.kt` (49 lines) | Send-only, uses `/sendMessage` |
| 5.5 | Telegram incoming (long polling) | ❌ | No `getUpdates` or webhook | Can't receive messages FROM Telegram |
| 5.6 | Discord (webhook send) | 🔧 | `channels/DiscordChannel.kt` (31 lines) | Webhook-only, no gateway WebSocket, no incoming |
| 5.7 | Discord gateway (incoming, slash commands) | ❌ | Not found | — |
| 5.8 | Slack (webhook send) | 🔧 | `channels/SlackChannel.kt` (31 lines) | Webhook-only, no Socket Mode, no incoming |
| 5.9 | WhatsApp | ⚠️ | `channels/WhatsAppChannel.kt` (92 lines) | Uses deep links, not Cloud API |
| 5.10 | Signal | 🔧 | `channels/SignalChannel.kt` (59 lines) | Need to verify if signald integration works |
| 5.11 | Matrix | ⚠️ | `channels/MatrixChannel.kt` (75 lines) | Exists but no E2EE, no sync loop |
| 5.12 | Email (IMAP/SMTP) | 🔧 | `channels/EmailChannel.kt` (26 lines) | Intent-based only — no IMAP receive, no SMTP send |
| 5.13 | SMS channel | ✅ | `channels/SmsChannel.kt` (47 lines) | Uses SmsManager |
| 5.14 | GuappaChannelsModule (RN bridge) | ✅ | `channels/GuappaChannelsModule.kt` (240 lines) | — |
| 5.15 | Channel `incoming()` Flow (receive messages) | ❌ | Not in Channel interface | Interface only has `send()` — no bidirectional |
| 5.16 | ChannelHealthMonitor (auto-reconnect) | ❌ | Not found | — |
| 5.17 | Channel formatters (Markdown adaptation) | ❌ | Not found | Each channel gets raw text |

---

## Phase 6: Voice Pipeline — STT, TTS, Wake Word

### Current Implementation

| # | Feature | Status | Evidence | Gap |
|---|---------|--------|----------|-----|
| 6.1 | useVoiceRecording (STT hook) | ✅ | `hooks/useVoiceRecording.ts` | Deepgram WebSocket + Whisper.rn |
| 6.2 | Deepgram STT (cloud, streaming) | ✅ | WebSocket to `api.deepgram.com` in hook | Uses nova-2 — should upgrade to nova-3 or flux |
| 6.3 | Whisper STT (on-device, via whisper.rn) | ✅ | Uses `whisper.rn` npm package + GGML model download | tiny/base/small variants |
| 6.4 | WhisperModelManager (download GGML models) | ✅ | `voice/whisperModelManager.ts` + `native/modelDownloader.ts` | — |
| 6.5 | useTTS (text-to-speech hook) | ✅ | `hooks/useTTS.ts` | Uses `expo-speech` → wraps Android's built-in `TextToSpeech` engine |
| 6.6 | useVAD (voice activity detection) | ✅ | `hooks/useVAD.ts` | Energy-based with configurable thresholds |
| 6.7 | useWakeWord ("Hey Guappa") | ✅ | `hooks/useWakeWord.ts` | Energy + STT keyword matching |
| 6.8 | VoiceAmplitude (for swarm visualization) | ✅ | `swarm/audio/VoiceAmplitude.ts` | — |
| 6.9 | VoiceScreen UI | ✅ | `screens/tabs/VoiceScreen.tsx` (338 lines) | Neural swarm + mic button |
| 6.10 | Streaming TTS (read LLM output real-time) | ⚠️ | useTTS has sentence-level queue | Sentence-level streaming works; not word-level |

### Android Built-In STT — Research & Gaps

Android provides **two built-in STT** paths. Neither is currently used by Guappa:

| # | Engine | Status | Details |
|---|--------|--------|---------|
| 6.11 | **`android.speech.SpeechRecognizer`** (on-device) | ❌ | Android's built-in speech recognizer. Uses Google's on-device speech model (auto-downloaded, ~50MB). **Zero API cost, zero latency for short commands.** Available on all Google Play devices. Supports `EXTRA_PREFER_OFFLINE` for fully offline use. Streaming via `onPartialResults()`. Languages: 50+. Quality: good for commands, weaker for long-form dictation vs Deepgram. |
| 6.12 | **Google ML Kit Speech Recognition** (GenAI) | ❌ | Newer on-device STT via ML Kit GenAI SDK (`com.google.mlkit:genai-speech`). No download needed — uses pre-installed model. Better accuracy than legacy SpeechRecognizer for modern devices (Pixel 6+, Samsung S22+). |
| 6.13 | **Google Cloud Speech-to-Text** (cloud) | ❌ | Best accuracy for noisy environments, 125+ languages, streaming. Requires API key + billing. |

**Recommendation**: Implement `SpeechRecognizer` as zero-cost fallback when Deepgram key is not configured. Use `EXTRA_PREFER_OFFLINE=true` for fully on-device. This gives voice mode to ALL users without any API key.

### Android Built-In TTS — Research & Gaps

| # | Engine | Status | Details |
|---|--------|--------|---------|
| 6.14 | **Android `TextToSpeech`** (built-in) | ✅ (via expo-speech) | `expo-speech` wraps `android.speech.tts.TextToSpeech`. Already works. Supports all device-installed voices (Google, Samsung, etc.). Quality varies by device — Pixel/Samsung have neural voices. |
| 6.15 | **Picovoice Orca TTS** (on-device, <50ms) | ❌ | Planned as PRIMARY. Commercial license. Sub-50ms latency, streaming. |
| 6.16 | **Kokoro TTS** (on-device, Apache 2.0) | ❌ | 82M params, best open-source quality/size ratio. ONNX-based, runs on Android CPU. |
| 6.17 | **Piper TTS** (on-device, MIT) | ❌ | 100+ voices, ONNX. Good quality, lightweight (~15MB per voice model). |
| 6.18 | **ElevenLabs TTS** (cloud) | ❌ | Ultra-realistic. Requires API key + billing. |
| 6.19 | **OpenAI TTS** (cloud) | ❌ | 6 voices, streaming via API. |
| 6.20 | **Speechmatics TTS** (cloud) | ❌ | 27x cheaper than ElevenLabs, streaming. |
| 6.21 | **Google Cloud TTS** (cloud) | ❌ | 380+ voices, Neural2/Studio/Chirp3-HD quality. |

**Recommendation**: Current expo-speech (Android TextToSpeech) is adequate for MVP. Prioritize Kokoro or Piper for higher-quality on-device TTS without commercial licensing.

### Voice Infrastructure Gaps

| # | Feature | Status | Notes |
|---|---------|--------|-------|
| 6.22 | Picovoice Porcupine wake word | ❌ | Using custom energy-based approach instead |
| 6.23 | Silero VAD | ❌ | Using expo-av metering-based approach (less accurate) |
| 6.24 | Audio routing (speaker/earpiece/BT) | ❌ | — |
| 6.25 | Audio focus management | ❌ | — |
| 6.26 | Bluetooth SCO/A2DP routing | ❌ | — |
| 6.27 | STT/TTS engine selection UI (auto-select best) | ❌ | Hardcoded to Deepgram/Whisper + expo-speech |
| 6.28 | **`SpeechRecognizer` as free STT fallback** | ❌ | **HIGH PRIORITY** — enables voice mode without any API key |

### Deepgram Full Product Catalog

Guappa currently uses Deepgram for STT only (nova-2 via WebSocket). Deepgram offers a comprehensive voice AI platform with STT, TTS, and audio intelligence — all accessible with the **same API key** already configured.

#### Deepgram STT Models

| # | Model | Status | Description | Languages | Guappa Status |
|---|-------|--------|-------------|-----------|---------------|
| 6.30 | **Flux** (`flux-general-en`) | 🆕 | **Voice agent STT** — first model with built-in end-of-turn detection. Ultra-low latency, knows when to listen/think/speak. Designed for real-time conversational agents. | English only | ❌ Not integrated — **P0 for voice agent mode** |
| 6.31 | **Nova-3** (`nova-3`) | 🆕 | Best general ASR — 54% WER reduction vs competitors. Real-time multilingual, self-serve vocabulary customization. | 50+ languages (multi, ar, bn, cs, da, de, el, en, es, et, fa, fi, fr, he, hi, hu, id, it, ja, kn, ko, lv, lt, mk, mr, ms, nl, no, pl, pt, ro, ru, sk, sl, sr, sv, ta, te, tl, tr, uk, ur, vi + more) | ❌ Not integrated — **P0 upgrade from nova-2** |
| 6.32 | **Nova-3 Medical** (`nova-3-medical`) | 🆕 | Medical terminology optimized | English | ❌ |
| 6.33 | **Nova-2** (`nova-2`) | Current | Good general ASR, filler word identification | 40+ languages | ✅ Currently used |
| 6.34 | Nova-2 domain variants | — | `nova-2-meeting`, `nova-2-phonecall`, `nova-2-finance`, `nova-2-conversationalai`, `nova-2-voicemail`, `nova-2-video`, `nova-2-medical`, `nova-2-drivethru`, `nova-2-automotive`, `nova-2-atc` | English | ❌ Could be useful for call analysis |
| 6.35 | **Deepgram Whisper Cloud** (`whisper-tiny/base/small/medium/large`) | — | Managed OpenAI Whisper — limited to 15 concurrent requests, max 20min audio | 97 languages | ❌ Not needed (have local whisper.rn) |

#### Deepgram TTS Models (Aura)

| # | Model | Status | Description | Voices | Guappa Status |
|---|-------|--------|-------------|--------|---------------|
| 6.36 | **Aura-2** (`aura-2-{voice}-{lang}`) | 🆕 | Latest gen TTS — natural, expressive, streaming. Multiple accents (American, British, Australian, Irish, Filipino). | 40+ English voices, Spanish, Dutch, French, German, Italian, Japanese | ❌ **P1** — significant quality upgrade over Android built-in TTS |
| 6.37 | **Aura 1** (`aura-{voice}-en`) | — | Previous gen, English only | ~10 voices | ❌ Skip — go straight to Aura-2 |

**Featured Aura-2 voices** (best for agent persona):

| Voice | Model String | Gender | Accent | Character | Best For |
|-------|-------------|--------|--------|-----------|----------|
| Thalia | `aura-2-thalia-en` | F | American | Clear, Confident, Energetic | Default agent voice |
| Apollo | `aura-2-apollo-en` | M | American | Confident, Comfortable, Casual | Male alternative |
| Draco | `aura-2-draco-en` | M | British | Warm, Approachable, Baritone | Character voice |
| Pandora | `aura-2-pandora-en` | F | British | Smooth, Calm, Melodic | Calm agent voice |
| Luna | `aura-2-luna-en` | F | American | Friendly, Natural, Engaging | Casual assistant |
| Hyperion | `aura-2-hyperion-en` | M | Australian | Caring, Warm, Empathetic | Supportive agent |

**TTS API**: `POST https://api.deepgram.com/v1/speak?model=aura-2-thalia-en` with `{"text": "..."}` → streams MP3/WAV.

#### Deepgram Audio Intelligence

| # | Feature | Status | Description | Guappa Status |
|---|---------|--------|-------------|---------------|
| 6.38 | **Summarization** | 🆕 | Auto-summarize transcribed audio | ❌ — useful for call summaries |
| 6.39 | **Topic Detection** | 🆕 | Extract topics from audio | ❌ — useful for memory categorization |
| 6.40 | **Intent Recognition** | 🆕 | Detect user intents from speech | ❌ — could enhance voice command routing |
| 6.41 | **Sentiment Analysis** | 🆕 | Analyze emotional tone | ❌ — useful for agent emotional awareness |

**Key insight**: All Audio Intelligence features are add-on parameters to the STT request (e.g., `sentiment=true`, `topics=true`). Zero additional setup — just add query params. Returns structured JSON alongside the transcript.

#### Deepgram Integration Plan

| Priority | What | Impact | Effort |
|----------|------|--------|--------|
| **P0** | Upgrade STT from `nova-2` → `nova-3` | 54% better accuracy, 50+ languages, self-serve customization | 1 line change: `model=nova-3` |
| **P0** | Add **Flux** model for voice agent mode | Built-in turn detection — eliminates need for custom VAD/end-of-turn logic | Change model + handle `is_final` events differently |
| **P1** | Add **Aura-2 TTS** as cloud TTS option | Natural voices, streaming, 7 languages — massive quality upgrade over Android TTS | ~150 lines: HTTP POST to `/v1/speak`, stream MP3 to `ExoPlayer` |
| **P1** | Add STT model selector to UI | Let users pick: Flux (agent), Nova-3 (general), Whisper (offline) | UI dropdown + config |
| **P1** | Add TTS engine selector: Android built-in / Deepgram Aura-2 / Whisper | Choose quality vs cost vs offline | UI + engine abstraction |
| **P2** | Audio Intelligence (summarization, topics, sentiment) | Auto-categorize memories from voice, detect user mood | Add query params to existing STT requests |
| **P2** | Domain-specific STT models (phonecall, meeting) | Better accuracy for incoming call analysis | Config-driven model selection |

---

## Phase 7: Memory & Context

| # | Feature | Status | Evidence | Gap |
|---|---------|--------|----------|-----|
| 7.1 | MemoryManager (5-tier memory) | ✅ | `memory/MemoryManager.kt` (627 lines) | — |
| 7.2 | Tier 1: Working memory (context window) | ✅ | ContextCompactor manages | — |
| 7.3 | Tier 2: Short-term memory (session history) | ✅ | Room SessionEntity, MessageEntity | — |
| 7.4 | Tier 3: Long-term memory (facts, preferences) | ✅ | `MemoryFactEntity` with category, tier, key | — |
| 7.5 | Tier 4: Episodic memory (task outcomes) | ✅ | `EpisodeEntity` | — |
| 7.6 | Tier 5: Semantic memory (embeddings, RAG) | ✅ | `EmbeddingEntity` + `EmbeddingService.kt` | — |
| 7.7 | ContextCompactor (auto-summarization) | ✅ | `memory/ContextCompactor.kt` (343 lines) | — |
| 7.8 | SummarizationService | ✅ | `memory/SummarizationService.kt` | — |
| 7.9 | MemoryConsolidationWorker (background) | ✅ | `memory/MemoryConsolidationWorker.kt` | — |
| 7.10 | EmbeddingService (vector search) | ✅ | `memory/EmbeddingService.kt` | — |
| 7.11 | MemoryBridge (RN native module) | ✅ | `memory/MemoryBridge.kt` + `MemoryBridgePackage.kt` | — |
| 7.12 | MemoryScreen (UI) | ✅ | `screens/tabs/MemoryScreen.tsx` (363 lines) | — |
| 7.13 | On-device embedding model | ⚠️ | EmbeddingService exists | May use cloud API instead of on-device ONNX |
| 7.14 | Recursive summarization (multi-level) | ⚠️ | SummarizationService exists | Need to verify hierarchical summary chain |
| 7.15 | Memory export/import | ❌ | Not found | — |

---

## Phase 9: Testing & QA

| # | Feature | Status | Evidence | Gap |
|---|---------|--------|----------|-----|
| 9.1 | Unit tests (JUnit + MockK) | ⚠️ | 16 test files found | Only for swarm, tools, providers, channels — no agent/memory/proactive tests |
| 9.2 | Maestro E2E flows | ✅ | **100+ YAML flows** in `.maestro/` | Extensive coverage |
| 9.3 | Integration tests (Espresso) | ❌ | Not found | — |
| 9.4 | Performance benchmarks | ❌ | Not found | — |
| 9.5 | Firebase Test Lab config | ❌ | Not found | — |
| 9.6 | CI pipeline (.github/workflows) | ❌ | No workflows directory | — |
| 9.7 | Resilience tests (chaos engineering) | ⚠️ | Some Maestro flows cover restart/background | No systematic chaos testing |
| 9.8 | **UI Automator 2.4 instrumented tests** | ❌ | No `androidTest/` directory exists | See UI Automator 2.4 section below |
| 9.9 | `testInstrumentationRunner` configured | ❌ | Missing from `defaultConfig` in `build.gradle` | Required for any androidTest |
| 9.10 | UI Automator Shell (shell commands as shell user) | ❌ | Not found | `uiautomator-shell:1.0.0-alpha03` — backports `executeShellCommandRwe` |
| 9.11 | Macrobenchmark / Baseline Profiles | ❌ | Not found | UI Automator 2.4 enables TTFD measurement + jank benchmarks |

### UI Automator 2.4 — Recommended Testing Upgrade

**Current status**: The project has zero `androidTest/` instrumented tests. All E2E testing relies on Maestro (external YAML flows). There is no `testInstrumentationRunner` configured in `build.gradle`.

**Why UI Automator 2.4 over Maestro for key scenarios**:

| Capability | Maestro | UI Automator 2.4 |
|-----------|---------|-------------------|
| **Cross-app testing** (notifications, calls, system dialogs) | Limited — relies on ADB workarounds | Native — `onElement` works across app boundaries |
| **Permission dialogs** | Manual `tapOn` coordinates | Built-in `watchFor(PermissionDialog) { clickAllow() }` |
| **Multi-window** (PiP, split-screen) | Not supported | `windows().first { it.isInPictureInPictureMode }` |
| **Compose + View interop** | Works (accessibility tree) | Native — `onElement` renamed from `onView` specifically for Compose compat |
| **Wait/stability** | `waitForAnimationToEnd` + `extendedWaitUntil` | `onElement` has built-in timeout; `waitForStable()` for accessibility tree |
| **Screenshots in test results** | External (manual screenshots) | Built-in `takeScreenshot()` + `ResultsReporter` for Android Studio integration |
| **Performance testing** | Not supported | Direct Macrobenchmark integration (TTFD, jank benchmarks) |
| **Baseline Profile generation** | Not supported | CUJ automation generates Baseline Profiles for AOT compilation |
| **Shell commands** | Via orchestrator script | `uiautomator-shell` library — `executeShellCommandRwe` from test code |
| **Kotlin DSL** | YAML only | Native Kotlin, type-safe, IDE autocomplete |
| **CI integration** | Needs Maestro CLI installed | Standard `./gradlew connectedAndroidTest` |
| **Firebase Test Lab** | Needs Maestro-specific setup | Native support — just upload APK + test APK |

**Key API patterns (UI Automator 2.4 DSL)**:

```kotlin
// Modern Kotlin DSL — clean, predicate-based
@Test
fun agentMemoryStoreAndRecall() = uiAutomator {
    startApp("com.guappa.app")
    watchFor(PermissionDialog) { clickAllow() }

    // Type a message to store a memory
    onElement { viewIdResourceName == "chat_input" }.setText("Remember: my cat is named Luna")
    onElement { viewIdResourceName == "send_button" }.click()

    // Wait for agent response
    onElement(15_000) { textAsString().contains("Luna") }

    // Ask for recall
    onElement { viewIdResourceName == "chat_input" }.setText("What is my cat's name?")
    onElement { viewIdResourceName == "send_button" }.click()

    // Verify recall
    onElement(15_000) { textAsString().contains("Luna") }

    // Screenshot for test report
    val reporter = ResultsReporter("MemoryTest")
    val file = reporter.addNewFile("memory_recall", "Memory recall verification")
    activeWindow().takeScreenshot().saveToFile(file)
    reporter.reportToInstrumentation()
}
```

**Implementation plan**:

| Step | What | Size |
|------|------|------|
| 1. Add dependencies | `androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0-beta01")` + `uiautomator-shell:1.0.0-alpha03` + `androidx.test:runner` + `androidx.test:rules` | build.gradle only |
| 2. Configure runner | Add `testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"` to `defaultConfig` | 1 line |
| 3. Create `androidTest/` directory | `mobile-app/android/app/src/androidTest/java/com/guappa/app/` | Directory structure |
| 4. Port critical Maestro flows | Memory store/recall, call hook, voice STT, provider switching — as Kotlin `uiAutomator { }` tests | ~500 lines |
| 5. Add Macrobenchmark module | Separate `:benchmark` module for startup time + jank measurements | ~200 lines |
| 6. Generate Baseline Profile | Automate CUJs (app launch → first message → response) to generate AOT compilation profile | ~150 lines |
| 7. Firebase Test Lab config | `gcloud firebase test android run` with test APK | CI config |

**Priority**: **P1** — UI Automator 2.4 is the modern Android-native testing framework. Maestro remains useful for quick smoke tests, but UI Automator is needed for:
- CI/CD (`connectedAndroidTest` is standard Gradle)
- Firebase Test Lab (native support)
- Performance testing (Macrobenchmark/Baseline Profiles)
- Multi-window and cross-app scenarios (incoming calls, notifications, permission dialogs)
- Screenshot-based visual regression in Android Studio

**Dependency**: `2.4.0-beta01` (released 2026-02-11) — stable enough for adoption. The modern DSL (`uiAutomator { }`, `onElement`, `watchFor`) replaces the legacy `UiDevice.getInstance()` pattern.

---

## Phase 10: Live Config — Hot Reload

| # | Feature | Status | Evidence | Gap |
|---|---------|--------|----------|-----|
| 10.1 | GuappaConfigStore (DataStore + StateFlow) | ✅ | `config/GuappaConfigStore.kt` (413 lines) | — |
| 10.2 | ConfigBridge (RN native module) | ✅ | `config/ConfigBridge.kt` + `ConfigBridgePackage.kt` | — |
| 10.3 | ConfigChangeDispatcher | ✅ | `config/ConfigChangeDispatcher.kt` | — |
| 10.4 | ProviderHotSwap | ✅ | `config/ProviderHotSwap.kt` | — |
| 10.5 | ChannelHotSwap | ✅ | `config/ChannelHotSwap.kt` | — |
| 10.6 | ToolHotSwap | ✅ | `config/ToolHotSwap.kt` | — |
| 10.7 | SecurePrefs (API key storage) | ✅ | `config/SecurePrefs.kt` | — |
| 10.8 | TurboModule (New Architecture, Codegen) | ❌ | Uses old NativeModule bridge | No TurboModule spec, no Codegen |
| 10.9 | VoiceHotSwap | ❌ | Not found | — |
| 10.10 | MemoryHotSwap | ❌ | Not found | — |
| 10.11 | ConfigMigrator (cross-version migration) | ❌ | Not found | — |
| 10.12 | ConfigValidator | ❌ | Not found | — |

---

## Phase 11: World Wide Swarm

| # | Feature | Status | Evidence | Gap |
|---|---------|--------|----------|-----|
| 11.1 | SwarmManager | ✅ | `swarm/SwarmManager.kt` (261 lines) | — |
| 11.2 | SwarmConnectorClient (HTTP/WebSocket) | ✅ | `swarm/SwarmConnectorClient.kt` (185 lines) | Remote connector mode |
| 11.3 | SwarmConfig | ✅ | `swarm/SwarmConfig.kt` | — |
| 11.4 | SwarmIdentity (Ed25519, DID) | ✅ | `swarm/SwarmIdentity.kt` | — |
| 11.5 | SwarmChallengeSolver (anti-bot) | ✅ | `swarm/SwarmChallengeSolver.kt` | — |
| 11.6 | SwarmTask | ✅ | `swarm/SwarmTask.kt` | — |
| 11.7 | SwarmTaskPoller | ✅ | `swarm/SwarmTaskPoller.kt` | — |
| 11.8 | SwarmTaskExecutor | ✅ | `swarm/SwarmTaskExecutor.kt` | — |
| 11.9 | SwarmMessage | ✅ | `swarm/SwarmMessage.kt` | — |
| 11.10 | SwarmHolonParticipant (voting, proposals) | ✅ | `swarm/SwarmHolonParticipant.kt` | — |
| 11.11 | SwarmReputationTracker | ✅ | `swarm/SwarmReputationTracker.kt` | — |
| 11.12 | PeerInfo | ✅ | `swarm/PeerInfo.kt` | — |
| 11.13 | GuappaSwarmModule (RN bridge) | ✅ | `swarm/GuappaSwarmModule.kt` + `GuappaSwarmPackage.kt` | — |
| 11.14 | SwarmScreen (UI) | ✅ | `screens/tabs/SwarmScreen.tsx` (1694 lines) | Rich UI with identity, peers, feed |
| 11.15 | Embedded connector (Rust cross-compile) | ❌ | Not found | Phase 11b — future |
| 11.16 | mDNS local discovery | ❌ | Not found | — |

---

## Phase 12: Android UI

| # | Feature | Status | Evidence | Gap |
|---|---------|--------|----------|-----|
| 12.1 | 5-screen app (Voice, Chat, Command, Swarm, Config) | ✅ | All 5 screens + RootNavigator | — |
| 12.2 | FloatingDock (morphing pill navigation) | ✅ | `components/dock/FloatingDock.tsx` | — |
| 12.3 | SideRail (tablet layout) | ✅ | `components/dock/SideRail.tsx` | — |
| 12.4 | Glass design system | ✅ | 15 glass components in `components/glass/` | GlassCard, GlassButton, GlassInput, GlassModal, GlassSlider, GlassToggle, etc. |
| 12.5 | PlasmaOrb component | ✅ | `components/plasma/PlasmaOrb.tsx` | — |
| 12.6 | ChatScreen (streaming bubbles) | ✅ | `screens/tabs/ChatScreen.tsx` (342 lines) + ChatInputBar, MessageBubble, StreamingBubble | — |
| 12.7 | CommandScreen (tasks, schedules, triggers, memory) | ✅ | `screens/tabs/CommandScreen.tsx` (1497 lines) | — |
| 12.8 | ConfigScreen (provider, tools, permissions) | ✅ | `screens/tabs/ConfigScreen.tsx` (1373 lines) | — |
| 12.9 | OnboardingScreen | ✅ | `screens/OnboardingScreen.tsx` + 4 step components | Welcome, Permissions, ProviderSetup, ModelDownload |
| 12.10 | Color system (liquid futurism) | ✅ | `theme/colors.ts` | — |
| 12.11 | Typography (Orbitron, Exo 2, JetBrains Mono) | ✅ | `theme/typography.ts` | — |
| 12.12 | Animations (spring physics) | ✅ | `theme/animations.ts` + Reanimated | — |
| 12.13 | Gyroscope parallax on glass | ⚠️ | Camera3D uses Accelerometer | SwarmCanvas only — not on glass cards |

---

## Phase 14: Neural Swarm Visualization

| # | Feature | Status | Evidence | Gap |
|---|---------|--------|----------|-----|
| 14.1 | SwarmCanvas (Skia canvas, 420 neurons) | ✅ | `swarm/SwarmCanvas.tsx` (585 lines) | — |
| 14.2 | NeuronSystem (3D physics, spring animation) | ✅ | `swarm/neurons/NeuronSystem.ts` (329 lines) | — |
| 14.3 | Camera3D (3D projection + accelerometer) | ✅ | `swarm/camera/Camera3D.ts` (130 lines) | — |
| 14.4 | SwarmController (state machine: idle/listen/think/speak) | ✅ | `swarm/SwarmController.ts` (73 lines) | — |
| 14.5 | SwarmDirector (separate LLM for emotion/intent) | ✅ | `swarm/SwarmDirector.ts` (190 lines) | — |
| 14.6 | EmotionPalette (20 emotions) | ✅ | `swarm/emotion/EmotionPalette.ts` (63 lines) | — |
| 14.7 | EmotionBlender (smooth HSL transitions) | ✅ | `swarm/emotion/EmotionBlender.ts` (41 lines) | — |
| 14.8 | ShapeLibrary (18 shapes + 12 emoji faces) | ✅ | `swarm/formations/ShapeLibrary.ts` (620 lines) | — |
| 14.9 | TextRenderer (pixel font → point cloud) | ✅ | `swarm/formations/TextRenderer.ts` (105 lines) | — |
| 14.10 | HarmonicWaves (voice-reactive deformation) | ✅ | `swarm/waves/HarmonicWaves.ts` (138 lines) | — |
| 14.11 | VoiceAmplitude (microphone energy extraction) | ✅ | `swarm/audio/VoiceAmplitude.ts` (78 lines) | — |
| 14.12 | Neural swarm background on all screens | ✅ | RootNavigator: opacity per screen (voice=1, chat=0.15, etc.) | — |
| 14.13 | Swarm state ↔ voice pipeline integration | ✅ | VoiceScreen, useVoiceRecording, useTTS all set swarm state | — |

---

## Phase 13: Documentation

| # | Feature | Status | Evidence | Gap |
|---|---------|--------|----------|-----|
| 13.1 | Guides (providers, tools, channels, voice, memory, swarm, troubleshooting) | ✅ | 7 guide files in `docs/guides/` | — |
| 13.2 | Reference docs (all providers, tools, channels, etc.) | ❌ | Not found | Plan called for `docs/reference/` directory |
| 13.3 | Architecture docs | ❌ | Not found | Plan called for `docs/architecture/` directory |
| 13.4 | Development docs (setup, building, testing) | ❌ | Not found | Plan called for `docs/development/` directory |
| 13.5 | AGENTS.md | ✅ | Root `AGENTS.md` | — |

---

## Local Inference — Full Inventory

### Current Guappa Implementation

| # | Modality | Status | Engine | Details |
|---|----------|--------|--------|---------|
| L.1 | **Text (LLM) — GGUF** | ✅ | `llama.rn` (llama.cpp) | Full flow: download GGUF → `initLlama()` → NanoHTTPD OpenAI-compat server on `localhost:8888`. Any GGUF model. Configurable GPU layers, CPU threads, context length. |
| L.2 | **STT (on-device)** | ✅ | `whisper.rn` (Whisper GGML) | Download tiny/base/small models → on-device transcription. |
| L.3 | **STT (free, zero-download)** | ❌ | `android.speech.SpeechRecognizer` | Android built-in. Pre-installed. **Should be default fallback when no Deepgram key.** |
| L.4 | **TTS (on-device)** | ✅ (basic) | `expo-speech` → Android `TextToSpeech` | Uses device-installed voices. Quality varies. |
| L.5 | **TTS (high-quality on-device)** | ❌ | Kokoro / Piper | Neural TTS, significantly better than built-in. |
| L.6 | **Embeddings (on-device)** | ⚠️ | `EmbeddingService.kt` exists | Need to verify if on-device ONNX model or cloud API |
| L.7 | **Vision (on-device)** | ❌ | — | No local vision model. |
| L.8 | **Image generation (on-device)** | ❌ | — | No local diffusion model. |
| L.9 | **Text (LLM) — MediaPipe/LiteRT Task** | ❌ | — | Not supported |
| L.10 | **Text (LLM) — LiteRT LM** | ❌ | — | Not supported |
| L.11 | **Text (LLM) — ONNX** | ❌ | — | Not supported |
| L.12 | **Text (LLM) — Nexa SDK (GGUF+NPU)** | ❌ | — | Not supported |
| L.13 | **HardwareProbe (SoC/NPU detection)** | ❌ | — | No device capability detection |

### Reference: LLM-Hub Local Inference Architecture

[LLM-Hub](https://github.com/timmyy123/LLM-Hub) is a production Android app with comprehensive local inference. Their architecture provides a proven reference for what Guappa should support:

#### Inference Engines (4 backends, unified routing)

| Engine | Format | Acceleration | Library | Guappa Status |
|--------|--------|-------------|---------|---------------|
| **MediaPipe LlmInference** | `.task`, `.litertlm` | GPU (OpenCL/Metal) | `com.google.mediapipe:tasks-genai:0.10.32` | ❌ Missing |
| **Nexa SDK** | `.gguf` (+ NPU) | CPU + Qualcomm NPU (HTP) | `ai.nexa:core:0.0.24` | ❌ Missing — Guappa uses `llama.rn` (JS) for GGUF instead |
| **ONNX Runtime** | `.onnx` | CPU + NNAPI | `com.microsoft.onnxruntime:onnxruntime-android:1.24.1` | ❌ Missing |
| **MNN** | `.mnn` | CPU | MNN framework | ❌ Missing (used for image gen only) |

**Key insight**: LLM-Hub uses a `UnifiedInferenceService` that auto-routes by `modelFormat`:
- `"task"` / `"litertlm"` → MediaPipe
- `"gguf"` → Nexa SDK
- `"onnx"` → ONNX Runtime

Guappa currently only supports GGUF via `llama.rn` (React Native JS layer), missing native Kotlin inference.

#### Model Families Supported

| Family | Params | Formats | Vision | Audio | Guappa Status |
|--------|--------|---------|--------|-------|---------------|
| **Gemma-3** | 1B, 4B, 12B | `.task` (INT4/INT8) | ❌ | ❌ | ❌ Not in model catalog |
| **Gemma-3n** | E2B, E4B | `.litertlm` (INT4) | ✅ | ✅ | ❌ Not in model catalog |
| **Llama-3.2** | 1B, 3B | `.task`, `.gguf` (Q3–Q8) | ❌ | ❌ | ⚠️ GGUF works via llama.rn, no .task |
| **Phi-4 Mini** | 3.8B | `.litertlm` (INT4/INT8) | ❌ | ❌ | ❌ Not in model catalog |
| **LFM-2.5** (LiquidAI) | 1.2B, 1.6B | `.gguf` | ✅ (VL) | ❌ | ❌ Not in model catalog |
| **LFM2-24B-A2B** | 24B (2B active MoE) | `.gguf` | ❌ | ❌ | ❌ Not in model catalog |
| **Ministral-3** | 3B | `.gguf` (Q4–Q6) | ❌ | ❌ | ⚠️ Would work via llama.rn |
| **Granite 4.0** | Tiny, Small | `.gguf` (Q4–Q8) | ❌ | ❌ | ⚠️ Would work via llama.rn |
| **GPT-OSS** | 20B (3.6B active MoE) | `.gguf` (Q4–Q8) | ❌ | ❌ | ⚠️ Would work via llama.rn (needs 14GB+ RAM) |
| **Qwen3.5** | 0.8B, 2B, 4B, 9B, 27B, 35B-A3B (MoE), 122B-A10B (MoE), 397B-A17B (MoE) | `.gguf` (Q2–Q8, BF16) | ✅ (all sizes, early-fusion) | ❌ | ⚠️ GGUF works via llama.rn — **recommended default** |

#### Model Categories

| Category | Formats | Example | Guappa Status |
|----------|---------|---------|---------------|
| **text** | `.task`, `.litertlm`, `.gguf`, `.onnx` | Gemma-3 1B, Llama 3.2 | ⚠️ GGUF only |
| **multimodal** (vision+text) | `.gguf` (+ mmproj), `.litertlm` | Gemma-3n E2B, LFM-2.5 VL | ❌ |
| **embedding** | `.tflite` | Gecko-110M (64–1024 dim), EmbeddingGemma 300M | ❌ |
| **image_generation** | `.mnn` (CPU), `.qnn_npu` (Qualcomm NPU) | Stable Diffusion 1.5 (Absolute Reality) | ❌ |

#### Qwen3.5 Family — Recommended Local Models

Qwen3.5 is Alibaba/Qwen's latest model family. **All sizes are natively multimodal** (vision+text) via early-fusion training — no separate VL variant needed. Key features:
- **Unified Vision-Language**: Every model includes a vision encoder; text-only and image+text use the same weights
- **Gated Delta Networks + MoE**: Hybrid architecture for efficient inference (linear attention + sparse MoE)
- **201 languages** supported
- **262K native context** window
- **Thinking mode** (reasoning) supported on all sizes
- **Apache 2.0** license
- **mmproj file required** for vision (~200–900MB depending on model size)

| Model | Total Params | Active Params | GGUF Q4_K_M Size | Min RAM | Vision | Mobile Feasible? |
|-------|-------------|---------------|-----------------|---------|--------|-----------------|
| **Qwen3.5-0.8B** | 0.8B | 0.8B | 533 MB | 2 GB | ✅ (+207MB mmproj) | ✅ Excellent — smallest multimodal model |
| **Qwen3.5-2B** | 2B | 2B | 1.3 GB | 3 GB | ✅ (+671MB mmproj) | ✅ Good — best quality/size for mobile |
| **Qwen3.5-4B** | 4B | 4B | 2.7 GB | 5 GB | ✅ (+676MB mmproj) | ⚠️ Flagship phones only (8GB+ RAM) |
| **Qwen3.5-9B** | 9B | 9B | ~5 GB | 8 GB | ✅ | ❌ Too large for most phones |
| **Qwen3.5-35B-A3B** | 35B | 3B (MoE) | 22 GB | 14 GB | ✅ (+903MB mmproj) | ❌ Desktop/server only |
| **Qwen3.5-122B-A10B** | 122B | 10B (MoE) | ~60 GB | 40 GB | ✅ | ❌ Server only |
| **Qwen3.5-397B-A17B** | 397B | 17B (MoE) | ~200 GB | 100 GB | ✅ | ❌ Server only |

**Recommended for Guappa model catalog**:
- **Default**: Qwen3.5-0.8B Q4_K_M (533MB text-only, 740MB with vision) — runs on any modern phone
- **Quality pick**: Qwen3.5-2B Q4_K_M (1.3GB + 671MB mmproj) — significantly smarter, needs 3GB+ RAM
- **Power user**: Qwen3.5-4B Q4_K_M (2.7GB) — comparable to GPT-3.5 quality, needs flagship phone

**Key advantage over Gemma-3**: Qwen3.5 has vision built into every model (early fusion), while Gemma-3 requires separate VL models. Qwen3.5 also supports 262K context vs Gemma-3's 4K. GGUF format works with existing `llama.rn` infrastructure — no new engine needed.

**GGUF sources**: `unsloth/Qwen3.5-*-GGUF`, `bartowski/Qwen_Qwen3.5-*-GGUF`, `lmstudio-community/Qwen3.5-*-GGUF`

#### Hardware Detection

LLM-Hub implements `DeviceInfo` with SoC detection to:
- Auto-select optimal model variants
- Enable/disable NPU acceleration
- Route to correct Qualcomm HTP library version

```
SM8450 → 8gen1, SM8550 → 8gen2, SM8650 → 8gen3, SM8750 → 8gen4, SM8850 → 8gen5
NPU option shown only for 8 Gen 4+ devices
```

#### Embedding Models (for RAG)

LLM-Hub uses **Google AI Edge RAG SDK** with Gecko models:
- `Gecko-110M` (64/256/512/1024 dimensions, quantized + f32)
- `EmbeddingGemma-300M` (256/512/1024/2048 sequence length)
- All `.tflite` format, GPU-accelerated via `GeckoEmbeddingModel`

Guappa's `EmbeddingService.kt` exists but lacks a proven on-device embedding model.

### Implementation Plan — Local Inference Gaps

| Priority | Gap | What to Implement | Size Estimate |
|----------|-----|-------------------|---------------|
| **P0** | MediaPipe/LiteRT inference backend | Add `com.google.mediapipe:tasks-genai` dependency. Create `MediaPipeInferenceEngine.kt`. Support `.task` and `.litertlm` formats. This enables Gemma-3, Gemma-3n (vision+audio), Phi-4 Mini. | ~500 lines |
| **P0** | Model catalog with download UI | Create `ModelCatalog.kt` with curated list of recommended models (Gemma-3 1B INT4 as default — only 529MB). Add download screen or integrate into onboarding. | ~400 lines |
| **P0** | HardwareProbe (SoC detection) | Port LLM-Hub's `DeviceInfo` — detect Snapdragon SoC, available RAM, GPU type. Use to auto-recommend models and acceleration backends. | ~100 lines |
| **P1** | ONNX Runtime inference backend | Add `com.microsoft.onnxruntime:onnxruntime-android` dependency. Support `.onnx` models (Ministral, etc.). | ~300 lines |
| **P1** | On-device embedding model (Gecko/EmbeddingGemma) | Add `com.google.ai.edge.localagents:rag` dependency. Use Gecko-110M tflite for RAG embeddings instead of cloud API. | ~200 lines |
| **P1** | Unified inference routing | Create `UnifiedInferenceEngine.kt` that routes by model format: `.task`/`.litertlm` → MediaPipe, `.gguf` → llama.rn, `.onnx` → ONNX Runtime. | ~200 lines |
| **P1** | Vision model support (multimodal) | Enable vision-capable models: Gemma-3n (`.litertlm` with built-in vision) and LFM-2.5 VL (`.gguf` + mmproj file). Feed camera/gallery images to local model. | ~300 lines |
| **P2** | Nexa SDK (GGUF + NPU acceleration) | Add `ai.nexa:core` dependency for Qualcomm NPU acceleration on Snapdragon 8 Gen 4+ devices. Significant speedup over CPU-only llama.rn. | ~400 lines |
| **P2** | Image generation (Stable Diffusion) | MNN framework for CPU, QNN SDK for Qualcomm NPU. SD 1.5 at 512x512. ~1.2GB model. | ~600 lines |
| **P2** | Audio input models | Gemma-3n supports audio natively via LiteRT LM. Enable microphone → model for audio understanding. | ~200 lines |
| **P3** | HuggingFace model browser | Allow users to search and download any compatible model from HuggingFace (with HF_TOKEN for gated models). | ~400 lines |

### Local Inference E2E Test Requirements

All local inference paths must be validated by E2E tests running on the Android emulator:

| Test | Validates | How |
|------|-----------|-----|
| Chat with local GGUF LLM | L.1 | Download small GGUF → configure as local provider → send message → verify response |
| Chat with MediaPipe model | L.9 | Download Gemma-3 1B .task → load via MediaPipe → chat test |
| Voice with local Whisper | L.2 | Download whisper-tiny → speak via BlackHole → verify transcription |
| Voice with Android STT | L.3 | No API key configured → speak via BlackHole → verify `SpeechRecognizer` fallback |
| TTS with built-in | L.4 | Send message → verify expo-speech speaks response |
| Embedding with Gecko | L.6 | Download Gecko-110M tflite → store fact → RAG recall |
| Vision with local model | L.7 | Load Gemma-3n → send image → verify description (when implemented) |

---

## Summary — Critical Gaps for E2E Test Readiness

### 🔴 Blockers for E2E Tests

| Priority | Gap | Impact on Tests |
|----------|-----|-----------------|
| P0 | **Session persistence thinness** — `GuappaSession.kt` is 67 lines, no session types | Memory recall across turns may not persist reliably |
| P0 | **Channel incoming messages missing** — Telegram/Discord/Slack can only SEND, not RECEIVE | Can't test bidirectional messenger interaction |
| P0 | **No CI pipeline** — no GitHub Actions | Can't automate test runs |

### 🟡 Important Gaps

| Priority | Gap | Impact |
|----------|-----|--------|
| P1 | **OAuth for all subscription providers** — Copilot (device flow), OpenAI Codex (PKCE), Anthropic (PKCE), Google Gemini CLI (PKCE) — none implemented | Copilot is unusable; other providers work with API keys but OAuth enables subscription-based access (ChatGPT Plus, Claude Pro, etc.) |
| P1 | **Android SpeechRecognizer as free STT fallback** | Voice mode requires Deepgram API key — should work without any key |
| P1 | **AppFunctions API integration** | Now available (`androidx.appfunctions:0.1.0-alpha01`) — enables structured app control |
| P1 | No TurboModule bridge (still old NativeModule) | Performance, type safety — functional but suboptimal |
| P1 | No high-quality on-device TTS (Kokoro/Piper) | Built-in TTS quality varies wildly by device |
| P1 | **Deepgram Aura-2 TTS not integrated** | Same API key, streaming, 40+ natural voices — massive quality upgrade |
| P1 | **Deepgram STT still on nova-2** (nova-3 + Flux available) | 54% better accuracy (nova-3), built-in turn detection (Flux) |
| P1 | **Qwen3.5 not in model catalog** | Best local multimodal model family — vision built in, 0.8B–4B for mobile, Apache 2.0 |
| P1 | No STT/TTS engine selection UI | Users can't switch voice engines |
| P1 | No session encryption (SQLCipher) | Security concern for sensitive data |
| P1 | No audio routing / Bluetooth support | Voice mode only works on speaker |
| P1 | Limited unit test coverage | Only swarm/tools/providers/channels — no agent/memory tests |
| P1 | **No UI Automator 2.4 instrumented tests** | No `androidTest/`, no `testInstrumentationRunner`, can't run on Firebase Test Lab or CI |
| P1 | **No Macrobenchmark / Baseline Profile** | No TTFD measurement, no AOT compilation profile, suboptimal startup |

### 🟢 Nice-to-Have Gaps

| Priority | Gap | Impact |
|----------|-----|--------|
| P2 | Google Antigravity OAuth (Gemini 3, Claude, GPT via Google Cloud) | Pi supports this as additional Google OAuth variant |
| P2 | No local inference engines (LiteRT, GENIE, ONNX) for non-text modalities | All vision/image-gen requires cloud |
| P2 | No embedded swarm connector (Rust cross-compile) | Requires external wws-connector server |
| P2 | No token counter (tiktoken) | Approximate context management |
| P2 | No channel formatters | Raw text sent to all channels |
| P2 | No memory export/import | — |
| P2 | No DI framework (Hilt/Koin) | Manual wiring, works but less maintainable |
| P2 | Cloud TTS engines (ElevenLabs, OpenAI TTS, Google Cloud TTS, Speechmatics) | Deepgram Aura-2 is P1 (same API key); others are P2 |

---

## E2E Test Files (Created)

The following Maestro tests were created to validate the critical user scenarios end-to-end:

| File | What It Tests |
|------|---------------|
| `e2e_agent_memory_call_voice.yaml` | Full 5-phase test: memory store → call hook → call emulation → memory recall → voice STT |
| `e2e_agent_memory_call_voice.sh` | Orchestrator script: coordinates Maestro + ADB call emulation + TTS audio injection |
| `e2e_phase1_memory_store.yaml` | Standalone: tell agent "My wife's name is Kate" |
| `e2e_phase2_call_hook_setup.yaml` | Standalone: ask agent to set up incoming call notification |
| `e2e_phase3_call_emulate_verify.yaml` | Standalone: verify agent received call from 5551234567 |
| `e2e_phase4_memory_recall.yaml` | Standalone: ask "What is my wife's name?" → assert "Kate" |
| `e2e_phase5_voice_stt_blackhole.yaml` | Standalone: voice STT via BlackHole virtual audio cable |
| `play_tts.sh` | Helper: macOS TTS → 16kHz WAV → BlackHole → emulator mic |
| `README_voice_e2e.md` | Setup guide for BlackHole audio routing + prerequisites |

### E2E Tests Needed (To Create)

**Maestro flows** (quick smoke tests, developer iteration):

| File | What It Tests | Priority |
|------|---------------|----------|
| `e2e_local_llm_chat.yaml` | Download small GGUF model → configure local provider → send chat message → verify response | P0 |
| `e2e_local_whisper_stt.yaml` | Download whisper-tiny → switch STT to whisper → speak via BlackHole → verify transcription | P1 |
| `e2e_android_stt_fallback.yaml` | No Deepgram key → voice mode → speak → verify `SpeechRecognizer` fallback works | P1 |
| `e2e_builtin_tts_response.yaml` | Send chat message → verify TTS speaks the response (expo-speech / Android TextToSpeech) | P1 |
| `e2e_telegram_send_channel.yaml` | Configure Telegram bot → ask agent to send message to Telegram → verify via Bot API | P1 |
| `e2e_brave_web_search.yaml` | Configure Brave API key → ask agent to search → verify search results in chat | P1 |
| `e2e_copilot_oauth_flow.yaml` | Test GitHub OAuth device flow for Copilot provider (when implemented) | P2 |
| `e2e_appfunctions_control.yaml` | Test AppFunctions API integration for cross-app control (when implemented) | P2 |

**UI Automator 2.4 instrumented tests** (CI/CD, Firebase Test Lab, performance, cross-app):

| File | What It Tests | Priority |
|------|---------------|----------|
| `AgentMemoryTest.kt` | Store fact → recall fact, using `uiAutomator { onElement { ... } }` DSL with screenshots | P0 |
| `IncomingCallHookTest.kt` | Setup call hook → emulate call via shell → verify agent response. Uses `uiautomator-shell` for ADB commands from test code | P0 |
| `PermissionFlowTest.kt` | First-launch permission grants using `watchFor(PermissionDialog) { clickAllow() }` | P0 |
| `ProviderSwitchingTest.kt` | Switch between providers (local/cloud) mid-conversation, verify continuity | P1 |
| `VoiceSTTTest.kt` | Voice input → STT → agent response. Cross-app: interacts with microphone permission + system audio | P1 |
| `NotificationTest.kt` | Verify agent notifications appear correctly. Cross-app: reads notification shade | P1 |
| `MultiWindowTest.kt` | Agent in split-screen with another app, verify tool interactions | P2 |
| `StartupBenchmark.kt` | Macrobenchmark: cold/warm/hot start TTFD measurement | P1 |
| `ChatJankBenchmark.kt` | Macrobenchmark: scroll chat history, measure frame timings | P2 |
| `BaselineProfileGenerator.kt` | Generate Baseline Profile from: launch → configure provider → first message → response | P1 |

### Pre-existing Relevant Tests

| File | Coverage |
|------|----------|
| `e2e_full_agent_scenario.yaml` | Memory + call hook + recall (older version, inline call emulation note) |
| `test_scenario_incoming_call_telegram.yaml` | Call hook → Telegram notification |
| `test_scenario_memory_persistence.yaml` | Memory store → recall |
| `e2e_call_telegram.sh` | Full call → Telegram notification E2E with Telegram API polling |
| `guappa_voice_full_flow.yaml` | Voice screen: mic tap → listen → stop |
| `voice_interruptible_smoke.yaml` | Voice with Deepgram key |
| `e2e_voice_swarm_emotion.yaml` | Neural swarm emotion on voice screen |
| `live_openrouter_chat.yaml` | Real OpenRouter API chat test |

---

## Test Credentials (DO NOT COMMIT)

All credentials stored in environment variables or entered via app UI at test time.
**Never hardcode in YAML/script files. Never commit to git.**

| Service | Type | Where to Configure |
|---------|------|--------------------|
| OpenRouter | API key | App → Config → Provider → OpenRouter → API Key |
| Deepgram | API key | App → Settings → Deepgram API key |
| Telegram Bot | Bot token | App → Config → Integrations → Telegram |
| Brave Search | API key | App → Config → Web Search API Key |
| EAS | Token | Environment variable `EXPO_TOKEN` |

For the E2E call+Telegram test, set env vars:
```bash
export GUAPPA_TG_BOT_TOKEN="<telegram-bot-token>"
export GUAPPA_TG_CHAT_ID="<your-chat-id>"
export GUAPPA_TEST_CALLER_NUMBER="5551234567"
```

---

## Execution Plan

To close gaps and run E2E tests successfully:

### Phase A: Build & Deploy
1. **Build** the app: `cd mobile-app/android && ./gradlew assembleDebug`
2. **Install** on emulator: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

### Phase B: Configure (via app UI)
3. **Set provider**: OpenRouter with minimax/minimax-m2.5
4. **Set Deepgram key** in Settings
5. **Set Brave Search API key** in Config → Web Search
6. **Set Telegram Bot token** in Config → Integrations

### Phase C: Run Cloud Provider Tests
7. **Memory + Call + Recall**:
   ```bash
   maestro test mobile-app/.maestro/e2e_phase1_memory_store.yaml
   maestro test mobile-app/.maestro/e2e_phase2_call_hook_setup.yaml
   adb emu gsm call 5551234567 && sleep 10 && adb emu gsm cancel 5551234567
   maestro test mobile-app/.maestro/e2e_phase3_call_emulate_verify.yaml
   maestro test mobile-app/.maestro/e2e_phase4_memory_recall.yaml
   ```

### Phase D: Run Voice Tests
8. **Voice via BlackHole**: `bash mobile-app/.maestro/e2e_agent_memory_call_voice.sh`

### Phase E: Run Local Inference Tests
9. **Local LLM**: Download GGUF model → switch provider to local → chat test
10. **Local Whisper**: Download whisper-tiny → switch STT to whisper → voice test

### Phase F: Run Full Telegram E2E
11. **Call → Telegram**: `bash mobile-app/.maestro/e2e_call_telegram.sh`
