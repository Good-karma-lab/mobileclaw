# MobileClaw v2: Native Android Agent Backend — Architecture Plan

**Date**: 2026-03-06
**Status**: Proposal
**Scope**: Full backend rewrite — replace ZeroClaw Rust runtime with native Kotlin agent

---

## 1. Executive Summary

Replace the ZeroClaw Rust backend (compiled to `libzeroclaw.so` via JNI) with a **pure Kotlin
agent runtime** that runs natively on Android. The React Native frontend, UI, and existing
screens are preserved. The new backend is purpose-built for autonomous, long-running operation
on mobile devices with full hardware access, messenger integration, event-driven triggers,
local model inference, and voice control.

### Why rewrite?

| Concern | ZeroClaw (current) | Kotlin-native (proposed) |
|---|---|---|
| Android API access | Indirect via JNI + HTTP bridge to RN | Direct — first-class Android citizen |
| Build complexity | Cross-compile Rust → ARM, JNI glue, `libzeroclaw.so` | Standard Android/Gradle build |
| Sensor/hardware | Bridged through RN native modules | Direct Android SDK access |
| Foreground service | Managed externally, restarts fragile | Native Service lifecycle |
| Local inference | Separated (llama.rn in JS thread) | Unified (MediaPipe/ONNX/llama.cpp in-process) |
| Streaming | HTTP polling to gateway | Kotlin Flow / coroutines, zero-copy |
| Maintainability | Two languages, two build systems, JNI surface | Single language, single build system |

---

## 2. What We Keep (Unchanged)

- **React Native UI** — `mobile-app/App.tsx`, all screens (`ChatScreen`, `SettingsScreen`,
  `IntegrationsScreen`, `ScheduledTasksScreen`, `MemoryScreen`, `DeviceScreen`,
  `ActivityScreen`, `SecurityScreen`), navigation, theme, UI primitives
- **Android shell** — `MainActivity.kt`, `MainApplication.kt`, Android manifest, gradle
  wrapper, resources
- **Native modules interface contract** — The RN ↔ native bridge pattern stays. We replace
  the implementations behind the bridge, not the JS-side API
- **App identity** — package `com.mobileclaw.app`, app name, icons, splash

---

## 3. What We Remove

- `src/` — entire ZeroClaw Rust codebase (agent, channels, providers, tools, gateway, etc.)
- `Cargo.toml`, `Cargo.lock`, `.cargo/`, `build_android_jni.sh`
- `src/jni_bridge.rs` — JNI bridge to Rust
- `ZeroClawBackend.kt` — JNI wrapper class
- `ZeroClawDaemonModule.kt`, `ZeroClawDaemonService.kt`, `ZeroClawDaemonPackage.kt`
- `mobile-app/src/native/zeroClawDaemon.ts` — JS-side daemon bridge
- Dependency on `libzeroclaw.so` native library

---

## 4. High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   React Native UI                        │
│  ChatScreen │ Settings │ Integrations │ Memory │ Device  │
└──────────────────────┬──────────────────────────────────┘
                       │ React Native Bridge
                       ▼
