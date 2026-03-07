# Guappa — Master Implementation Plan

**Date**: 2026-03-06
**Status**: Proposal
**Scope**: Full product rebrand + native Kotlin agent backend + proactive agent + app control + push notifications + live config + docs overhaul + E2E testing

---

## 0. Vision

**Guappa** — это AI-помощница (девочка), работающая как автономный агент на Android-устройствах. Она умеет управлять другими приложениями, отвечать на сообщения, читать письма вслух, ставить будильники, писать посты в Twitter, выполнять задачи самостоятельно и уведомлять пользователя о результатах через push-уведомления. Guappa проактивна: она сама пишет в чат о выполнении заданий и задаёт уточняющие вопросы.

**Key principles:**
- Female persona ("она", "Guappa"), friendly and proactive
- Production-grade: complete provider/model/tool coverage as of March 2026
- Native Android backend (pure Kotlin, no Rust/JNI)
- Push notifications for all agent-initiated events
- Real-time config application from UI (no daemon restart) via TurboModules + StateFlow
- App control via **Android AppFunctions + UI Automation Framework** (NOT Accessibility Service — banned by Google Play for AI agents)
- Intent system for standard app actions (alarms, share, email, maps, etc.)
- Complete documentation rewrite under Guappa branding
- Maestro E2E test coverage for every feature

> **CRITICAL POLICY NOTE (February 2026):** Google Play explicitly prohibits using
> AccessibilityService API for autonomous AI agent decision-making. Only apps declared
> as accessibility tools (`isAccessibilityTool="true"`) for assisting people with
> disabilities are exempt. Android 16+ Advanced Protection Mode can disable non-a11y
> apps from using Accessibility API entirely. Our agent MUST use the new
> **AppFunctions** (structured API for app-to-agent communication) and
> **UI Automation Framework** (Google's sanctioned agent automation layer, currently
> in early preview on Galaxy S26 and Pixel 10) instead.
>
> Sources:
> - https://android-developers.googleblog.com/2026/02/the-intelligent-os-making-ai-agents.html
> - https://support.google.com/googleplay/android-developer/answer/10964491

---

## 1. Rebranding: ZeroClaw/MobileClaw → Guappa

### 1.1 Scope of Rebrand

**All references must be updated:**

| Old Name | New Name | Context |
|----------|----------|---------|
| ZeroClaw | Guappa | Backend, runtime, daemon, services |
| MobileClaw | Guappa | App name, UI, notifications, user-facing strings |
| OpenClaw | Guappa | Any legacy references |
| `com.mobileclaw.app` | `com.guappa.app` | Android package name |
| `zeroclaw-mobile.toml` | `guappa.toml` | Config file name |
| `libzeroclaw.so` | _(removed — no more JNI)_ | Native library |

### 1.2 Files Requiring Rebrand

**Android Native (Kotlin):**
- `AndroidAgentToolsModule.kt` — notification titles "MobileClaw" → "Guappa"
- `RuntimeBridge.kt` — notification channel "MobileClaw Runtime", config file name
- `RuntimeAlwaysOnService.kt` — foreground service title
- `AgentBrowserActivity.kt` — User-Agent header
- `ZeroClawDaemonModule.kt` → rename to `GuappaAgentModule.kt`
- `ZeroClawDaemonService.kt` → rename to `GuappaAgentService.kt`
- `ZeroClawBackend.kt` → remove (JNI bridge deleted)
- `AndroidManifest.xml` — package, service names, labels
- `strings.xml` — app name, service labels
- `build.gradle` — applicationId
- `settings.gradle` — project name

**React Native (TypeScript):**
- `api/mobileclaw.ts` — X-Title header, system instruction identity
- `runtime/supervisor.ts` — activity log titles
- `runtime/session.ts` — error messages, system instructions
- `screens/tabs/ChatScreen.tsx` — "MobileClaw is thinking" → "Guappa думает..."
- `native/zeroClawDaemon.ts` → rename to `guappaAgent.ts`
- `state/mobileclaw.ts` → rename to `state/guappa.ts`
- `app.config.js` — app name, slug
- `package.json` — name field

**Persona integration:**
- System instruction must establish Guappa as female AI assistant
- Chat responses should reflect friendly, proactive personality
- Default greeting: "Привет! Я Guappa, твой AI-помощник 🎀"
- Pronoun consistency: "она" in Russian, "she" in English

### 1.3 Documentation Removal & Recreation

**DELETE entirely (167 .md files in docs/):**
- All `docs/` content — ZeroClaw-branded docs no longer applicable
- All `README*.md` in root (except keep root `README.md` as rewritten)
- `CLAUDE.md` — will be rewritten for Guappa protocol
- `CONTRIBUTING.md` — will be rewritten

**CREATE new documentation (see Phase 8 for full plan):**
- New `README.md` — Guappa product overview
- New `docs/` structure — Guappa-specific
- Single language: English (i18n deferred to post-launch)

---

## 2. Phase Overview

| Phase | Name | Description | Depends On | Detailed Doc |
|-------|------|-------------|------------|--------------|
| **1** | Foundation | Agent core, orchestrator, message bus, foreground service | — | [phase-01-foundation.md](phase-01-foundation.md) |
| **2** | Provider Router | Dynamic model fetching, capability-based selection, hardware accel | Phase 1 | [phase-02-provider-router.md](phase-02-provider-router.md) |
| **3** | Tool Engine | 69 tools: device, app, web (fetch/search), AI, automation | Phase 1 | [phase-03-tool-engine.md](phase-03-tool-engine.md) |
| **4** | Proactive Agent & Push | Agent-initiated messages, push notifications, event triggers | Phase 1, 3 | [phase-04-proactive-push.md](phase-04-proactive-push.md) |
| **5** | Channel Hub | Telegram, Discord, Slack, WhatsApp, Signal, Matrix, Email, SMS | Phase 1, 4 | [phase-05-channel-hub.md](phase-05-channel-hub.md) |
| **6** | Voice Pipeline | STT + streaming TTS + wake word + VAD + voice mode | Phase 2 | [phase-06-voice-pipeline.md](phase-06-voice-pipeline.md) |
| **7** | Memory & Context | 5-tier memory, auto-summarization, recursive LLM, RAG, consolidation | Phase 1 | [phase-07-memory-context.md](phase-07-memory-context.md) |
| **8** | Documentation | Complete Guappa docs from scratch (40+ files) | Phase 1-7 | [phase-08-documentation.md](phase-08-documentation.md) |
| **9** | Testing & QA | Maestro E2E (40+ flows), JUnit, Espresso, resilience, CI | Phase 1-7 | [phase-09-testing-qa.md](phase-09-testing-qa.md) |
| **10** | Live Config | TurboModules bridge, reactive StateFlow, hot-swap everything | Phase 1, 2, 3 | [phase-10-live-config.md](phase-10-live-config.md) |

### Key Design Decisions (updated)

1. **NO HARDCODED MODELS** — all model lists fetched dynamically via provider APIs (see Phase 2)
2. **Capability-based settings** — separate provider+model selection per type: text, vision, image gen, video gen, STT, TTS, embedding, code, reasoning, web search (see Phase 2)
3. **Mandatory web tools** — `web_fetch`, `web_search`, `web_scrape`, `browser_session` (see Phase 3)
4. **69 tools total** — device (17) + app (14) + social (5) + automation (7) + file (6) + web (6) + AI (9) + system (5) (see Phase 3)
5. **5-tier memory** — working → short-term → long-term → episodic → semantic/vector (see Phase 7)
6. **Auto-summarization** — incremental, recursive (multi-level), map-reduce for long histories (see Phase 7)
7. **Recursive LLM** — task decomposition, long-doc processing, self-reflection (see Phase 7)
8. **TurboModules** — React Native New Architecture for zero-overhead JS↔Kotlin bridge (see Phase 10)

---

## Phase 1: Foundation — Agent Core

### 1.0 Research

**Study before implementing:**
- Android foreground service best practices (Android 14+ restrictions, FGS types)
- Kotlin coroutines structured concurrency patterns for long-running agents
- MessageBus patterns (Kotlin Channel, SharedFlow, EventBus)
- Context window management strategies (sliding window, summarization, hybrid)
- Existing implementations: ZeroClaw agent loop (`src/agent/`), LangGraph agent patterns

### 1.1 Architecture

```
agent/
├── GuappaOrchestrator.kt      — main agent loop (coroutine-based)
├── GuappaSession.kt           — session state, context window management
├── GuappaPlanner.kt           — task decomposition, multi-step planning
├── GuappaConfig.kt            — runtime configuration (hot-reloadable)
├── GuappaPersona.kt           — personality, system instruction, greeting
├── MessageBus.kt              — internal pub/sub (SharedFlow-based)
├── TaskManager.kt             — concurrent task tracking and status
├── ContextCompactor.kt        — automatic context window compaction
└── GuappaForegroundService.kt — Android foreground service (DATA_SYNC type)
```

### 1.2 Features

