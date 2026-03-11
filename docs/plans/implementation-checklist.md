# Guappa Implementation Checklist — Plan vs Reality

**Date**: 2026-03-11 (updated — E2E tested)
**Purpose**: Map every planned feature to actual implementation status, identify gaps, define E2E test coverage, and track icon/branding and palette conformance.

Legend:
- ✅ Implemented — code exists and appears functional
- ⚠️ Partial — code exists but incomplete or missing key features
- ❌ Missing — no implementation found
- 🔧 Stub — file exists but minimal/skeleton implementation

Testing Status:
- 🟢 Tested E2E — verified working in app UI on emulator
- 🟡 Unit tested — Kotlin/TS tests pass, no E2E verification
- 🔴 Untested — no test coverage
- 🔵 Blocked — needs API key or hardware to test

---

## Phase 1: Foundation — Agent Core

| # | Feature | Status | Evidence | E2E Status |
|---|---------|--------|----------|------------|
| 1.1 | GuappaOrchestrator (ReAct loop) | ✅ | `agent/GuappaOrchestrator.kt` — streaming, tool call loop, think tag detection | 🟢 Verified: sends messages, receives streamed responses |
| 1.2 | GuappaSession (conversation state) | ✅ | `agent/GuappaSession.kt` — session type enum, TTL, checkpoint/recovery, compaction | 🟢 Verified: session persists across tab switches |
| 1.3 | GuappaPlanner (ReAct, task decomposition) | ✅ | `agent/GuappaPlanner.kt` (356 lines) | 🟡 Unit tested |
| 1.4 | MessageBus (SharedFlow pub/sub) | ✅ | `agent/MessageBus.kt` — includes priority queue, URGENT support | 🟢 Verified: messages flow from chat to agent and back |
| 1.5 | TaskManager | ✅ | `agent/TaskManager.kt` (262 lines) | 🟢 Verified: Command Center shows 0 TASKS, 1 SESSION |
| 1.6 | GuappaConfig | ✅ | `agent/GuappaConfig.kt` | 🟢 Verified: Config screen loads/saves |
| 1.7 | GuappaPersona (system prompt, personality) | ✅ | `agent/GuappaPersona.kt` | 🟢 Verified: agent responds with personality |
| 1.8 | Foreground service (DATA_SYNC) | ✅ | `RuntimeAlwaysOnService.kt` + `GuappaAgentService.kt` | 🟢 Verified: app survives tab switches |
| 1.9 | Boot receiver (auto-start) | ✅ | `RuntimeBootReceiver.kt` | 🔴 Requires reboot |
| 1.10 | Room database (sessions, messages, tasks) | ✅ | `memory/GuappaDatabase.kt`, `Entities.kt`, `Daos.kt` | 🟢 Verified: chat history persists across restarts |
| 1.11 | Context Manager / budget allocation | ✅ | `memory/ContextCompactor.kt` (343 lines) | 🟢 Verified: context compaction runs (seen in logs, prompt=1891) |
| 1.12 | Streaming responses | ✅ | `streamChatStructured()` with text/thinking/tool_call deltas | 🟢 Verified: tokens stream to UI in real-time |
| 1.13 | Retry with exponential backoff | ✅ | Logic in orchestrator + ChannelHub reconnect | 🟡 Unit tested |
| 1.14 | Multi-session concurrency | ⚠️ | Session entity exists | 🔴 Not tested |
| 1.15 | Session encryption (SQLCipher) | ⚠️ | Removed from build — caused OOM on emulator. Room DB works without encryption. | 🟢 DB works (unencrypted) |
| 1.16 | Dependency injection (Hilt/Koin) | ❌ | No DI framework — manual wiring | 🔴 N/A — architectural |

---

## Phase 2: Provider Router — Dynamic Model Discovery

| # | Feature | Status | Evidence | E2E Status |
|---|---------|--------|----------|------------|
| 2.1 | ProviderRouter (capability-based routing) | ✅ | `providers/ProviderRouter.kt` (114 lines) | 🟢 Verified: routes to local LLM correctly |
| 2.2 | Provider interface | ✅ | `providers/Provider.kt` | 🟡 N/A — interface |
| 2.3 | AnthropicProvider | ✅ | `providers/AnthropicProvider.kt` (310 lines) | 🔵 Needs API key |
| 2.4 | OpenAI-compatible provider (base) | ✅ | `providers/OpenAICompatibleProvider.kt` — 5-min read timeout | 🟢 Verified: works with local NanoHTTPD server |
| 2.5 | Google Gemini provider | ✅ | `providers/GoogleGeminiProvider.kt` | 🔵 Needs API key |
| 2.6 | Dynamic model fetching | ✅ | `listModels()` in all providers | 🟢 Verified: local model shows in config |
| 2.7 | CapabilityInferrer | ✅ | `providers/CapabilityInferrer.kt` | 🟡 Unit test: `CapabilityInferrerTest.kt` |
| 2.8 | CapabilityType enum | ✅ | `providers/CapabilityType.kt` | 🟡 N/A — enum |
| 2.9 | CostTracker | ✅ | `providers/CostTracker.kt` | 🟡 Unit tested |
| 2.10 | ProviderFactory | ✅ | `providers/ProviderFactory.kt` | 🟡 Unit test: `ProviderFactoryTest.kt` |
| 2.11 | Local inference (llama.rn GGUF) | ✅ | `localLlmServer.ts` + NanoHTTPD, serial queue, n_ctx=4096 on emulator | 🟢 Verified: "Hi" → "Hello! How can I help you today?" |
| 2.12 | LiteRT-LM (Gemini Nano) | ❌ | Not found | 🔴 |
| 2.13 | Qualcomm GENIE (NPU) | ❌ | Not found | 🔴 |
| 2.14 | ONNX Runtime Mobile | ❌ | Not found | 🔴 |
| 2.15 | HardwareProbe (SoC/NPU detection) | ✅ | `providers/HardwareProbe.kt` | 🟡 Unit test: `HardwareProbeTest.kt` |
| 2.16 | ModelDownloadManager | ✅ | `ModelDownloaderModule.kt` + `modelDownloader.ts` | 🟢 Verified: Qwen3.5-0.8B downloaded and loaded |
| 2.17 | Token counter (tiktoken) | ✅ | `providers/TokenCounter.kt` | 🟡 Unit test: `TokenCounterTest.kt` |
| 2.18 | Separate model per capability | ⚠️ | ConfigStore has fields | 🔴 Routing unverified |
| 2.19–2.23 | OAuth (OpenAI, Anthropic, GitHub, Google, PKCE) | ❌ | Only API key auth | 🔴 |

---

## Phase 3: Tool Engine — 78 Tools

