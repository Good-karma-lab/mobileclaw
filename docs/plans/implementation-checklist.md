# Guappa Implementation Checklist — Plan vs Reality

**Date**: 2026-03-10 (updated)
**Purpose**: Map every planned feature to actual implementation status, identify gaps, define E2E test coverage, and track icon/branding and palette conformance.

Legend:
- ✅ Implemented — code exists and appears functional
- ⚠️ Partial — code exists but incomplete or missing key features
- ❌ Missing — no implementation found
- 🔧 Stub — file exists but minimal/skeleton implementation
- 🧪 E2E test exists (Maestro or UI Automator)
- 🚫 No E2E test

---

## Phase 1: Foundation — Agent Core

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 1.1 | GuappaOrchestrator (ReAct loop) | ✅ | `agent/GuappaOrchestrator.kt` (516 lines) | — | 🧪 `e2e_full_agent_scenario.yaml` |
| 1.2 | GuappaSession (conversation state) | ✅ | `agent/GuappaSession.kt` — has session type enum, TTL, checkpoint/recovery, compaction | — | 🧪 `e2e_session_persistence.yaml`, `e2e_resilience_session_persist.yaml` |
| 1.3 | GuappaPlanner (ReAct, task decomposition) | ✅ | `agent/GuappaPlanner.kt` (356 lines) | — | 🧪 `e2e_full_agent_scenario.yaml` (indirect) |
| 1.4 | MessageBus (SharedFlow pub/sub) | ✅ | `agent/MessageBus.kt` (74 lines) — includes priority queue, URGENT support | — | 🧪 Unit test: `MessageBusTest.kt` |
| 1.5 | TaskManager | ✅ | `agent/TaskManager.kt` (262 lines) | — | 🧪 `e2e_phase2_call_hook_setup.yaml` |
| 1.6 | GuappaConfig | ✅ | `agent/GuappaConfig.kt` | — | 🧪 Config screen tests |
| 1.7 | GuappaPersona (system prompt, personality) | ✅ | `agent/GuappaPersona.kt` | — | 🧪 `e2e_persona_tone.yaml` |
| 1.8 | Foreground service (DATA_SYNC) | ✅ | `RuntimeAlwaysOnService.kt` + `GuappaAgentService.kt` | — | 🧪 `e2e_service_survives_background.yaml` |
| 1.9 | Boot receiver (auto-start) | ✅ | `RuntimeBootReceiver.kt` (24 lines) | — | 🚫 Needs: `BootReceiverTest.kt` (UI Automator — reboot emulator) |
| 1.10 | Room database (sessions, messages, tasks) | ✅ | `memory/GuappaDatabase.kt`, `Entities.kt`, `Daos.kt` | — | 🧪 `test_scenario_memory_persistence.yaml` |
| 1.11 | Context Manager / budget allocation | ✅ | `memory/ContextCompactor.kt` (343 lines) | — | 🧪 `e2e_long_conversation_context.yaml` |
| 1.12 | Streaming responses | ✅ | All providers implement `streamChat()` | — | 🧪 `live_openrouter_chat.yaml` |
| 1.13 | Retry with exponential backoff | ✅ | Logic in orchestrator + ChannelHub reconnect | — | 🧪 `e2e_provider_failover.yaml` |
| 1.14 | Multi-session concurrency | ⚠️ | Session entity exists | Unclear if concurrent sessions tested | 🚫 Needs: `MultiSessionTest.kt` (UI Automator) |
| 1.15 | Session encryption (SQLCipher / Keystore) | ❌ | No encryption layer found | Plain Room database | 🚫 Needs: `e2e_encrypted_db_check.yaml` |
| 1.16 | Dependency injection (Hilt/Koin) | ❌ | No DI framework | Manual wiring | 🚫 N/A — architectural |

---

## Phase 2: Provider Router — Dynamic Model Discovery

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 2.1 | ProviderRouter (capability-based routing) | ✅ | `providers/ProviderRouter.kt` (114 lines) | — | 🧪 `live_openrouter_chat.yaml` (indirect) |
| 2.2 | Provider interface | ✅ | `providers/Provider.kt` | `chat()`, `streamChat()`, `listModels()` | 🚫 N/A — interface |
| 2.3 | AnthropicProvider | ✅ | `providers/AnthropicProvider.kt` (310 lines) | — | 🚫 Needs: `e2e_anthropic_chat.yaml` |
| 2.4 | OpenAI-compatible provider (base) | ✅ | `providers/OpenAICompatibleProvider.kt` (307 lines) | — | 🧪 `live_openrouter_chat.yaml` |
| 2.5 | Google Gemini provider | ✅ | `providers/GoogleGeminiProvider.kt` | — | 🚫 Needs: `e2e_gemini_chat.yaml` |
| 2.6 | Dynamic model fetching | ✅ | `listModels()` in all providers | — | 🧪 `e2e_model_list_fetch.yaml` |
| 2.7 | CapabilityInferrer | ✅ | `providers/CapabilityInferrer.kt` | — | 🧪 Unit test: `CapabilityInferrerTest.kt` |
| 2.8 | CapabilityType enum | ✅ | `providers/CapabilityType.kt` | — | 🚫 N/A — enum |
| 2.9 | CostTracker | ✅ | `providers/CostTracker.kt` | — | 🧪 `e2e_cost_display.yaml` + unit test |
| 2.10 | ProviderFactory | ✅ | `providers/ProviderFactory.kt` | — | 🧪 Unit test: `ProviderFactoryTest.kt` |
| 2.11 | Local inference (llama.rn GGUF) | ✅ | `localLlmServer.ts` + NanoHTTPD | — | 🧪 `e2e_local_llm_chat.yaml` |
| 2.12 | LiteRT-LM (Gemini Nano) | ❌ | Not found | — | 🚫 |
| 2.13 | Qualcomm GENIE (NPU) | ❌ | Not found | — | 🚫 |
| 2.14 | ONNX Runtime Mobile | ❌ | Not found | — | 🚫 |
| 2.15 | HardwareProbe (SoC/NPU detection) | ✅ | `providers/HardwareProbe.kt` — SoC detection, NPU, model recommendations | — | 🧪 Unit test: `HardwareProbeTest.kt` |
| 2.16 | ModelDownloadManager | ✅ | `ModelDownloaderModule.kt` + `modelDownloader.ts` | — | 🧪 `e2e_model_download.yaml` |
| 2.17 | Token counter (tiktoken) | ✅ | `providers/TokenCounter.kt` — approximate cl100k_base tokenizer | — | 🧪 Unit test: `TokenCounterTest.kt` |
| 2.18 | Separate model per capability | ⚠️ | ConfigStore has fields | UI supports but routing unverified | 🚫 Needs: `e2e_vision_model_routing.yaml` |
| 2.19 | OAuth — OpenAI Codex | ❌ | Only API key auth | — | 🚫 |
| 2.20 | OAuth — Anthropic | ❌ | Only API key auth | — | 🚫 |
| 2.21 | OAuth — GitHub Copilot | ❌ | Only API key auth | — | 🚫 |
| 2.22 | OAuth — Google Gemini CLI | ❌ | Only API key auth | — | 🚫 |
| 2.23 | OAuth infrastructure (PKCE, token refresh) | ❌ | No OAuth code anywhere | — | 🚫 |