1. **Agent Orchestrator**
   - Coroutine-based infinite loop processing MessageBus events
   - Sequential per-session message processing
   - Multi-session support (chat + background tasks concurrently)
   - Automatic retry with exponential backoff on provider errors
   - Graceful degradation when provider unavailable

2. **Session Management**
   - Context window with configurable max tokens (default: 128K)
   - Automatic compaction when approaching limit (summarize old messages)
   - Session persistence to SQLite (survive app restart)
   - Session isolation between chat, triggers, and background tasks

3. **Message Bus**
   - `SharedFlow`-based pub/sub
   - Message types: `UserMessage`, `SystemEvent`, `TriggerEvent`, `ToolResult`, `AgentMessage`
   - Priority queue for urgent messages (incoming calls)
   - Backpressure handling with `BUFFER_OVERFLOW.DROP_OLDEST`

4. **Foreground Service**
   - `FOREGROUND_SERVICE_TYPE_DATA_SYNC` (Android 14+)
   - Persistent notification showing Guappa status
   - Auto-restart on kill (START_STICKY)
   - Battery optimization exemption request on first launch
   - WakeLock for background processing

5. **Task Manager**
   - Track multi-step task execution
   - Task states: pending → running → completed/failed/cancelled
   - Progress reporting to UI via MessageBus
   - Concurrent task limit (configurable, default: 3)

6. **Persona**
   - System instruction template with Guappa identity
   - Locale-aware greetings
   - Proactive behavior configuration (when to initiate messages)
   - Personality traits: helpful, friendly, proactive, female

### 1.3 Documentation Plan
- `docs/architecture/agent-core.md` — orchestrator design
- `docs/architecture/message-bus.md` — event system design
- `docs/guides/getting-started.md` — setup and first run

### 1.4 Test Plan
- Unit: Orchestrator message processing loop
- Unit: Session compaction logic
- Unit: MessageBus delivery guarantees
- Integration: Foreground service lifecycle (start/stop/restart)
- Maestro E2E: App launch → service starts → notification visible
- Resilience: Kill app → auto-restart → session restored
- Resilience: Reboot device → boot receiver → service starts

---

## Phase 2: Provider Router — All LLM Providers

### 2.0 Research

**Study before implementing:**
- All available LLM provider APIs as of March 2026
- OpenAI API specification (streaming, tool_use, structured output)
- On-device inference: llama.cpp Android build, **LiteRT-LM** (replaces MediaPipe LLM), ONNX Runtime Mobile, Qualcomm GENIE
- Hardware acceleration: **LiteRT** (replaces NNAPI), Qualcomm QNN SDK, Samsung ONE SDK, Google AI Edge, MediaTek NeuroPilot
- Model quantization: GGUF, GPTQ, AWQ, EXL2 formats
- Token counting and cost tracking per provider

### 2.1 Architecture

```
providers/
├── ProviderRouter.kt          — routes to correct backend, model fallback chain
├── ProviderInterface.kt       — unified interface (chat, stream, embed)
├── ProviderConfig.kt          — per-provider settings (key, url, model, temp)
├── StreamingResponse.kt       — Kotlin Flow<ChatChunk> abstraction
├── TokenCounter.kt            — tiktoken-compatible counter per model family
├── CostTracker.kt             — per-request cost estimation
├── cloud/
│   ├── CloudProvider.kt       — base HTTP provider (OkHttp + Ktor)
│   ├── OpenAIProvider.kt      — OpenAI (GPT-5.4, GPT-5-mini, GPT-5-nano, o3-pro)
│   ├── AnthropicProvider.kt   — Anthropic (Claude Opus 4.6, Sonnet 4.6, Haiku 4.5)
│   ├── GoogleProvider.kt      — Google (Gemini 3.1 Pro, Gemini 3 Flash, Gemma 3n)
│   ├── DeepSeekProvider.kt    — DeepSeek (V3.2, R1)
│   ├── MistralProvider.kt     — Mistral (Large 3, Magistral Medium/Small, Codestral)
│   ├── MetaProvider.kt        — Meta (Llama 4 Scout, Maverick via API partners)
│   ├── XAIProvider.kt         — xAI (Grok 4, Grok 4 Mini)
│   ├── CohereProvider.kt      — Cohere (Command R+, Command A)
│   ├── GroqProvider.kt        — Groq (fast inference, Llama/Mixtral)
│   ├── TogetherProvider.kt    — Together AI (open model hosting)
│   ├── FireworksProvider.kt   — Fireworks AI (fast inference)
│   ├── PerplexityProvider.kt  — Perplexity (online search-augmented)
│   ├── OpenRouterProvider.kt  — OpenRouter (multi-provider gateway)
│   ├── CopilotProvider.kt     — GitHub Copilot API
│   ├── QwenProvider.kt        — Alibaba Qwen (Qwen 3.5, QwQ)
│   ├── GLMProvider.kt         — Zhipu GLM-4-Plus
│   ├── MoonshotProvider.kt    — Moonshot (Kimi K2.5)
│   ├── MinimaxProvider.kt     — Minimax
│   ├── VeniceProvider.kt      — Venice AI
│   ├── LMStudioProvider.kt    — LM Studio (local, OpenAI-compatible)
│   ├── OllamaProvider.kt      — Ollama (local, OpenAI-compatible)
│   └── CustomProvider.kt      — any OpenAI-compatible endpoint
├── local/
│   ├── LocalInferenceEngine.kt — unified local inference interface
│   ├── LlamaCppEngine.kt      — llama.cpp via JNI (GGUF models, widest model support)
│   ├── LiteRTLMEngine.kt     — Google LiteRT-LM (replaces MediaPipe LLM, powers Gemini Nano)
│   ├── QualcommGenieEngine.kt — Qualcomm GENIE (best perf on Snapdragon NPU)
│   ├── ONNXEngine.kt          — ONNX Runtime Mobile
│   ├── CactusEngine.kt        — Cactus SDK (sub-50ms TTFT, cross-platform)
│   └── ModelManager.kt        — download, cache, quantization selection
└── acceleration/
    ├── HardwareProbe.kt       — detect available accelerators (SoC, NPU, GPU)
    ├── LiteRTDelegate.kt      — Google LiteRT unified runtime (replaces NNAPI)
    ├── QualcommQNNDelegate.kt — Qualcomm QNN SDK (Hexagon NPU, 75 TOPS on SD 8 Elite)
    ├── SamsungONEDelegate.kt  — Samsung ONE (Exynos NPU, ~40 TOPS)
    ├── GoogleEdgeTPUDelegate.kt — Tensor G5 TPU (Pixel-optimized)
    ├── MediaTekNeuroPilot.kt  — MediaTek NeuroPilot (Dimensity 9400 APU, 50+ TOPS)
    ├── VulkanComputeDelegate.kt — Vulkan compute shaders (Adreno/Mali GPU)
    └── OpenCLDelegate.kt      — OpenCL fallback (older Adreno GPUs)
```

### 2.2 Complete Provider & Model List (March 2026)

#### Cloud Providers

| Provider | Models | Streaming | Tool Use | Vision | Auth |
|----------|--------|-----------|----------|--------|------|
| **OpenAI** | **GPT-5.4** (1M ctx, reasoning levels), GPT-5.4-pro, GPT-5-mini, GPT-5-nano, o3-pro | ✅ | ✅ | ✅ | API key |
| **Anthropic** | **Claude Opus 4.6** (200K, 1M beta), **Claude Sonnet 4.6**, Claude Haiku 4.5 | ✅ | ✅ | ✅ | API key |
| **Google** | **Gemini 3.1 Pro** (preview), **Gemini 3 Flash**, Gemini 3.1 Flash-Lite, Gemini 2.5 Pro/Flash | ✅ | ✅ | ✅ | API key / OAuth |
| **DeepSeek** | **DeepSeek-V3.2**, DeepSeek-V3.2-Speciale, DeepSeek-R1-0528 | ✅ | ✅ | ✅ | API key |
| **Mistral** | **Mistral Large 3** (675B MoE), Magistral Medium 1.2, Magistral Small, Codestral, Pixtral Large | ✅ | ✅ | ✅ | API key |
| **Meta** | **Llama 4 Maverick** (400B/17B active), **Llama 4 Scout** (109B/17B active, 10M ctx) | ✅ | ✅ | ✅ | via API partners |
| **xAI** | **Grok 4**, Grok 4 Mini | ✅ | ✅ | ✅ | API key |
| **Cohere** | **Command A**, Command A Reasoning, Command A Vision, Embed v4 | ✅ | ✅ | ✅ | API key |
| **Groq** | Llama 4 Scout, DeepSeek-R1, Qwen QwQ (ultra-fast inference) | ✅ | ✅ | ❌ | API key |
| **Together** | 100+ open models hosted | ✅ | ✅ | Varies | API key |
| **Fireworks** | Llama 4, DeepSeek, Qwen (fast inference) | ✅ | ✅ | Varies | API key |
| **Perplexity** | **Sonar Pro**, Sonar, Sonar Reasoning Pro, Sonar Deep Research | ✅ | ❌ | ❌ | API key |
| **OpenRouter** | 200+ models (unified gateway) | ✅ | ✅ | Varies | API key |
| **Qwen** | **Qwen 3.5** series (72B, 32B, 14B, 7B), Qwen 3.5 Small (0.8B-9B), QwQ | ✅ | ✅ | ✅ | API key |
| **GLM** | GLM-4-Plus, GLM-4V-Plus | ✅ | ✅ | ✅ | API key |
| **Moonshot** | **Kimi K2.5** (multimodal, Agent Swarm) | ✅ | ✅ | ✅ | API key |
| **Minimax** | MiniMax-01 | ✅ | ✅ | ❌ | API key |
| **Venice** | Open models (privacy-focused) | ✅ | ✅ | Varies | API key |
| **GitHub Copilot** | GPT-5.4, Claude Sonnet 4.6 (via Copilot) | ✅ | ✅ | ❌ | OAuth |
| **LM Studio** | Any GGUF model (local) | ✅ | ✅ | ❌ | None |
| **Ollama** | Any GGUF model (local) | ✅ | ✅ | Varies | None |