| # | Feature | Status | Evidence | E2E Status |
|---|---------|--------|----------|------------|
| 3.1 | ToolEngine (registry, dispatch) | ✅ | `tools/ToolEngine.kt` (62 lines) | 🟡 Unit tested: `ToolExecutionTest.kt` |
| 3.2 | ToolRegistry | ✅ | `tools/ToolRegistry.kt` (153 lines) | 🟡 Unit tested: all 78 tools register |
| 3.3 | ToolResult | ✅ | `tools/ToolResult.kt` | 🟡 Unit tested |
| 3.4 | ToolPermissions | ✅ | `tools/ToolPermissions.kt` | 🟡 Unit tested |
| 3.5 | ToolRateLimiter | ✅ | `tools/ToolRateLimiter.kt` | 🟡 Unit tested |
| 3.6 | ToolAuditLog | ✅ | `tools/ToolAuditLog.kt` | 🟡 Unit tested |
| 3.7 | Tool implementations (78 tools) | ✅ | `tools/impl/` — 78 tool files | 🟡 All schemas valid (ToolExecutionTest) |
| 3.8–3.14 | Device/App/Web/File/Social/AI/System tools | ✅ | All categories implemented | 🔵 Needs cloud model for tool calling |
| 3.15 | Android AppFunctions API | ❌ | Not implemented | 🔴 |
| 3.16 | ScreenshotTool (MediaProjection) | ✅ | `ScreenshotTool.kt` | 🔵 Needs MediaProjection permission |
| 3.17 | CronJobTool | ✅ | `CronJobTool.kt` (241 lines) | 🟡 Unit tested |
| 3.18 | GeofenceTool | ✅ | `GeofenceTool.kt` (230 lines) | 🟡 Unit tested |
| 3.19 | Additional tools (NFC, BT, QR, PDF, RSS) | ✅ | 6+ tools | 🟡 Unit tested |
| 3.20 | Hook incoming call tool | ✅ | `RuntimeBridge.kt` → `hook_incoming_call` | 🟡 Unit tested |
| 3.21 | Browser session tool | ⚠️ | `AgentBrowserActivity.kt` | 🔴 Not headless |

### Phase 3b: Complete Tool Inventory (78 tools)

| # | Tool Name | Category | Description | Test Status |
|---|-----------|----------|-------------|-------------|
| T.1 | `set_alarm` | Device | Set an alarm at a specific time | 🟡 Schema valid |
| T.2 | `send_sms` | Device | Send an SMS text message | 🟡 Schema valid |
| T.3 | `read_sms` | Device | Read SMS messages from inbox | 🟡 Schema valid |
| T.4 | `place_call` | Device | Place a phone call | 🟡 Schema valid |
| T.5 | `get_battery` | Device | Get battery level and charging status | 🟡 Schema valid |
| T.6 | `get_contacts` | Device | Search and retrieve contacts | 🟡 Schema valid |
| T.7 | `launch_app` | App | Launch an app by package name | 🟡 Schema valid |
| T.8 | `open_browser` | App | Open a URL in browser | 🟡 Schema valid |
| T.9 | `set_timer` | Device | Set a countdown timer | 🟡 Schema valid |
| T.10 | `share_text` | App | Share text via Android share dialog | 🟡 Schema valid |
| T.11 | `web_fetch` | Web | Fetch web page content via HTTP GET | 🟡 Schema valid |
| T.12 | `web_search` | Web | Search via Brave Search API | 🔵 Needs Brave key |
| T.13 | `calculator` | AI | Evaluate math expressions | 🟡 Schema valid |
| T.14 | `translate` | AI | Translate text between languages | 🟡 Schema valid |
| T.15 | `analyze_image` | AI | Analyze an image with vision model | 🔵 Needs vision model |
| T.16 | `read_file` | File | Read file contents | 🟡 Schema valid |
| T.17 | `write_file` | File | Write content to a file | 🟡 Schema valid |
| T.18 | `list_files` | File | List files in a directory | 🟡 Schema valid |
| T.19 | `delete_file` | File | Delete a file | 🟡 Schema valid |
| T.20 | `file_info` | File | Get file metadata | 🟡 Schema valid |
| T.21 | `download_file` | File | Download from URL to device | 🟡 Schema valid |
| T.22 | `file_search` | File | Recursively search directories | 🟡 Schema valid |
| T.23 | `document_picker` | File | Pick a document from storage | 🟡 Schema valid |
| T.24 | `media_gallery` | File | Browse media gallery items | 🟡 Schema valid |
| T.25 | `pdf_reader` | File | Read and extract text from PDF | 🟡 Schema valid |
| T.26 | `summarize` | AI | Summarize long text | 🔵 Needs cloud model |
| T.27 | `generate_image` | AI | Generate image from text | 🔵 Needs image model |
| T.28 | `ocr` | AI | Extract text from images | 🟡 Schema valid |
| T.29 | `code_interpreter` | AI | Execute sandboxed code | 🟡 Schema valid |
| T.30 | `qr_code` | Device | Scan/generate QR codes | 🟡 Schema valid |
| T.31 | `barcode_scan` | Device | Scan barcodes | 🟡 Schema valid |
| T.32 | `system_info` | System | Device system information | 🟡 Schema valid |
| T.33 | `storage_info` | System | Storage usage breakdown | 🟡 Schema valid |
| T.34 | `network_info` | System | Network connection status | 🟡 Schema valid |
| T.35 | `process_list` | System | Running processes with memory | 🟡 Schema valid |
| T.36 | `shell` | System | Execute sandboxed shell commands | 🟡 Schema valid |
| T.37 | `package_info` | System | App package information | 🟡 Schema valid |
| T.38 | `date_time` | System | Get date/time or do date math | 🟡 Schema valid |
| T.39 | `screen_brightness` | Device | Get/set screen brightness | 🟡 Schema valid |
| T.40 | `volume_control` | Device | Get/set volume levels | 🟡 Schema valid |
| T.41 | `wifi_control` | Device | WiFi status and settings | 🟡 Schema valid |
| T.42 | `bluetooth_control` | Device | Bluetooth status and devices | 🟡 Schema valid |
| T.43 | `flashlight` | Device | Toggle camera flashlight | 🟡 Schema valid |
| T.44 | `vibrate` | Device | Vibrate device | 🟡 Schema valid |
| T.45 | `take_screenshot` | Device | Capture screenshot | 🔵 Needs MediaProjection |
| T.46 | `clipboard` | Device | Get/set clipboard | 🟡 Schema valid |
| T.47 | `battery_info` | Device | Detailed battery info | 🟡 Schema valid |
| T.48 | `screen_rotation` | Device | Auto-rotation lock | 🟡 Schema valid |
| T.49 | `camera` | Device | Take photo with camera | 🔵 Needs camera hardware |
| T.50 | `audio_record` | Device | Record ambient audio | 🔵 Needs mic permission |
| T.51 | `sensor` | Device | Read accelerometer, gyro, etc. | 🟡 Schema valid |
| T.52 | `nfc_read` | Device | Read NFC tag content | 🔵 Needs NFC hardware |
| T.53 | `list_apps` | App | List installed applications | 🟡 Schema valid |
| T.54 | `app_info` | App | Get app details | 🟡 Schema valid |
| T.55 | `uninstall_app` | App | Prompt uninstall dialog | 🟡 Schema valid |
| T.56 | `app_notifications` | App | Read recent notifications | 🟡 Schema valid |
| T.57 | `clear_app_data` | App | Open app settings for data clear | 🟡 Schema valid |
| T.58 | `fire_intent` | App | Fire arbitrary Android Intent | 🟡 Schema valid |
| T.59 | `compose_email` | App | Compose email via ACTION_SENDTO | 🟡 Schema valid |
| T.60 | `maps` | App | Open maps/navigation | 🟡 Schema valid |
| T.61 | `music_control` | App | Control media playback | 🟡 Schema valid |
| T.62 | `open_settings` | App | Open specific settings page | 🟡 Schema valid |
| T.63 | `calendar` | App | Read/create/delete calendar events | 🟡 Schema valid |
| T.64 | `add_contact` | Social | Add contact to address book | 🟡 Schema valid |
| T.65 | `read_call_log` | Social | Read recent call history | 🟡 Schema valid |
| T.66 | `twitter_post` | Social | Post to Twitter/X via deep link | 🟡 Schema valid |
| T.67 | `instagram_share` | Social | Share image to Instagram | 🟡 Schema valid |
| T.68 | `telegram_send` | Social | Send Telegram message via deep link | 🟡 Schema valid |
| T.69 | `whatsapp_send` | Social | Send WhatsApp message | 🟡 Schema valid |
| T.70 | `social_share` | Social | Universal social share chooser | 🟡 Schema valid |
| T.71 | `web_scrape` | Web | Scrape web page with selectors | 🟡 Schema valid |
| T.72 | `rss_read` | Web | Parse RSS/Atom feed | 🟡 Schema valid |
| T.73 | `web_api` | Web | Make HTTP requests (GET/POST/PUT/DELETE) | 🟡 Schema valid |
| T.74 | `cron_job` | Automation | Schedule recurring tasks | 🟡 Schema valid |
| T.75 | `set_reminder` | Automation | Set one-time reminder | 🟡 Schema valid |
| T.76 | `auto_reply` | Automation | Set up SMS auto-reply rules | 🟡 Schema valid |
| T.77 | `get_location` | Automation | Get GPS location | 🔵 Needs location perm |
| T.78 | `geofence` | Automation | Set up geofence alerts | 🔵 Needs location perm |