┌─────────────────────────────────────────────────────────┐
│              Native Bridge Modules (Kotlin)               │
│  AgentBridgeModule │ InferenceBridgeModule │ VoiceBridge  │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────┐
│                 AGENT CORE (Kotlin)                       │
│                                                           │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────┐  │
│  │  Orchestrator│  │  Tool Engine  │  │ Memory Manager │  │
│  │  (Agent Loop)│  │              │  │                │  │
│  └──────┬──────┘  └──────┬───────┘  └───────┬────────┘  │
│         │                │                   │           │
│  ┌──────▼──────────────────────────────────────────────┐ │
│  │              Provider Router                         │ │
│  │  Cloud (OpenRouter/Anthropic/OpenAI/DeepSeek/...)   │ │
│  │  Local (MediaPipe/ONNX/llama.cpp/Whisper/SD)        │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────┐ │
│  │  Channel Hub  │  │  Trigger     │  │  Scheduler     │ │
│  │  (Messengers) │  │  Engine      │  │  (Cron/Alarm)  │ │
│  └──────────────┘  └──────────────┘  └────────────────┘ │
│                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────┐ │
│  │  Device Tools │  │  Voice       │  │  OAuth/Auth    │ │
│  │  (Full HW)   │  │  Pipeline    │  │  Manager       │ │
│  └──────────────┘  └──────────────┘  └────────────────┘ │
└─────────────────────────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────┐
│                 Android OS / Hardware                     │
│  Sensors │ Camera │ GPS │ SMS │ Calls │ Files │ BT │ NFC │
└─────────────────────────────────────────────────────────┘
```

---

## 5. Module Breakdown

### 5.1 Agent Core — `agent/`

The brain. An autonomous orchestration loop that:
- Maintains conversation context across all input sources
- Plans multi-step task execution
- Manages tool calls and interprets results
- Runs indefinitely as an Android foreground service
- Supports concurrent tasks (e.g., trading + monitoring calls)

```
agent/
├── AgentOrchestrator.kt      — main agent loop (coroutine-based)
├── AgentSession.kt           — session state, context window management
├── AgentPlanner.kt           — task decomposition, multi-step planning
├── AgentConfig.kt            — runtime configuration (model, provider, capabilities)
├── MessageBus.kt             — internal pub/sub for cross-module communication
└── AgentForegroundService.kt — Android foreground service for always-on operation
```

**Key design decisions:**
- The orchestrator is a **Kotlin coroutine** running in a `CoroutineScope` tied to the
  foreground service lifecycle
- All input sources (chat, voice, messenger, trigger) post messages to a unified
  `MessageBus` (Channel-based)
- The agent processes messages sequentially per-session but can run multiple sessions
  concurrently (e.g., "monitor stock prices" running alongside ad-hoc questions)
- Context window management with automatic compaction for long-running tasks

### 5.2 Provider Router — `providers/`

Unified interface to all LLM backends — cloud and local.

```
providers/
├── ProviderRouter.kt         — routes requests to the right backend
├── ProviderConfig.kt         — model/endpoint/key configuration
├── StreamingResponse.kt      — Kotlin Flow<String> streaming abstraction
├── cloud/
│   ├── CloudProvider.kt      — interface for HTTP-based providers
│   ├── OpenRouterProvider.kt — OpenRouter (multi-model gateway)
│   ├── AnthropicProvider.kt  — Claude direct API
│   ├── OpenAIProvider.kt     — OpenAI / compatible endpoints
│   ├── DeepSeekProvider.kt   — DeepSeek
│   ├── GoogleProvider.kt     — Gemini
│   └── CustomProvider.kt     — any OpenAI-compatible endpoint
├── local/
│   ├── LocalProvider.kt      — interface for on-device inference
│   ├── MediaPipeEngine.kt    — Google MediaPipe LiteRT (.task models, GPU/NPU)
│   ├── LlamaCppEngine.kt     — llama.cpp via JNI (.gguf models, CPU/GPU)
│   ├── OnnxEngine.kt         — ONNX Runtime (.onnx models)
│   ├── WhisperEngine.kt      — Whisper STT (local, streaming)
│   ├── StableDiffusionEngine.kt — Image generation (MNN/QNN accelerated)
│   ├── TtsEngine.kt          — Text-to-speech (local)
│   ├── VisionEngine.kt       — Image/video understanding (local multimodal)
│   └── ModelManager.kt       — download, cache, validate models; path resolution
└── subscription/
    └── SubscriptionManager.kt — manage provider subscriptions/API keys/quotas
```

**Local inference architecture** (inspired by [LLM-Hub](https://github.com/timmyy123/LLM-Hub)):
- **MediaPipe/LiteRT** for `.task` / `.litertlm` models — optimized for Tensor, GPU, NPU
- **llama.cpp** (via JNI, android-prebuilt) for `.gguf` quantized models — INT4/INT8
- **ONNX Runtime** for `.onnx` models — cross-platform
- **MNN** for image generation — Qualcomm QNN / GPU acceleration
- Hardware acceleration auto-detection: check chipset (Snapdragon 8 Gen 3/4 → QNN NPU,
  Mali GPU → OpenCL, Tensor G → MediaPipe delegate)
- `ModelManager` handles: download from HuggingFace/URL, verify checksum, cache in
  app-specific storage, serve to engines

**Streaming**: All providers return `Flow<StreamChunk>` — both cloud (SSE parsing) and
local (token-by-token callback). The UI receives streaming tokens via the RN bridge
event emitter.

### 5.3 Tool Engine — `tools/`

Every capability the agent can invoke. Tools are self-describing (JSON schema) and
sandboxed.

```
tools/
├── ToolRegistry.kt           — registry + dispatch
├── ToolSchema.kt             — parameter/result schema definitions
├── ToolResult.kt             — structured result type
├── device/
│   ├── SmsTool.kt            — send/read SMS
│   ├── CallTool.kt           — make/end calls, read call log
│   ├── ContactsTool.kt       — read/write contacts
│   ├── CalendarTool.kt       — read/write calendar events
│   ├── CameraTool.kt         — capture photo/video
│   ├── LocationTool.kt       — GPS, geofencing
│   ├── SensorsTool.kt        — accelerometer, gyro, barometer, etc.
│   ├── BluetoothTool.kt      — scan, connect, send/receive
│   ├── NfcTool.kt            — read/write NFC tags
│   ├── WifiTool.kt           — scan, connect, info
│   ├── NotificationTool.kt   — read/post notifications
│   ├── ClipboardTool.kt      — get/set clipboard
│   ├── ScreenshotTool.kt     — capture screen (accessibility service)
│   ├── BatteryTool.kt        — battery status, optimization
│   ├── VibrateTool.kt        — haptic feedback
│   ├── FlashlightTool.kt     — torch control
│   ├── AudioTool.kt          — record/play audio, volume control
│   └── MediaTool.kt          — access gallery, media store
├── apps/
│   ├── AppLaunchTool.kt      — launch/stop apps
│   ├── AppListTool.kt        — installed apps inventory
│   ├── IntentTool.kt         — fire arbitrary Android intents
│   ├── AccessibilityTool.kt  — UI automation (tap, swipe, read screen)
│   ├── BrowserTool.kt        — open URL, web automation
│   └── SettingsTool.kt       — system settings read/write
├── filesystem/
│   ├── FileReadTool.kt       — read files
│   ├── FileWriteTool.kt      — write/create files
│   ├── FileListTool.kt       — list directory
│   ├── FileSearchTool.kt     — search by name/content
│   └── FileDeleteTool.kt     — delete files
├── network/
│   ├── HttpRequestTool.kt    — arbitrary HTTP requests
│   ├── WebSearchTool.kt      — web search (Brave, Google)
│   └── WebScrapeTool.kt      — fetch and parse web pages
├── integrations/
│   ├── GitHubTool.kt         — repos, issues, PRs, actions
│   ├── GoogleDocsTool.kt     — read/write Google Docs/Sheets/Drive
│   ├── GoogleCalendarTool.kt — Google Calendar API
│   ├── GmailTool.kt          — read/send email via Gmail API
│   ├── SpotifyTool.kt        — playback control
│   ├── NotionTool.kt         — read/write Notion pages
│   └── OAuthTool.kt          — generic OAuth2 integration framework
├── ai/
│   ├── ImageGenTool.kt       — generate images (local SD or cloud DALL-E)
│   ├── ImageAnalyzeTool.kt   — analyze image content
│   ├── TranscribeTool.kt     — audio → text (local Whisper or cloud)
│   ├── TtsTool.kt            — text → speech
│   └── TranslateTool.kt      — text translation
├── memory/
│   ├── MemoryStoreTool.kt    — store facts in long-term memory
│   ├── MemorySearchTool.kt   — search memory
│   └── MemoryForgetTool.kt   — delete from memory
└── automotive/
    ├── ObdTool.kt            — OBD-II via Bluetooth (car diagnostics)
    ├── AndroidAutoTool.kt    — Android Auto integration
    └── VehicleSensorTool.kt  — vehicle-specific sensors (AAOS)