#### Local/On-Device Models

| Model | Size | Quantization | Use Case | Min RAM |
|-------|------|-------------|----------|---------|
| Gemini Nano | ~built-in | LiteRT-LM native | Google Pixel/Samsung (pre-installed) | 4GB |
| Gemma 3n 5B (2B effective) | ~1.5GB | Q4_K_M GGUF | Mobile-first, 2B memory footprint | 4GB |
| Qwen 3.5 Small 0.8B | ~500MB | Q4_K_M GGUF | Ultra-fast, simple tasks | 2GB |
| Qwen 3.5 Small 3B | ~2GB | Q4_K_M GGUF | Fast, good quality | 4GB |
| SmolLM3 3B | ~2GB | Q4_K_M GGUF | Think/no-think modes, ultra-efficient | 4GB |
| Phi-4-mini 3.8B | ~2.5GB | Q4_K_M GGUF | Microsoft, 128K context, code-optimized | 6GB |
| Ministral 3 | ~2GB | Q4_K_M GGUF | Mistral edge model | 4GB |
| Qwen 3.5 Small 9B | ~6GB | Q4_K_M GGUF | High quality, flagship phones | 8GB |
| Gemma 3 4B | ~2.8GB | Q4_K_M GGUF | Google-optimized, edge-first | 6GB |
| DeepSeek-R1-Distill 1.5B | ~1GB | Q4_K_M GGUF | Reasoning on device | 4GB |
| Llama 4 Scout 17B (MoE) | ~10GB | Q4_K_M GGUF | Best quality, 16GB+ RAM | 16GB |
| Qwen 3.5-VL-7B | ~5GB | Q4_K_M GGUF | Vision + language on device | 8GB |

**Performance expectations (March 2026):**
- Flagship (SD 8 Elite, 16GB): 15-30 tok/s for 7B, targeting 200 tok/s with NPU
- Mid-range (SD 7+ Gen 3, 8GB): 5-15 tok/s for 4B
- Budget (4-6GB RAM): 3-8 tok/s for 1.5B
- Rule of thumb: model file size × 1.5 = RAM needed

#### Hardware Acceleration Matrix

| Accelerator | Vendor | Devices | API | TOPS | Notes |
|-------------|--------|---------|-----|------|-------|
| **Hexagon NPU** | Qualcomm | SD 8 Elite (Gen 4), SD 8 Gen 3, SD 7+ Gen 3 | QNN SDK 2.x | **75 TOPS** (Elite) | 4.5x faster LLM inference vs Gen 3, attention mechanism HW |
| **Adreno GPU** | Qualcomm | All Snapdragon | Vulkan Compute / OpenCL | ~10 TOPS | Good fallback, shared with rendering |
| **Exynos NPU** | Samsung | Exynos 2400/2500 | Samsung ONE SDK | ~40 TOPS | Samsung flagships, Galaxy AI |
| **Mali GPU** | ARM | MediaTek, Exynos (older) | Vulkan Compute / OpenCL | ~5 TOPS | Wide compatibility, lower perf |
| **APU** | MediaTek | Dimensity 9300/9400 | MediaTek NeuroPilot | **50+ TOPS** | LiteRT integration built-in |
| **Tensor TPU** | Google | Pixel 9/10 (Tensor G4/G5) | Google AI Edge SDK | N/A | Powers Gemini Nano natively |
| **LiteRT** | Google | API 27+ (all devices) | LiteRT Runtime | Varies | **Replaces NNAPI** — unified CPU/GPU/NPU |
| **CPU (NEON)** | ARM | All ARM64 devices | llama.cpp built-in | ~2 TOPS | Always available, well-optimized |

> **Note:** NNAPI is deprecated. Google replaced it with **LiteRT** (repositioned TensorFlow Lite)
> as the unified on-device runtime. LiteRT provides CPU, GPU, and NPU delegates for Qualcomm and
> MediaTek hardware with consistent behavior across vendors. NPU is up to 100x faster than CPU
> and 10x faster than GPU for AI workloads.

### 2.3 Features

1. **Provider Router** — model fallback chain, auto-retry, load balancing
2. **Streaming** — Kotlin Flow-based streaming with backpressure
3. **Tool Use** — unified tool calling format across all providers
4. **Vision** — image input support (camera, gallery, screenshots)
5. **Cost Tracking** — per-request cost estimation and budget alerts
6. **Token Counting** — accurate counting per model family
7. **Hardware Probing** — auto-detect best accelerator on device
8. **Model Download** — background download with progress, resume, integrity check
9. **Quantization Selection** — auto-select best quantization for device RAM
10. **Hot Swap** — switch provider/model without restarting agent (see Phase 10)

### 2.4 Documentation Plan
- `docs/reference/providers.md` — all providers with setup instructions
- `docs/reference/models.md` — complete model catalog
- `docs/reference/local-inference.md` — on-device setup guide
- `docs/reference/hardware-acceleration.md` — device compatibility matrix
- `docs/guides/choosing-a-model.md` — decision guide

### 2.5 Test Plan
- Unit: Each cloud provider — auth, streaming, tool calling, error handling
- Unit: Local inference — model load, generate, unload
- Unit: Hardware probe — detect accelerators correctly
- Integration: Provider fallback chain (primary fails → fallback activates)
- Integration: Model hot-swap during conversation
- Maestro E2E: Settings → change provider → send message → get response
- Maestro E2E: Download local model → switch to local → inference works
- Resilience: Provider API down → graceful error in chat
- Resilience: Local model OOM → graceful degradation to smaller model

---

## Phase 3: Tool Engine — Device Tools & App Control

### 3.0 Research

**Study before implementing:**
- **Android AppFunctions API** (Feb 2026) — structured API for app-to-agent communication
- **Android UI Automation Framework** (Feb 2026) — sanctioned agent automation (preview on Galaxy S26, Pixel 10)
- **DroidRun** (3.8k GitHub stars) — open-source mobile AI agent framework, accessibility tree → structured data
- Android Intent system — implicit/explicit intents for app control
- `AlarmManager` API for setting alarms/timers (SCHEDULE_EXACT_ALARM denied by default on Android 14+)
- Social media app deep links (Twitter/X, Instagram, etc.)
- Android `ContentProvider` for contacts, calendar, call log, SMS
- `MediaProjection` API for screenshots
- `NotificationListenerService` for reading notifications
- Android `AutofillService` for form filling
- **Google Play Accessibility policy** — AccessibilityService BANNED for autonomous AI agent use

> **CRITICAL**: Do NOT use AccessibilityService for autonomous AI agent control.
> Google Play will reject the app. Use AppFunctions + UI Automation Framework instead.
> Accessibility Service may be used ONLY for development/testing (sideloaded builds)
> or as declared accessibility tool for users with disabilities.

### 3.1 Architecture