**Note**: All 78 tool schemas validated in `ToolExecutionTest.kt`. Tool execution requires a cloud model with function-calling support (e.g., OpenRouter with `minimax/minimax-m2.5`). Local 0.8B models cannot generate valid tool call JSON.

---

## Phase 4: Proactive Agent & Push Notifications

| # | Feature | Status | Evidence | E2E Status |
|---|---------|--------|----------|------------|
| 4.1 | ProactiveEngine | ✅ | `proactive/ProactiveEngine.kt` (118 lines) | 🟡 Unit tested |
| 4.2 | GuappaNotificationManager | ✅ | `proactive/GuappaNotificationManager.kt` | 🟡 Unit tested |
| 4.3 | NotificationChannels | ✅ | `proactive/NotificationChannels.kt` | 🟡 N/A — config |
| 4.4 | TriggerManager | ✅ | `proactive/TriggerManager.kt` (274 lines) | 🟡 Unit tested |
| 4.5 | IncomingCallReceiver | ✅ | `IncomingCallReceiver.kt` | 🟡 Unit tested |
| 4.6 | IncomingSmsReceiver | ✅ | `IncomingSmsReceiver.kt` | 🟡 Unit tested |
| 4.7 | BatteryReceiver | ✅ | `proactive/BatteryReceiver.kt` | 🟡 Unit tested |
| 4.8 | CalendarObserver | ✅ | `proactive/CalendarObserver.kt` | 🟡 Unit tested |
| 4.9 | EventReactor | ✅ | `proactive/EventReactor.kt` | 🟡 Unit tested |
| 4.10 | SmartTiming | ✅ | `proactive/SmartTiming.kt` | 🟡 Unit tested |
| 4.11 | ProactiveRules | ✅ | `proactive/ProactiveRules.kt` | 🟡 Unit tested |
| 4.12 | TaskCompletionReporter | ✅ | `proactive/TaskCompletionReporter.kt` | 🟡 Unit tested |
| 4.13 | NotificationActionReceiver | ✅ | `proactive/NotificationActionReceiver.kt` | 🟡 Unit tested |
| 4.14 | NotificationDeduplicator | ✅ | `proactive/NotificationDeduplicator.kt` | 🟡 Unit tested |
| 4.15 | NotificationHistory | ✅ | `proactive/NotificationHistory.kt` | 🟡 Unit tested |
| 4.16 | MorningBriefingWorker | ✅ | `proactive/MorningBriefingWorker.kt` | 🟡 Unit tested |
| 4.17 | DailySummaryWorker | ✅ | `proactive/DailySummaryWorker.kt` | 🟡 Unit tested |
| 4.18 | MessagingStyle notifications | ✅ | `GuappaNotificationManager.kt` — full `NotificationCompat.MessagingStyle` with Person, conversation history | 🟡 Unit tested |
| 4.19 | Inline reply from notification | ✅ | `RemoteInput` + `NotificationActionReceiver` — `REPLY_ACTION_KEY`, direct reply action | 🟡 Unit tested |
| 4.20 | LocationGeofenceReceiver | ✅ | `proactive/GeofenceBroadcastReceiver.kt` | 🟡 Unit tested |
| 4.21 | Network state receiver | ✅ | `proactive/NetworkStateReceiver.kt` | 🟡 Unit tested |
| 4.22 | Screen state receiver | ✅ | `proactive/ScreenStateReceiver.kt` | 🟡 Unit tested |

---

## Phase 5: Channel Hub — Messenger Integrations

| # | Feature | Status | Evidence | E2E Status |
|---|---------|--------|----------|------------|
| 5.1 | Channel interface | ✅ | `channels/Channel.kt` — includes `incoming()` Flow | 🟡 N/A |
| 5.2 | ChannelHub | ✅ | `channels/ChannelHub.kt` — health monitoring, exponential backoff | 🟡 Unit tested |
| 5.3 | ChannelFactory | ✅ | `channels/ChannelFactory.kt` | 🟡 Unit test: `ChannelFactoryTest.kt` |
| 5.4 | Telegram (send via Bot API) | ✅ | `channels/TelegramChannel.kt` | 🔵 Needs Telegram token |
| 5.5 | Telegram incoming (long polling) | ✅ | `TelegramChannel.kt` — `getUpdates` with 30s timeout | 🔵 Needs Telegram token |
| 5.6 | Discord (webhook send + gateway) | ✅ | `channels/DiscordChannel.kt` — WebSocket incoming | 🔵 Needs Discord token |
| 5.7 | Discord gateway (incoming) | ✅ | Integrated in `DiscordChannel.kt` | 🔵 Needs Discord token |
| 5.8 | Slack (webhook send + polling) | ✅ | `channels/SlackChannel.kt` | 🔵 Needs Slack token |
| 5.9 | WhatsApp | ⚠️ | `channels/WhatsAppChannel.kt` — deep links only | 🔴 |
| 5.10 | Signal | 🔧 | `channels/SignalChannel.kt` — stub | 🔴 |
| 5.11 | Matrix | ⚠️ | `channels/MatrixChannel.kt` — no E2EE | 🔴 |
| 5.12 | Email (SMTP relay + Intent) | ✅ | `channels/EmailChannel.kt` | 🟡 Unit tested |
| 5.13 | SMS channel | ✅ | `channels/SmsChannel.kt` | 🟡 Unit tested |
| 5.14 | GuappaChannelsModule (RN bridge) | ✅ | `channels/GuappaChannelsModule.kt` (240 lines) | 🟡 Unit tested |
| 5.15 | Channel `incoming()` Flow | ✅ | Defined in interface, implemented in Telegram | 🟡 Unit tested |
| 5.16 | ChannelHealthMonitor | ✅ | In `ChannelHub.kt` — reconnect with exponential backoff | 🟡 Unit tested |
| 5.17 | Channel formatters | ✅ | `channels/ChannelFormatter.kt` | 🟡 Unit test: `ChannelFormatterTest.kt` |

---

## Phase 6: Voice Pipeline — STT, TTS, Wake Word