---

## Phase 3: Tool Engine — 65+ Tools

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 3.1 | ToolEngine (registry, dispatch) | ✅ | `tools/ToolEngine.kt` (62 lines) | — | 🧪 All tool tests exercise this |
| 3.2 | ToolRegistry | ✅ | `tools/ToolRegistry.kt` (153 lines) | — | 🧪 Unit test: `ToolRegistryTest.kt` |
| 3.3 | ToolResult | ✅ | `tools/ToolResult.kt` | — | 🧪 Unit test: `ToolResultTest.kt` |
| 3.4 | ToolPermissions | ✅ | `tools/ToolPermissions.kt` | — | 🧪 `PermissionFlowTest.kt` (UI Automator) |
| 3.5 | ToolRateLimiter | ✅ | `tools/ToolRateLimiter.kt` | — | 🧪 `e2e_rate_limit_feedback.yaml` + unit test |
| 3.6 | ToolAuditLog | ✅ | `tools/ToolAuditLog.kt` | — | 🧪 `e2e_audit_log_visible.yaml` + unit test |
| 3.7 | Tool implementations (78 tools) | ✅ | `tools/impl/` | Exceeds 65 target | 🧪 Multiple Maestro flows |
| 3.8 | Device tools | ✅ | SMS, call, contacts, calendar, camera, location, sensors | — | 🧪 `test_scenario_incoming_call_telegram.yaml`, `e2e_phase2_call_hook_setup.yaml` |
| 3.9 | App tools | ✅ | Launch, list, alarm, timer, reminder, email, browser, maps, music | — | 🧪 `e2e_set_alarm.yaml`, `e2e_launch_app.yaml` |
| 3.10 | Web tools | ✅ | WebFetch, WebSearch, WebScrape, WebApi | — | 🧪 `e2e_brave_web_search.yaml` |
| 3.11 | File tools | ✅ | Read, write, search, list, delete | — | 🧪 `e2e_file_read_write.yaml` |
| 3.12 | Social tools | ✅ | Twitter, Instagram, Telegram, WhatsApp, SocialShare | — | 🧪 `e2e_telegram_send_channel.yaml` |
| 3.13 | AI tools | ✅ | ImageAnalyze, OCR, CodeInterpreter, Calculator, Translation | — | 🧪 `e2e_calculator_tool.yaml`, `e2e_image_analyze.yaml` |
| 3.14 | System tools | ✅ | Shell, PackageInfo, SystemInfo, ProcessList | — | 🧪 `e2e_system_info.yaml` |
| 3.15 | Android AppFunctions API | ❌ | Not implemented | `androidx.appfunctions:0.1.0-alpha01` now available | 🚫 |
| 3.15b | Android UI Automation Framework | ⚠️ | `AgentAccessibilityService.kt` exists | Uses accessibility, not AppFunctions | 🚫 |
| 3.16 | ScreenshotTool (MediaProjection) | ✅ | `ScreenshotTool.kt` | — | 🧪 `e2e_screenshot_tool.yaml` |
| 3.17 | CronJobTool | ✅ | `CronJobTool.kt` (241 lines) | — | 🧪 `e2e_cron_schedule.yaml` |
| 3.18 | GeofenceTool | ✅ | `GeofenceTool.kt` (230 lines) | — | 🧪 `e2e_geofence_setup.yaml` |
| 3.19 | Additional tools (NFC, BT, QR, PDF, RSS) | ✅ | 6+ tools | — | 🧪 `e2e_qr_scan.yaml` |
| 3.20 | Hook incoming call tool | ✅ | `RuntimeBridge.kt` → `hook_incoming_call` | — | 🧪 `e2e_phase2_call_hook_setup.yaml` + `IncomingCallHookTest.kt` |
| 3.21 | Browser session tool | ⚠️ | `AgentBrowserActivity.kt` | Not headless as planned | 🧪 `e2e_browser_session.yaml` |

---

