# Phase 1: Foundation — Agent Core

**Date**: 2026-03-06
**Status**: Proposal
**Depends On**: —
**Blocks**: All other phases

---

## 1. Objective

Build the core agent runtime: orchestrator loop, session management, message bus, foreground service, task manager, and persona system. This is the skeleton that all other phases plug into.

---

## 2. Research Checklist

Before writing code, study:

- [ ] Android foreground service restrictions (Android 14+: `FOREGROUND_SERVICE_TYPE_DATA_SYNC`, FGS launch limits from background)
- [ ] Kotlin Coroutines structured concurrency (`SupervisorJob`, `CoroutineScope`, `supervisorScope`)
- [ ] `SharedFlow` vs `StateFlow` vs `Channel` for internal message bus
- [ ] Context window management patterns: sliding window, summarization, hybrid
- [ ] Existing ZeroClaw agent loop (`src/agent/`) — understand current orchestration model
- [ ] LangGraph / CrewAI / AutoGen agent loop patterns for multi-step task execution
- [ ] Android WorkManager vs AlarmManager for scheduled tasks
- [ ] Android `BootReceiver` for auto-start after reboot
- [ ] Android DataStore (Preferences) for reactive config storage
- [ ] Room database for session persistence (WAL mode, migrations)

---

## 3. Architecture

### 3.1 Module Structure

```
android/app/src/main/java/com/guappa/app/
└── agent/
    ├── GuappaOrchestrator.kt        — main agent loop (coroutine-based)
    ├── GuappaSession.kt             — session state, conversation history, context window
    ├── GuappaPlanner.kt             — task decomposition, multi-step planning, ReAct loop
    ├── GuappaConfig.kt              — runtime configuration (hot-reloadable via StateFlow)
    ├── GuappaPersona.kt             — personality, system instruction, greeting, locale
    ├── MessageBus.kt                — internal pub/sub (SharedFlow-based)
    ├── TaskManager.kt               — concurrent task tracking, status, cancellation
    ├── ContextManager.kt            — context budget allocation, compaction triggers
    ├── GuappaForegroundService.kt   — Android foreground service (DATA_SYNC type)
    ├── BootReceiver.kt              — auto-start on device boot
    ├── models/
    │   ├── AgentMessage.kt          — all message types (sealed class hierarchy)
    │   ├── AgentTask.kt             — task model (state machine)
    │   ├── AgentEvent.kt            — event types (sealed class)
    │   ├── ContextBudget.kt         — token budget allocation model
    │   └── SessionState.kt          — serializable session state
    ├── persistence/
    │   ├── SessionDatabase.kt       — Room database (sessions, messages, tasks)
    │   ├── SessionDao.kt            — DAO for session CRUD
    │   ├── MessageDao.kt            — DAO for messages
    │   ├── TaskDao.kt               — DAO for tasks
    │   └── Migrations.kt            — Room schema migrations
    └── di/
        └── AgentModule.kt           — Hilt/Koin dependency injection for agent components
```

### 3.2 Data Flow Diagram

```
┌─────────────┐     ┌───────────────┐     ┌───────────────────┐
│  UI (React   │────▶│  MessageBus   │────▶│  GuappaOrchestrator│
│  Native)     │     │  (SharedFlow) │     │  (coroutine loop)  │
└─────────────┘     └───────────────┘     └─────────┬─────────┘
                           ▲                         │
                           │                         ▼
┌─────────────┐           │              ┌──────────────────┐
│  Channels   │───────────┤              │  GuappaPlanner   │
│  (Telegram  │           │              │  (ReAct loop)    │
│   Discord)  │           │              └────────┬─────────┘
└─────────────┘           │                       │
                           │                       ▼
┌─────────────┐           │              ┌──────────────────┐
│  Proactive  │───────────┤              │  ToolEngine      │
│  Engine     │           │              │  (execute tools)  │
└─────────────┘           │              └────────┬─────────┘
                           │                       │
                           │                       ▼
┌─────────────┐           │              ┌──────────────────┐
│  Device     │───────────┘              │  ProviderRouter  │
│  Events     │                          │  (LLM call)      │
└─────────────┘                          └──────────────────┘
```

