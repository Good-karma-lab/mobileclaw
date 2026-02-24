# ZeroClaw vs MobileClaw Feature Comparison Report

**Generated:** 2026-02-23
**Updated:** 2026-02-24
**Purpose:** Comprehensive analysis of ZeroClaw features vs MobileClaw implementation status for Android port.

---

## Executive Summary

MobileClaw is a **partial port** of ZeroClaw to Android with a mobile UI. The core Rust agent runtime has been successfully ported via JNI, and Android-specific device tools have been added. Recent updates have added Memory Management UI and improved Scheduler visibility.

| Category | ZeroClaw | MobileClaw | Coverage |
|----------|----------|------------|----------|
| **Core Agent Runtime** | Full | Full (via JNI) | ✅ 100% |
| **Providers** | 28+ providers | 20 providers in UI | ✅ 71% |
| **Channels** | 13+ channels | 5 configured, limited functionality | ⚠️ 38% |
| **Tools** | 20+ core tools | 21 tools via gateway + 40 Android tools | ✅ 75% |
| **Memory System** | 5 backends + hybrid search | SQLite + Memory UI + API | ✅ 80% |
| **Scheduler** | Full cron + delivery | Read-only UI + full backend | ⚠️ 70% |
| **Gateway** | Full HTTP/WebSocket | Full endpoints + SSE streaming | ✅ 95% |
| **Security** | 5-layer defense | Simplified security controls | ⚠️ 40% |
| **Hardware Tools** | GPIO, STM32, Arduino | Hardware board tools (limited) | ⚠️ 30% |
| **Mobile UI** | CLI/Chat only | Full React Native mobile app | ✅ New |

**Overall Coverage: ~70-75%** (up from ~55-60%)

---

## 1. Provider System

### ZeroClaw Providers (28+)

ZeroClaw supports 28+ LLM providers through a unified interface:

| Provider | Status | MobileClaw UI |
|----------|--------|---------------|
| `openrouter` | ✅ Working | ✅ Yes |
| `openai` | ✅ Working | ✅ Yes |
| `anthropic` | ✅ Working | ✅ Yes |
| `gemini` | ✅ Working | ✅ Yes |
| `ollama` | ✅ Working | ✅ Yes |
| `copilot` | ✅ Working | ✅ Yes |
| `mistral` | ✅ Rust ready | ✅ Yes |
| `deepseek` | ✅ Rust ready | ✅ Yes |
| `xai`/`grok` | ✅ Rust ready | ✅ Yes |
| `groq` | ✅ Rust ready | ✅ Yes |
| `venice` | ✅ Rust ready | ✅ Yes |
| `together` | ✅ Rust ready | ✅ Yes |
| `fireworks` | ✅ Rust ready | ✅ Yes |
| `perplexity` | ✅ Rust ready | ✅ Yes |
| `cohere` | ✅ Rust ready | ✅ Yes |
| `moonshot`/`kimi` | ✅ Rust ready | ✅ Yes |
| `glm`/`zhipu` | ✅ Rust ready | ✅ Yes |
| `minimax` | ✅ Rust ready | ✅ Yes |
| `qwen`/`dashscope` | ✅ Rust ready | ✅ Yes |
| `lm-studio` | ✅ Rust ready | ✅ Yes (local, host via 10.0.2.2) |
| `zai`/`z.ai` | ✅ Rust ready | ❌ No UI |
| `nvidia`/`nvidia-nim` | ✅ Rust ready | ❌ No UI |
| `astrai` | ✅ Rust ready | ❌ No UI |
| Custom endpoints | ✅ Rust ready | ❌ No UI |

**Gap:** 20 of 28+ providers now have UI configuration (up from 6). The underlying Rust supports all providers via the OpenAI-compatible API path.

### Provider Features Missing

| Feature | ZeroClaw | MobileClaw |
|---------|----------|------------|
| **API Key Rotation** | ✅ Round-robin rotation | ❌ Not exposed |
| **Fallback Chains** | ✅ Model fallback config | ❌ Not exposed |
| **Provider Aliases** | ✅ China region aliases | ❌ Not exposed |
| **Model Routes** | ✅ Hint-based routing | ❌ Not exposed |
| **OAuth Flows** | ✅ Codex, Anthropic, Gemini | ⚠️ Partial (token input only) |
| **Streaming** | ✅ SSE parsing | ✅ SSE via `/agent/stream` + React Native async generator |

---

## 2. Channel System

### ZeroClaw Channels (13+)