## Phase 4: Proactive Agent & Push Notifications

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 4.1 | ProactiveEngine | ✅ | `proactive/ProactiveEngine.kt` (118 lines) | — | 🧪 `e2e_proactive_trigger.yaml` |
| 4.2 | GuappaNotificationManager | ✅ | `proactive/GuappaNotificationManager.kt` | — | 🧪 `NotificationTest.kt` (UI Automator) |
| 4.3 | NotificationChannels | ✅ | `proactive/NotificationChannels.kt` | — | 🚫 N/A — config |
| 4.4 | TriggerManager | ✅ | `proactive/TriggerManager.kt` (274 lines) | — | 🧪 `e2e_phase2_call_hook_setup.yaml` |
| 4.5 | IncomingCallReceiver | ✅ | `IncomingCallReceiver.kt` | Emits to RN + MessageBus | 🧪 `test_scenario_incoming_call_telegram.yaml` |
| 4.6 | IncomingSmsReceiver | ✅ | `IncomingSmsReceiver.kt` | — | 🧪 `e2e_incoming_sms_trigger.yaml` |
| 4.7 | BatteryReceiver | ✅ | `proactive/BatteryReceiver.kt` | — | 🧪 `e2e_battery_low_trigger.yaml` |
| 4.8 | CalendarObserver | ✅ | `proactive/CalendarObserver.kt` | — | 🚫 Needs: `e2e_calendar_reminder.yaml` |
| 4.9 | EventReactor | ✅ | `proactive/EventReactor.kt` | — | 🧪 Tested via trigger tests |
| 4.10 | SmartTiming | ✅ | `proactive/SmartTiming.kt` | — | 🚫 Needs: `e2e_dnd_respects_timing.yaml` |
| 4.11 | ProactiveRules | ✅ | `proactive/ProactiveRules.kt` | — | 🧪 `e2e_proactive_rules_config.yaml` |
| 4.12 | TaskCompletionReporter | ✅ | `proactive/TaskCompletionReporter.kt` | — | 🚫 Covered by task tests |
| 4.13 | NotificationActionReceiver | ✅ | `proactive/NotificationActionReceiver.kt` | — | 🚫 Needs: `NotificationActionTest.kt` (UI Automator) |
| 4.14 | NotificationDeduplicator | ✅ | `proactive/NotificationDeduplicator.kt` | — | 🚫 Unit test sufficient |
| 4.15 | NotificationHistory | ✅ | `proactive/NotificationHistory.kt` | — | 🧪 `e2e_notification_history.yaml` |
| 4.16 | MorningBriefingWorker | ✅ | `proactive/MorningBriefingWorker.kt` | — | 🚫 Hard to time — use WorkManager test utils |
| 4.17 | DailySummaryWorker | ✅ | `proactive/DailySummaryWorker.kt` | — | 🚫 Same as above |
| 4.18 | MessagingStyle notifications | ⚠️ | NotificationManager exists | Verify MessagingStyle used | 🚫 |
| 4.19 | Inline reply from notification | ⚠️ | NotificationActionReceiver exists | Verify direct reply input | 🚫 |
| 4.20 | LocationGeofenceReceiver | ✅ | `proactive/GeofenceBroadcastReceiver.kt` — receives PendingIntent transitions, publishes to MessageBus | — | 🧪 Unit test: `GeofenceReceiverTest.kt` |
| 4.21 | Network state receiver | ✅ | `proactive/NetworkStateReceiver.kt` — publishes to MessageBus | — | 🚫 Unit test sufficient |
| 4.22 | Screen state receiver | ✅ | `proactive/ScreenStateReceiver.kt` — dynamic registration | — | 🚫 Unit test sufficient |

---

## Phase 5: Channel Hub — Messenger Integrations

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 5.1 | Channel interface | ✅ | `channels/Channel.kt` — includes `incoming()` Flow | — | 🚫 N/A — interface |
| 5.2 | ChannelHub | ✅ | `channels/ChannelHub.kt` (153 lines) — health monitoring, exponential backoff reconnect | — | 🚫 N/A — manager |
| 5.3 | ChannelFactory | ✅ | `channels/ChannelFactory.kt` (52 lines) | — | 🧪 Unit test: `ChannelFactoryTest.kt` |
| 5.4 | Telegram (send via Bot API) | ✅ | `channels/TelegramChannel.kt` | — | 🧪 `e2e_telegram_send_channel.yaml` |
| 5.5 | Telegram incoming (long polling) | ✅ | `TelegramChannel.kt` — `getUpdates` with 30s timeout, offset tracking | — | 🧪 `e2e_telegram_receive.yaml` |
| 5.6 | Discord (webhook send + gateway) | ✅ | `channels/DiscordChannel.kt` (~170 lines) — webhook send + Bot Gateway WebSocket incoming | — | 🧪 Unit test: `DiscordChannelTest.kt` |
| 5.7 | Discord gateway (incoming) | ✅ | Integrated in `DiscordChannel.kt` — WebSocket, heartbeat, MESSAGE_CREATE events | — | 🧪 Unit test: `DiscordChannelTest.kt` |
| 5.8 | Slack (webhook send + polling) | ✅ | `channels/SlackChannel.kt` (~115 lines) — webhook send + conversations.history polling | — | 🧪 Unit test: `SlackChannelTest.kt` |
| 5.9 | WhatsApp | ⚠️ | `channels/WhatsAppChannel.kt` (92 lines) | Uses deep links, not Cloud API | 🚫 |
| 5.10 | Signal | 🔧 | `channels/SignalChannel.kt` (59 lines) | Verify signald integration | 🚫 |
| 5.11 | Matrix | ⚠️ | `channels/MatrixChannel.kt` (75 lines) | No E2EE, no sync loop | 🚫 |
| 5.12 | Email (SMTP relay + Intent) | ✅ | `channels/EmailChannel.kt` (~100 lines) — SMTP relay API or Intent fallback | — | 🧪 `e2e_email_compose.yaml` |
| 5.13 | SMS channel | ✅ | `channels/SmsChannel.kt` (47 lines) | — | 🧪 `e2e_sms_send.yaml` |
| 5.14 | GuappaChannelsModule (RN bridge) | ✅ | `channels/GuappaChannelsModule.kt` (240 lines) | — | 🧪 Tested via channel-specific tests |
| 5.15 | Channel `incoming()` Flow | ✅ | Defined in `Channel.kt` interface, implemented in TelegramChannel | — | 🧪 `e2e_telegram_receive.yaml` |
| 5.16 | ChannelHealthMonitor | ✅ | Integrated in `ChannelHub.kt` — reconnect with exponential backoff | — | 🚫 Unit test sufficient |
| 5.17 | Channel formatters | ✅ | `channels/ChannelFormatter.kt` — per-channel formatting (Telegram, Discord, Slack, SMS) | — | 🧪 Unit test: `ChannelFormatterTest.kt` |