```

### 5.4 Channel Hub — `channels/`

Bidirectional messenger integration. The agent can **receive messages from** and
**proactively send messages to** any channel.

```
channels/
├── ChannelManager.kt         — lifecycle, routing, multiplexing
├── ChannelMessage.kt         — unified message model
├── InboundDispatcher.kt      — routes inbound messages to agent
├── OutboundDispatcher.kt     — agent sends messages to channels
├── telegram/
│   ├── TelegramChannel.kt    — Telegram Bot API (long polling)
│   └── TelegramConfig.kt
├── whatsapp/
│   ├── WhatsAppChannel.kt    — WhatsApp Business API or local bridge
│   └── WhatsAppConfig.kt
├── slack/
│   ├── SlackChannel.kt       — Slack Bot (Socket Mode)
│   └── SlackConfig.kt
├── discord/
│   ├── DiscordChannel.kt     — Discord Bot (Gateway WebSocket)
│   └── DiscordConfig.kt
├── signal/
│   ├── SignalChannel.kt      — Signal via signal-cli or linked device
│   └── SignalConfig.kt
├── sms/
│   ├── SmsChannel.kt         — native Android SMS as a channel
│   └── SmsConfig.kt
├── email/
│   ├── EmailChannel.kt       — IMAP/SMTP or Gmail API
│   └── EmailConfig.kt
└── matrix/
    ├── MatrixChannel.kt      — Matrix protocol
    └── MatrixConfig.kt
```

**Proactive messaging**: The agent can initiate messages to any configured channel at
any time — e.g., "courier is calling → notify owner via Telegram". This is a first-class
capability, not a hack.

### 5.5 Trigger Engine — `triggers/`

Event-driven automation. Triggers watch for conditions and fire agent actions.

```
triggers/
├── TriggerEngine.kt          — evaluates and dispatches triggers
├── TriggerConfig.kt          — persisted trigger definitions
├── TriggerCondition.kt       — condition DSL (and/or/threshold/match)
├── sources/
│   ├── CallTrigger.kt        — incoming/outgoing call events
│   ├── SmsTrigger.kt         — incoming SMS matching pattern
│   ├── LocationTrigger.kt    — geofence enter/exit
│   ├── SensorTrigger.kt      — sensor value threshold (e.g., temperature > X)
│   ├── TimeTrigger.kt        — cron/alarm-based
│   ├── AppTrigger.kt         — app launched/closed
│   ├── BluetoothTrigger.kt   — device connected/disconnected
│   ├── BatteryTrigger.kt     — charge level threshold
│   ├── NotificationTrigger.kt— specific notification received
│   ├── WebhookTrigger.kt     — external HTTP webhook
│   ├── ChannelTrigger.kt     — message received on a channel
│   └── CameraTrigger.kt      — motion detection, object recognition
└── actions/
    ├── SendMessageAction.kt  — send message to a channel
    ├── RunToolAction.kt      — execute a tool
    ├── AgentTaskAction.kt    — give the agent a full task to execute
    └── CompoundAction.kt     — chain multiple actions