| Channel | ZeroClaw | MobileClaw Config | MobileClaw Inbound |
|---------|----------|-------------------|---------------------|
| **CLI** | ✅ | N/A | N/A |
| **Telegram** | ✅ Full | ✅ Bot token + Chat ID | ✅ Working |
| **Discord** | ✅ Full | ✅ Bot token only | ❌ No webhook |
| **Slack** | ✅ Full | ✅ Bot token only | ❌ No webhook |
| **WhatsApp** | ✅ Full (Cloud API) | ✅ Access token only | ⚠️ Gateway endpoint exists |
| **Matrix** | ✅ E2EE support | ❌ Missing | ❌ Missing |
| **Email** | ✅ IMAP/SMTP | ❌ Missing | ❌ Missing |
| **Lark/Feishu** | ✅ WebSocket | ❌ Missing | ❌ Missing |
| **Mattermost** | ✅ REST API | ❌ Missing | ❌ Missing |
| **DingTalk** | ✅ Stream WebSocket | ❌ Missing | ❌ Missing |
| **IRC** | ✅ IRC protocol | ❌ Missing | ❌ Missing |
| **iMessage** | ✅ AppleScript (macOS) | ❌ N/A | ❌ N/A |
| **Signal** | ✅ D-Bus (signal-cli) | ❌ Missing | ❌ Missing |
| **QQ** | ✅ WebSocket | ❌ Missing | ❌ Missing |

**Gap:** MobileClaw has integration config UI for 5 channels but lacks:
- Webhook verification for WhatsApp (gateway endpoint exists but not wired)
- Proper message ingestion for Discord/Slack
- All China-specific channels (DingTalk, QQ, Lark)
- Email, Matrix, Signal, IRC

### Channel Features Missing

| Feature | ZeroClaw | MobileClaw |
|---------|----------|------------|
| **Streaming Drafts** | ✅ Telegram/Discord edit | ❌ Not implemented |
| **Typing Indicators** | ✅ Periodic typing | ❌ Not implemented |
| **Allowlist Security** | ✅ Per-channel allowlists | ❌ Not exposed |
| **Mention-Only Mode** | ✅ Telegram/Discord | ❌ Not exposed |
| **Runtime Model Switching** | ✅ `/models` command | ❌ Not implemented |
| **Channel Delivery Instructions** | ✅ Media markers | ❌ Not implemented |
| **Supervised Listeners** | ✅ Auto-restart with backoff | ✅ Working |

---

## 3. Tool System

### ZeroClaw Core Tools (via Gateway)

| Tool | ZeroClaw | MobileClaw Rust | MobileClaw UI |
|------|----------|-----------------|---------------|
| `shell` | ✅ | ✅ | ❌ No UI |
| `file_read` | ✅ | ✅ | ❌ No UI |
| `file_write` | ✅ | ✅ | ❌ No UI |
| `memory_store` | ✅ | ✅ | ✅ Memory Screen |
| `memory_recall` | ✅ | ✅ | ✅ Memory Screen |
| `memory_forget` | ✅ | ✅ | ✅ Memory Screen |
| `git_operations` | ✅ | ✅ | ❌ No UI |
| `schedule` | ✅ | ✅ | ⚠️ Tasks Screen (read-only) |
| `pushover` | ✅ | ✅ | ❌ No UI |
| `telegram_notify` | ✅ | ✅ | ❌ No UI |
| `screenshot` | ✅ | ✅ | ❌ No UI |
| `image_info` | ✅ | ✅ | ❌ No UI |
| `http_request` | ✅ | ✅ | ❌ No UI |
| `cron_add` | ✅ | ✅ | ❌ No UI (agent can use) |
| `cron_list` | ✅ | ✅ | ✅ Tasks Screen |
| `cron_remove` | ✅ | ✅ | ❌ No UI (agent can use) |
| `cron_update` | ✅ | ✅ | ❌ No UI (agent can use) |
| `cron_run` | ✅ | ✅ | ❌ No UI (agent can use) |
| `cron_runs` | ✅ | ✅ | ❌ No UI |
| `android_device` | ✅ | ✅ | ✅ Device Screen |
| `browser_open` | ✅ | ✅ | ❌ No UI |
| `web_search` | ✅ Brave Search | ✅ Brave + DuckDuckGo fallback | ✅ Settings UI (Brave API key) |

### ZeroClaw Integration Tools