---

## Phase 6: Voice Pipeline — STT, TTS, Wake Word

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 6.1 | useVoiceRecording (STT hook) | ✅ | `hooks/useVoiceRecording.ts` | — | 🧪 `guappa_voice_full_flow.yaml` |
| 6.2 | Deepgram STT (cloud, streaming) | ✅ | WebSocket to `api.deepgram.com` — uses **nova-3** | — | 🧪 `voice_interruptible_smoke.yaml` |
| 6.3 | Whisper STT (on-device) | ✅ | `whisper.rn` + GGML download | — | 🧪 `e2e_local_whisper_stt.yaml` |
| 6.4 | WhisperModelManager | ✅ | `voice/whisperModelManager.ts` | — | 🚫 Covered by whisper tests |
| 6.5 | useTTS (text-to-speech) | ✅ | `hooks/useTTS.ts` | Uses `expo-speech` | 🧪 `e2e_builtin_tts_response.yaml` |
| 6.6 | useVAD (voice activity detection) | ✅ | `hooks/useVAD.ts` | Energy-based | 🧪 Tested via voice flow |
| 6.7 | useWakeWord ("Hey Guappa") | ✅ | `hooks/useWakeWord.ts` | Energy + STT keyword | 🚫 Needs: `e2e_wake_word.yaml` |
| 6.8 | VoiceAmplitude | ✅ | `swarm/audio/VoiceAmplitude.ts` | — | 🧪 `e2e_voice_swarm_emotion.yaml` |
| 6.9 | VoiceScreen UI | ✅ | `screens/tabs/VoiceScreen.tsx` (338 lines) | — | 🧪 `guappa_voice_full_flow.yaml` |
| 6.10 | Streaming TTS | ⚠️ | Sentence-level queue | Not word-level | 🚫 |
| 6.11 | Android `SpeechRecognizer` (built-in) | ✅ | `voice/AndroidSTTModule.kt` + `AndroidSTTPackage.kt` — free, zero API key | — | 🧪 `e2e_android_stt_fallback.yaml` |
| 6.12 | Google ML Kit Speech | ❌ | Not found | — | 🚫 |
| 6.13 | Google Cloud Speech-to-Text | ❌ | Not found | — | 🚫 |
| 6.14 | Android TextToSpeech (built-in) | ✅ | Via `expo-speech` | — | 🧪 Via TTS tests |
| 6.15 | Picovoice Orca TTS | ❌ | Not found | Commercial | 🚫 |
| 6.16 | Kokoro TTS (on-device) | ❌ | Not found | Apache 2.0, best quality/size | 🚫 |
| 6.17 | Piper TTS (on-device) | ❌ | Not found | MIT, 100+ voices | 🚫 |
| 6.18 | ElevenLabs TTS (cloud) | ❌ | Not found | — | 🚫 |
| 6.19 | OpenAI TTS (cloud) | ❌ | Not found | — | 🚫 |
| 6.20 | Speechmatics TTS (cloud) | ❌ | Not found | — | 🚫 |
| 6.21 | Google Cloud TTS | ❌ | Not found | — | 🚫 |
| 6.22 | Picovoice Porcupine wake word | ❌ | Using custom energy-based | — | 🚫 |
| 6.23 | Silero VAD | ⚠️ | `voice/SileroVADEngine.kt` — energy-based placeholder, ONNX inference TODO | — | 🧪 Unit test: `SileroVADTest.kt` |
| 6.24 | Audio routing (speaker/earpiece/BT) | ✅ | `voice/AudioRoutingManager.kt` — speaker, earpiece, BT SCO, wired headset, auto-detect | — | 🧪 Unit test: `AudioRoutingManagerTest.kt` |
| 6.25 | Audio focus management | ✅ | `voice/AudioFocusManager.kt` — transient exclusive, delayed gain, focus callbacks | — | 🧪 Unit test: `AudioFocusManagerTest.kt` |
| 6.26 | Bluetooth SCO/A2DP routing | ✅ | Integrated in `AudioRoutingManager.kt` — SCO connect/disconnect, A2DP detection | — | 🧪 Unit test: `AudioRoutingManagerTest.kt` |
| 6.27 | STT/TTS engine selection UI | ✅ | `voice/voiceEngineManager.ts` — STT/TTS engine selection | — | 🧪 `e2e_stt_engine_switch.yaml` |
| 6.28 | SpeechRecognizer as free STT fallback | ✅ | `voice/AndroidSTTModule.kt` — uses `SpeechRecognizer` API | — | 🧪 `e2e_android_stt_fallback.yaml` |

### Deepgram Full Product Catalog