```

**Examples from your requirements:**
- "If courier calls → notify me on Telegram" →
  `CallTrigger(pattern="courier_contact") → SendMessageAction(channel=telegram)`
- "If I approach home → SMS wife" →
  `LocationTrigger(geofence=home, event=enter) → RunToolAction(sms, to=wife)`
- "Trade stocks at market open" →
  `TimeTrigger(cron="0 9 30 * * MON-FRI") → AgentTaskAction("execute trading strategy")`

### 5.6 Scheduler — `scheduler/`

Persistent task scheduling that survives app/device restarts.

```
scheduler/
├── TaskScheduler.kt          — schedule/cancel/list tasks
├── ScheduledTask.kt          — task definition (cron, one-shot, recurring)
├── AlarmScheduler.kt         — Android AlarmManager integration
├── WorkScheduler.kt          — Android WorkManager for deferrable tasks
└── ScheduleStore.kt          — persist schedules to SQLite
```

Uses `AlarmManager` for precise timing and `WorkManager` for battery-friendly
background execution. All schedules survive device reboots via `BOOT_COMPLETED`
receiver.

### 5.7 Memory System — `memory/`

Long-term persistent memory shared across all sessions, channels, and triggers.

```
memory/
├── MemoryManager.kt          — unified read/write interface
├── MemoryStore.kt            — SQLite + FTS5 for text search
├── VectorStore.kt            — embeddings stored as BLOBs, cosine similarity
├── EmbeddingEngine.kt        — on-device embeddings (MediaPipe/ONNX)
├── ContextCompactor.kt       — summarize old context to save tokens
├── FactExtractor.kt          — extract structured facts from conversations
└── SharedContext.kt           — cross-session context sharing
```

**Key features:**
- Hybrid search: FTS5 keyword + vector similarity (same approach as ZeroClaw, but in SQLite)
- On-device embeddings via MediaPipe (no cloud dependency for memory operations)
- Automatic fact extraction: agent extracts key facts from conversations and stores them
- Context compaction: when conversation grows, older context is summarized and stored
- All memory is shared across all channels/sessions — the agent has one unified "brain"

### 5.8 Voice Pipeline — `voice/`

Full voice I/O with streaming.

```
voice/
├── VoicePipeline.kt          — orchestrates STT → Agent → TTS flow
├── StreamingStt.kt           — streaming speech-to-text
├── StreamingTts.kt           — streaming text-to-speech
├── WakeWordDetector.kt       — "Hey MobileClaw" / custom wake word
├── VoiceActivityDetector.kt  — detect speech start/end
├── AudioSessionManager.kt   — manage audio focus, routing (speaker/BT/earpiece)
└── VoiceBridgeModule.kt     — RN bridge for voice UI
```

**Streaming architecture:**
1. Audio capture → `VoiceActivityDetector` (detects speech boundaries)
2. Speech chunks → `StreamingStt` (local Whisper or cloud) → partial transcripts
3. Partial transcripts streamed to UI via RN bridge event emitter
4. Complete transcript → Agent orchestrator
5. Agent response → `StreamingTts` (token-by-token as agent generates)
6. Audio output → speaker/BT/earpiece

### 5.9 OAuth / Auth Manager — `auth/`

Centralized authentication for all external integrations.

```
auth/
├── OAuthManager.kt           — OAuth2 flow handler (PKCE)
├── TokenStore.kt             — encrypted token storage (AndroidKeyStore)
├── ProviderAuth.kt           — LLM provider API key management
├── IntegrationAuth.kt        — per-integration auth state
└── SecurityPolicy.kt         — permission checks, capability gating
```

Supports:
- Google OAuth (Drive, Docs, Calendar, Gmail, YouTube)
- GitHub OAuth (repos, issues, PRs)
- Slack, Discord, Telegram bot tokens
- Spotify, Notion, and arbitrary OAuth2 providers
- All secrets encrypted at rest using Android KeyStore

### 5.10 Automotive Module — `automotive/`

When running on Android Automotive OS (AAOS) or connected to vehicle via OBD-II.

```
automotive/
├── AutomotiveManager.kt      — detect automotive environment
├── VehiclePropertyReader.kt  — Car API property reading (AAOS)
├── ObdBridge.kt              — OBD-II Bluetooth connection
├── DrivingModePolicy.kt      — safety restrictions while driving
└── CarNotificationBridge.kt  — car display notifications
```

---

## 6. Technology Stack

| Layer | Technology | Rationale |
|---|---|---|
| Language | **Kotlin** | Native Android, coroutines, Flow, null safety |
| Async | **Kotlin Coroutines + Flow** | Structured concurrency, streaming, cancellation |
| HTTP client | **OkHttp + Ktor Client** | SSE streaming, WebSocket, HTTP/2 |
| JSON | **kotlinx.serialization** | Compile-time, no reflection, fast |
| Database | **Room + SQLite** | Typed queries, migrations, FTS5 |
| DI | **Koin** (or manual) | Lightweight, no annotation processing |
| Local LLM | **llama.cpp (android JNI)** | GGUF models, GPU via Vulkan/OpenCL |
| Local LLM (alt) | **MediaPipe LLM Inference API** | .task models, GPU/NPU delegate |
| Local STT | **Whisper.cpp (JNI)** or **MediaPipe** | Streaming transcription |
| Local TTS | **Piper TTS** or **Android native TTS** | Offline speech synthesis |
| Local SD | **MNN + Stable Diffusion** | Image generation with QNN/GPU |
| Local embeddings | **MediaPipe Text Embedder** | On-device vector generation |
| Vision | **MediaPipe Vision** or **ONNX** | Image understanding |
| Background | **WorkManager + AlarmManager + ForegroundService** | Reliable long-running |
| RN Bridge | **React Native New Architecture (TurboModules)** | Type-safe, faster bridge |
| Security | **AndroidKeyStore + EncryptedSharedPreferences** | Encrypted secrets |

---

## 7. Data Flow Examples

### 7.1 User sends chat message (UI)

```
UI (ChatScreen) → RN Bridge → AgentBridgeModule.sendMessage(text)
  → MessageBus.post(UserMessage)
  → AgentOrchestrator.process()
    → ProviderRouter.chat(messages, tools) → Flow<StreamChunk>
      → [if tool_call] → ToolRegistry.execute(tool, params)
        → [e.g., SmsTool.send()] → Android SmsManager
      → loop until final response
    → Flow<StreamChunk> streamed back
  → RN Bridge EventEmitter → UI updates token by token