| Tool | ZeroClaw | MobileClaw Rust | MobileClaw UI |
|------|----------|-----------------|---------------|
| `composio` | ✅ 1000+ apps | ✅ | ⚠️ Config only |
| `delegate` | ✅ Sub-agents | ✅ | ❌ No UI |

### ZeroClaw Hardware Tools

| Tool | ZeroClaw | MobileClaw Rust | MobileClaw UI |
|------|----------|-----------------|---------------|
| `hardware_board_info` | ✅ | ✅ | ⚠️ Device tools toggle |
| `hardware_memory_map` | ✅ | ✅ | ⚠️ Device tools toggle |
| `hardware_memory_read` | ✅ | ✅ | ⚠️ Device tools toggle |
| `gpio_read` | ✅ RPi GPIO | ❌ Missing | ❌ No UI |
| `gpio_write` | ✅ RPi GPIO | ❌ Missing | ❌ No UI |
| `arduino_upload` | ✅ | ❌ Missing | ❌ No UI |
| `nucleo_flash` | ✅ STM32 | ❌ Missing | ❌ No UI |

### MobileClaw Android Device Tools (New)

MobileClaw adds Android-specific tools not in ZeroClaw:

| Tool | MobileClaw | Status |
|------|------------|--------|
| `android_device.open_app` | ✅ New | ✅ Working |
| `android_device.list_apps` | ✅ New | ✅ Working |
| `android_device.open_url` | ✅ New | ✅ Working |
| `android_device.open_settings` | ✅ New | ✅ Working |
| `android_device.notifications.read` | ✅ New | ✅ Working |
| `android_device.notifications.post` | ✅ New | ✅ Working |
| `android_device.location.read` | ✅ New | ✅ Working |
| `android_device.location.geofence` | ✅ New | ✅ Working |
| `android_device.camera.capture` | ✅ New | ✅ Working |
| `android_device.camera.scan_qr` | ✅ New | ✅ Working |
| `android_device.microphone.record` | ✅ New | ✅ Working |
| `android_device.contacts.read` | ✅ New | ✅ Working |
| `android_device.calendar.read_write` | ✅ New | ✅ Working |
| `android_device.calls.start` | ✅ New | ✅ Working |
| `android_device.calls.incoming_hook` | ✅ New | ✅ Working |
| `android_device.sms.send` | ✅ New | ✅ Working |
| `android_device.sms.incoming_hook` | ✅ New | ✅ Working |
| `android_device.ui.tap` | ✅ New | ✅ Working |
| `android_device.ui.swipe` | ✅ New | ✅ Working |
| `android_device.ui.click_text` | ✅ New | ✅ Working |
| `android_device.ui.back` | ✅ New | ✅ Working |
| `android_device.ui.home` | ✅ New | ✅ Working |
| `android_device.ui.recents` | ✅ New | ✅ Working |
| `android_device.browser.*` | ✅ New | ✅ Working |
| `android_device.sensor.*` | ✅ New | ✅ Working |
| `android_device.bluetooth.*` | ✅ New | ✅ Working |
| `android_device.nfc.*` | ✅ New | ✅ Working |
| `android_device.userdata.*` | ✅ New | ✅ Working |
| `android_device.storage.*` | ✅ New | ✅ Working |

---

## 4. Memory System

### ZeroClaw Memory Features

| Feature | ZeroClaw | MobileClaw |
|---------|----------|------------|
| **SQLite Backend** | ✅ | ✅ |
| **PostgreSQL Backend** | ✅ | ❌ Not exposed |
| **Lucid CLI Bridge** | ✅ | ❌ Not exposed |
| **Markdown Backend** | ✅ | ❌ Not exposed |
| **None Backend** | ✅ | ❌ Not exposed |
| **Hybrid Search** | ✅ FTS5 + vector | ✅ Working (backend) |
| **Embedding Provider** | ✅ OpenAI, custom | ❌ Not configured |
| **Memory Categories** | ✅ Core, Fact, etc. | ✅ Visible in UI |
| **Snapshot Export** | ✅ MEMORY_SNAPSHOT.md | ❌ Not implemented |
| **Auto-Hydration** | ✅ DB recovery | ❌ Not implemented |
| **Embedding Cache** | ✅ LRU cache | ❌ Not configured |
| **Vector Weight** | ✅ Configurable | ❌ Not exposed |
| **Keyword Weight** | ✅ Configurable | ❌ Not exposed |
| **Memory Management UI** | ❌ CLI only | ✅ **NEW - MemoryScreen** |
| **Memory API Endpoints** | ❌ Internal only | ✅ **NEW - GET/DELETE /memory** |