| # | Feature | Status | Description | E2E Test |
|---|---------|--------|-------------|----------|
| 6.30 | Flux STT (voice agent) | ❌ | Built-in end-of-turn detection | 🚫 |
| 6.31 | Nova-3 STT | ✅ | Currently used (`?model=nova-3` in useVoiceRecording) | 🧪 `voice_interruptible_smoke.yaml` |
| 6.32 | Nova-3 Medical | ❌ | Medical terminology | 🚫 |
| 6.33 | Nova-2 STT | ✅ | Superseded by Nova-3 | 🧪 Legacy |
| 6.36 | Aura-2 TTS | ✅ | `voice/deepgramTTS.ts` — 10 voices, 7 languages, streaming | 🚫 Needs: `e2e_deepgram_aura2_tts.yaml` |
| 6.38 | Audio Intelligence (summarize, topics, sentiment) | ❌ | Add-on query params to STT | 🚫 |

---

## Phase 7: Memory & Context

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 7.1 | MemoryManager (5-tier) | ✅ | `memory/MemoryManager.kt` (627 lines) | — | 🧪 `test_scenario_memory_persistence.yaml` |
| 7.2 | Tier 1: Working memory | ✅ | ContextCompactor manages | — | 🧪 `e2e_phase1_memory_store.yaml` |
| 7.3 | Tier 2: Short-term memory | ✅ | Room SessionEntity, MessageEntity | — | 🧪 `e2e_phase4_memory_recall.yaml` |
| 7.4 | Tier 3: Long-term memory (facts) | ✅ | `MemoryFactEntity` | — | 🧪 `e2e_phase4_memory_recall.yaml` |
| 7.5 | Tier 4: Episodic memory | ✅ | `EpisodeEntity` | — | 🧪 `e2e_episodic_recall.yaml` |
| 7.6 | Tier 5: Semantic memory (embeddings) | ✅ | `EmbeddingEntity` + `EmbeddingService.kt` | — | 🧪 `e2e_semantic_search.yaml` |
| 7.7 | ContextCompactor | ✅ | `memory/ContextCompactor.kt` (343 lines) | — | 🧪 `e2e_long_conversation_context.yaml` |
| 7.8 | SummarizationService | ✅ | `memory/SummarizationService.kt` | — | 🚫 Tested via context compaction |
| 7.9 | MemoryConsolidationWorker | ✅ | `memory/MemoryConsolidationWorker.kt` | — | 🧪 `e2e_memory_consolidation.yaml` |
| 7.10 | EmbeddingService | ✅ | `memory/EmbeddingService.kt` | — | 🧪 Tested via semantic search |
| 7.11 | MemoryBridge (RN module) | ✅ | `memory/MemoryBridge.kt` — includes export/import | — | 🧪 Via memory screen tests |
| 7.12 | MemoryScreen (UI) | ✅ | `screens/tabs/MemoryScreen.tsx` (363 lines) | — | 🧪 `e2e_memory_screen_browse.yaml` |
| 7.13 | On-device embedding model | ⚠️ | EmbeddingService exists | May use cloud API | 🚫 Needs: `e2e_offline_embedding.yaml` |
| 7.14 | Recursive summarization | ⚠️ | SummarizationService exists | Verify hierarchical chain | 🚫 |
| 7.15 | Memory export/import | ✅ | `MemoryBridge.kt` — export and import methods | — | 🧪 `e2e_memory_export_import.yaml` |

---

## Phase 9: Testing & QA

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 9.1 | Unit tests (JUnit + MockK) | ✅ | **21 test files** — agent, providers, config, tools, channels, swarm | — | 🧪 All pass (178+ tests) |
| 9.2 | Maestro E2E flows | ✅ | **207 YAML flows** in `.maestro/` including 107 `e2e_*` tests | — | 🧪 Extensive |
| 9.3 | Integration tests (Espresso) | ❌ | Not found | — | 🚫 |
| 9.4 | Performance benchmarks | ❌ | Not found | — | 🚫 |
| 9.5 | Firebase Test Lab config | ❌ | Not found | — | 🚫 |
| 9.6 | CI pipeline (.github/workflows) | ✅ | `.github/workflows/ci.yml` — TS tests → Kotlin tests → Android build → Maestro E2E | — | 🧪 |
| 9.7 | Resilience tests | ⚠️ | `e2e_resilience_session_persist.yaml` exists | No systematic chaos testing | 🧪 Partial |
| 9.8 | UI Automator 2.4 instrumented tests | ✅ | **4 files**: `AgentMemoryTest.kt`, `PermissionFlowTest.kt`, `NotificationTest.kt`, `IncomingCallHookTest.kt` | — | 🧪 |
| 9.9 | `testInstrumentationRunner` | ✅ | `defaultConfig { testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner" }` | — | 🧪 |
| 9.10 | UI Automator Shell | ❌ | Not found | — | 🚫 |
| 9.11 | Macrobenchmark / Baseline Profiles | ❌ | Not found | — | 🚫 |

---

## Phase 10: Live Config — Hot Reload

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 10.1 | GuappaConfigStore | ✅ | `config/GuappaConfigStore.kt` (413 lines) | — | 🧪 Config screen Maestro tests |
| 10.2 | ConfigBridge (RN module) | ✅ | `config/ConfigBridge.kt` | — | 🧪 Config screen tests |
| 10.3 | ConfigChangeDispatcher | ✅ | `config/ConfigChangeDispatcher.kt` | — | 🚫 Tested indirectly |
| 10.4 | ProviderHotSwap | ✅ | `config/ProviderHotSwap.kt` | — | 🧪 `e2e_provider_hot_swap.yaml` |
| 10.5 | ChannelHotSwap | ✅ | `config/ChannelHotSwap.kt` | — | 🚫 |
| 10.6 | ToolHotSwap | ✅ | `config/ToolHotSwap.kt` | — | 🚫 |
| 10.7 | SecurePrefs | ✅ | `config/SecurePrefs.kt` | — | 🧪 `e2e_api_key_persists.yaml` |
| 10.8 | TurboModule (New Architecture) | ❌ | Uses old NativeModule | — | 🚫 |
| 10.9 | VoiceHotSwap | ✅ | `config/VoiceHotSwap.kt` — STT/TTS engine switching at runtime | — | 🧪 `e2e_stt_engine_switch.yaml` |
| 10.10 | MemoryHotSwap | ✅ | `config/MemoryHotSwap.kt` — embedding model, consolidation interval | — | 🚫 Unit test sufficient |
| 10.11 | ConfigMigrator | ✅ | `config/ConfigMigrator.kt` — schema versioning, called in `MainApplication.onCreate()` | — | 🚫 Unit test sufficient |
| 10.12 | ConfigValidator | ✅ | `config/ConfigValidator.kt` — API key, URL, model validation | — | 🧪 `e2e_invalid_config_feedback.yaml` + unit test: `ConfigValidatorTest.kt` |