### 3.3 Agent Loop (Pseudo-code)

```kotlin
class GuappaOrchestrator(
    private val messageBus: MessageBus,
    private val planner: GuappaPlanner,
    private val providerRouter: ProviderRouter,
    private val toolEngine: ToolEngine,
    private val contextManager: ContextManager,
    private val sessionStore: SessionDatabase,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            messageBus.events.collect { event ->
                when (event) {
                    is UserMessage -> handleUserMessage(event)
                    is SystemEvent -> handleSystemEvent(event)
                    is TriggerEvent -> handleTrigger(event)
                    is ToolResult -> handleToolResult(event)
                }
            }
        }
    }

    private suspend fun handleUserMessage(msg: UserMessage) {
        val session = sessionStore.getOrCreate(msg.sessionId)
        session.addMessage(msg)

        // ReAct loop: reason → act → observe → repeat
        var done = false
        while (!done) {
            // 1. Build context (system prompt + memories + conversation + tools)
            val context = contextManager.buildContext(session)

            // 2. Call LLM
            val response = providerRouter.chat(context)

            // 3. Check if LLM wants to use tools
            if (response.hasToolCalls()) {
                for (toolCall in response.toolCalls) {
                    val result = toolEngine.execute(toolCall)
                    session.addToolResult(result)
                }
                // Loop continues — LLM will see tool results
            } else {
                // 4. LLM responded with text — send to user
                session.addAssistantMessage(response.text)
                messageBus.emit(AgentResponse(msg.sessionId, response.text))
                done = true
            }

            // 5. Check context budget — compact if needed
            if (contextManager.shouldCompact(session)) {
                contextManager.compact(session)
            }
        }

        // 6. Persist session
        sessionStore.save(session)
    }
}
```

---

## 4. Feature Specifications

### 4.1 Agent Orchestrator

**Responsibilities:**
- Listen to MessageBus for incoming events
- Route events to appropriate handlers
- Manage ReAct (Reason-Act-Observe) loop for tool-using conversations
- Handle concurrent sessions (chat + background tasks)
- Retry on provider errors with exponential backoff (1s, 2s, 4s, 8s, max 30s)
- Graceful degradation when all providers unavailable

**Configuration:**
```kotlin
data class OrchestratorConfig(
    val maxConcurrentSessions: Int = 5,          // max parallel sessions
    val maxToolCallsPerTurn: Int = 10,            // prevent infinite tool loops
    val maxReActIterations: Int = 20,             // prevent infinite ReAct loops
    val retryMaxAttempts: Int = 3,                // provider retry attempts
    val retryBaseDelayMs: Long = 1000,            // exponential backoff base
    val requestTimeoutMs: Long = 120_000,         // 2 minute timeout per LLM call
    val streamingEnabled: Boolean = true,          // stream responses to UI
)
```

**Error handling:**
- Provider timeout → retry with backoff → fallback provider → user error message
- Tool execution error → include error in context → LLM decides next action
- Session corruption → recover from last checkpoint → create new if unrecoverable
- OOM → release non-essential resources → reduce context size → notify user

### 4.2 Session Management

**Session types:**
| Type | Description | Persistence | Context Limit |
|------|-------------|-------------|---------------|
| `CHAT` | User-facing conversation | Persistent (SQLite) | Configurable (default 128K tokens) |
| `BACKGROUND_TASK` | Agent-initiated background task | Persistent | 32K tokens |
| `TRIGGER` | Event-driven reaction (SMS, call) | Temporary | 16K tokens |
| `SYSTEM` | Internal system operations | None | 8K tokens |

**Session lifecycle:**
```
CREATED → ACTIVE → IDLE → ARCHIVED
                 ↘ COMPACTED (context compressed)
                 ↘ EXPIRED (TTL reached)
```

**Persistence schema (Room):**
```kotlin
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val type: SessionType,
    val state: SessionState,
    val createdAt: Long,
    val updatedAt: Long,
    val contextTokens: Int,
    val messageCount: Int,
    val metadata: String,  // JSON blob for session-specific data
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: MessageRole,  // USER, ASSISTANT, SYSTEM, TOOL
    val content: String,
    val toolCalls: String?,  // JSON array of tool calls
    val toolCallId: String?,  // for TOOL role
    val tokenCount: Int,
    val timestamp: Long,
    val summarized: Boolean = false,  // true if this message was summarized
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val description: String,
    val state: TaskState,  // PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    val progress: Float,   // 0.0 - 1.0
    val result: String?,
    val error: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
```