**Recent Improvements:**
- ✅ Memory Management UI (MemoryScreen) with search, category filter, delete
- ✅ Memory API endpoints: GET /memory, GET /memory/recall, GET /memory/count, DELETE /memory
- ✅ Memory count display
- ✅ Category-based filtering

**Remaining Gaps:**
- No embedding provider configuration
- No memory snapshot/export
- No hybrid search weight configuration

---

## 5. Scheduler System

### ZeroClaw Scheduler Features

| Feature | ZeroClaw | MobileClaw |
|---------|----------|------------|
| **Cron Expression Support** | ✅ | ✅ |
| **Interval Jobs** | ✅ | ✅ |
| **One-Time Jobs** | ✅ | ✅ |
| **Shell Jobs** | ✅ | ✅ |
| **Agent Jobs** | ✅ | ✅ |
| **Job Persistence** | ✅ SQLite | ✅ SQLite |
| **Delivery to Channels** | ✅ Telegram, Discord, etc. | ✅ |
| **Retry with Backoff** | ✅ Exponential | ✅ |
| **Security Enforcement** | ✅ Full | ✅ |
| **Health Monitoring** | ✅ | ✅ |
| **High-Frequency Warnings** | ✅ | ✅ |
| **Run History** | ✅ `cron_runs` table | ✅ |
| **Job List UI** | CLI only | ✅ **ScheduledTasksScreen** |
| **Job Creation UI** | CLI only | ❌ Missing |
| **Job Editing UI** | CLI only | ❌ Missing |
| **Run History UI** | CLI only | ❌ Missing |

**Recent Improvements:**
- ✅ ScheduledTasksScreen shows active cron jobs
- ✅ Hook status display (Incoming Call/SMS)
- ✅ Gateway health indicator
- ✅ Real-time job list refresh

**Remaining Gaps:**
- No job creation UI (users must ask agent to create jobs)
- No job editing UI
- No run history visualization

---

## 6. Gateway System

### ZeroClaw Gateway Endpoints

| Endpoint | ZeroClaw | MobileClaw |
|----------|----------|------------|
| `GET /health` | ✅ | ✅ |
| `GET /metrics` | ✅ Prometheus | ❌ Not exposed |
| `POST /pair` | ✅ Pairing auth | ✅ Disabled by default |
| `POST /webhook` | ✅ Simple chat | ✅ |
| `POST /agent/message` | ✅ Full agent with tools | ✅ |
| `POST /agent/event` | ✅ Device events | ✅ Android-specific |
| `GET /whatsapp` | ✅ Webhook verify | ✅ Endpoint exists |
| `POST /whatsapp` | ✅ Message webhook | ✅ Endpoint exists |
| `GET /cron/jobs` | ✅ | ✅ |
| `DELETE /cron/jobs` | ✅ | ✅ |
| `GET /memory` | ❌ Internal only | ✅ **NEW** |
| `GET /memory/recall` | ❌ Internal only | ✅ **NEW** |
| `GET /memory/count` | ❌ Internal only | ✅ **NEW** |
| `DELETE /memory` | ❌ Internal only | ✅ **NEW** |
| `GET /rules` | ❌ Internal only | ✅ **NEW** |
| `POST /rules` | ❌ Internal only | ✅ **NEW** |
| `GET /rules/{id}` | ❌ Internal only | ✅ **NEW** |
| `DELETE /rules/{id}` | ❌ Internal only | ✅ **NEW** |
| `POST /rules/{id}/toggle` | ❌ Internal only | ✅ **NEW** |
| `POST /agent/stream` | ❌ Missing | ✅ **NEW** — SSE streaming endpoint |

### ZeroClaw Gateway Security Features

| Feature | ZeroClaw | MobileClaw |
|---------|----------|------------|
| **Network Isolation** | ✅ 127.0.0.1 default | ✅ Android localhost |
| **Pairing Guard** | ✅ 6-digit code | ⚠️ Disabled by default |
| **Bearer Token Auth** | ✅ | ⚠️ Disabled by default |
| **Rate Limiting** | ✅ Sliding window | ✅ |
| **Idempotency Store** | ✅ TTL-based | ✅ |
| **Webhook Secret** | ✅ SHA-256 | ✅ Supported |
| **WhatsApp Signature** | ✅ HMAC-SHA256 | ✅ Supported |
| **Body Size Limit** | ✅ 64KB | ✅ |
| **Request Timeout** | ✅ 300s | ✅ |
| **Tunnel Support** | ✅ Cloudflare, Tailscale, ngrok | ❌ Not applicable |

