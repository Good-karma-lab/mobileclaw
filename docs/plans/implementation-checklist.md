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
| 2.11 | Local inference (llama.cpp JNI) | ⚠️ | `LocalLlmServerModule.kt` (121 lines) | Server module exists but no direct llama.cpp JNI or GGUF loading in providers |
| 2.12 | LiteRT-LM (Gemini Nano) | ❌ | Not found | — |
| 2.13 | Qualcomm GENIE (NPU) | ❌ | Not found | — |
| 2.14 | ONNX Runtime Mobile | ❌ | Not found | Only referenced in EmbeddingService |
| 2.15 | HardwareProbe (SoC/NPU detection) | ❌ | Not found | — |
| 2.16 | ModelDownloadManager | ✅ | `ModelDownloaderModule.kt` (91 lines) + `whisperModelManager.ts` | — |
| 2.17 | Token counter (tiktoken) | ❌ | Not found | Token counting may be approximate |
| 2.18 | Separate model per capability (text/vision/image) | ⚠️ | ConfigStore has fields for this | UI supports it but routing needs verification |

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
| 3.15 | Automation tools (AppFunctions, UI Automation Framework) | ❌ | `AgentAccessibilityService.kt` exists but no AppFunctions/UIAutomation | Plan mentions Google's new APIs — not yet available for production |
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

| # | Feature | Status | Evidence | Gap |
|---|---------|--------|----------|-----|
| 6.1 | useVoiceRecording (STT hook) | ✅ | `hooks/useVoiceRecording.ts` | Deepgram WebSocket + Whisper.rn |
| 6.2 | Deepgram STT (cloud, streaming) | ✅ | WebSocket to `api.deepgram.com` in hook | — |
| 6.3 | Whisper STT (on-device) | ✅ | Uses `whisper.rn` + model manager | — |
| 6.4 | WhisperModelManager (download GGML models) | ✅ | `voice/whisperModelManager.ts` | tiny/base/small variants |
| 6.5 | useTTS (text-to-speech hook) | ✅ | `hooks/useTTS.ts` | expo-speech based, swarm integration |
| 6.6 | useVAD (voice activity detection) | ✅ | `hooks/useVAD.ts` | Energy-based with configurable thresholds |
| 6.7 | useWakeWord ("Hey Guappa") | ✅ | `hooks/useWakeWord.ts` | Energy + STT keyword matching |
| 6.8 | VoiceAmplitude (for swarm visualization) | ✅ | `swarm/audio/VoiceAmplitude.ts` | — |
| 6.9 | VoiceScreen UI | ✅ | `screens/tabs/VoiceScreen.tsx` (338 lines) | Neural swarm + mic button |
| 6.10 | Streaming TTS (read LLM output real-time) | ⚠️ | useTTS has queue support | Sentence-level streaming may not work end-to-end |
| 6.11 | Picovoice Orca TTS (on-device, <50ms) | ❌ | Not found | Plan listed as PRIMARY |
| 6.12 | Kokoro TTS (on-device) | ❌ | Not found | — |
| 6.13 | Piper TTS (on-device) | ❌ | Not found | — |
| 6.14 | ElevenLabs TTS (cloud) | ❌ | Not found | — |
| 6.15 | Google Cloud STT | ❌ | Not found | Only Deepgram + Whisper |
| 6.16 | Picovoice Porcupine wake word | ❌ | Not found | Using custom energy-based approach |
| 6.17 | Silero VAD | ❌ | Not found | Using expo-av metering-based approach |
| 6.18 | Audio routing (speaker/earpiece/BT) | ❌ | Not found | — |
| 6.19 | Audio focus management | ❌ | Not found | — |
| 6.20 | Bluetooth SCO/A2DP routing | ❌ | Not found | — |
| 6.21 | STT/TTS engine selection (auto-select best) | ❌ | Not found | Hardcoded to Deepgram/Whisper + expo-speech |

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
| P1 | No TurboModule bridge (still old NativeModule) | Performance, type safety — functional but suboptimal |
| P1 | No on-device TTS (Picovoice/Kokoro/Piper) | Voice mode requires network for TTS |
| P1 | No STT/TTS engine selection UI | Users can't switch voice engines |
| P1 | No session encryption (SQLCipher) | Security concern for sensitive data |
| P1 | No audio routing / Bluetooth support | Voice mode only works on speaker |
| P1 | Limited unit test coverage | Only swarm/tools/providers/channels — no agent/memory tests |

### 🟢 Nice-to-Have Gaps

| Priority | Gap | Impact |
|----------|-----|--------|
| P2 | No local inference engines (LiteRT, GENIE, ONNX) | All inference requires cloud providers |
| P2 | No embedded swarm connector (Rust cross-compile) | Requires external wws-connector server |
| P2 | No AppFunctions / UI Automation Framework | Planned APIs not yet available |
| P2 | No token counter (tiktoken) | Approximate context management |
| P2 | No channel formatters | Raw text sent to all channels |
| P2 | No memory export/import | — |
| P2 | No DI framework (Hilt/Koin) | Manual wiring, works but less maintainable |

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

## Execution Plan

To close gaps and run E2E tests successfully:

1. **Verify** `GuappaSession` persists context across ReAct iterations (memory store + recall)
2. **Verify** `hook_incoming_call` actually fires agent response in chat when `IncomingCallReceiver` triggers
3. **Build** the app: `cd mobile-app/android && ./gradlew assembleDebug`
4. **Install** on emulator: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
5. **Configure** provider API key in app settings
6. **Configure** Deepgram API key for voice STT (if testing voice phase)
7. **Run** phase-by-phase:
   ```bash
   maestro test mobile-app/.maestro/e2e_phase1_memory_store.yaml
   maestro test mobile-app/.maestro/e2e_phase2_call_hook_setup.yaml
   adb emu gsm call 5551234567 && sleep 10 && adb emu gsm cancel 5551234567
   maestro test mobile-app/.maestro/e2e_phase3_call_emulate_verify.yaml
   maestro test mobile-app/.maestro/e2e_phase4_memory_recall.yaml
   ```
8. **For voice**: configure BlackHole, then run `bash mobile-app/.maestro/e2e_agent_memory_call_voice.sh`