```
tools/
├── ToolEngine.kt               — tool registry, schema validation, execution
├── ToolInterface.kt            — base tool interface (name, schema, execute)
├── ToolResult.kt               — structured result (success/error/needs_approval)
├── ToolPermissions.kt          — per-tool permission checking
├── device/
│   ├── SmsTool.kt              — read/send SMS
│   ├── CallTool.kt             — place/end calls, read call log
│   ├── ContactsTool.kt         — CRUD contacts
│   ├── CalendarTool.kt         — CRUD calendar events
│   ├── CameraTool.kt           — take photo, record video
│   ├── LocationTool.kt         — get current location, geofencing
│   ├── AudioRecordTool.kt      — record ambient audio
│   ├── SensorTool.kt           — accelerometer, gyro, proximity, light
│   ├── BatteryTool.kt          — battery status, charging, health
│   ├── NetworkTool.kt          — WiFi/cellular status, speed test
│   ├── BluetoothTool.kt        — scan, connect, data transfer
│   ├── NFCTool.kt              — read/write NFC tags
│   ├── ClipboardTool.kt        — get/set clipboard
│   ├── VibrateTool.kt          — haptic feedback patterns
│   ├── FlashlightTool.kt       — toggle flashlight
│   ├── ScreenBrightnessTool.kt — adjust brightness
│   └── VolumeTool.kt           — adjust media/ring/alarm volume
├── apps/
│   ├── AppLaunchTool.kt        — launch any installed app by package name
│   ├── AppListTool.kt          — list installed apps
│   ├── IntentTool.kt           — fire arbitrary Android Intents
│   ├── AlarmTool.kt            — set/cancel alarms via AlarmClock intents
│   ├── TimerTool.kt            — set countdown timers
│   ├── ReminderTool.kt         — create reminders (Google Keep, system)
│   ├── EmailTool.kt            — compose/read email (Intent + content provider)
│   ├── BrowserTool.kt          — open URLs, fetch page content
│   ├── MapsTool.kt             — open maps, navigate to address
│   ├── MusicTool.kt            — play/pause music (MediaSession)
│   ├── SettingsNavTool.kt      — open specific Android settings pages
│   └── ShareTool.kt            — share content to any app via share sheet
├── social/
│   ├── TwitterPostTool.kt      — compose tweet via Intent/deep link
│   ├── InstagramTool.kt        — share photo to Instagram
│   ├── TelegramSendTool.kt     — send message via Telegram Intent
│   ├── WhatsAppSendTool.kt     — send message via WhatsApp Intent
│   └── SocialShareTool.kt      — universal social media share
├── automation/
│   ├── AppFunctionsClient.kt    — Android AppFunctions API client (structured app control)
│   ├── UIAutomationClient.kt    — Android UI Automation Framework client (Google-sanctioned)
│   ├── DroidRunAdapter.kt       — DroidRun-style accessibility tree → structured data (dev/test only)
│   ├── UIAutomationTool.kt     — tap, swipe, scroll, type text (via UI Automation Framework)
│   ├── ScreenReaderTool.kt     — read current screen content (via UI Automation Framework)
│   ├── AppNavigationTool.kt    — navigate within apps (back, home, recents, switch)
│   ├── FormFillerTool.kt       — fill form fields by label
│   ├── ScreenshotTool.kt       — capture and analyze screenshots (MediaProjection)
│   └── NotificationReaderTool.kt — read/dismiss/act on notifications (NotificationListenerService)
├── files/
│   ├── FileReadTool.kt         — read file content
│   ├── FileWriteTool.kt        — write/create files
│   ├── FileSearchTool.kt       — search files by name/content
│   ├── DocumentPickerTool.kt   — SAF document picker
│   └── MediaGalleryTool.kt     — browse photos/videos
├── web/
│   ├── WebFetchTool.kt         — HTTP GET/POST with parsing
│   ├── WebSearchTool.kt        — Brave Search / Google Custom Search
│   ├── WebScrapeTool.kt        — extract structured data from pages
│   └── WebBrowserSessionTool.kt — headless browser via WebView
└── system/
    ├── ShellTool.kt            — execute shell commands (sandboxed)
    ├── PackageManagerTool.kt   — install/uninstall APKs
    ├── SystemInfoTool.kt       — device info, OS version, storage
    └── AccessibilityConfigTool.kt — check/request accessibility permissions
```

### 3.2 Complete Tool List (50+ tools)

#### Device Tools (17)
| Tool | Description | Permission Required |
|------|-------------|-------------------|
| `send_sms` | Send SMS message | SEND_SMS |
| `read_sms` | Read SMS inbox/sent | READ_SMS |
| `place_call` | Make phone call | CALL_PHONE |
| `read_call_log` | Read call history | READ_CALL_LOG |
| `read_contacts` | Search/read contacts | READ_CONTACTS |
| `write_contacts` | Create/update contacts | WRITE_CONTACTS |
| `read_calendar` | Read calendar events | READ_CALENDAR |
| `write_calendar` | Create/update events | WRITE_CALENDAR |
| `take_photo` | Capture photo with camera | CAMERA |
| `get_location` | Current GPS coordinates | ACCESS_FINE_LOCATION |
| `record_audio` | Record ambient audio | RECORD_AUDIO |
| `read_sensors` | Accelerometer, gyro, etc. | None |
| `get_battery` | Battery status | None |
| `get_network` | Network connectivity status | None |
| `scan_bluetooth` | Scan BLE devices | BLUETOOTH_SCAN |
| `read_nfc` | Read NFC tag | NFC |
| `write_nfc` | Write NFC tag | NFC |

#### App Control Tools (12)
| Tool | Description | Mechanism |
|------|-------------|-----------|
| `launch_app` | Open any installed app | PackageManager + Intent |
| `list_apps` | List installed apps | PackageManager |
| `set_alarm` | Set alarm at time | AlarmClock Intent |
| `set_timer` | Set countdown timer | AlarmClock Intent |
| `compose_email` | Open email compose | ACTION_SENDTO Intent |
| `read_email_aloud` | Read latest email via TTS | ContentProvider + TTS |
| `open_url` | Open URL in browser | ACTION_VIEW Intent |
| `navigate_to` | Open maps navigation | geo: Intent |
| `play_music` | Control media playback | MediaSession |
| `open_settings` | Open specific settings | Settings Intent |
| `share_content` | Share to any app | ACTION_SEND Intent |
| `fire_intent` | Fire arbitrary Intent | Custom Intent builder |

#### Social Media Tools (5)
| Tool | Description | Mechanism |
|------|-------------|-----------|
| `post_tweet` | Post to Twitter/X | `twitter://post?message=` deep link |
| `share_instagram` | Share image to Instagram | Intent + MIME filter |
| `send_telegram` | Send Telegram message | `tg://msg?text=` deep link |
| `send_whatsapp` | Send WhatsApp message | `whatsapp://send?text=` deep link |
| `social_share` | Universal social share | ACTION_SEND + chooser |

#### App Automation Tools (7)
| Tool | Description | Mechanism |
|------|-------------|-----------|
| `ui_tap` | Tap at coordinates or by text | **UI Automation Framework** (Google-sanctioned) |
| `ui_swipe` | Swipe gesture | **UI Automation Framework** |
| `ui_type_text` | Type text in focused field | **UI Automation Framework** |
| `read_screen` | Read current screen content | **UI Automation Framework** → structured data |
| `navigate_app` | Back, home, recents, app switch | **AppFunctions API** / system navigation |
| `fill_form` | Fill form fields by label | **AppFunctions API** / UI Automation |
| `screenshot` | Capture + analyze screenshot | **MediaProjection API** |

> **Note:** All UI automation uses Google's official **AppFunctions** and **UI Automation Framework**
> APIs (announced Feb 2026), NOT AccessibilityService. This ensures Google Play compliance.
> For development/testing only, a DroidRun-style accessibility adapter is available in sideloaded builds.

#### File & Web Tools (9)
| Tool | Description |
|------|-------------|
| `read_file` | Read file content |
| `write_file` | Create/write file |
| `search_files` | Search by name/content |
| `pick_document` | SAF document picker |
| `web_fetch` | HTTP request with response parsing |
| `web_search` | Search the web (Brave/Google) |
| `web_scrape` | Extract structured data from page |
| `browser_session` | Interactive WebView session |
| `read_notifications` | Read notification history |

### 3.3 App Control Deep Dive: Intent-Based Actions

**Setting an Alarm:**
```kotlin
val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
    putExtra(AlarmClock.EXTRA_HOUR, hour)
    putExtra(AlarmClock.EXTRA_MINUTES, minutes)
    putExtra(AlarmClock.EXTRA_MESSAGE, label)
    putExtra(AlarmClock.EXTRA_SKIP_UI, true) // silent — no UI
}
startActivity(intent)
```

**Posting to Twitter/X:**
```kotlin
// Option 1: Deep link (if Twitter installed)
val intent = Intent(Intent.ACTION_VIEW, Uri.parse("twitter://post?message=$encodedText"))
// Option 2: Intent share (fallback)
val intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, text)
    setPackage("com.twitter.android")
}
```

**Reading Email Aloud:**
```kotlin
// 1. Query email content provider for latest unread
// 2. Extract subject + body
// 3. Feed to TTS engine (see Phase 6)
val tts = TextToSpeech(context) { status -> ... }
tts.speak("Новое письмо от ${sender}: ${subject}. ${body}", QUEUE_FLUSH, null, "email-read")
```

### 3.4 Documentation Plan
- `docs/reference/tools.md` — complete tool catalog with schemas
- `docs/reference/app-control.md` — Intent-based app control guide
- `docs/reference/accessibility.md` — accessibility service setup
- `docs/reference/permissions.md` — required permissions per tool
- `docs/guides/controlling-apps.md` — user guide for app automation

### 3.5 Test Plan
- Unit: Each tool — valid input, invalid input, missing permissions
- Unit: Intent builder — correct Intent generation for each app
- Unit: Accessibility — node tree parsing, gesture dispatch
- Integration: Tool engine → permission check → execute → result
- Maestro E2E: "Поставь будильник на 7 утра" → alarm actually set
- Maestro E2E: "Открой Twitter и напиши пост" → Twitter opens with text
- Maestro E2E: "Прочитай новое письмо вслух" → TTS reads email
- Maestro E2E: "Отправь SMS маме" → SMS composed and sent
- Resilience: Tool execution after app restart
- Resilience: Accessibility service survives reboot
- Resilience: Permission revoked → graceful error message