---

## 7. Security System

### ZeroClaw Security Layers

| Layer | ZeroClaw | MobileClaw |
|-------|----------|------------|
| **Network Binding** | ✅ Refuse public bind | ✅ Android localhost |
| **Authentication** | ✅ Pairing + bearer tokens | ⚠️ Disabled |
| **Authorization** | ✅ AutonomyLevel | ⚠️ Simplified |
| **Isolation** | ✅ Workspace scoping | ⚠️ Simplified |
| **Data Protection** | ✅ ChaCha20-Poly1305 | ❌ Not implemented |

### ZeroClaw Autonomy Levels

| Level | ZeroClaw | MobileClaw |
|-------|----------|------------|
| **ReadOnly** | ✅ Read-only ops | ❌ Not exposed |
| **Supervised** | ✅ Approval required | ⚠️ Partial (security screen) |
| **Full** | ✅ No restrictions | ⚠️ Partial |

### MobileClaw Security Config

| Config | Description |
|--------|-------------|
| `requireApproval` | Require approval for calls/SMS |
| `highRiskActions` | Enable high-risk actions |
| `incomingCallHooks` | Enable incoming call hooks |
| `incomingSmsHooks` | Enable incoming SMS hooks |
| `includeCallerNumber` | Share caller number with agent |
| `directExecution` | Direct execute calls/SMS/camera |
| `preferStandardWebTool` | Use standard web read tool |
| `alwaysOnRuntime` | Always-on runtime mode |

**Gap:** Mobile security is simplified:
- No workspace isolation
- No path restrictions
- No command allowlist
- No action rate limiting
- No secret encryption

---

## 8. Observability

### ZeroClaw Observability Features

| Feature | ZeroClaw | MobileClaw |
|---------|----------|------------|
| **Tracing** | ✅ `tracing` crate | ✅ In Rust |
| **Prometheus Metrics** | ✅ | ❌ Not exposed |
| **Health Checks** | ✅ Component tracking | ✅ Activity Screen |
| **Event Recording** | ✅ AgentStart, ToolExecution, etc. | ✅ Activity log |
| **Cost Tracking** | ✅ Token/cost tracking | ❌ Not implemented |
| **Audit Logging** | ✅ | ⚠️ Activity log only |

---

## 9. Configuration System

### ZeroClaw Configuration

| Config Section | ZeroClaw | MobileClaw |
|----------------|----------|------------|
| **Provider Config** | ✅ Full | ⚠️ Simplified (20 providers) |
| **Agent Config** | ✅ | ✅ Settings Screen |
| **Channels Config** | ✅ 13+ channels | ⚠️ 5 channels |
| **Memory Config** | ✅ 5 backends | ❌ Hardcoded SQLite |
| **Gateway Config** | ✅ Full | ⚠️ Simplified |
| **Autonomy Config** | ✅ Full | ⚠️ Simplified |
| **Runtime Config** | ✅ Native/Docker | ⚠️ Native only |
| **Browser Config** | ✅ | ❌ Not exposed |
| **Composio Config** | ✅ | ⚠️ API key only |
| **Secrets Config** | ✅ Encrypted | ❌ Not implemented |
| **Tunnel Config** | ✅ | ❌ Not applicable |
| **Proxy Config** | ✅ | ❌ Not exposed |
| **Model Routes** | ✅ | ❌ Not exposed |
| **Agents (Delegation)** | ✅ | ❌ Not exposed |

---

## 10. Hardware Peripherals

### ZeroClaw Hardware Support

| Feature | ZeroClaw | MobileClaw |
|---------|----------|------------|
| **Raspberry Pi GPIO** | ✅ `rppal` crate | ❌ Not applicable |
| **STM32/Nucleo** | ✅ `probe-rs` | ❌ Not applicable |
| **Arduino Upload** | ✅ `arduino-cli` | ❌ Not applicable |
| **Serial Communication** | ✅ | ⚠️ Limited |
| **Hardware Memory Read** | ✅ | ✅ |
| **Board Discovery** | ✅ | ⚠️ Limited |

**Note:** Hardware peripherals are primarily for embedded/IoT use cases not applicable to mobile.

---

## 11. Mobile-Specific Features (New in MobileClaw)

MobileClaw adds features not present in ZeroClaw:

| Feature | Description |
|---------|-------------|
| **React Native Mobile UI** | Full mobile app with navigation |
| **Android Device Tools** | 40+ Android-specific tools |
| **Accessibility Service** | UI automation via accessibility |
| **Incoming Call Hooks** | React to phone calls |
| **Incoming SMS Hooks** | React to SMS messages |
| **Geofencing** | Background location monitoring |
| **Camera/QR Tools** | Photo capture and QR scanning |
| **Bluetooth LE** | BLE scanning and connection |
| **NFC Tools** | NFC tag read/write |
| **Voice Recording** | Microphone capture with Deepgram |
| **Always-On Service** | Persistent background service |
| **Boot Receiver** | Start on device boot |
| **In-App Browser** | Browser automation session |
| **Memory Management UI** | View/search/delete memories |
| **Activity Feed** | Real-time event log |
| **Device Tools Toggle** | Enable/disable Android capabilities |

---

## 12. Implementation Status by Priority

### ✅ COMPLETED (High Priority)

| Feature | Status | Notes |
|---------|--------|-------|
| **Memory Management UI** | ✅ DONE | MemoryScreen with search, filter, delete |
| **Memory API Endpoints** | ✅ DONE | GET/DELETE /memory, /memory/recall, /memory/count |
| **Scheduler Job List UI** | ✅ DONE | ScheduledTasksScreen shows active jobs |
| **Core Tools via Gateway** | ✅ DONE | 20 tools available through agent |
| **Telegram Integration** | ✅ DONE | Full send/receive working |
| **Android Device Tools** | ✅ DONE | 40+ tools, Device Screen toggles |
| **Activity Feed** | ✅ DONE | Real-time event logging |
| **Security Screen** | ✅ DONE | Simplified controls |
| **Integrations Screen** | ✅ DONE | 5 channels configured |
| **Incoming Call/SMS Hooks** | ✅ DONE | Real-time hooks working |
| **Trigger-Action Rules Engine** | ✅ DONE | Rules with triggers, conditions, actions |
| **More Provider UI** | ✅ DONE | 20 providers in UI (mistral, deepseek, xai, groq, together, fireworks, perplexity, cohere, minimax, venice, moonshot, glm, qwen, lm-studio added) |
| **Web Search Tool** | ✅ DONE | `web_search` Rust tool with Brave Search + DuckDuckGo fallback; Settings UI for Brave API key; `braveApiKey` threaded through TS → Kotlin → JNI |
| **Streaming Responses** | ✅ DONE | SSE endpoint `POST /agent/stream`; `runZeroClawAgentStream` async generator; ChatScreen streams with non-streaming fallback |

### ❌ NOT STARTED (Remaining Features)

| Feature | Priority | Effort | Notes |
|---------|----------|--------|-------|
| **Job Creation UI** | Medium | 2 days | Agent can create jobs, but no direct UI |
| **Cost/Token Tracking** | Medium | 2 days | Show API usage per conversation |
| **Conversation History UI** | Low | 2 days | Browse past conversations |
| **Tool Execution Visualization** | Medium | 2 days | Show which tools are being called |
| **Delegate Tool UI** | Low | 2-3 days | Sub-agent configuration |
| **Git Operations UI** | Low | 2 days | Repository management |
| **Secret Encryption** | Low | 3 days | Secure credential storage |
| **Audit Logging** | Low | 2 days | Action history screen |
| **Prometheus Metrics** | Low | 1 day | Observability endpoint |
| **Action Rate Limiting** | Low | 1 day | Prevent runaway execution |
| **Webhook Verification UI** | Low | 1 day | WhatsApp/Discord/Slack verification |
| **Memory Backend Selection** | Low | 2 days | Allow PostgreSQL, Markdown backends |
| **Embedding Provider Config** | Low | 2 days | Hybrid search configuration |

---

## 13. Implementation Plan

### Phase 1: High-Impact Features (1-2 weeks)

1. **Streaming Chat Responses** (2-3 days)
   - Implement SSE parsing in React Native
   - Show responses as they stream in
   - Add abort capability
   - Files: `mobile-app/src/api/mobileclaw.ts`, `mobile-app/src/screens/tabs/ChatScreen.tsx`

2. **Web Search Tool** (1-2 days)
   - Add `web_search` tool to Rust tools
   - Integrate Brave Search API
   - Add configuration in Settings
   - Files: `src/tools/web_search.rs`, `mobile-app/src/screens/tabs/SettingsScreen.tsx`