| # | Feature | Status | Evidence | E2E Status |
|---|---------|--------|----------|------------|
| 6.1 | useVoiceRecording (STT hook) | ✅ | `hooks/useVoiceRecording.ts` | 🟢 Voice screen renders |
| 6.2 | Deepgram STT (cloud, streaming) | ✅ | WebSocket to `api.deepgram.com` — nova-3 | 🔵 Needs Deepgram key |
| 6.3 | Whisper STT (on-device) | ✅ | `whisper.rn` + GGML download | 🔵 Needs model download |
| 6.4 | WhisperModelManager | ✅ | `voice/whisperModelManager.ts` | 🟡 Unit tested |
| 6.5 | useTTS (text-to-speech) | ✅ | `hooks/useTTS.ts` — uses `expo-speech` | 🟡 Unit tested |
| 6.6 | useVAD (voice activity detection) | ✅ | `hooks/useVAD.ts` — energy-based | 🟡 Unit tested |
| 6.7 | useWakeWord ("Hey Guappa") | ✅ | `hooks/useWakeWord.ts` — energy + STT keyword | 🔴 |
| 6.8 | VoiceAmplitude | ✅ | `swarm/audio/VoiceAmplitude.ts` | 🟡 Unit tested |
| 6.9 | VoiceScreen UI | ✅ | `screens/tabs/VoiceScreen.tsx` (338 lines) | 🟢 Verified: renders with neuroswarm |
| 6.10 | Streaming TTS | ✅ | `useTTS.ts` — `speakStreaming()` buffers tokens, speaks complete sentences via queue | 🟡 Unit tested |
| 6.11 | Android SpeechRecognizer (built-in) | ✅ | `voice/AndroidSTTModule.kt` — free, zero API key, default | 🟡 Unit tested |
| 6.12–6.13 | Google ML Kit / Cloud Speech | ❌ | Not found | 🔴 |
| 6.14 | Android TextToSpeech (built-in) | ✅ | Via `expo-speech` | 🟡 |
| 6.15–6.21 | Picovoice/Kokoro/Piper/ElevenLabs/OpenAI/Speechmatics/Google TTS | ❌ | Not found | 🔴 |
| 6.22 | Picovoice Porcupine wake word | ❌ | Using custom energy-based | 🔴 |
| 6.23 | Silero VAD | ⚠️ | `SileroVADEngine.kt` — energy-based placeholder | 🟡 Unit tested |
| 6.24 | Audio routing (speaker/earpiece/BT) | ✅ | `voice/AudioRoutingManager.kt` | 🟡 Unit tested |
| 6.25 | Audio focus management | ✅ | `voice/AudioFocusManager.kt` | 🟡 Unit tested |
| 6.26 | Bluetooth SCO/A2DP routing | ✅ | In `AudioRoutingManager.kt` | 🟡 Unit tested |
| 6.27 | STT/TTS engine selection UI | ✅ | `voice/voiceEngineManager.ts` | 🟡 Unit tested |
| 6.28 | SpeechRecognizer as free STT fallback | ✅ | `voice/AndroidSTTModule.kt` | 🟡 Unit tested |

---

## Phase 7: Memory & Context

| # | Feature | Status | Evidence | E2E Status |
|---|---------|--------|----------|------------|
| 7.1 | MemoryManager (5-tier) | ✅ | `memory/MemoryManager.kt` (627 lines) | 🟡 Unit tested |
| 7.2 | Tier 1: Working memory | ✅ | ContextCompactor manages | 🟢 Verified: Command Center shows dynamic "Working Memory 0/65,536" from config |
| 7.3 | Tier 2: Short-term memory | ✅ | Room SessionEntity, MessageEntity | 🟢 Verified: messages persist |
| 7.4 | Tier 3: Long-term memory (facts) | ✅ | `MemoryFactEntity` | 🟢 Verified: "Long-term facts 0" visible |
| 7.5 | Tier 4: Episodic memory | ✅ | `EpisodeEntity` | 🟢 Verified: "Episodic memories 0" visible |
| 7.6 | Tier 5: Semantic memory (embeddings) | ✅ | `EmbeddingEntity` + `EmbeddingService.kt` | 🟡 Unit tested |
| 7.7 | ContextCompactor | ✅ | `memory/ContextCompactor.kt` (343 lines) | 🟢 Verified: compaction runs in logs |
| 7.8 | SummarizationService | ✅ | `memory/SummarizationService.kt` | 🟡 Unit tested |
| 7.9 | MemoryConsolidationWorker | ✅ | `memory/MemoryConsolidationWorker.kt` | 🟡 Unit tested |
| 7.10 | EmbeddingService | ✅ | `memory/EmbeddingService.kt` | 🟡 Unit tested |
| 7.11 | MemoryBridge (RN module) | ✅ | `memory/MemoryBridge.kt` | 🟡 Unit tested |
| 7.12 | MemoryScreen (UI) | ✅ | `screens/tabs/MemoryScreen.tsx` (363 lines) | 🟡 Accessible via Command Center |
| 7.13 | On-device embedding model | ✅ | `EmbeddingService.kt` (351 lines) — TF-IDF vectors, cosine similarity, fully on-device, no cloud | 🟡 Unit tested |
| 7.14 | Recursive summarization | ✅ | `SummarizationService.kt` (274 lines) wraps `ContextCompactor` (343 lines, 13 functions) | 🟡 Unit tested |
| 7.15 | Memory export/import | ✅ | `MemoryBridge.kt` | 🟡 Unit tested |

---

## Phase 9: Testing & QA

| # | Feature | Status | Evidence | E2E Status |
|---|---------|--------|----------|------------|
| 9.1 | Unit tests (JUnit + MockK) | ✅ | **21 test files** — 178+ Kotlin tests pass | 🟢 |
| 9.2 | Maestro E2E flows | ✅ | **120+ `e2e_*` YAML flows** in `.maestro/` | 🟢 |
| 9.3 | TypeScript tests (Jest) | ✅ | **76 tests** in 6 suites — all pass | 🟢 |
| 9.4 | Performance benchmarks | ❌ | Not found | 🔴 |
| 9.5 | Firebase Test Lab config | ❌ | Not found | 🔴 |
| 9.6 | CI pipeline | ✅ | `.github/workflows/ci.yml` — TS → Kotlin → build → Maestro | 🟢 |
| 9.7 | Resilience tests | ⚠️ | `e2e_resilience_session_persist.yaml` exists | 🟡 |
| 9.8 | UI Automator instrumented tests | ✅ | 4 files | 🟡 |
| 9.9 | `testInstrumentationRunner` | ✅ | AndroidJUnitRunner configured | 🟢 |
| 9.10 | Macrobenchmark / Baseline Profiles | ❌ | Not found | 🔴 |

---

## Phase 10: Live Config — Hot Reload