---

## Phase 4: Proactive Agent & Push Notifications

### 4.0 Research

**Study before implementing:**
- Android `NotificationManager` — channels, priority, actions, direct reply
- `NotificationListenerService` — intercepting system notifications
- Firebase Cloud Messaging (FCM) for remote push (optional)
- Local notification scheduling (`AlarmManager` + `BroadcastReceiver`)
- Android 13+ notification permission (`POST_NOTIFICATIONS`)
- Notification grouping and stacking best practices
- Conversation-style notifications (Android 11+ `MessagingStyle`)
- Proactive agent patterns in existing AI assistants

**Android 15/16 notification constraints (from research):**
- App standby buckets (active/working set/frequent/restricted) throttle background notifications
- OEM battery management (Samsung, Xiaomi, OnePlus) aggressively kills background services
- Android 16 AI-powered Notification Organizer auto-categorizes/silences low-priority notifications
- Android 15 Notification Cooldown reduces volume/vibration for rapid successive notifications

**Best practices:**
- Use FCM **high-priority messages** for time-sensitive agent notifications (bypass Doze)
- Request battery optimization exemption on first launch
- Use Foreground Service with persistent notification (required for background work)
- Keep notification content short (10 words or fewer = 2x click rate)
- Implement granular notification channels for user control
- Use WorkManager for deferrable background tasks

### 4.1 Architecture

```
notifications/
├── GuappaNotificationManager.kt — notification creation, channels, grouping
├── NotificationChannels.kt      — channel definitions (chat, tasks, alerts)
├── ConversationNotification.kt  — MessagingStyle chat notifications
├── TaskNotification.kt          — task progress/completion notifications
├── QuestionNotification.kt      — clarifying question with reply action
├── AlertNotification.kt         — urgent alerts (incoming call, error)
└── NotificationActionReceiver.kt — handles notification button actions

proactive/
├── ProactiveEngine.kt           — decides when agent should initiate messages
├── TaskCompletionReporter.kt    — reports task completion to chat + push
├── ClarificationRequester.kt   — asks user questions when stuck
├── ScheduledCheckIn.kt          — periodic status updates
├── EventDrivenProactive.kt      — react to device events (SMS, call, etc.)
└── ProactiveConfig.kt           — user preferences for proactive behavior
```

### 4.2 Features

1. **Notification Channels**
   - `guappa_chat` — agent messages (HIGH priority, conversation style)
   - `guappa_tasks` — task progress and completion (DEFAULT priority)
   - `guappa_alerts` — urgent alerts (MAX priority, sound + vibration)
   - `guappa_background` — foreground service notification (LOW priority)

2. **Proactive Agent Messages**
   - Agent writes to chat when:
     - Task completed: "Готово! Будильник установлен на 7:00 🎯"
     - Task failed: "Не смогла отправить SMS — нет разрешения. Разрешить?"
     - Clarification needed: "Какой будильник — на завтра или каждый день?"
     - Event reaction: "Пропущенный звонок от Мама. Перезвонить?"
     - Scheduled check-in: "Доброе утро! У тебя 3 события на сегодня."
   - Each agent message triggers push notification if app in background

3. **Push Notification Types**
   - **Chat message** — `MessagingStyle` with Guappa avatar, direct reply action
   - **Task completion** — summary with "Open" action
   - **Question** — inline reply action for quick response
   - **Alert** — full-screen intent for urgent events (incoming call summary)
   - **Progress** — ongoing notification with progress bar for long tasks

4. **Notification Actions**
   - Reply inline (direct reply from notification)
   - Open chat (deep link to ChatScreen)
   - Approve/Deny (for permission requests)
   - Snooze (defer question for later)
   - Custom actions per notification type

5. **Agent → Chat Flow**
   ```
   Agent decides to message → MessageBus.emit(AgentMessage) →
     ├── ChatScreen (if visible) → add to message list → scroll to bottom
     └── NotificationManager → push notification with MessagingStyle
         └── User taps → opens ChatScreen with context
   ```

6. **Event-Driven Proactive Behavior**
   - Incoming SMS → agent analyzes → suggests reply
   - Missed call → agent notes → offers to call back
   - Calendar event approaching → agent reminds
   - Low battery → agent adjusts behavior, notifies
   - New email → agent offers to read aloud or summarize

### 4.3 Documentation Plan
- `docs/reference/notifications.md` — notification types and channels
- `docs/guides/proactive-agent.md` — how Guappa proactively communicates
- `docs/architecture/event-system.md` — event-driven proactive behavior

### 4.4 Test Plan
- Unit: NotificationManager — correct channel, priority, actions
- Unit: ProactiveEngine — event → decision → message
- Unit: Notification actions — reply, approve, deny, snooze
- Integration: Agent completes task → chat message + push notification
- Integration: Agent asks question → notification with inline reply → response flows back
- Maestro E2E: Send task → background app → receive push → tap → chat opens
- Maestro E2E: Incoming SMS → agent suggests reply via notification
- Maestro E2E: Agent asks clarification → inline reply works
- Resilience: Notifications after app restart
- Resilience: Notification channels survive app update
- Resilience: Push works after device reboot

---

## Phase 5: Channel Hub — Messenger Integrations

### 5.0 Research

**Study before implementing:**
- Telegram Bot API (latest, March 2026) — long polling, webhooks, inline keyboards
- Discord Bot API — gateway, slash commands, message intents
- Slack Bot API — Events API, Socket Mode, Block Kit
- WhatsApp Business API — Cloud API, message templates
- Signal Bot — signald / signal-cli integration patterns
- Matrix (Element) — Client-Server API, E2EE (Vodozemac)
- Email — IMAP/SMTP via JavaMail
- Android share targets — receive shared content from other apps

### 5.1 Architecture

```
channels/
├── ChannelHub.kt              — unified channel manager
├── ChannelInterface.kt        — base interface (send, receive, health)
├── ChannelConfig.kt           — per-channel settings
├── telegram/
│   ├── TelegramChannel.kt    — Telegram Bot API client
│   ├── TelegramPolling.kt    — long polling for updates
│   └── TelegramFormatter.kt  — markdown → Telegram formatting
├── discord/
│   ├── DiscordChannel.kt     — Discord gateway client
│   └── DiscordFormatter.kt   — markdown → Discord formatting
├── slack/
│   ├── SlackChannel.kt       — Slack Socket Mode client
│   └── SlackFormatter.kt     — markdown → Slack Block Kit
├── whatsapp/
│   ├── WhatsAppChannel.kt    — WhatsApp Cloud API client
│   └── WhatsAppFormatter.kt  — message template formatting
├── signal/
│   └── SignalChannel.kt      — Signal via signald/signal-cli
├── matrix/
│   ├── MatrixChannel.kt      — Matrix Client-Server API
│   └── MatrixE2EE.kt         — end-to-end encryption
├── email/
│   ├── EmailChannel.kt       — IMAP receive + SMTP send
│   └── EmailParser.kt        — email content extraction
└── sms/
    └── SmsChannel.kt         — native Android SMS as channel
```

### 5.2 Complete Channel List

| Channel | Protocol | Send | Receive | Media | Auth |
|---------|----------|------|---------|-------|------|
| **In-App Chat** | Direct (MessageBus) | ✅ | ✅ | Images, files | None |
| **Telegram** | Bot API (HTTPS) | ✅ | ✅ | Images, files, voice | Bot token |
| **Discord** | Gateway (WebSocket) | ✅ | ✅ | Images, files | Bot token |
| **Slack** | Socket Mode (WebSocket) | ✅ | ✅ | Images, files | Bot token |
| **WhatsApp** | Cloud API (HTTPS) | ✅ | ✅ | Images, templates | Access token |
| **Signal** | signald (Unix socket) | ✅ | ✅ | Images, files | Phone number |
| **Matrix** | CS API (HTTPS) | ✅ | ✅ | E2EE, files | Access token |
| **Email** | IMAP/SMTP | ✅ | ✅ | Attachments | Credentials |
| **SMS** | Android API | ✅ | ✅ | Text only | Permissions |

### 5.3 Documentation Plan
- `docs/reference/channels.md` — all channels with setup instructions
- `docs/guides/telegram-setup.md` — Telegram bot setup guide
- `docs/guides/messenger-integration.md` — multi-channel setup

### 5.4 Test Plan
- Unit: Each channel — send, receive, format, health check
- Unit: Channel hub — routing, failover
- Integration: Message received on Telegram → agent processes → reply sent
- Integration: Agent proactive message → sent to all configured channels
- Maestro E2E: Configure Telegram in Settings → send test message → verify in Telegram
- Resilience: Channel reconnection after network loss
- Resilience: Channel config survives app update

---

## Phase 6: Voice Pipeline

### 6.0 Research