3. **Job Creation UI** (2 days)
   - Add "Create Job" button to ScheduledTasksScreen
   - Form for cron expression, prompt/command
   - Preview next run times
   - Files: `mobile-app/src/screens/tabs/ScheduledTasksScreen.tsx`

### Phase 2: User Experience (2-3 weeks)

4. **More Provider UI** (2-3 days)
   - Add Mistral, DeepSeek, xAI, Groq, etc.
   - Group by region (US, EU, China)
   - Custom endpoint support
   - Files: `mobile-app/src/screens/tabs/SettingsScreen.tsx`, `mobile-app/src/state/mobileclaw.ts`

5. **Cost/Token Tracking** (2 days)
   - Track tokens per conversation
   - Show cost estimates
   - Daily/weekly usage charts
   - Files: `mobile-app/src/state/usage.ts`, new `UsageScreen.tsx`

6. **Tool Execution Visualization** (2 days)
   - Show tool calls in chat bubbles
   - Expand/collapse tool results
   - Status indicators (running, success, error)
   - Files: `mobile-app/src/screens/tabs/ChatScreen.tsx`

### Phase 3: Advanced Features (3-4 weeks)

7. **Channel Inbound Webhooks** (2-3 days)
   - Discord webhook verification
   - Slack events API
   - WhatsApp signature validation
   - Files: `src/channels/*.rs`, gateway routes

8. **Secret Encryption** (3 days)
   - Implement ChaCha20-Poly1305 for credentials
   - Secure storage using Android Keystore
   - Migration from plaintext
   - Files: `src/security/secrets.rs`, JNI bridge

9. **Delegate Tool UI** (2-3 days)
   - Configure sub-agents
   - Task distribution settings
   - Files: `mobile-app/src/screens/tabs/AgentsScreen.tsx`

---

## 14. Test Coverage

### E2E Maestro Test Results (2026-02-24)

| Test | Result | Agent Response | Notes |
|------|--------|----------------|-------|
| `mobile_smoke_minimal.yaml` | ✅ PASS | Chat input visible | App launches without crash |
| `daemon_health_check.yaml` | ✅ PASS | Activity screen shows daemon healthy | Double-tap dock + 15s timeout |
| `e2e_test_memory_store_recall.yaml` | ✅ PASS | "Your secret code word is **QUANTUM_BANANA_42**" | Memory correctly recalled after app restart |
| `e2e_test_battery_status.yaml` | ✅ PASS | "Your battery is at **100%**" | Device sensor query working |
| `e2e_test_http_request.yaml` | ✅ PASS | "returned a **200 OK**" | HTTP request tool working |
| `e2e_test_cron_job_create.yaml` | ✅ PASS | "Created cron job **e2e_test_job**..." | Job appeared in Tasks screen |
| `e2e_test_telegram_notify.yaml` | ✅ PASS | "Done! The Telegram message...has been sent" | Message sent to configured chat |
| `e2e_test_call_hook_setup.yaml` | ✅ PASS | "I've registered a real-time hook..." | Hook registered + rules engine works |
| `e2e_test_memory_screen_verify.yaml` | ✅ PASS | Memory screen shows memories | Fixed navigation + `extendedWaitUntil` for scroll |
| `e2e_test_provider_extended.yaml` | ✅ PASS | Mistral AI, Groq, DeepSeek visible | New providers selectable from picker |
| `e2e_test_web_search_settings.yaml` | ✅ PASS | Web Search section + Brave API key input visible | Settings UI for web search config |
| `e2e_test_web_search_agent.yaml` | ✅ PASS | Agent returns web search results | Agent uses `web_search` tool end-to-end |

### Fixes Applied This Session (2026-02-24)

1. **`src/config/schema.rs` corruption** ✅ FIXED
   - Root cause: commit `24181a0` introduced duplicate `agent:` field, stray `;`, and test code inside `fn default()`
   - Fix: reconstructed file from git history — lines 1-1876 from HEAD + correct closing + HEAD~1 tail

2. **Axum 0.8 route syntax crash (SIGABRT)** ✅ FIXED
   - Root cause: `/rules/:id` Express-style syntax invalid in Axum 0.8; caused SIGABRT at startup
   - Fix: → `/rules/{id}` and `/rules/{id}/toggle`

3. **Android 14+ `ForegroundServiceStartNotAllowedException`** ✅ FIXED
   - Root cause: foreground service dataSync quota exhausted from repeated test runs
   - Fix: `tryStartForeground()` try-catch wrapper in `ZeroClawDaemonService.kt` + emulator reboot