### 4.3 Message Bus

**Message types (sealed class hierarchy):**
```kotlin
sealed class AgentEvent {
    // Inbound
    data class UserMessage(
        val sessionId: String,
        val text: String,
        val attachments: List<Attachment> = emptyList(),
        val channel: String = "in_app",
        val priority: Priority = Priority.NORMAL,
    ) : AgentEvent()

    data class SystemEvent(
        val type: SystemEventType,
        val payload: Map<String, Any>,
    ) : AgentEvent()

    data class TriggerEvent(
        val trigger: TriggerType,  // INCOMING_SMS, MISSED_CALL, CALENDAR_REMINDER, etc.
        val data: Map<String, String>,
    ) : AgentEvent()

    data class ToolResult(
        val sessionId: String,
        val toolCallId: String,
        val result: ToolResultData,
    ) : AgentEvent()

    // Outbound
    data class AgentResponse(
        val sessionId: String,
        val text: String,
        val streaming: Boolean = false,
    ) : AgentEvent()

    data class AgentStreamChunk(
        val sessionId: String,
        val chunk: String,
        val isLast: Boolean = false,
    ) : AgentEvent()

    data class AgentNotification(
        val type: NotificationType,
        val title: String,
        val body: String,
        val actions: List<NotificationAction> = emptyList(),
    ) : AgentEvent()

    data class TaskStatusUpdate(
        val taskId: String,
        val state: TaskState,
        val progress: Float,
        val message: String?,
    ) : AgentEvent()
}
```

**Bus implementation:**
```kotlin
class MessageBus {
    // High-capacity buffer for events
    private val _events = MutableSharedFlow<AgentEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    // Priority queue for urgent messages (incoming calls, errors)
    private val _urgentEvents = MutableSharedFlow<AgentEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    val urgentEvents: SharedFlow<AgentEvent> = _urgentEvents.asSharedFlow()

    suspend fun emit(event: AgentEvent) {
        if (event.isUrgent()) {
            _urgentEvents.emit(event)
        }
        _events.emit(event)
    }
}
```

### 4.4 Foreground Service

**Service type:** `FOREGROUND_SERVICE_TYPE_DATA_SYNC` (Android 14+)

**Lifecycle:**
```
App Launch → startForegroundService() → onCreate() →
  onStartCommand() → startForeground(notification) →
  start Orchestrator coroutine scope →
  ... running ...
  onDestroy() → save all sessions → cancel scope →
  if killed: START_STICKY → system restarts service
```

**Implementation details:**
- Request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` on first launch (user prompt)
- Use `WakeLock` (PARTIAL_WAKE_LOCK) for critical operations only, release ASAP
- Persistent notification shows:
  - Guappa status (idle / thinking / executing task)
  - Current task name (if any)
  - Quick actions: Open chat, Pause agent
- On Android 15+: handle FGS launch restrictions from background
  - Use `pendingIntent` from user interaction to start service
  - Boot receiver is exempt from background launch restrictions

**Boot receiver:**
```kotlin
class GuappaBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, GuappaForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
```

### 4.5 Task Manager

**Task state machine:**
```
PENDING → RUNNING → COMPLETED
                  ↘ FAILED
                  ↘ CANCELLED (user cancelled)