---

## Phase 11: World Wide Swarm

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 11.1 | SwarmManager | ✅ | `swarm/SwarmManager.kt` (261 lines) | — | 🧪 `e2e_swarm_connect.yaml` |
| 11.2 | SwarmConnectorClient | ✅ | `swarm/SwarmConnectorClient.kt` (185 lines) | — | 🧪 `e2e_swarm_connect.yaml` |
| 11.3 | SwarmConfig | ✅ | `swarm/SwarmConfig.kt` | — | 🧪 Unit test: `SwarmConfigTest.kt` |
| 11.4 | SwarmIdentity (Ed25519, DID) | ✅ | `swarm/SwarmIdentity.kt` | — | 🧪 `e2e_swarm_identity_gen.yaml`, `e2e_swarm_identity_flow.yaml` |
| 11.5 | SwarmChallengeSolver | ✅ | `swarm/SwarmChallengeSolver.kt` | — | 🧪 Unit test: `SwarmChallengeSolverTest.kt` |
| 11.6–11.12 | SwarmTask, Poller, Executor, Message, Holon, Reputation, PeerInfo | ✅ | All exist | — | 🧪 `e2e_swarm_task_lifecycle.yaml` + unit tests |
| 11.13 | GuappaSwarmModule (RN bridge) | ✅ | `swarm/GuappaSwarmModule.kt` | — | 🧪 Swarm screen tests |
| 11.14 | SwarmScreen (UI) | ✅ | `screens/tabs/SwarmScreen.tsx` (1694 lines) | — | 🧪 `e2e_swarm_screen_tabs.yaml`, `e2e_swarm_pull_refresh.yaml` |
| 11.15 | Embedded connector (Rust) | ❌ | Not found | Phase 11b | 🚫 |
| 11.16 | mDNS local discovery | ✅ | `swarm/MdnsDiscovery.kt` — NsdManager, `_guappa-swarm._tcp` service type, auto-connect | — | 🧪 Unit test: `MdnsDiscoveryTest.kt` |

---

## Phase 12: Android UI

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 12.1 | 5-screen app | ✅ | All 5 screens + RootNavigator | — | 🧪 `e2e_navigate_all_screens.yaml`, `e2e_navigation_all_tabs.yaml` |
| 12.2 | FloatingDock | ✅ | `components/dock/FloatingDock.tsx` — bright white icons, glass blur | — | 🧪 `e2e_dock_icons_visible.yaml` |
| 12.3 | SideRail (tablet) | ✅ | `components/dock/SideRail.tsx` | — | 🚫 Needs: `e2e_tablet_side_rail.yaml` (tablet emulator) |
| 12.4 | Glass design system | ✅ | 15 glass components including LiquidGlass (Skia) | — | 🚫 |
| 12.5 | PlasmaOrb | ✅ | `components/plasma/PlasmaOrb.tsx` | — | 🚫 |
| 12.6 | ChatScreen | ✅ | `screens/tabs/ChatScreen.tsx` (342 lines) | — | 🧪 `e2e_chat_send_message.yaml`, `e2e_chat_empty_state.yaml` |
| 12.7 | CommandScreen | ✅ | `screens/tabs/CommandScreen.tsx` (1497 lines) | — | 🧪 `e2e_command_screen_sections.yaml`, `e2e_command_empty_states.yaml` |
| 12.8 | ConfigScreen | ✅ | `screens/tabs/ConfigScreen.tsx` (1373 lines) | — | 🧪 `e2e_config_screen_sections.yaml`, `e2e_config_section_icons.yaml` |
| 12.9 | OnboardingScreen | ✅ | `screens/OnboardingScreen.tsx` + 4 steps | — | 🧪 `e2e_onboarding_flow.yaml`, `e2e_onboarding_complete.yaml` |
| 12.10 | Color system | ✅ | `theme/colors.ts` — Storm Palette with bright secondary/tertiary text | — | 🧪 `e2e_storm_palette_visual.yaml` |
| 12.11 | Typography | ✅ | `theme/typography.ts` | — | 🚫 |
| 12.12 | Animations | ✅ | `theme/animations.ts` + Reanimated | — | 🚫 |
| 12.13 | Gyroscope parallax | ⚠️ | Camera3D uses Accelerometer | SwarmCanvas only | 🧪 `e2e_tilt_3d_rotation.yaml` |

---