4. **Memory Screen navigation broken (React Navigation v7)** ✅ FIXED
   - Root cause: `navigation.getParent("root-stack")` returned `undefined` in React Navigation v7
   - Fix: `navigation.getParent()` without ID argument

5. **FloatingDock: tap on already-focused tab was no-op** ✅ FIXED
   - Root cause: `tabPress` event not emitted when tab already focused → navigation state stale
   - Fix: always emit `tabPress` event before checking `isFocused`

6. **Maestro scroll-dependent test failures** ✅ FIXED
   - `memory-search`, "Mistral AI", "Web Search" elements below fold when asserted
   - Fix: `extendedWaitUntil` for memory-search; `scrollUntilVisible` for providers and Settings sections

### Test Summary

**Fully Passing: 12/12 tests (100%)**

### Working Features (Verified via E2E):
- ✅ App launch and daemon health
- ✅ Memory store and recall (long-term memory persists across sessions)
- ✅ Cron job creation via natural language
- ✅ Telegram message sending
- ✅ Device sensors (battery)
- ✅ HTTP requests to external APIs
- ✅ Incoming call hook registration with automation rules
- ✅ Memory screen navigation and search
- ✅ Extended provider UI (20 providers, Mistral/Groq/DeepSeek visible and selectable)
- ✅ Web Search settings UI (Brave API key configuration)
- ✅ Web Search agent queries (DuckDuckGo fallback working)

---

## 15. Conclusion

MobileClaw successfully ports the ZeroClaw core runtime to Android and adds excellent Android-specific device tools. Recent updates have significantly improved coverage:

**What's Working Well:**
- ✅ Core agent runtime (Rust via JNI)
- ✅ Android device tools (40+ comprehensive tools)
- ✅ Memory system with UI and API
- ✅ Scheduler with job listing UI
- ✅ Telegram integration (send/receive)
- ✅ Incoming call/SMS hooks
- ✅ Gateway with full endpoints including SSE streaming
- ✅ Activity feed and observability
- ✅ 20 providers in UI (expanded from 6)
- ✅ Web search tool (Brave Search + DuckDuckGo fallback) with Settings UI
- ✅ Streaming chat responses (SSE via `/agent/stream`)

**Major Gaps Remaining:**
- ⚠️ No job creation UI (agent-only)
- ⚠️ Discord/Slack inbound webhooks not wired
- ⚠️ No cost/token tracking
- ⚠️ No secret/credential encryption

**Coverage Estimate:** MobileClaw now covers approximately **70-75%** of ZeroClaw's feature set (up from ~55-60%), with significant improvements to provider coverage, web search, and streaming.

---

## Appendix A: File Reference

### ZeroClaw Source Files

- `src/providers/mod.rs` - Provider factory
- `src/channels/mod.rs` - Channel dispatcher
- `src/tools/mod.rs` - Tool registry
- `src/memory/mod.rs` - Memory backends
- `src/cron/scheduler.rs` - Job scheduler
- `src/gateway/mod.rs` - HTTP gateway
- `src/security/mod.rs` - Security policy
- `src/config/schema.rs` - Configuration schema

### MobileClaw Source Files

- `src/jni_bridge.rs` - JNI bridge for Android
- `src/tools/mod.rs` - Tool registry (includes Android tools)
- `src/tools/android_device.rs` - Android device tools
- `src/gateway/mod.rs` - Android gateway (with memory endpoints)
- `mobile-app/src/runtime/tooling.ts` - Tool execution bridge
- `mobile-app/src/native/androidAgentBridge.ts` - Native module bridge
- `mobile-app/src/state/mobileclaw.ts` - Mobile app state
- `mobile-app/src/screens/tabs/MemoryScreen.tsx` - **NEW** Memory management UI
- `mobile-app/src/screens/tabs/ScheduledTasksScreen.tsx` - Tasks/hooks UI
- `mobile-app/src/screens/tabs/ChatScreen.tsx` - Chat interface
- `mobile-app/src/screens/tabs/ActivityScreen.tsx` - Activity feed
- `mobile-app/src/screens/tabs/DeviceScreen.tsx` - Tool toggles
- `mobile-app/src/screens/tabs/SettingsScreen.tsx` - Provider config
- `mobile-app/src/screens/tabs/IntegrationsScreen.tsx` - Channel config
- `mobile-app/src/screens/tabs/SecurityScreen.tsx` - Security settings
- `mobile-app/src/api/mobileclaw.ts` - API client (with memory functions)

---

*End of Report - Updated 2026-02-24*