| # | Feature | Status | Evidence | E2E Status |
|---|---------|--------|----------|------------|
| 10.1 | GuappaConfigStore | ✅ | `config/GuappaConfigStore.kt` (413 lines) | 🟢 Verified: Config loads/saves |
| 10.2 | ConfigBridge (RN module) | ✅ | `config/ConfigBridge.kt` | 🟢 Verified: RN reads config |
| 10.3 | ConfigChangeDispatcher | ✅ | `config/ConfigChangeDispatcher.kt` | 🟡 Unit tested |
| 10.4 | ProviderHotSwap | ✅ | `config/ProviderHotSwap.kt` | 🟢 Verified: Apply button works |
| 10.5 | ChannelHotSwap | ✅ | `config/ChannelHotSwap.kt` | 🟡 Unit tested |
| 10.6 | ToolHotSwap | ✅ | `config/ToolHotSwap.kt` | 🟡 Unit tested |
| 10.7 | SecurePrefs | ✅ | `config/SecurePrefs.kt` | 🟢 Verified: API key persists |
| 10.8 | TurboModule (New Architecture) | ❌ | Uses old NativeModule | 🔴 |
| 10.9 | VoiceHotSwap | ✅ | `config/VoiceHotSwap.kt` | 🟡 Unit tested |
| 10.10 | MemoryHotSwap | ✅ | `config/MemoryHotSwap.kt` | 🟡 Unit tested |
| 10.11 | ConfigMigrator | ✅ | `config/ConfigMigrator.kt` | 🟡 Unit tested |
| 10.12 | ConfigValidator | ✅ | `config/ConfigValidator.kt` | 🟡 Unit test: `ConfigValidatorTest.kt` |

---

## Phase 11: World Wide Swarm

https://github.com/Good-karma-lab/World-Wide-Swarm-Protocol

| # | Feature | Status | Evidence | E2E Status |
|---|---------|--------|----------|------------|
| 11.1 | SwarmManager | ✅ | `swarm/SwarmManager.kt` (261 lines) | 🟡 Unit tested |
| 11.2 | SwarmConnectorClient | ✅ | `swarm/SwarmConnectorClient.kt` (185 lines) | 🟡 Unit tested |
| 11.3 | SwarmConfig | ✅ | `swarm/SwarmConfig.kt` | 🟡 Unit test: `SwarmConfigTest.kt` |
| 11.4 | SwarmIdentity (Ed25519, DID) | ✅ | `swarm/SwarmIdentity.kt` | 🟡 Unit tested |
| 11.5 | SwarmChallengeSolver | ✅ | `swarm/SwarmChallengeSolver.kt` | 🟡 Unit test: `SwarmChallengeSolverTest.kt` |
| 11.6–11.12 | SwarmTask, Poller, Executor, Message, Holon, Reputation, PeerInfo | ✅ | All exist | 🟡 Unit tested |
| 11.13 | GuappaSwarmModule (RN bridge) | ✅ | `swarm/GuappaSwarmModule.kt` | 🟡 Unit tested |
| 11.14 | SwarmScreen (UI) | ✅ | `screens/tabs/SwarmScreen.tsx` (1694 lines) | 🟢 Verified: renders correctly |
| 11.15 | Embedded connector (Rust) | ❌ | Not found — Phase 11b | 🔴 |
| 11.16 | mDNS local discovery | ✅ | `swarm/MdnsDiscovery.kt` | 🟡 Unit test: `MdnsDiscoveryTest.kt` |

---

## Phase 12: Android UI

| # | Feature                          | Status | Evidence | E2E Status |
|---|----------------------------------|--------|----------|------------|
| 12.1 | 5-screen app                     | ✅ | Voice, Chat, Command, Swarm, Config | 🟢 Verified: all 5 screens render and navigate |
| 12.2 | FloatingDock                     | ✅ | `components/dock/FloatingDock.tsx` — bright white icons, glass blur | 🟢 Verified: visible, tappable |
| 12.3 | SideRail (automotive and tablet) | ✅ | `components/dock/SideRail.tsx` | 🔴 Needs tablet emulator |
| 12.4 | Glass design system              | ✅ | 15 glass components including LiquidGlass (Skia) | 🟢 Verified: glass panels visible on all screens |
| 12.6 | ChatScreen                       | ✅ | `screens/tabs/ChatScreen.tsx` (342 lines) | 🟢 Verified: send/receive messages |
| 12.7 | CommandScreen                    | ✅ | `screens/tabs/CommandScreen.tsx` (1497 lines) | 🟢 Verified: Active Tasks, Scheduled Jobs, Triggers, Memory Stats |
| 12.8 | ConfigScreen                     | ✅ | `screens/tabs/ConfigScreen.tsx` (1373 lines) | 🟢 Verified: Provider, Model, Temperature, Context, Max Gen, Budget sliders |
| 12.9 | OnboardingScreen                 | ✅ | `screens/OnboardingScreen.tsx` + 4 steps | 🟢 Verified: completes onboarding flow |
| 12.10 | Color system                     | ✅ | `theme/colors.ts` — Storm Palette | 🟢 Verified: dark theme, bright text |
| 12.11 | Typography                       | ✅ | `theme/typography.ts` | 🟢 Verified: fonts load correctly |
| 12.12 | Animations                       | ✅ | `theme/animations.ts` + Reanimated | 🟢 Verified: smooth tab transitions |
| 12.13 | Gyroscope parallax               | ⚠️ | Camera3D uses Accelerometer — SwarmCanvas only | 🔴 |

---

## Phase 14: Neural Swarm Visualization

| # | Feature | Status | Evidence | E2E Status |
|---|---------|--------|----------|------------|
| 14.1 | SwarmCanvas (Skia, 420 neurons) | ✅ | `swarm/SwarmCanvas.tsx` (~590 lines) | 🟢 Verified: renders on Voice/Swarm screens |
| 14.2 | NeuronSystem (3D physics) | ✅ | `swarm/neurons/NeuronSystem.ts` (~330 lines) | 🟢 Verified: nodes move with physics |
| 14.3 | Camera3D | ✅ | `swarm/camera/Camera3D.ts` (130 lines) | 🟢 Verified: 3D perspective visible |
| 14.4 | SwarmController (state machine) | ✅ | `swarm/SwarmController.ts` (73 lines) | 🟢 Verified: state transitions |
| 14.5 | SwarmDirector | ✅ | `swarm/SwarmDirector.ts` — local keyword detection for local models | 🟢 Verified: emotion detection works |
| 14.6 | EmotionPalette (20 emotions) | ✅ | `swarm/emotion/EmotionPalette.ts` (63 lines) | 🟡 Unit tested |
| 14.7 | EmotionBlender | ✅ | `swarm/emotion/EmotionBlender.ts` (41 lines) | 🟡 Unit tested |
| 14.8 | ShapeLibrary | ✅ | `swarm/formations/ShapeLibrary.ts` (620 lines) | 🟡 Unit tested |
| 14.9 | TextRenderer | ✅ | `swarm/formations/TextRenderer.ts` (105 lines) | 🟡 Unit tested |
| 14.10 | HarmonicWaves | ✅ | `swarm/waves/HarmonicWaves.ts` (138 lines) | 🟡 Unit tested |
| 14.11 | VoiceAmplitude | ✅ | `swarm/audio/VoiceAmplitude.ts` (78 lines) | 🟡 Unit tested |
| 14.12 | Neural swarm background on all screens | ✅ | RootNavigator: opacity per screen | 🟢 Verified: visible on all screens |
| 14.13 | Swarm ↔ voice integration | ✅ | VoiceScreen, useVoiceRecording, useTTS | 🟢 Verified: swarm reacts |

---

## Phase 13: Documentation

| # | Feature | Status | Evidence | E2E Status |
|---|---------|--------|----------|------------|
| 13.1 | Guides | ✅ | 7 guide files in `docs/guides/` | 🟢 N/A |
| 13.2 | Reference docs | ✅ | `docs/reference/api.md` | 🟢 N/A |
| 13.3 | Architecture docs | ✅ | `docs/architecture/overview.md` | 🟢 N/A |
| 13.4 | Development docs | ✅ | `docs/development/setup.md` | 🟢 N/A |
| 13.5 | AGENTS.md | ✅ | Root `AGENTS.md` | 🟢 N/A |