## Phase 14: Neural Swarm Visualization

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 14.1 | SwarmCanvas (Skia, 420 neurons) | ✅ | `swarm/SwarmCanvas.tsx` (~590 lines) — HDR star nodes, bright connections | — | 🧪 `e2e_voice_swarm_emotion.yaml` |
| 14.2 | NeuronSystem (3D physics) | ✅ | `swarm/neurons/NeuronSystem.ts` (~330 lines) — bigger nodes, higher brightness | — | 🚫 |
| 14.3 | Camera3D | ✅ | `swarm/camera/Camera3D.ts` (130 lines) | — | 🚫 |
| 14.4 | SwarmController (state machine) | ✅ | `swarm/SwarmController.ts` (73 lines) | — | 🧪 `e2e_voice_swarm_emotion.yaml` |
| 14.5 | SwarmDirector | ✅ | `swarm/SwarmDirector.ts` (190 lines) | — | 🧪 `e2e_swarm_director_formation.yaml` |
| 14.6 | EmotionPalette (20 emotions) | ✅ | `swarm/emotion/EmotionPalette.ts` (63 lines) | — | 🚫 Needs: `e2e_emotion_palette_colors.yaml` |
| 14.7 | EmotionBlender | ✅ | `swarm/emotion/EmotionBlender.ts` (41 lines) | — | 🚫 |
| 14.8 | ShapeLibrary | ✅ | `swarm/formations/ShapeLibrary.ts` (620 lines) | — | 🚫 |
| 14.9 | TextRenderer | ✅ | `swarm/formations/TextRenderer.ts` (105 lines) | — | 🚫 |
| 14.10 | HarmonicWaves | ✅ | `swarm/waves/HarmonicWaves.ts` (138 lines) | — | 🚫 |
| 14.11 | VoiceAmplitude | ✅ | `swarm/audio/VoiceAmplitude.ts` (78 lines) | — | 🧪 `e2e_voice_swarm_emotion.yaml` |
| 14.12 | Neural swarm background on all screens | ✅ | RootNavigator: opacity per screen | — | 🚫 |
| 14.13 | Swarm ↔ voice integration | ✅ | VoiceScreen, useVoiceRecording, useTTS | — | 🧪 `e2e_voice_swarm_emotion.yaml` |

---

## Phase 13: Documentation

| # | Feature | Status | Evidence | Gap | E2E Test |
|---|---------|--------|----------|-----|----------|
| 13.1 | Guides | ✅ | 7 guide files in `docs/guides/` | — | 🚫 N/A |
| 13.2 | Reference docs | ✅ | `docs/reference/api.md` — complete API reference for all 7 native modules | — | 🚫 N/A |
| 13.3 | Architecture docs | ✅ | `docs/architecture/overview.md` — system diagram, data flow, module boundaries | — | 🚫 N/A |
| 13.4 | Development docs | ✅ | `docs/development/setup.md` — setup, testing guide, project structure | — | 🚫 N/A |
| 13.5 | AGENTS.md | ✅ | Root `AGENTS.md` | — | 🚫 N/A |

---

## Local Inference — Full Inventory

| # | Modality | Status | Engine | E2E Test |
|---|----------|--------|--------|----------|
| L.1 | Text (LLM) — GGUF | ✅ | `llama.rn` + NanoHTTPD | 🧪 `e2e_local_llm_chat.yaml` |
| L.2 | STT (on-device Whisper) | ✅ | `whisper.rn` GGML | 🧪 `e2e_local_whisper_stt.yaml` |
| L.3 | STT (free, zero-download) | ✅ | `AndroidSTTModule.kt` — `SpeechRecognizer` API | 🧪 `e2e_android_stt_fallback.yaml` |
| L.4 | TTS (built-in) | ✅ | `expo-speech` | 🧪 `e2e_builtin_tts_response.yaml` |
| L.5 | TTS (high-quality on-device) | ❌ | Kokoro / Piper | 🚫 |
| L.6 | Embeddings (on-device) | ⚠️ | `EmbeddingService.kt` | 🚫 Needs: `e2e_offline_embedding.yaml` |
| L.7 | Vision (on-device) | ❌ | — | 🚫 |
| L.8 | Image generation (on-device) | ❌ | — | 🚫 |
| L.9 | Text — MediaPipe/LiteRT | ❌ | — | 🚫 |
| L.10 | Text — LiteRT LM | ❌ | — | 🚫 |
| L.11 | Text — ONNX | ❌ | — | 🚫 |
| L.12 | Text — Nexa SDK (NPU) | ❌ | — | 🚫 |
| L.13 | HardwareProbe (SoC detection) | ✅ | `providers/HardwareProbe.kt` — SoC, NPU, RAM, model recommendations | 🧪 Unit test: `HardwareProbeTest.kt` |

---

## Voice E2E Testing Methodology — Virtual Audio Cable

Voice features (STT, TTS, wake word, neuroswarm voice reactions) require **real audio flowing through the emulator microphone**. We use a Virtual Audio Cable to bridge host TTS output → emulator mic input, enabling fully automated voice E2E tests.

### Prerequisites (Host Setup — Done Once)

| Step | macOS | Windows |
|------|-------|---------|
| 1. Install virtual audio driver | **BlackHole 2ch** (`brew install blackhole-2ch`) | **VB-CABLE Virtual Audio Device** |
| 2. Set host sound output | System Settings → Sound → Output → **BlackHole 2ch** | Settings → Sound → Output → **CABLE Input** |
| 3. Configure emulator mic | Emulator sidebar `...` → Settings → Microphone → **"Virtual microphone uses host audio input" = ON** | Same |
| 4. Install TTS generator | `pip install gTTS` | `pip install gTTS` |

### TTS-to-STT Automation Script

**File**: `mobile-app/.maestro/scripts/generate_and_play.py`

```python
import sys
from gtts import gTTS
import os
import platform
import subprocess

text = sys.argv[1]
tts = gTTS(text=text, lang='en')
tts.save("/tmp/guappa_speech.mp3")

if platform.system() == "Darwin":
    subprocess.Popen(["afplay", "/tmp/guappa_speech.mp3"])
else:
    os.system("start /B ffplay -nodisp -autoexit /tmp/guappa_speech.mp3")
```