**Study before implementing:**
- On-device STT: **WhisperKit Android** (Argmax, new port), whisper.cpp, **Google ML Kit GenAI Speech Recognition**, Vosk
- On-device TTS: **Picovoice Orca** (streaming, sub-50ms latency), **Kokoro** (Apache 2.0, 82M params), Android native TTS, Piper TTS
- Cloud TTS: **Speechmatics** (~150ms first audio byte, 27x cheaper than ElevenLabs), Deepgram Aura, ElevenLabs, OpenAI TTS
- Cloud STT: **Google Cloud STT** (best real-time streaming), Deepgram Nova-2, OpenAI Whisper API
- Wake word detection: Porcupine (Picovoice), OpenWakeWord, Mycroft Precise
- Continuous voice interaction patterns (Siri, Google Assistant model)
- Audio focus and ducking on Android
- VoIP / audio routing best practices

### 6.1 Architecture

```
voice/
├── VoicePipeline.kt           — orchestrates STT → Agent → TTS flow
├── stt/
│   ├── STTInterface.kt        — unified speech-to-text interface
│   ├── WhisperKitSTT.kt       — WhisperKit Android (Argmax, optimized port)
│   ├── WhisperCppSTT.kt       — whisper.cpp via JNI (fallback)
│   ├── GoogleMLKitSTT.kt      — Google ML Kit GenAI Speech Recognition (on-device)
│   ├── GoogleCloudSTT.kt      — Google Cloud Speech-to-Text (best real-time streaming)
│   ├── VoskSTT.kt             — Vosk offline recognition
│   └── DeepgramSTT.kt         — Deepgram Nova-2 cloud API
├── tts/
│   ├── TTSInterface.kt        — unified text-to-speech interface
│   ├── PicovoiceOrcaTTS.kt    — Picovoice Orca (streaming, sub-50ms latency, on-device)
│   ├── KokoroTTS.kt           — Kokoro neural TTS (Apache 2.0, 82M params, on-device)
│   ├── AndroidTTS.kt          — Android native TextToSpeech (fallback, no streaming)
│   ├── PiperTTS.kt            — Piper neural TTS (on-device, 100+ voices)
│   ├── SpeechmaticsTTS.kt     — Speechmatics cloud (~150ms, 27x cheaper than ElevenLabs)
│   ├── DeepgramTTS.kt         — Deepgram Aura API
│   ├── ElevenLabsTTS.kt       — ElevenLabs API (ultra-realistic, voice cloning)
│   └── OpenAITTS.kt           — OpenAI TTS API (6 voices)
├── wakeword/
│   ├── WakeWordDetector.kt    — wake word detection loop
│   ├── PorcupineDetector.kt   — Picovoice Porcupine ("Hey Guappa")
│   └── OpenWakeWord.kt        — open-source wake word
└── audio/
    ├── AudioRouter.kt         — audio focus, speaker/earpiece/BT routing
    ├── AudioRecorder.kt       — microphone capture (AudioRecord)
    └── AudioPlayer.kt         — playback (AudioTrack / MediaPlayer)
```

### 6.2 STT Options (March 2026)

| Engine | Type | Languages | Quality | Latency | Size | Notes |
|--------|------|-----------|---------|---------|------|-------|
| **WhisperKit Android** | On-device | 99+ | Excellent | ~1.5s | ~800MB | Argmax port, optimized for mobile |
| **Whisper Large V3 Turbo** | On-device | 99+ | Excellent | ~2s | ~800MB | via whisper.cpp JNI |
| **Whisper Medium** | On-device | 99+ | Very Good | ~1.5s | ~500MB | Best quality/size tradeoff |
| **Whisper Small** | On-device | 99+ | Good | ~0.8s | ~250MB | Recommended for most devices |
| **Whisper Tiny** | On-device | 99+ | Fair | ~0.3s | ~75MB | Ultra-fast, lower accuracy |
| **Google ML Kit GenAI** | On-device | 60+ | Very Good | ~0.4s | ~30MB | Google's on-device ASR, no download |
| **Google Cloud STT** | Cloud | 125+ | Excellent | ~0.3s | — | **Best real-time streaming**, diarization |
| **Deepgram Nova-2** | Cloud | 36+ | Excellent | ~0.3s | — | Fast, competitive pricing |
| **OpenAI Whisper API** | Cloud | 99+ | Excellent | ~1s | — | Best for noisy audio, diverse accents |
| **Vosk** | On-device | 20+ | Good | ~0.5s | ~50MB | Lightweight offline fallback |

**Recommendation:** Hybrid approach — Google ML Kit GenAI for real-time streaming input
(live user speech), WhisperKit Android for batch processing (voice messages, audio files).
Cloud Google STT as premium option for users with API key.

### 6.3 TTS Options (March 2026)

| Engine | Type | Voices | Quality | Latency | Streaming | Notes |
|--------|------|--------|---------|---------|-----------|-------|
| **Picovoice Orca** | On-device | Limited | Very Good | **<50ms** | ✅ **Yes** | **Best for LLM output streaming**, 6.5x faster than ElevenLabs |
| **Kokoro** | On-device | 15+ | Very Good | ~0.2s | ❌ | Apache 2.0, 82M params, best size/quality |
| **Piper TTS** | On-device | 100+ | Good | ~0.3s | ❌ | Wide voice selection |
| **Android Native** | On-device | System | Fair | Instant | ❌ | **Cannot stream** — needs full text first |
| **Speechmatics** | Cloud | 20+ | Very Good | **~150ms** | ✅ **Yes** | 27x cheaper than ElevenLabs |
| **Deepgram Aura** | Cloud | 12 | Very Good | ~0.3s | ✅ Yes | Good pricing |
| **ElevenLabs** | Cloud | 1000+ | Excellent | ~0.5s | ✅ Yes | Ultra-realistic, voice cloning |
| **OpenAI TTS** | Cloud | 6 | Excellent | ~0.5s | ✅ Yes | Simple API |
| **Google Cloud TTS** | Cloud | 380+ | Excellent | ~0.5s | ✅ Yes | WaveNet/Neural2 models |

**Recommendation:** Use **Picovoice Orca** for on-device streaming TTS (reading LLM output in
real-time). Fallback to **Kokoro** for offline non-streaming use. **Speechmatics** for cloud
streaming (cheapest high-quality option). **ElevenLabs** as premium option for voice cloning.
Android native TTS is insufficient for streaming LLM outputs — it requires complete text before synthesis.

### 6.4 Documentation Plan
- `docs/reference/voice.md` — STT/TTS options and setup
- `docs/guides/voice-setup.md` — configure voice interaction
- `docs/guides/wake-word.md` — wake word configuration

### 6.5 Test Plan
- Unit: Each STT engine — transcription accuracy
- Unit: Each TTS engine — audio output quality
- Unit: Wake word — detection rate, false positive rate
- Integration: Voice → STT → Agent → TTS → Audio output
- Maestro E2E: Tap mic → speak → response spoken
- Maestro E2E: Wake word → command → action executed
- Resilience: Audio focus interrupted by phone call → resume after

---

## Phase 7: Memory & Context

### 7.0 Research

**Study before implementing:**
- SQLite on Android — Room library, WAL mode, full-text search
- Vector embeddings on device — ONNX embedding models, sentence-transformers
- RAG patterns for mobile — chunking, retrieval, context injection
- Conversation summarization strategies
- Long-term memory patterns (episodic, semantic, procedural)

### 7.1 Architecture

```
memory/
├── MemoryManager.kt           — unified memory interface
├── ConversationMemory.kt      — recent conversation history (SQLite)
├── LongTermMemory.kt          — persistent facts and preferences (SQLite + FTS5)
├── EpisodicMemory.kt          — past task executions and outcomes
├── VectorStore.kt             — embedding-based semantic search
├── EmbeddingEngine.kt         — on-device embedding generation
├── ContextBuilder.kt          — assemble context from all memory sources
├── Summarizer.kt              — compress old conversations
└── MemoryMigration.kt         — schema migrations
```

### 7.2 Features
1. **Conversation History** — SQLite with Room, last N turns per session
2. **Long-Term Memory** — user facts, preferences, learned patterns (FTS5 search)
3. **Episodic Memory** — past task executions with outcomes
4. **Vector Search** — semantic retrieval using on-device embeddings
5. **Context Assembly** — combine relevant memories into agent context
6. **Auto-Summarization** — compress old conversations to save tokens
7. **Memory Export/Import** — backup and restore memory data

### 7.3 Documentation Plan
- `docs/reference/memory.md` — memory system architecture
- `docs/guides/memory-management.md` — user guide for memory features

### 7.4 Test Plan
- Unit: Room database CRUD operations
- Unit: FTS5 search accuracy
- Unit: Vector similarity search
- Unit: Context assembly from multiple sources
- Integration: Long conversation → auto-summarization → context fits window
- Maestro E2E: Tell Guappa a fact → restart app → ask about fact → remembers
- Resilience: Database migration on app update
- Resilience: Database integrity after crash

---

## Phase 8: Documentation — Complete Guappa Docs

### 8.0 Scope

**Delete all existing docs** (167 markdown files in `docs/`) and create new Guappa-branded documentation from scratch.