```

### 7.2 Telegram message arrives

```
TelegramChannel (long polling) → InboundDispatcher
  → MessageBus.post(ChannelMessage(source=telegram))
  → AgentOrchestrator.process()
    → ... (same as above)
  → OutboundDispatcher.send(response, channel=telegram)
  → TelegramChannel.sendMessage(chatId, text)
```

### 7.3 Incoming call triggers notification

```
IncomingCallReceiver.onReceive()
  → TriggerEngine.evaluate(CallEvent)
  → matches: CallTrigger(contact_group="courier")
  → fires: SendMessageAction(channel=telegram, template="Courier is calling: {phone}")
  → OutboundDispatcher.send(message, channel=telegram)
```

### 7.4 Voice command

```
Microphone → WakeWordDetector ("Hey MobileClaw")
  → AudioCapture starts
  → VoiceActivityDetector (detects speech end)
  → StreamingStt → partial transcripts → UI overlay
  → Complete transcript → MessageBus.post(VoiceMessage)
  → AgentOrchestrator.process()
  → Response → StreamingTts → speaker output
```

### 7.5 Scheduled trading task

```
AlarmManager fires at 09:30 EST
  → TaskScheduler.onAlarm(taskId)
  → AgentTaskAction("Execute morning trading strategy per config")
  → MessageBus.post(ScheduledTask)
  → AgentOrchestrator.process()
    → multiple tool calls: HttpRequestTool (market data), MemorySearchTool (strategy),
      HttpRequestTool (place orders), MemoryStoreTool (log trades)
    → SendMessageAction(telegram, "Morning trades executed: ...")
```

---

## 8. Android Service Architecture

```
┌──────────────────────────────────────────┐
│         AgentForegroundService            │
│  (START_STICKY, survives app close)      │
│                                           │
│  ┌────────────────────────────────────┐  │
│  │     AgentOrchestrator              │  │
│  │  (CoroutineScope tied to service)  │  │
│  └────────────────────────────────────┘  │
│                                           │
│  ┌─────────────┐  ┌──────────────────┐  │
│  │ ChannelHub  │  │ TriggerEngine    │  │
│  │ (listeners) │  │ (event watchers) │  │
│  └─────────────┘  └──────────────────┘  │
│                                           │
│  ┌─────────────┐  ┌──────────────────┐  │
│  │ VoicePipeline│ │ TaskScheduler    │  │
│  │ (wake word) │  │ (alarms)         │  │
│  └─────────────┘  └──────────────────┘  │
└──────────────────────────────────────────┘

┌──────────────────────────────────────────┐
│         BootCompletedReceiver            │
│  → restarts AgentForegroundService       │
└──────────────────────────────────────────┘