### Maestro Voice Test Pattern

```yaml
appId: com.guappa.app
---
# 1. Navigate to voice screen
- tapOn:
    id: "dock-tab-voice"
- waitForAnimationToEnd

# 2. Generate and play TTS audio into virtual mic
- runScript:
    file: scripts/generate_and_play.py
    args: ["Find recipes for chocolate cake"]

# 3. Tap the microphone button to start STT
- tapOn:
    id: "voice-mic-button"
- waitForAnimationToEnd

# 4. Wait for STT transcription + agent response
- extendedWaitUntil:
    visible:
      text: ".*chocolate cake.*"
    timeout: 15000

# 5. Verify neuroswarm reacted (processing state)
- assertVisible:
    text: "Processing"
    optional: true
```

### Voice Test Matrix

| Test | Script | What It Validates |
|------|--------|-------------------|
| `e2e_voice_stt_basic.yaml` | `"Hello Guappa"` | STT transcription appears in chat |
| `e2e_voice_tts_playback.yaml` | Send text → assert TTS plays | TTS engine produces audio |
| `e2e_voice_wake_word.yaml` | `"Hey Guappa, what time is it"` | Wake word triggers STT → agent response |
| `e2e_voice_swarm_reaction.yaml` | `"Tell me a story"` | Neuroswarm transitions to listening → processing → speaking |
| `e2e_voice_deepgram_stt.yaml` | `"Search for nearby restaurants"` | Deepgram Nova-3 cloud STT with API key |
| `e2e_voice_android_stt.yaml` | `"Set a timer for five minutes"` | Android SpeechRecognizer fallback STT |
| `e2e_voice_interrupt.yaml` | Play TTS → interrupt mid-sentence | Interruptible voice pipeline |

### Key Notes

- **BlackHole must be the active output** during test runs — no sound on speakers while testing
- `afplay` is non-blocking via `subprocess.Popen` — Maestro continues immediately
- For multi-turn voice tests, add `- scroll` or `- delay: 3000` between utterances
- The emulator mic setting persists across restarts but resets on cold boot

---

## Summary — E2E Coverage Stats

| Phase | Total Features | Has E2E | Missing E2E | Coverage |
|-------|---------------|---------|-------------|----------|
| Phase 1: Foundation | 16 | 12 | 4 | **75%** |
| Phase 2: Provider Router | 23 | 9 | 14 | **39%** |
| Phase 3: Tool Engine | 21 | 18 | 3 | **86%** |
| Phase 4: Proactive | 22 | 12 | 10 | **55%** |
| Phase 5: Channels | 17 | 12 | 5 | **71%** |
| Phase 6: Voice | 28 | 16 | 12 | **57%** |
| Phase 7: Memory | 15 | 13 | 2 | **87%** |
| Phase 9: Testing | 11 | 6 | 5 | **55%** |
| Phase 10: Config | 12 | 9 | 3 | **75%** |
| Phase 11: Swarm | 16 | 11 | 5 | **69%** |
| Phase 12: UI | 13 | 10 | 3 | **77%** |
| Phase 14: Visualization | 13 | 5 | 8 | **38%** |
| Phase 13: Documentation | 5 | 5 | 0 | **100%** |
| Local Inference | 13 | 6 | 7 | **46%** |
| **TOTAL** | **225** | **144** | **81** | **64%** |

---

## 🔴 Critical Gaps — Prioritized

### P0 — Blockers

| # | Gap | Impact | Status |
|---|-----|--------|--------|
| 1 | **Session encryption (SQLCipher)** | Plain Room database — security concern | ❌ Needs SQLCipher dependency |
| 2 | ~~Discord/Slack gateway incoming~~ | ~~Send-only~~ | ✅ **RESOLVED** — Discord gateway + Slack polling implemented |
| 3 | **OAuth infrastructure** | No OAuth for any provider — all API key only | ❌ Needs PKCE flow |

### P1 — Important

| # | Gap | Impact | Status |
|---|-----|--------|--------|
| 4 | ~~Audio routing (speaker/earpiece/BT)~~ | ~~Can't route voice to Bluetooth~~ | ✅ **RESOLVED** — `AudioRoutingManager.kt` |
| 5 | ~~Audio focus management~~ | ~~Conflicts with other audio apps~~ | ✅ **RESOLVED** — `AudioFocusManager.kt` |
| 6 | Embedded swarm connector (Rust) | Requires external server | ❌ Separate project |
| 7 | On-device high-quality TTS (Kokoro/Piper) | Quality limited to expo-speech | ❌ Needs native build |
| 8 | Macrobenchmark / Baseline Profiles | No startup performance optimization | ❌ |
| 9 | TurboModule (New Architecture) | Uses old NativeModule bridge | ❌ Major refactor |
| 10 | Local inference engines (LiteRT, ONNX, Nexa) | All non-text local modalities need cloud | ❌ SDK deps |
| 11 | ~~mDNS local discovery~~ | ~~Manual connector URL entry~~ | ✅ **RESOLVED** — `MdnsDiscovery.kt` |

### P2 — Nice to Have

| # | Gap | Impact | Status |
|---|-----|--------|--------|
| 12 | DI framework (Hilt/Koin) | Manual wiring | ❌ Architectural |
| 13 | ~~Silero VAD~~ | ~~Energy-based VAD less accurate~~ | ⚠️ Stub created — `SileroVADEngine.kt` |
| 14 | Firebase Test Lab | No cloud device testing | ❌ |
| 15 | Espresso integration tests | No Android UI integration tests | ❌ |
| 16 | ~~Geofence broadcast receiver~~ | ~~No dedicated receiver~~ | ✅ **RESOLVED** — `GeofenceBroadcastReceiver.kt` |