PENDING → CANCELLED (cancelled before start)
```

**Features:**
- Track up to `maxConcurrentTasks` (default: 3) running tasks
- Each task gets its own session (BACKGROUND_TASK type)
- Progress reporting via MessageBus (TaskStatusUpdate events)
- Cancellation support via `Job.cancel()` on coroutine
- Task history stored in Room database for episodic memory (Phase 7)

**Task examples:**
```kotlin
// User: "Найди лучшие рестораны рядом и составь список"
AgentTask(
    id = "task_123",
    description = "Найти лучшие рестораны рядом",
    steps = listOf(
        "Получить текущую геолокацию",
        "Поиск ресторанов через web_search",
        "Получить рейтинги и отзывы",
        "Составить отсортированный список",
        "Отправить результат в чат",
    ),
    state = TaskState.RUNNING,
    currentStep = 2,
)
```

### 4.6 Context Manager

**Context budget allocation:**
```kotlin
data class ContextBudget(
    val totalTokens: Int,           // e.g., 128_000
    val systemPromptBudget: Int,    // ~2000 tokens (persona + instructions)
    val toolSchemasBudget: Int,     // ~3000 tokens (available tool descriptions)
    val memoryBudget: Int,          // ~5000 tokens (relevant long-term memories)
    val conversationBudget: Int,    // remaining tokens for conversation history
    val reserveBuffer: Int = 4096,  // always keep reserve for response
) {
    val conversationBudget: Int
        get() = totalTokens - systemPromptBudget - toolSchemasBudget -
                memoryBudget - reserveBuffer
}
```

**Compaction triggers:**
- When conversation tokens > 80% of conversationBudget
- When total context > 90% of totalTokens
- When session has been idle > 30 minutes (pre-emptive compaction)

**Compaction strategy (see Phase 7 for full details):**
1. Summarize oldest messages into a single summary message
2. Keep last N messages verbatim (sliding window)
3. Keep all tool call/result pairs from recent turns
4. Preserve user-flagged messages ("remember this")

### 4.7 Persona

**System instruction template:**
```
You are Guappa (Гуаппа) — a friendly, proactive AI assistant running as an Android agent.
You are female ("она" in Russian, "she" in English).
Your personality: helpful, friendly, proactive, slightly playful.

Current capabilities:
{available_tools_summary}

Current context:
- Device: {device_model}
- Time: {current_datetime}
- Location: {last_known_location}
- Battery: {battery_level}%

Long-term memories about this user:
{relevant_memories}