┌──────────────────────────────────────────┐
│     AgentAccessibilityService            │
│  (UI automation: tap, swipe, read)       │
│  → unchanged from current implementation │
└──────────────────────────────────────────┘
```

The foreground service is the single root of all background operation. It:
- Shows a persistent notification ("MobileClaw agent active")
- Holds a partial wake lock for long-running tasks
- Restarts automatically (`START_STICKY`) if killed
- Restarts after device reboot via `BootCompletedReceiver`
- All coroutines are scoped to the service — clean cancellation on stop

---

## 9. RN ↔ Kotlin Bridge Contract

We replace `ZeroClawDaemonModule` and `ZeroClawBackend` (JNI) with new **TurboModules**:

### `AgentBridgeModule`
```kotlin
// Replaces ZeroClawDaemonModule + ZeroClawBackend
@ReactMethod fun startAgent(config: ReadableMap, promise: Promise)
@ReactMethod fun stopAgent(promise: Promise)
@ReactMethod fun isAgentRunning(promise: Promise)
@ReactMethod fun sendMessage(text: String, sessionId: String, promise: Promise)
@ReactMethod fun getHistory(sessionId: String, promise: Promise)
@ReactMethod fun cancelCurrentTask(promise: Promise)

// Events emitted to JS:
// "onStreamToken" { token: String, sessionId: String }
// "onToolCall" { tool: String, params: JSON, sessionId: String }
// "onToolResult" { tool: String, result: JSON, sessionId: String }
// "onAgentStatus" { status: "thinking"|"acting"|"idle"|"error" }
```

### `DeviceToolsBridgeModule`
```kotlin
// Replaces AndroidAgentToolsModule (simplified — tools now run natively)
// Only exposes what RN UI needs to display/control
@ReactMethod fun getDeviceStatus(promise: Promise)  // battery, network, sensors summary
@ReactMethod fun getActivityLog(limit: Int, promise: Promise)
@ReactMethod fun getTriggers(promise: Promise)
@ReactMethod fun setTrigger(config: ReadableMap, promise: Promise)
@ReactMethod fun deleteTrigger(id: String, promise: Promise)
@ReactMethod fun getScheduledTasks(promise: Promise)
@ReactMethod fun setScheduledTask(config: ReadableMap, promise: Promise)
@ReactMethod fun deleteScheduledTask(id: String, promise: Promise)
```

### `InferenceBridgeModule`
```kotlin
// Replaces LocalLlmServerModule
@ReactMethod fun getAvailableModels(promise: Promise)
@ReactMethod fun downloadModel(url: String, name: String, promise: Promise)
@ReactMethod fun deleteModel(name: String, promise: Promise)
@ReactMethod fun getModelStatus(name: String, promise: Promise)
@ReactMethod fun getInferenceCapabilities(promise: Promise)  // GPU, NPU support

// Events:
// "onDownloadProgress" { name: String, progress: Float }
```

### `VoiceBridgeModule`
```kotlin
@ReactMethod fun startListening(promise: Promise)
@ReactMethod fun stopListening(promise: Promise)
@ReactMethod fun setWakeWord(word: String, promise: Promise)
@ReactMethod fun speak(text: String, promise: Promise)
@ReactMethod fun stopSpeaking(promise: Promise)

// Events:
// "onPartialTranscript" { text: String }
// "onFinalTranscript" { text: String }
// "onWakeWordDetected" {}
// "onSpeechStart" {}
// "onSpeechEnd" {}
```

---

## 10. Local Inference Detail

### Model format support matrix

| Format | Engine | Acceleration | Use case |
|---|---|---|---|
| `.gguf` | llama.cpp (JNI) | CPU, Vulkan GPU, OpenCL | Text LLMs (Llama, Mistral, Phi, Qwen) |
| `.task` / `.litertlm` | MediaPipe | GPU delegate, NPU (Tensor) | Gemma, optimized models |
| `.onnx` | ONNX Runtime | CPU, NNAPI, QNN | Various, cross-platform |
| `.mnn` | MNN | CPU, Vulkan, OpenCL, QNN | Stable Diffusion |
| Whisper `.bin` | whisper.cpp (JNI) | CPU, Vulkan | Speech-to-text |
| Piper `.onnx` | Piper TTS | CPU, NNAPI | Text-to-speech |

### Hardware acceleration auto-detection

```kotlin
object HardwareCapabilities {
    val chipset: Chipset = detectChipset()  // Snapdragon, Tensor, Exynos, MediaTek
    val hasVulkan: Boolean
    val hasOpenCL: Boolean
    val hasNnapi: Boolean
    val hasQnn: Boolean  // Qualcomm NPU (Snapdragon 8 Gen 3+)
    val hasTensorDelegate: Boolean  // Google Tensor

    fun bestAccelerator(format: ModelFormat): Accelerator {
        return when {
            format == GGUF && hasVulkan -> Accelerator.VULKAN
            format == TASK && hasTensorDelegate -> Accelerator.TENSOR_NPU
            format == TASK && hasQnn -> Accelerator.QNN_NPU
            format == ONNX && hasNnapi -> Accelerator.NNAPI
            else -> Accelerator.CPU
        }
    }
}
```

### Model management

```
/data/data/com.mobileclaw.app/files/models/
├── llm/
│   ├── phi-3-mini-4k-q4.gguf
│   └── gemma-2b.task
├── stt/
│   └── whisper-small.bin
├── tts/
│   └── piper-en-amy.onnx
├── embeddings/
│   └── all-minilm-l6-v2.onnx
└── image/
    └── sd-1.5-q8.mnn