### 8.1 New Documentation Structure

```
docs/
├── README.md                      — Guappa docs hub
├── SUMMARY.md                     — table of contents
├── getting-started/
│   ├── README.md                  — getting started overview
│   ├── installation.md            — install from Play Store / APK
│   ├── first-setup.md             — first launch, permissions, provider setup
│   ├── quick-tour.md              — 5-minute tour of all features
│   └── faq.md                     — frequently asked questions
├── guides/
│   ├── README.md                  — guides overview
│   ├── choosing-a-model.md        — how to pick the right LLM
│   ├── voice-setup.md             — configure voice interaction
│   ├── wake-word.md               — wake word setup
│   ├── controlling-apps.md        — how Guappa controls other apps
│   ├── telegram-setup.md          — connect Telegram bot
│   ├── messenger-integration.md   — all messenger channels
│   ├── memory-management.md       — memory and context
│   ├── proactive-agent.md         — proactive behavior config
│   ├── local-inference.md         — on-device model setup
│   ├── privacy-security.md        — security model and data handling
│   └── troubleshooting.md         — common issues and fixes
├── reference/
│   ├── README.md                  — reference overview
│   ├── providers.md               — all LLM providers
│   ├── models.md                  — complete model catalog
│   ├── tools.md                   — all 50+ tools with schemas
│   ├── channels.md                — all messenger channels
│   ├── app-control.md             — Intent-based app control
│   ├── accessibility.md           — accessibility service
│   ├── voice.md                   — STT/TTS/wake word options
│   ├── hardware-acceleration.md   — device compatibility matrix
│   ├── notifications.md           — push notification types
│   ├── permissions.md             — Android permissions per feature
│   ├── memory.md                  — memory system architecture
│   └── config.md                  — all configuration options
├── architecture/
│   ├── README.md                  — architecture overview
│   ├── agent-core.md              — orchestrator design
│   ├── message-bus.md             — event system
│   ├── provider-router.md         — provider routing and fallback
│   ├── tool-engine.md             — tool execution pipeline
│   ├── event-system.md            — event-driven proactive behavior
│   └── diagrams/
│       ├── architecture.svg       — high-level architecture diagram
│       └── data-flow.svg          — data flow diagram
├── development/
│   ├── README.md                  — development overview
│   ├── setup.md                   — dev environment setup
│   ├── building.md                — build instructions
│   ├── testing.md                 — test guide
│   └── contributing.md            — contribution guidelines
└── plans/
    ├── 2026-03-06-guappa-master-plan.md — this file
    └── (keep historical plans as reference)
```

### 8.2 Documentation Rules
- All docs in English (single language for v1)
- Use "Guappa" consistently (never ZeroClaw, MobileClaw, OpenClaw)
- Reference "она" (she) when referring to Guappa in Russian-language UI strings
- Keep docs concise, actionable, and example-driven
- Every reference doc includes complete lists (all providers, all tools, etc.)
- Every guide includes step-by-step instructions with screenshots

### 8.3 Test Plan
- Markdown lint check on all docs
- Link integrity verification
- All code examples compile/run
- Screenshots match current UI

---

## Phase 9: Testing & QA

### 9.0 Research

**Study before implementing:**
- Maestro testing framework (latest, March 2026) — YAML flows, assertions, device control
- Android Espresso for unit/integration testing
- JUnit 5 for Kotlin unit tests
- MockK for Kotlin mocking
- Robolectric for Android unit tests without device
- Firebase Test Lab for cloud device testing
- Android Gradle test configurations
- Resilience testing patterns (chaos engineering for mobile)

### 9.1 Test Framework Stack

| Layer | Framework | Purpose |
|-------|-----------|---------|
| **Unit** | JUnit 5 + MockK | Kotlin logic testing |
| **Android Unit** | Robolectric | Android API mocking |
| **Integration** | AndroidX Test + Espresso | Component integration |
| **E2E UI** | Maestro | Full user flow testing |
| **Performance** | AndroidX Benchmark | Inference latency, memory |
| **Cloud Testing** | Firebase Test Lab | Multi-device matrix |

### 9.2 Maestro E2E Test Flows

```
maestro/
├── flows/
│   ├── 01-app-launch.yaml          — app starts, service running, notification visible
│   ├── 02-onboarding.yaml          — first setup, permissions, provider config
│   ├── 03-chat-basic.yaml          — send message, receive response
│   ├── 04-chat-streaming.yaml      — streaming response renders correctly
│   ├── 05-voice-input.yaml         — tap mic, speak, transcript appears
│   ├── 06-voice-output.yaml        — response spoken via TTS
│   ├── 07-set-alarm.yaml           — "set alarm" → alarm actually created
│   ├── 08-send-sms.yaml            — "send SMS" → SMS composed
│   ├── 09-open-twitter.yaml        — "open Twitter and post" → Twitter opens
│   ├── 10-read-email.yaml          — "read email aloud" → TTS plays
│   ├── 11-take-photo.yaml          — "take photo" → camera opens → photo taken
│   ├── 12-set-timer.yaml           — "set timer for 5 min" → timer starts
│   ├── 13-push-notification.yaml   — background task → push notification received
│   ├── 14-notification-reply.yaml  — inline reply from notification
│   ├── 15-proactive-question.yaml  — agent asks clarification → user replies
│   ├── 16-provider-switch.yaml     — change provider in settings → next message uses new provider
│   ├── 17-model-switch.yaml        — change model → immediate effect
│   ├── 18-local-model.yaml         — download model → switch to local → inference works
│   ├── 19-telegram-config.yaml     — configure Telegram → test message
│   ├── 20-memory-persistence.yaml  — tell fact → restart → remembers
│   ├── 21-accessibility-setup.yaml — enable accessibility → UI automation works
│   ├── 22-multi-step-task.yaml     — complex task → multiple tool calls → completion report
│   ├── 23-concurrent-tasks.yaml    — two tasks simultaneously → both complete
│   ├── 24-error-recovery.yaml      — provider error → graceful message
│   ├── 25-permission-denied.yaml   — missing permission → agent asks for it
├── resilience/
│   ├── 30-app-restart.yaml         — kill app → reopens → session restored
│   ├── 31-device-reboot.yaml       — reboot → service auto-starts
│   ├── 32-network-loss.yaml        — airplane mode → reconnect → resume
│   ├── 33-provider-down.yaml       — provider unavailable → fallback
│   ├── 34-oom-recovery.yaml        — low memory → model unloaded → fallback
│   ├── 35-update-migration.yaml    — install new version → data preserved
│   ├── 36-permission-revoke.yaml   — revoke permission → graceful handling
│   └── 37-battery-saver.yaml       — battery saver mode → reduced functionality
└── config/
    ├── maestro-config.yaml         — global test config
    └── devices.yaml                — target device matrix
```

### 9.3 Resilience Testing Matrix

| Scenario | Test Method | Expected Behavior |
|----------|-------------|-------------------|
| App killed by OS | Force stop → verify auto-restart | Service restarts, session restored |
| Device reboot | Reboot → verify boot receiver | Service starts, config loaded |
| Network loss | Airplane mode toggle | Graceful error, auto-reconnect |
| Provider API down | Mock 500 errors | Fallback provider or error message |
| OOM kill | Stress memory → verify recovery | Model unloaded, smaller model loaded |
| App update | Install new APK over old | Data migration, service restart |
| Permission revoked | Revoke SMS permission | Tool returns error, agent informs user |
| Battery saver | Enable battery saver | Reduced polling, deferred tasks |
| Storage full | Fill storage | Graceful error, cleanup suggestion |
| Concurrent access | Multiple channels message simultaneously | All messages processed correctly |

### 9.4 Documentation Plan
- `docs/development/testing.md` — complete test guide
- `docs/development/maestro-setup.md` — Maestro installation and usage
- All Maestro flows documented inline with comments

### 9.5 CI Pipeline
```yaml
# .github/workflows/android-test.yml
- Build debug APK
- Run unit tests (JUnit 5)
- Run integration tests (Espresso)
- Start emulator (API 34)
- Install APK
- Run Maestro flows
- Collect screenshots on failure
- Report results
```

---

## Phase 10: Live Config — Real-Time Configuration

### 10.0 Research

**Study before implementing:**
- **TurboModules** (React Native New Architecture) — type-safe JSI bridge, no JSON serialization
- **Codegen** — auto-generate native stubs from TypeScript specs
- **Native Event Emitters** via TurboModule — push config updates from Kotlin to JS in real-time
- Kotlin StateFlow for reactive config propagation
- Android DataStore (Preferences) for reactive storage
- Provider client lifecycle management (connect/disconnect)
- Hermes engine compatibility requirements

**Key pattern (from research):**
```
SettingsScreen (JS) → TurboModule.configure() → Kotlin ConfigStore.update() →
  StateFlow emits → Subsystems react → TurboModule.sendEvent("configChanged") →
  JS listener → UI state update
```