Instructions:
1. Be concise and helpful
2. When you complete a task, report the result
3. When you're unsure, ask clarifying questions
4. When you spot something the user might want to know (missed call, new email), mention it proactively
5. Use tools to accomplish tasks — don't just describe what you would do
6. Respond in the same language the user writes in
```

---

## 5. Configuration Schema

```kotlin
data class FoundationConfig(
    // Orchestrator
    val maxConcurrentSessions: Int = 5,
    val maxToolCallsPerTurn: Int = 10,
    val maxReActIterations: Int = 20,
    val retryMaxAttempts: Int = 3,
    val requestTimeoutMs: Long = 120_000,
    val streamingEnabled: Boolean = true,

    // Session
    val defaultContextTokens: Int = 128_000,
    val sessionIdleTtlMinutes: Int = 30,
    val sessionArchiveDays: Int = 30,

    // Context
    val compactionThreshold: Float = 0.8f,  // % of budget before compaction
    val slidingWindowMessages: Int = 20,     // keep last N messages verbatim
    val reserveBufferTokens: Int = 4096,

    // Task Manager
    val maxConcurrentTasks: Int = 3,
    val taskTimeoutMinutes: Int = 30,

    // Persona
    val personaLocale: String = "ru",  // default greeting locale
    val proactiveEnabled: Boolean = true,

    // Service
    val autoStartOnBoot: Boolean = true,
    val batteryOptimizationExempt: Boolean = true,
)
```

---

## 6. Error Handling Matrix

| Error | Handling | User-Facing |
|-------|----------|-------------|
| Provider timeout | Retry 3x with backoff → fallback provider → error | "Извини, сервер не отвечает. Попробую другой..." |
| Provider auth error | Log error, prompt user to check API key | "API ключ недействителен. Проверь настройки." |
| Tool execution fail | Include error in context, let LLM decide next | "Не получилось выполнить {tool}: {error}" |
| Context overflow | Trigger compaction, retry | (silent — compaction is transparent) |
| Session corruption | Recover from last checkpoint | "Произошла ошибка. Начинаю новую сессию." |
| Service killed by OS | START_STICKY auto-restart | (notification briefly disappears, then returns) |
| OOM | Release local model, reduce context | "Мало памяти. Переключаюсь на облачную модель." |
| Database error | Retry, fall back to in-memory | "Ошибка сохранения. Работаю без сохранения." |

---

## 7. Security Considerations

- Sessions stored encrypted (Android Keystore + SQLCipher or EncryptedSharedPreferences)
- API keys stored in Android Keystore, never in plaintext
- Session data never logged in production builds
- Tool execution requires explicit per-tool permission grants
- Foreground service notification clearly indicates agent is running (no stealth)
- All network calls use TLS 1.3+ (OkHttp default)

---

## 8. Dependencies

```kotlin
// build.gradle.kts
dependencies {
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.+")

    // Room (session persistence)
    implementation("androidx.room:room-runtime:2.6.+")
    implementation("androidx.room:room-ktx:2.6.+")
    ksp("androidx.room:room-compiler:2.6.+")

    // DataStore (config)
    implementation("androidx.datastore:datastore-preferences:1.1.+")

    // Hilt (DI)
    implementation("com.google.dagger:hilt-android:2.51+")
    ksp("com.google.dagger:hilt-compiler:2.51+")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.+")
}
```

---

## 9. Test Plan

### 9.1 Unit Tests

| Test | Input | Expected |
|------|-------|----------|
| Orchestrator processes user message | UserMessage → bus | LLM called, response emitted |
| Orchestrator handles tool calls | LLM returns tool_call | Tool executed, result fed back |
| Orchestrator max iterations | 21 ReAct loops | Loop breaks, error emitted |
| Session creation | New session ID | Session entity created in Room |
| Session compaction trigger | 82% context used | Compaction triggered |
| MessageBus delivery | Emit event | All collectors receive event |
| MessageBus priority | Urgent event | Arrives on urgentEvents flow |
| TaskManager concurrency | 4 tasks when max=3 | 4th task queued |
| TaskManager cancellation | Cancel running task | Coroutine cancelled, state=CANCELLED |
| ContextBudget allocation | 128K total, defaults | Correct per-section budgets |

### 9.2 Integration Tests

| Test | Scenario | Expected |
|------|----------|----------|
| Full message flow | User → Bus → Orchestrator → Provider → Bus → UI | Response reaches UI |
| Session persistence | Process message → kill service → restart | Session restored with history |
| Multi-session | Chat + background task simultaneously | Both process independently |
| Service lifecycle | Start → stop → start | Service restarts, sessions intact |

### 9.3 Maestro E2E

```yaml
# 01-app-launch.yaml
- launchApp: com.guappa.app
- assertVisible: "Guappa"
- assertVisible:
    id: "notification_status"
    text: "Guappa работает"

# 02-send-message.yaml
- launchApp: com.guappa.app
- tapOn:
    id: "message_input"
- inputText: "Привет!"
- tapOn:
    id: "send_button"
- assertVisible:
    text: ".*"  # any response from agent
    timeout: 30000
```

### 9.4 Resilience Tests

| Scenario | Method | Expected |
|----------|--------|----------|
| App force-killed | `adb shell am force-stop` | Service restarts via START_STICKY |
| Device reboot | `adb reboot` | BootReceiver starts service |
| 100 rapid messages | Send 100 msgs in 1 second | All queued, processed in order |
| Memory pressure | Fill device RAM | Service survives, low-priority tasks deferred |

---

## 10. Acceptance Criteria

- [ ] Agent responds to text messages within 5 seconds (cloud provider)
- [ ] Streaming responses render character-by-character in UI
- [ ] Foreground service persists notification with correct status
- [ ] Service auto-restarts after kill (within 10 seconds)
- [ ] Service auto-starts on device boot
- [ ] Sessions survive app restart with full history
- [ ] Multiple sessions (chat + background task) work concurrently
- [ ] Context compaction triggers at configured threshold
- [ ] Tool calls execute and results feed back into conversation
- [ ] Task progress updates appear in real-time
- [ ] All unit tests pass
- [ ] All Maestro E2E flows pass

---

## 11. Rollback Strategy

- This phase creates new files only (no modifications to existing code until later phases)
- Rollback: revert the PR / delete the `agent/` directory
- No database migrations to undo (first-time schema creation)
- No config migration needed (new config format)