```

User can:
1. Download models from a curated list in the app
2. Download from any HuggingFace URL
3. Specify a local path (e.g., model on SD card)
4. Model files are verified via SHA256 checksum

---

## 11. Project Structure (New)

```
mobileclaw/
├── mobile-app/                    — React Native app (KEPT)
│   ├── App.tsx
│   ├── src/
│   ├── ui/
│   └── android/
│       ├── app/
│       │   └── src/main/java/com/mobileclaw/app/
│       │       ├── MainActivity.kt          (kept)
│       │       ├── MainApplication.kt       (kept, modified)
│       │       ├── AgentAccessibilityService.kt (kept)
│       │       ├── RuntimeBootReceiver.kt   (kept, modified)
│       │       ├── DirectPhotoCaptureActivity.kt (kept)
│       │       ├── AgentBrowserActivity.kt  (kept)
│       │       │
│       │       ├── bridge/                  — NEW: RN bridge modules
│       │       │   ├── AgentBridgeModule.kt
│       │       │   ├── DeviceToolsBridgeModule.kt
│       │       │   ├── InferenceBridgeModule.kt
│       │       │   ├── VoiceBridgeModule.kt
│       │       │   └── BridgePackage.kt
│       │       │
│       │       │── REMOVED: ZeroClawBackend.kt, ZeroClawDaemon*.kt
│       │       │
│       │       └── ... (other kept files)
│       └── build.gradle           (modified — remove Rust/JNI, add new deps)
│
├── agent-core/                    — NEW: Kotlin library module
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/mobileclaw/agent/
│       ├── core/
│       │   ├── AgentOrchestrator.kt
│       │   ├── AgentSession.kt
│       │   ├── AgentPlanner.kt
│       │   ├── AgentConfig.kt
│       │   ├── MessageBus.kt
│       │   └── AgentForegroundService.kt
│       ├── providers/
│       │   ├── ProviderRouter.kt
│       │   ├── StreamingResponse.kt
│       │   ├── cloud/ ...
│       │   ├── local/ ...
│       │   └── subscription/
│       ├── tools/
│       │   ├── ToolRegistry.kt
│       │   ├── device/ ...
│       │   ├── apps/ ...
│       │   ├── filesystem/ ...
│       │   ├── network/ ...
│       │   ├── integrations/ ...
│       │   ├── ai/ ...
│       │   ├── memory/ ...
│       │   └── automotive/ ...
│       ├── channels/
│       │   ├── ChannelManager.kt
│       │   ├── telegram/ ...
│       │   ├── whatsapp/ ...
│       │   ├── slack/ ...
│       │   ├── discord/ ...
│       │   ├── sms/ ...
│       │   └── email/ ...
│       ├── triggers/
│       │   ├── TriggerEngine.kt
│       │   ├── sources/ ...
│       │   └── actions/ ...
│       ├── scheduler/
│       │   ├── TaskScheduler.kt
│       │   └── ScheduleStore.kt
│       ├── memory/
│       │   ├── MemoryManager.kt
│       │   ├── MemoryStore.kt
│       │   ├── VectorStore.kt
│       │   └── EmbeddingEngine.kt
│       ├── voice/
│       │   ├── VoicePipeline.kt
│       │   ├── StreamingStt.kt
│       │   ├── StreamingTts.kt
│       │   └── WakeWordDetector.kt
│       ├── auth/
│       │   ├── OAuthManager.kt
│       │   └── TokenStore.kt
│       └── automotive/
│           ├── AutomotiveManager.kt
│           └── ObdBridge.kt
│
├── inference-engines/             — NEW: native inference wrappers
│   ├── llama-cpp-android/         — prebuilt llama.cpp JNI
│   ├── whisper-cpp-android/       — prebuilt whisper.cpp JNI
│   └── build.gradle.kts
│
├── docs/                          — documentation (kept)
├── .github/                       — CI/CD (modified)
├── CLAUDE.md                      — (updated for new architecture)
└── settings.gradle.kts            — (new, multi-module)
```

---

## 12. Migration Path

### Phase 1: Foundation (Week 1-2)
1. Create `agent-core/` Kotlin module with Gradle build
2. Implement `AgentOrchestrator`, `MessageBus`, `AgentForegroundService`
3. Implement `ProviderRouter` with `OpenRouterProvider` (cloud only)
4. Implement `AgentBridgeModule` (RN bridge)
5. Replace `ZeroClawDaemonModule` → `AgentBridgeModule` in JS
6. Verify: chat works end-to-end via cloud provider with streaming

### Phase 2: Device Tools (Week 2-3)
7. Port `AndroidAgentToolsModule` capabilities into `tools/device/`
8. Implement `ToolRegistry` with JSON schema self-description
9. Agent can call tools and receive results
10. Verify: agent can send SMS, read contacts, take photos, etc.

### Phase 3: Channels & Proactive Messaging (Week 3-4)
11. Implement `TelegramChannel` with long polling
12. Implement `ChannelManager`, `InboundDispatcher`, `OutboundDispatcher`
13. Agent can receive messages from Telegram and respond
14. Agent can proactively send messages to Telegram
15. Add Slack, Discord channels

### Phase 4: Triggers & Scheduler (Week 4-5)
16. Implement `TriggerEngine` with call, SMS, location triggers
17. Implement `TaskScheduler` with `AlarmManager` + `WorkManager`
18. Implement `ScheduleStore` (SQLite persistence)
19. Verify: trigger-based automation works (call → Telegram notification)

### Phase 5: Local Inference (Week 5-7)
20. Integrate llama.cpp JNI for GGUF models
21. Integrate MediaPipe LLM Inference for .task models
22. Implement `ModelManager` (download, cache, validate)
23. Implement `WhisperEngine` for local STT
24. Implement `TtsEngine` for local TTS
25. Hardware acceleration auto-detection

### Phase 6: Voice Pipeline (Week 7-8)
26. Implement `StreamingStt` with streaming partial transcripts
27. Implement `StreamingTts` with token-by-token output
28. Implement `WakeWordDetector`
29. Implement `VoiceBridgeModule` for RN
30. Verify: full voice loop works (wake word → speech → agent → speech)

### Phase 7: Memory & Long-term Operation (Week 8-9)
31. Implement `MemoryStore` (SQLite + FTS5)
32. Implement `VectorStore` (embeddings + cosine similarity)
33. Implement on-device `EmbeddingEngine`
34. Implement `ContextCompactor` for infinite conversations
35. Verify: agent remembers facts across sessions and channels

### Phase 8: Integrations & Polish (Week 9-10)
36. Implement OAuth flow for Google, GitHub, Spotify, Notion
37. Implement integration tools (GoogleDocs, Gmail, GitHub)
38. Automotive module (OBD-II, AAOS detection)
39. Additional cloud providers (Anthropic, OpenAI, DeepSeek, Google)
40. Image generation (local SD + cloud DALL-E)

### Phase 9: Cleanup (Week 10-11)
41. Remove all Rust code (`src/`, `Cargo.toml`, etc.)
42. Remove `build_android_jni.sh`, JNI-related CI
43. Update CLAUDE.md for new architecture
44. Update documentation
45. Update CI/CD for Kotlin-only build

---

## 13. Language Decision: Why Kotlin

| Alternative | Verdict | Reason |
|---|---|---|
| **Kotlin** | **Selected** | Native Android citizen, coroutines for async, Flow for streaming, full Android SDK access, Jetpack integration, huge ecosystem |
| Rust (current) | Rejected | JNI overhead, cross-compile complexity, no direct Android API access, two build systems |
| Java | Rejected | Verbose, no coroutines/Flow, Kotlin interops perfectly anyway |
| C++ | Rejected | No Android SDK access, memory safety concerns, even worse DX than Rust JNI |
| Dart/Flutter | Rejected | Would require rewriting the RN frontend |
| Python (Kivy) | Rejected | Performance, packaging, no native Android API access |

Kotlin is the only language that gives us:
- Zero-overhead Android API access (sensors, contacts, SMS, camera — all native)
- Structured concurrency (coroutines) for long-running agent tasks
- Flow for streaming (token-by-token from LLM to UI)
- WorkManager/AlarmManager integration for persistent background operation
- First-class support in Android Studio, Gradle, and Google's toolchain
- Easy JNI when needed (for llama.cpp, whisper.cpp native libraries)

---

## 14. Key Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Battery drain from always-on agent | Battery optimization modes, adaptive polling intervals, doze-aware scheduling |
| Android killing foreground service | `START_STICKY` + `BOOT_COMPLETED` restart + periodic WorkManager heartbeat |
| llama.cpp / whisper.cpp JNI stability | Pre-built binaries from established projects (ggml-org/llama.cpp), crash isolation |
| Large APK from local models | Models downloaded separately, not bundled; APK stays lean |
| Rate limiting on messenger APIs | Exponential backoff, queue-based dispatch, respect API limits |
| Memory pressure from local inference | Offload models when not in use, single model loaded at a time, memory monitoring |
| Privacy/security of full device access | Per-tool permission checks, user consent UI, encrypted storage, no data exfiltration |

---

## 15. Success Criteria

- [ ] Agent runs 24/7 as foreground service without crashing
- [ ] Chat streaming works with <100ms first-token latency (cloud) / <500ms (local)
- [ ] All messenger channels (Telegram, Slack, Discord) work bidirectionally
- [ ] Agent proactively sends messages based on triggers
- [ ] Scheduled tasks execute reliably (survive reboots)
- [ ] Local GGUF model inference works with GPU acceleration
- [ ] Voice pipeline: wake word → STT → agent → TTS in under 3 seconds
- [ ] Memory persists across app restarts and is searchable
- [ ] OAuth integrations work (GitHub, Google, etc.)
- [ ] Build is a standard `./gradlew assembleRelease` — no Rust toolchain needed