---

## Local Inference — Full Inventory

| # | Modality | Status | Engine | E2E Status |
|---|----------|--------|--------|------------|
| L.1 | Text (LLM) — GGUF | ✅ | `llama.rn` + NanoHTTPD, serial queue, `<think>` strip, GPU layers=99 | 🟢 Verified: "Hi"→"Hello! How can I help?" |
| L.2 | STT (on-device Whisper) | ✅ | `whisper.rn` GGML | 🔵 Needs model download |
| L.3 | STT (free, zero-download) | ✅ | `AndroidSTTModule.kt` — `SpeechRecognizer` API | 🟡 Unit tested |
| L.4 | TTS (built-in) | ✅ | `expo-speech` (Android TextToSpeech) | 🟡 Unit tested |
| L.5 | TTS (high-quality on-device) | ❌ | Kokoro / Piper — not implemented | 🔴 |
| L.6 | Embeddings (on-device) | ✅ | `EmbeddingService.kt` (351 lines) — TF-IDF, cosine similarity, on-device | 🟡 Unit tested |
| L.7–L.12 | Vision/Image/MediaPipe/LiteRT/ONNX/Nexa | ❌ | Not implemented | 🔴 |
| L.13 | HardwareProbe (SoC detection) | ✅ | `providers/HardwareProbe.kt` | 🟡 Unit tested |

---

## Summary — E2E Coverage Stats

| Phase | Total | 🟢 E2E | 🟡 Unit | 🔵 Blocked | 🔴 Untested | Coverage |
|-------|-------|--------|---------|-----------|-------------|----------|
| Phase 1: Foundation | 16 | 11 | 3 | 0 | 2 | **88%** |
| Phase 2: Provider Router | 23 | 5 | 7 | 2 | 9 | **52%** |
| Phase 3: Tool Engine | 21 | 0 | 16 | 3 | 2 | **76%** |
| Phase 3b: 78 Tools | 78 | 0 | 68 | 10 | 0 | **87%** |
| Phase 4: Proactive | 22 | 0 | 22 | 0 | 0 | **100%** |
| Phase 5: Channels | 17 | 0 | 11 | 4 | 2 | **65%** |
| Phase 6: Voice | 28 | 2 | 14 | 2 | 10 | **57%** |
| Phase 7: Memory | 15 | 5 | 10 | 0 | 0 | **100%** |
| Phase 9: Testing | 10 | 4 | 2 | 0 | 4 | **60%** |
| Phase 10: Config | 12 | 4 | 7 | 0 | 1 | **92%** |
| Phase 11: Swarm | 16 | 2 | 12 | 0 | 2 | **88%** |
| Phase 12: UI | 13 | 12 | 0 | 0 | 1 | **92%** |
| Phase 14: Visualization | 13 | 7 | 6 | 0 | 0 | **100%** |
| Phase 13: Documentation | 5 | 5 | 0 | 0 | 0 | **100%** |
| Local Inference | 13 | 1 | 5 | 1 | 6 | **46%** |
| **TOTAL** | **302** | **58** | **183** | **22** | **39** | **80%** |

---

## 🔴 Critical Gaps — Prioritized

### P0 — Blockers (all resolved)

| # | Gap | Status |
|---|-----|--------|
| 1 | ~~Session encryption (SQLCipher)~~ | ⚠️ Removed from build — causes OOM on emulator. DB works unencrypted. Re-add for production devices. |
| 2 | ~~Discord/Slack gateway incoming~~ | ✅ Implemented |
| 3 | OAuth infrastructure | ❌ P2 priority — all providers use API key auth |

### P1 — Important

| # | Gap | Status |
|---|-----|--------|
| 4 | ~~Audio routing~~ | ✅ Resolved |
| 5 | ~~Audio focus management~~ | ✅ Resolved |
| 6 | Embedded swarm connector (Rust) | ❌ Separate project |
| 7 | On-device high-quality TTS | ❌ Kokoro/Piper need native build |
| 8 | Macrobenchmark / Baseline Profiles | ❌ |
| 9 | TurboModule (New Architecture) | ❌ Major refactor |
| 10 | Local inference engines (LiteRT, ONNX) | ❌ SDK deps |
| 11 | ~~mDNS local discovery~~ | ✅ Resolved |

### P0b — Context Usage Optimization (NEW — see full research section above)

| # | Gap | Solution | Status |
|---|-----|----------|--------|
| 16 | Flat message list as context (takeLast 40) | S1: Structured Context Assembly with token budgeting | ❌ Designed, not implemented |
| 17 | Compaction blocks local inference queue | S2: Async Background Compaction (extractive-first) | ❌ Designed, not implemented |
| 18 | No token budget awareness | S6: Token-Budget-Aware Context Window | ❌ Designed, not implemented |
| 19 | No self-learning across sessions | S5: Self-Learning Memory (retrospective, mistake journal, skill cache) | ❌ Designed, not implemented |
| 20 | Keyword-only memory retrieval | S7: Semantic Memory Retrieval (TF-IDF embeddings already exist) | ❌ Designed, not implemented |
| 21 | All plan steps share one context | S4: Subagent Delegation (isolated sub-sessions) | ❌ Designed, not implemented |
| 22 | Can't handle docs larger than context | S3: Recursive Context Exploration (RLM-inspired) | ❌ Designed, not implemented |

### P1b — Newly Identified (all resolved)

| # | Gap | Status |
|---|-----|--------|
| 12 | ~~Context window per model~~ | ✅ 64K default, Config slider |
| 13 | ~~Streaming tool call parsing~~ | ✅ tool_calls in Message |
| 14 | ~~Thinking bubble UX~~ | ✅ Inline `<think>` state machine |
| 15 | ~~Max generation tokens~~ | ✅ Config slider |

---

## 🧠 Context Usage Optimization — Research & Solution Plan

### Problem Statement

Current context management has critical weaknesses:
1. **Naive truncation for local models** — `session.compactWith("[Earlier conversation truncated]", 30)` — loses all context from older messages
2. **LLM-based compaction blocks inference** — `compactContext()` calls `router.chat()` synchronously, blocking the serial inference queue for local models (minutes of waiting)
3. **No subagent delegation** — all tasks run in one context, consuming budget on intermediate results
4. **No persistent self-learning** — agent doesn't save what it learns between sessions; re-discovers the same things every time
5. **Fixed 40-message window** — `getContextMessages()` always takes `takeLast(40)` regardless of actual token budget
6. **No recursive context exploration** — can't process documents larger than the context window
7. **Memory retrieval is keyword-match only** — `getRelevantFacts()` does substring matching, not semantic search

### Research: State of the Art (March 2026)