> **Migration requirement:** React Native must be upgraded to New Architecture with:
> - Hermes engine enabled
> - TurboModules replacing legacy NativeModules
> - Codegen run to generate type-safe Java/Kotlin stubs
> - All native modules migrated from ReactContextBaseJavaModule to TurboModule specs

### 10.1 Architecture

```
config/
├── GuappaConfigStore.kt        — reactive config (DataStore + StateFlow)
├── ConfigChangeDispatcher.kt   — dispatches config changes to subsystems
├── ProviderHotSwap.kt          — swap provider/model without restart
├── ChannelHotSwap.kt           — add/remove channels without restart
├── ToolConfigApplier.kt        — enable/disable tools in real-time
└── ConfigBridgeModule.kt       — React Native ↔ Kotlin config bridge
```

### 10.2 Features

1. **Reactive Config Store**
   - Android DataStore (Preferences) backing
   - `StateFlow<GuappaConfig>` exposed to all subsystems
   - Subsystems collect flow and react to changes
   - No daemon restart needed for any config change

2. **Provider Hot-Swap**
   - Change provider/model/API key → immediate effect
   - Current request completes with old provider
   - Next request uses new provider
   - No conversation loss during switch

3. **Channel Hot-Swap**
   - Enable/disable messenger channels in real-time
   - New channels connect immediately
   - Removed channels disconnect gracefully

4. **Tool Config**
   - Enable/disable tools from UI
   - Tool permissions update in real-time
   - Agent system instruction reflects available tools

5. **Config Bridge (RN ↔ Kotlin)**
   ```
   SettingsScreen → configureRuntimeBridge() → ConfigBridgeModule →
     GuappaConfigStore.update() → StateFlow emits →
       ├── ProviderRouter reacts → switches provider
       ├── ChannelHub reacts → updates channels
       ├── ToolEngine reacts → updates available tools
       └── AgentOrchestrator reacts → updates system instruction
   ```

### 10.3 Config Changes That Apply Immediately

| Setting | Current Behavior | New Behavior |
|---------|-----------------|--------------|
| Provider | Daemon restart | Hot-swap, next request uses new provider |
| Model | Daemon restart | Hot-swap, next request uses new model |
| API Key | Daemon restart | Hot-swap, next request uses new key |
| Temperature | Daemon restart | Immediate, next request uses new value |
| Capabilities | Daemon restart | Immediate, tool list updated |
| Channel tokens | Daemon restart | Channel reconnects |
| Voice provider | App restart | Immediate switch |
| Local model | App restart | Load/unload model dynamically |

### 10.4 Documentation Plan
- `docs/reference/config.md` — all configuration options with hot-swap behavior
- `docs/architecture/live-config.md` — reactive config architecture

### 10.5 Test Plan
- Unit: ConfigStore — set/get/observe/migrate
- Unit: Hot-swap — provider switch during active session
- Unit: Channel hot-swap — connect/disconnect
- Integration: UI change → config store → subsystem reacts
- Maestro E2E: Change provider in Settings → immediately send message → new provider used
- Maestro E2E: Change model → verify in next response
- Maestro E2E: Disable tool → agent can't use it → re-enable → agent can
- Maestro E2E: Add Telegram token → channel connects → remove → disconnects
- Resilience: Config change during active request → request completes → next uses new config
- Resilience: Config survives app update

---

## Implementation Timeline

| Phase | Name | Estimated Effort | Dependencies |
|-------|------|-----------------|--------------|
| 1 | Foundation | Large | — |
| 2 | Provider Router | Large | Phase 1 |
| 3 | Tool Engine | Large | Phase 1 |
| 4 | Proactive Agent & Push | Medium | Phase 1, 3 |
| 5 | Channel Hub | Medium | Phase 1, 4 |
| 6 | Voice Pipeline | Medium | Phase 2 |
| 7 | Memory & Context | Medium | Phase 1 |
| 8 | Documentation | Medium | Phase 1-7 |
| 9 | Testing & QA | Large | Phase 1-7 |
| 10 | Live Config | Medium | Phase 1, 2, 3 |

**Phases 1-3 can partially overlap (3 depends on 1, 2 depends on 1, but 2 and 3 are independent).**
**Phase 4 requires Phase 3 (tool completion triggers notifications).**
**Phase 10 can start after Phase 2 (hot-swap provider is the first target).**

---

## Rollback Strategy

Each phase is independently deployable and revertible:
- Each phase is a separate branch/PR
- Each phase has its own Maestro test suite
- If a phase causes regression, revert the PR
- Memory migrations include down-migration scripts
- Config changes are backward-compatible

---

## Non-Goals (Explicitly Out of Scope)

- iOS support (Android-only for v1)
- Multi-language documentation (English-only for v1)
- Web dashboard (keep existing if desired, but not prioritized)
- Rust backend maintenance (fully replaced by Kotlin)
- Desktop/CLI support
- Cloud hosting / server deployment
- Payment / billing integration

---

## Research Sources (March 2026)

### Android AI Agent APIs
- [Google: The Intelligent OS — AppFunctions + UI Automation Framework](https://android-developers.googleblog.com/2026/02/the-intelligent-os-making-ai-agents.html)
- [Google Play AccessibilityService policy (banned for AI agents)](https://support.google.com/googleplay/android-developer/answer/10964491)
- [DroidRun — mobile AI agent framework (3.8k stars)](https://github.com/nicepkg/droidrun)
- [agent-device by Callstack](https://www.callstack.com/blog/agent-device-ai-native-mobile-automation-for-ios-android)

### LLM Providers & Models
- [Top LLM API Providers 2026](https://futureagi.substack.com/p/top-11-llm-api-providers-in-2026)
- [Best LLMs 2026 — Zapier](https://zapier.com/blog/best-llm/)
- [Top Open Source LLMs 2026](https://o-mega.ai/articles/top-10-open-source-llms-the-deepseek-revolution-2026)

### On-Device Inference
- [LiteRT-LM announcement (replaces MediaPipe LLM)](https://developers.googleblog.com/on-device-genai-in-chrome-chromebook-plus-and-pixel-watch-with-litert-lm/)
- [Best LLMs for Mobile Deployment 2026](https://www.siliconflow.com/articles/en/best-LLMs-for-mobile-deployment)
- [Cactus on-device inference SDK](https://www.infoq.com/news/2025/12/cactus-on-device-inference/)
- [Local LLMs on Mobile — reality check](https://www.callstack.com/blog/local-llms-on-mobile-are-a-gimmick)

### Hardware Acceleration
- [Qualcomm Hexagon NPU (75 TOPS)](https://www.qualcomm.com/processors/hexagon)
- [LiteRT + Qualcomm NPU performance](https://developers.googleblog.com/unlocking-peak-performance-on-qualcomm-npu-with-litert/)
- [MediaTek NPU + LiteRT](https://developers.googleblog.com/mediatek-npu-and-litert-powering-the-next-generation-of-on-device-ai/)
- [NNAPI deprecated → LiteRT](https://medium.com/softaai-blogs/nnapi-explained-the-ultimate-2025-guide-to-androids-ai-acceleration-33c0087f2ddf)

### Push Notifications
- [Android Push Notifications 2026 — Pushwoosh](https://www.pushwoosh.com/blog/android-push-notifications/)
- [App Background Activity OS Restrictions](https://alexrooter.com/os-background-limits/)
- [Push Notification Best Practices 2026](https://appbot.co/blog/app-push-notifications-2026-best-practices/)

### Testing
- [Maestro Testing Framework (v2.2.0, 10.8k stars)](https://maestro.dev/)
- [Best Mobile Testing Frameworks 2026](https://www.qawolf.com/blog/best-mobile-app-testing-frameworks-2026)

### Voice (STT/TTS)
- [WhisperKit Android — Argmax](https://github.com/argmaxinc/WhisperKitAndroid)
- [Google ML Kit GenAI Speech Recognition](https://developers.google.com/ml-kit/genai/speech-recognition/android)
- [Picovoice Orca — streaming on-device TTS](https://picovoice.ai/blog/android-streaming-text-to-speech/)
- [Kokoro TTS (Apache 2.0, 82M params)](https://www.bentoml.com/blog/exploring-the-world-of-open-source-text-to-speech-models)
- [Speechmatics TTS (27x cheaper than ElevenLabs)](https://www.speechmatics.com/company/articles-and-news/best-tts-apis-in-2025-top-12-text-to-speech-services-for-developers)
- [Whisper vs Google STT comparison 2026](https://is4.ai/blog/our-blog-1/whisper-vs-google-speech-to-text-comparison-2026-267)

### React Native Bridge
- [React Native New Architecture — Fabric + TurboModules](https://isitdev.com/react-native-new-architecture-fabric-turbomodules-2025-2/)
- [Emitting Events in TurboModules — React Native docs](https://reactnative.dev/docs/the-new-architecture/native-modules-custom-events)
- [Android Native Bridge to React Native 2026](https://oneuptime.com/blog/post/2026-01-15-react-native-android-bridge/view)

### Android Intents
- [Common Android Intents — developer.android.com](https://developer.android.com/guide/components/intents-common)
- [Exact alarms denied by default — Android 14](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)