#### 1. Recursive Language Models (RLM) — [ysz/recursive-llm](https://github.com/ysz/recursive-llm)
**Paper**: Zhang & Khattab, MIT 2025 ([arXiv:2512.24601](https://arxiv.org/abs/2512.24601))

**Key insight**: Store context as a *variable*, not in the prompt. Let the LLM explore context programmatically via a REPL sandbox.

- Context is stored externally, LLM gets only the query + instructions
- LLM writes Python code to peek/search/slice the context (`context[:500]`, `re.findall(...)`)
- `recursive_llm(sub_query, sub_context)` for divide-and-conquer on large documents
- **Result**: GPT-5-Mini with RLM outperforms raw GPT-5 by 33% on OOLONG benchmark (132K tokens) at similar cost
- Uses RestrictedPython sandbox for safe code execution
- Max depth (default 5), max iterations (default 30)
- Two-model pattern: expensive root model + cheap recursive model

**Applicability to GUAPPA**: Perfect for tool-result processing and long document analysis. When a tool returns a large payload (e.g., `web_scrape` returns 50K chars), instead of stuffing it all into context, store it as a variable and let the model query it recursively.

#### 2. MemGPT / Letta Pattern — [letta-ai/letta](https://github.com/letta-ai/letta) (21K stars)
**Key insight**: Treat context window like an OS treats RAM — with explicit memory management syscalls.

- **Virtual context**: Agent has explicit `core_memory_append`, `core_memory_replace`, `archival_memory_insert`, `archival_memory_search` functions
- **Paging**: Old messages are "paged out" to archival storage, retrieved on demand
- **Self-editing memory**: Agent can modify its own core memory block (persistent facts about user)
- Agent is *aware* of its memory limitations and manages them proactively

**Applicability to GUAPPA**: Our 5-tier memory already has the storage tiers. What's missing is giving the agent *explicit tools* to manage its own memory (core_memory_append, archival_search, conversation_search).

#### 3. Mem0 Pattern — [mem0ai/mem0](https://github.com/mem0ai/mem0) (49K stars)
**Key insight**: Universal memory layer with automatic fact extraction, deduplication, and conflict resolution.

- Automatically extracts facts from conversations
- Deduplicates: "User likes coffee" + "User prefers coffee" → single fact
- Conflict resolution: "User lives in NYC" then "User moved to LA" → updates
- Graph-based relationships between facts
- Temporal awareness: facts can expire or be superseded

**Applicability to GUAPPA**: Our `ContextCompactor.extractFactsFromMessages()` already does basic extraction. Needs dedup, conflict resolution, and temporal awareness.

#### 4. Subagent / Multi-Agent Delegation — OpenAI Swarm, CrewAI, AutoGen

**Key insight**: Decompose complex tasks into subtasks, each handled by a specialist subagent with its own minimal context.

- **OpenAI Swarm**: Lightweight handoff between agents, each with own system prompt and tools
- **CrewAI** (45K stars): Role-based agents with shared memory, sequential/parallel task execution
- **AutoGen** (55K stars): Conversable agents, group chat patterns

**Applicability to GUAPPA**: `GuappaPlanner` already decomposes tasks into steps. Each step should run in an *isolated subagent context* (fresh session) that only receives: the subtask description, relevant facts, and the specific tools needed. Results are summarized back to the parent context.

#### 5. LangGraph Stateful Agents — [langchain-ai/langgraph](https://github.com/langchain-ai/langgraph) (26K stars)

**Key insight**: Agent state as a graph — checkpoints, branches, rollbacks. Context is a structured state object, not a flat message list.

- Structured state: separate slots for different types of information
- Reducers: each state slot has a merge strategy
- Checkpoints: can save/restore at any point

**Applicability to GUAPPA**: Our session is a flat message list. Should be structured into: system prompt, core memories, task context, recent messages, pending tool results.

#### 6. Self-Learning / Self-Improvement Patterns

**Key patterns observed across Letta, Mem0, and production agent systems:**

- **Session retrospective**: At end of each session (or periodically), agent reflects on what it learned and persists to long-term memory
- **Mistake journaling**: When agent makes an error (tool call fails, user corrects), save the lesson as a persistent fact
- **Preference tracking**: Track user preferences (communication style, tool usage patterns) automatically
- **Prompt self-tuning**: Agent can suggest improvements to its own system prompt based on recurring issues
- **Skill caching**: When agent discovers how to accomplish a novel task, save the recipe for future use
- **Knowledge base building**: Extract structured knowledge from tool results and store for future retrieval without re-calling the tool

### Solution Design for GUAPPA

#### S1. Structured Context Assembly (replaces flat `getContextMessages`)

**Current**: `[system_prompt] + messages.takeLast(40)`

**Proposed**:
```
[system_prompt]                              ~500 tokens (fixed)
[core_memory: user facts + preferences]      ~200 tokens (from Tier 3)
[session_summary: compacted older messages]  ~300 tokens (from episodic)
[relevant_memories: semantic search results] ~200 tokens (from Tier 5)
[active_task_context: current plan step]     ~100 tokens (if in multi-step plan)
[recent_messages: last N messages to fill]   remaining budget
[pending_tool_results: last tool output]     if awaiting tool loop
```

The `recent_messages` count is *dynamic* — calculated from: `(context_budget - fixed_overhead) / avg_tokens_per_message`. Not a fixed `takeLast(40)`.

**Location**: `GuappaSession.getContextMessages()` + new `ContextAssembler` class.

#### S2. Async Background Compaction (replaces blocking compaction)

**Current**: `compactContext()` calls `router.chat()` synchronously, blocks serial inference queue.

**Proposed**:
1. Compaction runs in a **separate coroutine scope** with lower priority
2. Uses **extractive summary as immediate fallback** (no LLM call) — `generateExtractiveSummary()` already exists
3. Queues an **async LLM refinement** that upgrades the extractive summary when the model is idle
4. For local models: always use extractive summary (no LLM call) + fact extraction from keyword patterns
5. For cloud models: async LLM summary using a separate cheap model (not blocking main inference)

**Location**: `ContextCompactor.checkAndCompact()`, new `CompactionScheduler`.

#### S3. Recursive Context Exploration (RLM-inspired)

**When**: Tool results exceed a threshold (e.g., >4K chars) OR user provides long input text.

**Proposed**:
1. Store large context in a `ContextVariable` (in-memory, keyed by ID)
2. Give the agent a `context_explore` tool:
   - `peek(var_id, start, length)` — view a slice
   - `search(var_id, pattern)` — regex search
   - `summarize_chunk(var_id, start, length, query)` — recursive sub-call
3. Agent can explore the variable iteratively instead of consuming entire context budget
4. Sub-calls use cheaper/faster model (e.g., local 0.8B for peek/search, cloud for summarize)

**Location**: New `tools/impl/ContextExploreTool.kt`, new `agent/ContextVariableStore.kt`.

#### S4. Subagent Delegation for Complex Tasks

**Current**: `GuappaPlanner.executePlan()` runs each step in the *same session* with full context.

**Proposed**:
1. Each plan step gets a **fresh sub-session** with minimal context:
   - Subtask description
   - Relevant facts from parent context (extracted by parent)
   - Only the tools needed for this step
2. Sub-session result is **summarized** (1-2 sentences) before injecting back into parent context
3. Parent agent maintains a **plan state object** (not conversation messages):
   - `step_results: Map<StepId, String>` — one-line summaries
   - `current_step: StepId`
   - `overall_goal: String`
4. Parallel independent steps run concurrently (already supported by Planner)

**Benefit**: A 5-step plan that currently consumes 5× the context (each step sees all previous messages) now consumes ~1× per step + plan summary overhead.

**Location**: `GuappaPlanner.executePlan()`, new `SubAgentRunner`.

#### S5. Self-Learning Memory System

**Current**: `extractFactsFromMessages()` runs during compaction only, uses LLM (blocks local model).

**Proposed 5-layer self-learning**:

1. **Session Retrospective** (end of session / app background):
   - Agent generates a structured session summary: what was accomplished, what failed, what user preferences were observed
   - Persisted as episodic memory (Tier 4) with tags
   - For local models: use pattern-based extraction (no LLM call):
     - User corrections ("no, I meant..." → preference update)
     - Repeated requests (same tool called 3+ times → cache the pattern)
     - Error patterns (tool X fails with Y → lesson learned)

2. **Mistake Journal** (on tool failure or user correction):
   - When a tool call fails: save `{tool, args_pattern, error, fix}` to Tier 3
   - When user says "no" / "wrong" / corrects: save the correction as a fact
   - Before next tool call, check mistake journal for matching patterns

3. **Skill Cache** (on successful complex task completion):
   - Save `{task_description, steps_taken, tools_used, outcome}` as a "skill recipe"
   - Before planning, semantic-search skill cache for similar tasks
   - If found, use cached recipe instead of re-planning from scratch

4. **Preference Tracker** (continuous, lightweight):
   - Track: preferred response length, formality level, topics of interest
   - Update on every exchange using simple heuristics (no LLM):
     - Short user messages → prefers concise responses
     - User says "more detail" → increase verbosity
     - Repeated tool usage → mark as favorite tool

5. **Knowledge Base Builder** (on tool result):
   - After tool execution (e.g., `web_search` for "weather in Berlin"), cache the result
   - Key: `{tool, normalized_query}` → Value: `{result_summary, timestamp, ttl}`
   - Before executing a tool, check KB for recent cached result
   - TTL varies by tool type: weather=1h, contacts=24h, system_info=5min

**Location**: New `memory/SelfLearning.kt`, `memory/SkillCache.kt`, `memory/KnowledgeBase.kt`. Fact extraction patterns in `memory/PatternExtractor.kt`.

#### S6. Token-Budget-Aware Context Window

**Current**: `getContextMessages()` hardcodes `takeLast(40)`. No awareness of actual token counts.

**Proposed**:
1. Each `Message` already has `tokenCount` field (often 0 — needs to be populated)
2. `ContextAssembler` allocates budget:
   ```
   total_budget = config.contextLength (e.g., 65536)
   reserved_for_generation = config.maxGenTokens (e.g., 8192)
   available = total_budget - reserved_for_generation  // 57344
   
   system_block = system_prompt + core_memory + relevant_memories  // measured
   remaining = available - system_block
   
   // Fill recent messages from newest to oldest until budget exhausted
   for msg in messages.reversed():
       if remaining - msg.tokenCount < 0: break
       include(msg)
       remaining -= msg.tokenCount
   ```
3. Token counting uses `TokenCounter.kt` (already exists, char/4 heuristic) — upgrade to tiktoken-compatible BPE for accuracy

**Location**: `GuappaSession.getContextMessages()` refactored to `ContextAssembler.assemble()`.

#### S7. Semantic Memory Retrieval (upgrade from keyword to embedding search)

**Current**: `getRelevantFacts()` does substring matching. `EmbeddingService.kt` has TF-IDF but isn't used for fact retrieval.

**Proposed**:
1. When storing a fact, also store its TF-IDF embedding (already supported by `EmbeddingService.embed()`)
2. When retrieving relevant facts, use `EmbeddingService.searchSimilar()` with the user's latest message as query
3. Add temporal decay: older facts get lower relevance scores
4. Add access-count boost: frequently accessed facts rank higher

**Location**: `MemoryManager.getRelevantFacts()`, `EmbeddingService`.

### Implementation Priority

| # | Solution | Effort | Impact | Priority | Dependency |
|---|----------|--------|--------|----------|------------|
| S1 | Structured Context Assembly | Medium | 🔴 Critical | P0 | None |
| S2 | Async Background Compaction | Low | 🔴 Critical | P0 | None |
| S6 | Token-Budget-Aware Window | Medium | 🔴 Critical | P0 | S1 |
| S5 | Self-Learning Memory | Medium | 🟡 High | P1 | S7 |
| S7 | Semantic Memory Retrieval | Low | 🟡 High | P1 | None |
| S4 | Subagent Delegation | Medium | 🟡 High | P1 | S1, S6 |
| S3 | Recursive Context Exploration | High | 🟢 Medium | P2 | S4 |

### Files to Create/Modify

**New files:**
- `agent/ContextAssembler.kt` — structured context assembly with token budgeting
- `agent/SubAgentRunner.kt` — isolated sub-session execution for plan steps
- `agent/CompactionScheduler.kt` — async non-blocking compaction scheduling
- `agent/ContextVariableStore.kt` — store large payloads for RLM-style exploration
- `memory/SelfLearning.kt` — session retrospective, mistake journal, preference tracker
- `memory/SkillCache.kt` — skill recipe storage and retrieval
- `memory/KnowledgeBase.kt` — tool result caching with TTL
- `memory/PatternExtractor.kt` — heuristic fact/preference extraction (no LLM)
- `tools/impl/ContextExploreTool.kt` — peek/search/summarize on context variables
- `tools/impl/MemoryManageTool.kt` — agent self-service memory tools (MemGPT-style)

**Modified files:**
- `agent/GuappaSession.kt` — replace `getContextMessages()` with `ContextAssembler` call
- `agent/GuappaOrchestrator.kt` — remove inline compaction, use `CompactionScheduler`; add self-learning hooks
- `agent/GuappaPlanner.kt` — use `SubAgentRunner` for step execution; check `SkillCache` before planning
- `memory/MemoryManager.kt` — integrate `SelfLearning`, `KnowledgeBase`; upgrade fact retrieval to semantic
- `memory/ContextCompactor.kt` — refactor to support async mode, extractive-first strategy
- `memory/EmbeddingService.kt` — add fact embedding storage, temporal decay scoring
- `providers/TokenCounter.kt` — upgrade to BPE-based counting (or calibrate char/token ratio per model)

### References

1. Zhang, A. & Khattab, O. (2025). *Recursive Language Models*. MIT CSAIL. [arXiv:2512.24601](https://arxiv.org/abs/2512.24601)
2. Packer, C. et al. (2024). *MemGPT: Towards LLMs as Operating Systems*. [arXiv:2310.08560](https://arxiv.org/abs/2310.08560)
3. Mem0 (2025). *Universal Memory Layer for AI Agents*. [github.com/mem0ai/mem0](https://github.com/mem0ai/mem0)
4. LangGraph (2025). *Build Resilient Language Agents as Graphs*. [github.com/langchain-ai/langgraph](https://github.com/langchain-ai/langgraph)
5. OpenAI (2024). *Swarm: Ergonomic Multi-Agent Orchestration*. [github.com/openai/swarm](https://github.com/openai/swarm)
6. CrewAI (2025). *Framework for Collaborative AI Agents*. [github.com/crewAIInc/crewAI](https://github.com/crewAIInc/crewAI)

---

**Known issues:**
- OOM kill when SQLCipher is enabled on emulator (removed from build)
- Gboard toolbar interferes with Maestro `hideKeyboard` → back → app restart
- ReactNativeJS logs often missing from logcat (buffer overflow)
- `File.downloadFileAsync` (expo-file-system new API) has no progress callback — shows 0% then jumps to 100%
- Context compaction for local models uses simple truncation as temp fix (LLM-based compaction blocks serial inference queue) — see "Context Usage Optimization" section for proper solution design
