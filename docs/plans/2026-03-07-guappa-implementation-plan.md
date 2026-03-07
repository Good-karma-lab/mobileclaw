# GUAPPA Implementation Plan — Full Product Build

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Transform MobileClaw into GUAPPA — an ultra-futuristic autonomous AI agent Android app with liquid glass UI, plasma orb voice mode, World Wide Swarm connectivity, and 122 Maestro E2E tests.

**Architecture:** Milestone-driven vertical slices. Each milestone delivers a working feature end-to-end (Kotlin backend + React Native UI). Milestones are tested on Android emulator and committed before proceeding. Git worktrees isolate each milestone's work.

**Tech Stack:** React Native 0.81 + Expo 54 + Kotlin (Android native) + TurboModules (JSI bridge) + @shopify/react-native-skia (shaders) + react-native-reanimated 4.x (animations) + expo-blur (glass) + Maestro 2.2 (E2E)

**Emulator:** API 34 x86_64 (primary), API 30 (compat)

**Security:** API keys and tokens are NEVER committed to git. They are entered by users at runtime and stored in Android Keystore / EncryptedSharedPreferences on-device only.

---

## Milestone Overview

| Milestone | Scope | Depends On | Deliverable |
|-----------|-------|------------|-------------|
| **M0** | Rebrand + cleanup | — | All ZeroClaw/MobileClaw references removed, com.guappa.app package |
| **M1** | Foundation + basic UI shell | M0 | Agent loop, message bus, foreground service, dock navigation, basic chat |
| **M2** | Provider Router + Config screen | M1 | Dynamic model fetching, capability-first settings, smart defaults |
| **M3** | Tool Engine + Onboarding | M1, M2 | 69 tools, first-run wizard, permission grants, local model download |
| **M4** | Voice Pipeline + Voice screen | M2 | STT/TTS, plasma orb, wake word, voice-to-chat sync |
| **M5** | Memory & Context + Command Center | M1 | 5-tier memory, auto-summarization, tasks/schedules/triggers UI |
| **M6** | Proactive Agent + Push | M3, M5 | Event triggers, push notifications, morning briefing |
| **M7** | Channel Hub + Config channels | M6 | Telegram, Discord, Slack, WhatsApp, Signal, Matrix, Email, SMS |
| **M8** | Live Config (hot-reload) | M2, M3 | TurboModules bridge, StateFlow, zero-restart config changes |
| **M9** | World Wide Swarm + Swarm screen | M1, M6 | WWSP connector, P2P feed, topology graph |
| **M10** | Glass design system + UI polish | M1-M9 | Liquid glass components, final animations, responsive layouts |
| **M11** | Full Maestro test suite | M0-M10 | 122 E2E flows, CI pipeline, Firebase Test Lab |
| **M12** | Documentation | M0-M11 | Complete Guappa docs, README, guides |

---

## Pre-Implementation Setup

### Emulator Setup

```bash
# Create API 34 emulator
sdkmanager "system-images;android-34;google_apis;x86_64"
avdmanager create avd -n guappa-test -k "system-images;android-34;google_apis;x86_64" -d pixel_7
emulator -avd guappa-test -no-snapshot-load &

# Install Maestro
curl -Ls "https://get.maestro.mobile.dev" | bash
```

### Worktree Strategy

Each milestone gets its own worktree branching from current work:

```bash
# From main repo
git worktree add ../guappa-m0 -b milestone/m0-rebrand
# After M0 merges:
git worktree add ../guappa-m1 -b milestone/m1-foundation
# etc.
```

### Validation Commands (run after every task)

```bash
# Build check
cd mobile-app/android && ./gradlew assembleDebug

# TypeScript check
cd mobile-app && npx tsc --noEmit

# Maestro smoke test (after APK installed on emulator)
maestro test mobile-app/.maestro/smoke_navigation.yaml

# Full Maestro suite
maestro test mobile-app/.maestro/
```

---

## M0: Rebrand + Cleanup

**Goal:** Remove all ZeroClaw/MobileClaw references. Establish com.guappa.app identity.

**Worktree:** `git worktree add ../guappa-m0 -b milestone/m0-rebrand`

### Task M0.1: Rename Android Package

**Files:**
- Move: `mobile-app/android/app/src/main/java/com/mobileclaw/app/` → `mobile-app/android/app/src/main/java/com/guappa/app/`
- Modify: All 21 Kotlin files (package declaration line 1)

**Step 1: Create new package directory**

```bash
cd mobile-app/android/app/src/main/java
mkdir -p com/guappa/app
```

**Step 2: Move all Kotlin files**

```bash
mv com/mobileclaw/app/*.kt com/guappa/app/
rmdir com/mobileclaw/app
rmdir com/mobileclaw
```

**Step 3: Update package declarations in all 21 files**

Find and replace in every `.kt` file:
```
OLD: package com.mobileclaw.app
NEW: package com.guappa.app
```

Also update all internal imports:
```
OLD: import com.mobileclaw.app.
NEW: import com.guappa.app.
```

**Step 4: Update build.gradle**

File: `mobile-app/android/app/build.gradle`
```
OLD: namespace "com.mobileclaw.app"
OLD: applicationId "com.mobileclaw.app"
NEW: namespace "com.guappa.app"
NEW: applicationId "com.guappa.app"
```

**Step 5: Update settings.gradle**

File: `mobile-app/android/settings.gradle`
```
OLD: rootProject.name = 'MobileClaw'
NEW: rootProject.name = 'Guappa'
```

**Step 6: Update AndroidManifest.xml**

File: `mobile-app/android/app/src/main/AndroidManifest.xml`
- Replace all `com.mobileclaw.app` → `com.guappa.app`
- Replace scheme `"mobileclaw"` → `"guappa"`

**Step 7: Update strings.xml**

File: `mobile-app/android/app/src/main/res/values/strings.xml`
```xml
OLD: <string name="app_name">MobileClaw</string>
NEW: <string name="app_name">Guappa</string>
```

**Step 8: Verify build**

```bash
cd mobile-app/android && ./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

### Task M0.2: Rename Kotlin Classes

**Files:**
- Rename: `ZeroClawDaemonService.kt` → `GuappaAgentService.kt`
- Rename: `ZeroClawDaemonModule.kt` → `GuappaAgentModule.kt`
- Rename: `ZeroClawDaemonPackage.kt` → `GuappaAgentPackage.kt`
- Delete: `ZeroClawBackend.kt` (deprecated JNI bridge)
- Modify: All files referencing old class names

**Step 1: Rename files and update class names**

```bash
cd mobile-app/android/app/src/main/java/com/guappa/app/
mv ZeroClawDaemonService.kt GuappaAgentService.kt
mv ZeroClawDaemonModule.kt GuappaAgentModule.kt
mv ZeroClawDaemonPackage.kt GuappaAgentPackage.kt
rm ZeroClawBackend.kt
```

**Step 2: Update class declarations and references**

In `GuappaAgentService.kt`:
```kotlin
OLD: class ZeroClawDaemonService
NEW: class GuappaAgentService
```

In `GuappaAgentModule.kt`:
```kotlin
OLD: class ZeroClawDaemonModule
OLD: override fun getName() = "ZeroClawDaemon"
NEW: class GuappaAgentModule
NEW: override fun getName() = "GuappaAgent"
```

In `GuappaAgentPackage.kt`:
```kotlin
OLD: class ZeroClawDaemonPackage
NEW: class GuappaAgentPackage
```

In `MainApplication.kt`:
```kotlin
OLD: ZeroClawDaemonPackage()
NEW: GuappaAgentPackage()
```

In `RuntimeBridge.kt`, `RuntimeBootReceiver.kt`, and all other files referencing old names — update all references.

In `AndroidManifest.xml`:
```xml
OLD: android:name=".ZeroClawDaemonService"
NEW: android:name=".GuappaAgentService"
```

**Step 3: Update notification strings**

In `GuappaAgentService.kt` and `RuntimeBridge.kt`:
```kotlin
OLD: "MobileClaw Runtime"
OLD: "MobileClaw is running"
NEW: "Guappa Agent"
NEW: "Guappa is running"
```

**Step 4: Verify build**

```bash
cd mobile-app/android && ./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: rename Android package com.mobileclaw.app → com.guappa.app and ZeroClaw classes → Guappa"
```

### Task M0.3: Rename TypeScript Modules

**Files:**
- Rename: `mobile-app/src/native/zeroClawDaemon.ts` → `mobile-app/src/native/guappaAgent.ts`
- Rename: `mobile-app/src/state/mobileclaw.ts` → `mobile-app/src/state/guappa.ts`
- Rename: `mobile-app/src/api/mobileclaw.ts` → `mobile-app/src/api/guappa.ts`
- Modify: All files importing the old modules

**Step 1: Rename files**

```bash
cd mobile-app/src
mv native/zeroClawDaemon.ts native/guappaAgent.ts
mv state/mobileclaw.ts state/guappa.ts
mv api/mobileclaw.ts api/guappa.ts
```

**Step 2: Update imports in all TypeScript files**

Search and replace across all `.ts`/`.tsx` files:
```
OLD: from '../native/zeroClawDaemon'
NEW: from '../native/guappaAgent'

OLD: from '../state/mobileclaw'
NEW: from '../state/guappa'

OLD: from '../api/mobileclaw'
NEW: from '../api/guappa'
```

**Step 3: Update native module name in guappaAgent.ts**

```typescript
OLD: NativeModules.ZeroClawDaemon
NEW: NativeModules.GuappaAgent
```

**Step 4: Update AsyncStorage key prefixes**

In all files using AsyncStorage:
```typescript
OLD: 'mobileclaw:agent:v1'
OLD: 'mobileclaw:integrations:v1'
OLD: 'mobileclaw:security:v1'
OLD: 'mobileclaw:device-tools:v1'
OLD: 'mobileclaw:daemon:v1'
NEW: 'guappa:agent:v1'
NEW: 'guappa:integrations:v1'
NEW: 'guappa:security:v1'
NEW: 'guappa:device-tools:v1'
NEW: 'guappa:daemon:v1'
```

**Step 5: Update app.config.js**

```javascript
OLD: name: "MobileClaw" (or similar)
OLD: slug: "mobileclaw"
OLD: scheme: "mobileclaw"
OLD: bundleIdentifier: "com.mobileclaw.app"
OLD: package: "com.mobileclaw.app"
NEW: name: "Guappa"
NEW: slug: "guappa"
NEW: scheme: "guappa"
NEW: bundleIdentifier: "com.guappa.app"
NEW: package: "com.guappa.app"
```

**Step 6: Update package.json**

```json
OLD: "name": "mobileclaw-mobile"
NEW: "name": "guappa"
```

**Step 7: Update persona strings in chat/API**

In `api/guappa.ts`:
```typescript
OLD: X-Title: 'MobileClaw'
NEW: X-Title: 'Guappa'
```

In `runtime/session.ts`:
```typescript
OLD: "MobileClaw is thinking"
NEW: "Guappa is thinking"
```

**Step 8: Verify TypeScript compiles**

```bash
cd mobile-app && npx tsc --noEmit
```
Expected: No errors

**Step 9: Verify build**

```bash
cd mobile-app/android && ./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

**Step 10: Commit**

```bash
git add -A
git commit -m "refactor: rename TypeScript modules mobileclaw/zeroClaw → guappa"
```

### Task M0.4: Update Maestro Tests

**Files:**
- Modify: All 82 files in `mobile-app/.maestro/`

**Step 1: Batch replace appId**

```bash
cd mobile-app/.maestro
find . -name "*.yaml" -exec sed -i '' 's/com\.mobileclaw\.app/com.guappa.app/g' {} +
```

**Step 2: Replace any MobileClaw/ZeroClaw text references in test assertions**

```bash
find . -name "*.yaml" -exec sed -i '' 's/MobileClaw/Guappa/g' {} +
find . -name "*.yaml" -exec sed -i '' 's/mobileclaw/guappa/g' {} +
find . -name "*.yaml" -exec sed -i '' 's/ZeroClaw/Guappa/g' {} +
find . -name "*.yaml" -exec sed -i '' 's/zeroClaw/guappa/g' {} +
```

**Step 3: Verify YAML syntax**

```bash
for f in *.yaml; do python3 -c "import yaml; yaml.safe_load(open('$f'))"; done
```
Expected: No errors

**Step 4: Commit**

```bash
git add -A
git commit -m "refactor: update 82 Maestro tests with com.guappa.app package"
```

### Task M0.5: Delete Legacy ZeroClaw Files

**Files to delete:**
- `mobile-app/android/app/src/main/java/com/guappa/app/ZeroClawBackend.kt` (already deleted in M0.2, verify)
- `mobile-app/android/app/src/main/jniLibs/` — remove Rust binary assets if any remain
- Any `*.so` files referencing zeroclaw/mobileclaw

**Step 1: Verify no ZeroClaw references remain**

```bash
cd mobile-app
grep -rn "ZeroClaw\|zeroClaw\|mobileclaw\|MobileClaw\|com\.mobileclaw" --include="*.kt" --include="*.ts" --include="*.tsx" --include="*.java" --include="*.xml" --include="*.json" --include="*.js" .
```
Expected: Zero matches (or only in node_modules which we ignore)

**Step 2: Check for stale native libraries**

```bash
find mobile-app/android -name "libzeroclaw*" -o -name "libmobileclaw*"
```
If any found, delete them.

**Step 3: Clean build**

```bash
cd mobile-app/android && ./gradlew clean assembleDebug
```
Expected: BUILD SUCCESSFUL

**Step 4: Install on emulator and smoke test**

```bash
adb install -r mobile-app/android/app/build/outputs/apk/debug/app-debug.apk
maestro test mobile-app/.maestro/smoke_navigation.yaml
```
Expected: PASS

**Step 5: Commit**

```bash
git add -A
git commit -m "chore: remove all legacy ZeroClaw/MobileClaw references and binaries"
```

### Task M0.6: Fix Master Plan Phase Numbering

**Files:**
- Modify: `docs/plans/2026-03-06-guappa-master-plan.md`

**Step 1: Update phase table**

Remove the broken Phase 8 entry. The documentation phase is Phase 13. Update the phase table to reflect the correct numbering including the new Phase 12 (Android UI) already added.

**Step 2: Fix the broken hyperlink**

```
OLD: | **8** | Documentation | Complete Guappa docs from scratch (40+ files) | Phase 1-7 | [phase-08-documentation.md](phase-08-documentation.md) |
NEW: | **13** | Documentation | Complete Guappa docs from scratch (40+ files) | Phase 1-12 | [phase-13-documentation.md](phase-13-documentation.md) |
```

**Step 3: Commit**

```bash
git add docs/plans/2026-03-06-guappa-master-plan.md
git commit -m "docs: fix master plan phase numbering (Phase 8 → Phase 13)"
```

---

## M1: Foundation + Basic UI Shell

**Goal:** Working agent loop with message bus, foreground service, and the new 5-tab dock navigation with basic chat functionality.

**Worktree:** `git worktree add ../guappa-m1 -b milestone/m1-foundation`

### Task M1.1: Agent Core — MessageBus

**Files:**
- Create: `mobile-app/android/app/src/main/java/com/guappa/app/agent/MessageBus.kt`
- Test: Build + unit verification via Maestro agent health check

**Step 1: Create agent directory**

```bash
mkdir -p mobile-app/android/app/src/main/java/com/guappa/app/agent
```

**Step 2: Implement MessageBus**

```kotlin
// MessageBus.kt
package com.guappa.app.agent

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.BufferOverflow

enum class MessagePriority { NORMAL, URGENT }

sealed class BusMessage {
    abstract val priority: MessagePriority
    abstract val timestamp: Long

    data class UserMessage(
        val text: String,
        val sessionId: String,
        override val priority: MessagePriority = MessagePriority.NORMAL,
        override val timestamp: Long = System.currentTimeMillis()
    ) : BusMessage()

    data class AgentMessage(
        val text: String,
        val sessionId: String,
        val isStreaming: Boolean = false,
        override val priority: MessagePriority = MessagePriority.NORMAL,
        override val timestamp: Long = System.currentTimeMillis()
    ) : BusMessage()

    data class SystemEvent(
        val type: String,
        val data: Map<String, Any?> = emptyMap(),
        override val priority: MessagePriority = MessagePriority.NORMAL,
        override val timestamp: Long = System.currentTimeMillis()
    ) : BusMessage()

    data class ToolResult(
        val toolName: String,
        val result: String,
        val success: Boolean,
        val sessionId: String,
        override val priority: MessagePriority = MessagePriority.NORMAL,
        override val timestamp: Long = System.currentTimeMillis()
    ) : BusMessage()

    data class TriggerEvent(
        val trigger: String,
        val data: Map<String, Any?> = emptyMap(),
        override val priority: MessagePriority = MessagePriority.URGENT,
        override val timestamp: Long = System.currentTimeMillis()
    ) : BusMessage()
}

class MessageBus {
    private val _messages = MutableSharedFlow<BusMessage>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _urgentMessages = MutableSharedFlow<BusMessage>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.SUSPEND
    )

    val messages: SharedFlow<BusMessage> = _messages.asSharedFlow()
    val urgentMessages: SharedFlow<BusMessage> = _urgentMessages.asSharedFlow()

    suspend fun publish(message: BusMessage) {
        when (message.priority) {
            MessagePriority.URGENT -> _urgentMessages.emit(message)
            MessagePriority.NORMAL -> _messages.emit(message)
        }
    }
}
```

**Step 3: Verify build**

```bash
cd mobile-app/android && ./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add -A
git commit -m "feat(agent): add MessageBus with SharedFlow pub/sub and priority queue"
```

### Task M1.2: Agent Core — Session & Orchestrator

**Files:**
- Create: `mobile-app/android/app/src/main/java/com/guappa/app/agent/GuappaSession.kt`
- Create: `mobile-app/android/app/src/main/java/com/guappa/app/agent/GuappaOrchestrator.kt`
- Create: `mobile-app/android/app/src/main/java/com/guappa/app/agent/GuappaConfig.kt`

**Step 1: Implement GuappaConfig**

```kotlin
// GuappaConfig.kt
package com.guappa.app.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AgentConfig(
    val maxConcurrentSessions: Int = 5,
    val maxToolCallsPerTurn: Int = 10,
    val maxReActIterations: Int = 20,
    val requestTimeoutMs: Long = 120_000,
    val defaultContextTokens: Int = 128_000,
    val compactionThreshold: Float = 0.8f
)

class GuappaConfig {
    private val _config = MutableStateFlow(AgentConfig())
    val config: StateFlow<AgentConfig> = _config.asStateFlow()

    fun update(transform: AgentConfig.() -> AgentConfig) {
        _config.value = _config.value.transform()
    }
}
```

**Step 2: Implement GuappaSession**

```kotlin
// GuappaSession.kt
package com.guappa.app.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class SessionType { CHAT, BACKGROUND_TASK, TRIGGER, SYSTEM }
enum class SessionState { ACTIVE, IDLE, COMPACTING, CLOSED }

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user", "assistant", "system", "tool"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCallId: String? = null,
    val tokenCount: Int = 0
)

class GuappaSession(
    val id: String = UUID.randomUUID().toString(),
    val type: SessionType = SessionType.CHAT,
    private val maxTokens: Int = 128_000
) {
    private val _messages = mutableListOf<Message>()
    val messages: List<Message> get() = _messages.toList()

    private val _state = MutableStateFlow(SessionState.IDLE)
    val state = _state.asStateFlow()

    private var totalTokens: Int = 0

    val contextUsageRatio: Float get() = totalTokens.toFloat() / maxTokens

    fun addMessage(message: Message) {
        _messages.add(message)
        totalTokens += message.tokenCount
        _state.value = SessionState.ACTIVE
    }

    fun needsCompaction(threshold: Float): Boolean = contextUsageRatio > threshold

    fun getContextMessages(systemPrompt: String): List<Message> {
        val result = mutableListOf(Message(role = "system", content = systemPrompt))
        result.addAll(_messages.takeLast(40)) // Keep last 40 messages
        return result
    }

    fun close() {
        _state.value = SessionState.CLOSED
    }
}
```

**Step 3: Implement GuappaOrchestrator**

```kotlin
// GuappaOrchestrator.kt
package com.guappa.app.agent

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class GuappaOrchestrator(
    private val messageBus: MessageBus,
    private val config: GuappaConfig
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessions = mutableMapOf<String, GuappaSession>()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var defaultSessionId: String? = null

    fun start() {
        _isRunning.value = true

        // Process normal messages
        scope.launch {
            messageBus.messages.collect { message ->
                handleMessage(message)
            }
        }

        // Process urgent messages with priority
        scope.launch {
            messageBus.urgentMessages.collect { message ->
                handleMessage(message)
            }
        }
    }

    fun stop() {
        _isRunning.value = false
        scope.cancel()
    }

    fun getOrCreateDefaultSession(): GuappaSession {
        val id = defaultSessionId
        if (id != null && sessions[id]?.state?.value != SessionState.CLOSED) {
            return sessions[id]!!
        }
        val session = GuappaSession(type = SessionType.CHAT)
        sessions[session.id] = session
        defaultSessionId = session.id
        return session
    }

    private suspend fun handleMessage(message: BusMessage) {
        when (message) {
            is BusMessage.UserMessage -> {
                val session = sessions[message.sessionId] ?: getOrCreateDefaultSession()
                session.addMessage(Message(role = "user", content = message.text))
                // Provider call will be wired in M2
            }
            is BusMessage.ToolResult -> {
                val session = sessions[message.sessionId] ?: return
                session.addMessage(Message(
                    role = "tool",
                    content = message.result,
                    toolCallId = message.toolName
                ))
            }
            is BusMessage.TriggerEvent -> {
                val session = GuappaSession(type = SessionType.TRIGGER)
                sessions[session.id] = session
                // Handle trigger in background
            }
            else -> { /* SystemEvent, AgentMessage handled by UI layer */ }
        }
    }
}
```

**Step 4: Verify build**

```bash
cd mobile-app/android && ./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add -A
git commit -m "feat(agent): add GuappaOrchestrator, GuappaSession, and GuappaConfig"
```

### Task M1.3: Foreground Service

**Files:**
- Modify: `mobile-app/android/app/src/main/java/com/guappa/app/GuappaAgentService.kt`
- Modify: `mobile-app/android/app/src/main/java/com/guappa/app/RuntimeBootReceiver.kt`

**Step 1: Rewrite GuappaAgentService to use new agent core**

```kotlin
// GuappaAgentService.kt
package com.guappa.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.guappa.app.agent.*

class GuappaAgentService : Service() {

    companion object {
        const val CHANNEL_ID = "guappa_service"
        const val NOTIFICATION_ID = 1
        lateinit var messageBus: MessageBus
            private set
        lateinit var orchestrator: GuappaOrchestrator
            private set
        lateinit var config: GuappaConfig
            private set
        var isInitialized = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        config = GuappaConfig()
        messageBus = MessageBus()
        orchestrator = GuappaOrchestrator(messageBus, config)
        orchestrator.start()
        isInitialized = true

        val notification = buildNotification("Guappa is active")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        orchestrator.stop()
        isInitialized = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Guappa Agent",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Guappa background agent service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Guappa")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
```

**Step 2: Update RuntimeBootReceiver**

```kotlin
// RuntimeBootReceiver.kt
package com.guappa.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class RuntimeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, GuappaAgentService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
```

**Step 3: Verify build and install**

```bash
cd mobile-app/android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Step 4: Commit**

```bash
git add -A
git commit -m "feat(agent): wire GuappaAgentService with orchestrator, message bus, and boot receiver"
```

### Task M1.4: New Navigation — Floating Dock

**Files:**
- Create: `mobile-app/src/components/dock/FloatingDock.tsx`
- Create: `mobile-app/src/components/dock/SideRail.tsx`
- Create: `mobile-app/src/theme/colors.ts`
- Create: `mobile-app/src/theme/typography.ts`
- Create: `mobile-app/src/theme/spacing.ts`
- Create: `mobile-app/src/theme/animations.ts`
- Rewrite: `mobile-app/src/navigation/RootNavigator.tsx`
- Create: `mobile-app/src/screens/tabs/VoiceScreen.tsx` (placeholder)
- Create: `mobile-app/src/screens/tabs/CommandScreen.tsx` (placeholder)
- Create: `mobile-app/src/screens/tabs/SwarmScreen.tsx` (placeholder)
- Create: `mobile-app/src/screens/tabs/ConfigScreen.tsx` (placeholder)
- Modify: `mobile-app/src/screens/tabs/ChatScreen.tsx` (simplify for now)

**Step 1: Create theme tokens**

```typescript
// theme/colors.ts
export const colors = {
  base: {
    spaceBlack: '#050510',
    midnightBlue: '#0A0A2E',
  },
  glass: {
    fill: 'rgba(255, 255, 255, 0.08)',
    border: 'rgba(255, 255, 255, 0.15)',
    fillActive: 'rgba(255, 255, 255, 0.12)',
  },
  accent: {
    cyan: '#00F0FF',
    violet: '#8B5CF6',
    rose: '#FF3366',
    amber: '#FFAA33',
    gold: '#FFD700',
  },
  semantic: {
    success: '#14B8A6',
    error: '#EF4444',
    warning: '#F59E0B',
    info: '#3B82F6',
  },
  text: {
    primary: 'rgba(255, 255, 255, 0.90)',
    secondary: 'rgba(255, 255, 255, 0.60)',
    tertiary: 'rgba(255, 255, 255, 0.35)',
  },
} as const;
```

```typescript
// theme/typography.ts
export const typography = {
  display: { fontFamily: 'Orbitron-Bold', fontWeight: '700' as const },
  body: { fontFamily: 'Exo2-Regular', fontWeight: '400' as const },
  bodyMedium: { fontFamily: 'Exo2-Medium', fontWeight: '500' as const },
  bodySemiBold: { fontFamily: 'Exo2-SemiBold', fontWeight: '600' as const },
  mono: { fontFamily: 'JetBrainsMono-Regular', fontWeight: '400' as const },
} as const;
```

```typescript
// theme/spacing.ts
export const spacing = {
  xs: 4,
  sm: 8,
  md: 16,
  lg: 24,
  xl: 32,
  xxl: 48,
} as const;

export const radius = {
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  pill: 999,
} as const;
```

```typescript
// theme/animations.ts
export const springs = {
  gentle: { damping: 20, stiffness: 180 },
  snappy: { damping: 15, stiffness: 150 },
  bouncy: { damping: 12, stiffness: 100 },
} as const;
```

**Step 2: Create FloatingDock component**

```typescript
// components/dock/FloatingDock.tsx
import React from 'react';
import { View, Pressable, StyleSheet } from 'react-native';
import { BlurView } from 'expo-blur';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors } from '../../theme/colors';
import { springs } from '../../theme/animations';

interface DockTab {
  key: string;
  icon: string;
  label: string;
}

interface FloatingDockProps {
  tabs: DockTab[];
  activeTab: string;
  onTabPress: (key: string) => void;
}

const ICON_SIZE = 28;
const TAB_WIDTH = 56;
const DOCK_PADDING = 8;

export function FloatingDock({ tabs, activeTab, onTabPress }: FloatingDockProps) {
  const insets = useSafeAreaInsets();
  const dockWidth = tabs.length * TAB_WIDTH + DOCK_PADDING * 2;

  return (
    <View
      style={[styles.container, { bottom: Math.max(insets.bottom, 16) }]}
      pointerEvents="box-none"
      testID="floating-dock"
    >
      <BlurView intensity={20} tint="dark" style={[styles.dock, { width: dockWidth }]}>
        <View style={styles.dockInner}>
          {tabs.map((tab) => {
            const isActive = tab.key === activeTab;
            return (
              <DockIcon
                key={tab.key}
                tab={tab}
                isActive={isActive}
                onPress={() => onTabPress(tab.key)}
              />
            );
          })}
        </View>
      </BlurView>
    </View>
  );
}

function DockIcon({
  tab,
  isActive,
  onPress,
}: {
  tab: DockTab;
  isActive: boolean;
  onPress: () => void;
}) {
  const scale = useSharedValue(isActive ? 1.2 : 1);

  React.useEffect(() => {
    scale.value = withSpring(isActive ? 1.2 : 1, springs.snappy);
  }, [isActive]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
    opacity: isActive ? 1 : 0.5,
  }));

  return (
    <Pressable onPress={onPress} testID={`dock-tab-${tab.key}`}>
      <Animated.View style={[styles.tabIcon, animatedStyle]}>
        <Animated.Text style={[styles.iconText, isActive && styles.iconActive]}>
          {tab.icon}
        </Animated.Text>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
    zIndex: 1000,
  },
  dock: {
    borderRadius: 999,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: colors.glass.border,
  },
  dockInner: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: DOCK_PADDING,
    paddingVertical: 8,
    backgroundColor: colors.glass.fill,
  },
  tabIcon: {
    width: TAB_WIDTH,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconText: {
    fontSize: ICON_SIZE,
    color: colors.text.secondary,
  },
  iconActive: {
    color: colors.accent.cyan,
  },
});
```

**Step 3: Create SideRail component (tablet/automotive)**

```typescript
// components/dock/SideRail.tsx
import React from 'react';
import { View, Pressable, Text, StyleSheet } from 'react-native';
import { BlurView } from 'expo-blur';
import { colors } from '../../theme/colors';
import { typography } from '../../theme/typography';

interface SideRailProps {
  tabs: Array<{ key: string; icon: string; label: string }>;
  activeTab: string;
  onTabPress: (key: string) => void;
}

const RAIL_WIDTH = 72;

export function SideRail({ tabs, activeTab, onTabPress }: SideRailProps) {
  return (
    <BlurView intensity={20} tint="dark" style={styles.rail} testID="side-rail">
      <View style={styles.railInner}>
        {tabs.map((tab) => {
          const isActive = tab.key === activeTab;
          return (
            <Pressable
              key={tab.key}
              onPress={() => onTabPress(tab.key)}
              style={[styles.railTab, isActive && styles.railTabActive]}
              testID={`rail-tab-${tab.key}`}
            >
              {isActive && <View style={styles.activeBar} />}
              <Text style={[styles.railIcon, isActive && styles.railIconActive]}>
                {tab.icon}
              </Text>
            </Pressable>
          );
        })}
      </View>
    </BlurView>
  );
}

const styles = StyleSheet.create({
  rail: {
    width: RAIL_WIDTH,
    height: '100%',
    borderRightWidth: 1,
    borderRightColor: colors.glass.border,
  },
  railInner: {
    flex: 1,
    paddingTop: 60,
    alignItems: 'center',
    backgroundColor: colors.glass.fill,
  },
  railTab: {
    width: '100%',
    height: 56,
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  railTabActive: {},
  activeBar: {
    position: 'absolute',
    left: 0,
    top: 12,
    bottom: 12,
    width: 3,
    backgroundColor: colors.accent.cyan,
    borderTopRightRadius: 2,
    borderBottomRightRadius: 2,
  },
  railIcon: {
    fontSize: 24,
    color: colors.text.secondary,
  },
  railIconActive: {
    color: colors.accent.cyan,
  },
});
```

**Step 4: Rewrite RootNavigator**

```typescript
// navigation/RootNavigator.tsx
import React, { useState } from 'react';
import { View, StyleSheet, StatusBar } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { LinearGradient } from 'expo-linear-gradient';
import { useLayoutContext } from '../state/layout';
import { FloatingDock } from '../components/dock/FloatingDock';
import { SideRail } from '../components/dock/SideRail';
import { VoiceScreen } from '../screens/tabs/VoiceScreen';
import { ChatScreen } from '../screens/tabs/ChatScreen';
import { CommandScreen } from '../screens/tabs/CommandScreen';
import { SwarmScreen } from '../screens/tabs/SwarmScreen';
import { ConfigScreen } from '../screens/tabs/ConfigScreen';
import { colors } from '../theme/colors';

const TABS = [
  { key: 'voice', icon: '🎙', label: 'Voice' },
  { key: 'chat', icon: '💬', label: 'Chat' },
  { key: 'command', icon: '⚡', label: 'Command' },
  { key: 'swarm', icon: '🌐', label: 'Swarm' },
  { key: 'config', icon: '⚙', label: 'Config' },
];

const SCREENS: Record<string, React.ComponentType> = {
  voice: VoiceScreen,
  chat: ChatScreen,
  command: CommandScreen,
  swarm: SwarmScreen,
  config: ConfigScreen,
};

export default function RootNavigator() {
  const [activeTab, setActiveTab] = useState('voice');
  const { useSidebar } = useLayoutContext();
  const Screen = SCREENS[activeTab];

  return (
    <SafeAreaProvider>
      <StatusBar barStyle="light-content" translucent backgroundColor="transparent" />
      <LinearGradient
        colors={[colors.base.spaceBlack, colors.base.midnightBlue]}
        style={styles.root}
      >
        <View style={styles.content}>
          {useSidebar && (
            <SideRail
              tabs={TABS}
              activeTab={activeTab}
              onTabPress={setActiveTab}
            />
          )}
          <View style={styles.screen} testID={`${activeTab}-screen`}>
            <Screen />
          </View>
        </View>
        {!useSidebar && (
          <FloatingDock
            tabs={TABS}
            activeTab={activeTab}
            onTabPress={setActiveTab}
          />
        )}
      </LinearGradient>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  content: { flex: 1, flexDirection: 'row' },
  screen: { flex: 1 },
});
```

**Step 5: Create placeholder screens**

Create minimal placeholder screens for VoiceScreen, CommandScreen, SwarmScreen, ConfigScreen:

```typescript
// screens/tabs/VoiceScreen.tsx
import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { colors } from '../../theme/colors';

export function VoiceScreen() {
  return (
    <View style={styles.container} testID="voice-screen">
      <Text style={styles.title}>GUAPPA</Text>
      <Text style={styles.subtitle}>Voice mode coming in M4</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  title: { fontSize: 24, color: colors.text.primary, fontWeight: '700' },
  subtitle: { fontSize: 14, color: colors.text.tertiary, marginTop: 8 },
});
```

Repeat similarly for CommandScreen, SwarmScreen, ConfigScreen with appropriate placeholder text and testIDs (`command-screen`, `swarm-screen`, `config-screen`).

**Step 6: Verify build and test navigation**

```bash
cd mobile-app && npx tsc --noEmit
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Manual test: open app, verify 5 tabs in floating dock, tap each to switch screens.

**Step 7: Commit**

```bash
git add -A
git commit -m "feat(ui): add floating dock navigation with 5 tabs (Voice, Chat, Command, Swarm, Config)"
```

### Task M1.5: Basic Chat Screen (glass bubbles, no streaming yet)

**Files:**
- Rewrite: `mobile-app/src/screens/tabs/ChatScreen.tsx`
- Create: `mobile-app/src/components/chat/MessageBubble.tsx`
- Create: `mobile-app/src/components/chat/ChatInputBar.tsx`

This task implements the basic chat UI with glass-styled message bubbles and the input bar. Streaming will be added in M2 when the provider router is ready. Messages are stored in local state for now (AsyncStorage persistence in M1.6).

**Step 1:** Create `MessageBubble.tsx` with glass tinting (cool for GUAPPA, warm for user), entry animations via reanimated (SlideInLeft/Right + FadeIn), markdown rendering, and testID per message (`chat-message-user-{n}` / `chat-message-assistant-{n}`).

**Step 2:** Create `ChatInputBar.tsx` with glass-styled text input, attachment button (left), send/mic toggle button (right), multiline support (max 4 lines). TestIDs: `chat-input`, `chat-send-button`, `chat-mic-button`.

**Step 3:** Rewrite `ChatScreen.tsx` — glass header with "Chat" title, FlatList of MessageBubble components, ChatInputBar at bottom. Empty state shows "Start a conversation" text. Send button calls MessageBus.publish with UserMessage. TestID: `chat-screen`.

**Step 4:** Verify on emulator: type message, see user bubble. No AI response yet (wired in M2).

**Step 5: Commit**

```bash
git add -A
git commit -m "feat(ui): add glass-styled chat screen with message bubbles and input bar"
```

### Task M1.6: Chat Persistence (AsyncStorage)

**Files:**
- Modify: `mobile-app/src/screens/tabs/ChatScreen.tsx`

**Step 1:** Add AsyncStorage load/save for messages array (key: `guappa:chat:messages:v1`). Load on mount, save on each new message with 300ms debounce.

**Step 2:** Verify on emulator: send message, kill app, reopen, messages persist.

**Step 3: Commit**

```bash
git add -A
git commit -m "feat(chat): persist chat messages to AsyncStorage"
```

### Task M1.7: M1 Emulator Validation

**Step 1: Full build and install**

```bash
cd mobile-app/android && ./gradlew clean assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Step 2: Run existing Maestro smoke tests**

```bash
maestro test mobile-app/.maestro/smoke_navigation.yaml
```

Update smoke test if needed to match new 5-tab navigation.

**Step 3: Manual verification checklist**

- [ ] App launches with Voice screen as default
- [ ] Floating dock visible with 5 icons
- [ ] Tap each dock tab — screen switches
- [ ] Active tab icon is highlighted
- [ ] Chat screen: type and send message — user bubble appears
- [ ] Chat screen: kill and reopen — messages persist
- [ ] Foreground notification shows "Guappa is active"
- [ ] No ZeroClaw/MobileClaw text visible anywhere

**Step 4: Commit milestone tag**

```bash
git add -A
git commit -m "milestone(M1): foundation + basic UI shell complete"
git tag m1-foundation
```

---

## M2: Provider Router + Config Screen

**Goal:** Dynamic model fetching from 20+ providers, capability-based selection, and the capability-first configuration screen with smart defaults.

**Worktree:** `git worktree add ../guappa-m2 -b milestone/m2-provider-router`

### Task M2.1: Provider Interface & OpenAI-Compatible Base

**Files:**
- Create: `mobile-app/android/app/src/main/java/com/guappa/app/providers/Provider.kt`
- Create: `mobile-app/android/app/src/main/java/com/guappa/app/providers/OpenAICompatibleProvider.kt`
- Create: `mobile-app/android/app/src/main/java/com/guappa/app/providers/ProviderRouter.kt`
- Create: `mobile-app/android/app/src/main/java/com/guappa/app/providers/CapabilityType.kt`

**Step 1:** Define `CapabilityType` enum: TEXT_CHAT, VISION, IMAGE_GENERATION, VIDEO_GENERATION, AUDIO_STT, AUDIO_TTS, EMBEDDING, CODE, TOOL_USE, STREAMING, SEARCH, REASONING.

**Step 2:** Define `Provider` interface with: `id`, `name`, `fetchModels()`, `chat(messages, tools)`, `streamChat(messages, tools): Flow<String>`, `healthCheck()`.

**Step 3:** Implement `OpenAICompatibleProvider` as base class handling 13 providers (OpenAI, DeepSeek, Mistral, xAI, Groq, Fireworks, Perplexity, OpenRouter, Qwen, Moonshot, Venice, LM Studio, Ollama) with shared HTTP client (OkHttp), model fetching via `/v1/models`, and streaming via SSE.

**Step 4:** Implement `ProviderRouter` with: capability-to-provider mapping, model cache (1h TTL), fallback chain, cost tracking.

**Step 5:** Verify build.

**Step 6: Commit**

```bash
git commit -m "feat(providers): add Provider interface, OpenAI-compatible base, and ProviderRouter"
```

### Task M2.2: Anthropic & Google Providers

**Files:**
- Create: `mobile-app/android/app/src/main/java/com/guappa/app/providers/AnthropicProvider.kt`
- Create: `mobile-app/android/app/src/main/java/com/guappa/app/providers/GoogleGeminiProvider.kt`

**Step 1:** Implement Anthropic provider (custom headers: x-api-key, anthropic-version, different message format).

**Step 2:** Implement Google Gemini provider (completely different API — generateContent endpoint, different message schema).

**Step 3:** Register both in ProviderRouter factory.

**Step 4: Commit**

```bash
git commit -m "feat(providers): add Anthropic and Google Gemini providers"
```

### Task M2.3: Wire Provider to Chat (Streaming)

**Files:**
- Modify: `mobile-app/android/app/src/main/java/com/guappa/app/agent/GuappaOrchestrator.kt`
- Modify: `mobile-app/android/app/src/main/java/com/guappa/app/GuappaAgentModule.kt`
- Create: `mobile-app/src/components/chat/StreamingBubble.tsx`
- Create: `mobile-app/src/components/chat/TypingIndicator.tsx`
- Modify: `mobile-app/src/screens/tabs/ChatScreen.tsx`

**Step 1:** Wire orchestrator's handleMessage(UserMessage) to call `providerRouter.streamChat()` and publish streamed tokens via MessageBus as AgentMessage(isStreaming=true).

**Step 2:** Expose streaming via TurboModule/NativeModule: `sendMessage(text): void` and subscribe to agent responses via event emitter.

**Step 3:** Create `TypingIndicator.tsx` — mini pulsing orb (32px, animated scale 0.8-1.2, 1s period).

**Step 4:** Create `StreamingBubble.tsx` — glass bubble that appears on first token, text fills token-by-token, bubble resizes with spring animation, glow cursor (cyan 2px, pulsing) fades on completion.

**Step 5:** Update ChatScreen to show TypingIndicator → StreamingBubble flow.

**Step 6:** Test on emulator with a real provider API key (entered at runtime via Config screen, NOT committed).

**Step 7: Commit**

```bash
git commit -m "feat(chat): wire streaming provider responses with glass bubbles and glow cursor"
```

### Task M2.4: Config Screen — Capability-First Layout

**Files:**
- Rewrite: `mobile-app/src/screens/tabs/ConfigScreen.tsx`
- Create: `mobile-app/src/components/glass/GlassCard.tsx`
- Create: `mobile-app/src/components/glass/GlassDropdown.tsx`
- Create: `mobile-app/src/components/glass/GlassInput.tsx`
- Create: `mobile-app/src/components/glass/GlassSlider.tsx`
- Create: `mobile-app/src/components/glass/GlassToggle.tsx`
- Create: `mobile-app/src/components/glass/GlassButton.tsx`
- Create: `mobile-app/src/components/glass/CollapsibleSection.tsx`

**Step 1:** Build glass component library (GlassCard, GlassDropdown, GlassInput, GlassSlider, GlassToggle, GlassButton, CollapsibleSection) with consistent liquid glass styling per Phase 12 spec.

**Step 2:** Build ConfigScreen with capability-first sections:
- "How GUAPPA Thinks" — text provider/model dropdowns, fallback, temperature slider, budget
- "How GUAPPA Sees" — vision/image/video provider selectors
- "How GUAPPA Speaks & Listens" — STT/TTS engine selectors (placeholder, wired in M4)
- "How GUAPPA Connects" — channel tiles (placeholder, wired in M7)
- "What GUAPPA Can Do" — tool toggles (placeholder, wired in M3)
- "What GUAPPA Remembers" — memory config (placeholder, wired in M5)
- "How GUAPPA Acts on Her Own" — proactive config (placeholder, wired in M6)
- "Local Intelligence" — on-device model management
- "Permissions Summary" — all Android permissions with grant flow
- "Download Debug Info" — bottom action card

Each section: collapsible glass card, expand/collapse with spring animation.

**Step 3:** Wire "How GUAPPA Thinks" to ProviderRouter via native module: provider selection updates StateFlow → next chat request uses new provider.

**Step 4:** Implement smart defaults:
- Auto-detect if any API key is configured
- If no provider configured → show setup prompt in "How GUAPPA Thinks" section
- Default temperature: 0.7
- Default budget: $5/day

**Step 5:** Test on emulator: open Config, select a provider, enter API key, go to Chat, send message, verify response comes from selected provider.

**Step 6: Commit**

```bash
git commit -m "feat(config): add capability-first configuration screen with glass components"
```

### Task M2.5: Download Debug Info

**Files:**
- Create: `mobile-app/src/hooks/useDebugInfo.ts`
- Modify: `mobile-app/src/screens/tabs/ConfigScreen.tsx`
- Modify: `mobile-app/android/app/src/main/java/com/guappa/app/GuappaAgentModule.kt`

**Step 1:** Add Kotlin method `collectDebugInfo(): String` that:
- Collects app version, build number, device model, OS version
- Collects SoC/NPU/GPU/RAM info
- Collects application logs (last 7 days)
- Snapshots current config (API keys REDACTED with `***REDACTED***`)
- Snapshots provider health status
- Snapshots crash logs (if any)
- Snapshots memory stats, swarm state, sessions, permissions
- Packages as ZIP: `guappa-debug-YYYY-MM-DD-HHmmss.zip`
- Returns file path

**Step 2:** Create `useDebugInfo.ts` hook: calls native module, shows loading overlay, triggers Android share sheet on completion, shows success toast.

**Step 3:** Wire to ConfigScreen's "Download Debug Info" card at bottom.

**Step 4:** Test on emulator: tap button, verify ZIP created, share sheet opens.

**Step 5: Commit**

```bash
git commit -m "feat(config): add Download Debug Info with redacted ZIP export and share sheet"
```

### Task M2.6: M2 Emulator Validation

**Step 1:** Full build, install, run Maestro smoke test.

**Step 2:** Manual verification:
- [ ] Config screen: all 9 capability sections visible and collapsible
- [ ] Select text provider (e.g., OpenAI) → enter API key → model list fetches
- [ ] Select model → go to Chat → send message → streaming response appears
- [ ] Temperature slider updates value
- [ ] Download Debug Info → ZIP created → share sheet opens
- [ ] No crashes on rapid section expand/collapse

**Step 3: Commit milestone**

```bash
git commit -m "milestone(M2): provider router + config screen complete"
git tag m2-provider-router
```

---

## M3: Tool Engine + Onboarding

**Goal:** 69 tools with permission management, first-run onboarding wizard, smart defaults, and local model auto-download prompt.

**Worktree:** `git worktree add ../guappa-m3 -b milestone/m3-tools-onboarding`

### Task M3.1: Tool Interface & Registry

**Files:**
- Create: `mobile-app/android/app/src/main/java/com/guappa/app/tools/Tool.kt`
- Create: `mobile-app/android/app/src/main/java/com/guappa/app/tools/ToolEngine.kt`
- Create: `mobile-app/android/app/src/main/java/com/guappa/app/tools/ToolRegistry.kt`
- Create: `mobile-app/android/app/src/main/java/com/guappa/app/tools/ToolResult.kt`

**Step 1:** Define Tool interface: `name`, `description`, `parametersSchema: JsonObject`, `isAvailable(): Boolean`, `execute(params: JsonObject): ToolResult`.

**Step 2:** Define ToolResult: `Success(content, data, attachments)`, `Error(message, code, retryable)`, `NeedsApproval(description, type)`.

**Step 3:** Implement ToolEngine: receives tool calls from LLM response, validates parameters, checks permissions, executes, returns results.

**Step 4:** Implement ToolRegistry: factory for all 69 tools, filtered by availability (permissions granted).

**Step 5: Commit**

```bash
git commit -m "feat(tools): add Tool interface, ToolEngine, and ToolRegistry"
```

### Task M3.2: Core Tools (MVP set — 15 tools)

Implement the most impactful tools first:

**Device:** set_alarm, send_sms, read_sms, place_call, get_battery, get_contacts
**App:** launch_app, open_browser, set_timer, share_text
**Web:** web_fetch, web_search (using Brave Search API)
**AI:** image_analyze (via vision provider), calculator, translation

Each tool:
1. Implement the Tool interface
2. Declare required Android permissions
3. Add JSON schema for parameters
4. Register in ToolRegistry
5. Test individually

**Step 1-15:** Implement each tool (one per step, with tests).

**Step 16: Commit**

```bash
git commit -m "feat(tools): implement 15 core tools (device, app, web, AI)"
```

### Task M3.3: Remaining Tools (54 tools)

Implement remaining tools in categories:
- Device: camera, sensors, clipboard, flashlight, volume, WiFi, Bluetooth, NFC
- App Control: music, email, maps, settings navigation, intents
- Social: Twitter, Instagram, Telegram, WhatsApp deep links
- Automation: AppFunctions, UI Automation, screenshot, form filler, notification reader, app navigator
- File: read, write, search, document picker, PDF extraction
- Web: web_scrape, browser_session
- AI: OCR, code_interpreter, QR, barcode, summarize, image_generate
- System: shell (sandboxed), system_info, package_manager, wifi_manager

**Step 1-54:** Implement each tool.

**Step 55: Commit**

```bash
git commit -m "feat(tools): implement remaining 54 tools (69 total)"
```

### Task M3.4: Wire Tools to Orchestrator

**Files:**
- Modify: `mobile-app/android/app/src/main/java/com/guappa/app/agent/GuappaOrchestrator.kt`

**Step 1:** When provider response contains tool_calls, parse them, route to ToolEngine, collect results, feed back into conversation, and continue the ReAct loop (up to maxReActIterations).

**Step 2:** Tool execution results appear in chat as structured cards (tool name + result preview).

**Step 3:** Test on emulator: "Set an alarm for 7 AM" → verify alarm tool called → confirmation in chat.

**Step 4: Commit**

```bash
git commit -m "feat(agent): wire ToolEngine to orchestrator ReAct loop"
```

### Task M3.5: Onboarding — First-Run Wizard

**Files:**
- Create: `mobile-app/src/screens/OnboardingScreen.tsx`
- Create: `mobile-app/src/components/onboarding/WelcomeStep.tsx`
- Create: `mobile-app/src/components/onboarding/ProviderSetupStep.tsx`
- Create: `mobile-app/src/components/onboarding/ModelDownloadStep.tsx`
- Create: `mobile-app/src/components/onboarding/PermissionsStep.tsx`
- Modify: `mobile-app/src/navigation/RootNavigator.tsx`

**Step 1:** Create OnboardingScreen as a multi-step wizard (4 steps):

**Welcome Step:**
- GUAPPA logo / plasma orb animation (simplified)
- "Meet GUAPPA — your AI companion" heading
- Brief feature highlights (3 bullet points)
- "Get Started" glass button

**Provider Setup Step:**
- "Choose how GUAPPA thinks" heading
- Two clear paths:
  - "Use cloud AI" → show top 3 providers (OpenAI, Anthropic, Google) with simple API key input
  - "Use on-device AI (free, private)" → show recommended small model for device
- Smart recommendation based on device specs (RAM, SoC)
- Skip button for advanced users

**Model Download Step** (shown if on-device selected or no cloud key entered):
- Recommended small model (e.g., Gemma 2B, Phi-3 Mini) based on device RAM
- Download size, estimated performance
- "Download" glass button with progress bar
- "Skip for now" option (but warn: "GUAPPA needs a model to chat")

**Permissions Step:**
- "GUAPPA needs a few permissions to help you"
- Group permissions by impact (Essential / Recommended / Optional):
  - Essential: Notifications, Internet
  - Recommended: Microphone (voice), Contacts, Calendar
  - Optional: Camera, SMS, Call Log, Location
- Each with clear explanation of WHY it's needed
- "Grant" buttons per permission or "Grant All Essential" button

**Step 2:** In RootNavigator: check AsyncStorage for `guappa:onboarding:completed:v1`. If not set, show OnboardingScreen instead of dock navigation. On completion, set flag and transition to main app.

**Step 3:** If user completes onboarding without setting any provider AND without downloading a local model → redirect to Config screen with "How GUAPPA Thinks" section expanded and a prompt banner: "Select a model to start chatting."

**Step 4:** Test on emulator: fresh install → onboarding appears → complete all steps → main app loads → re-launch → onboarding skipped.

**Step 5: Commit**

```bash
git commit -m "feat(onboarding): add first-run wizard with provider setup, model download, and permissions"
```

### Task M3.6: Config Screen — Tools Section

**Files:**
- Modify: `mobile-app/src/screens/tabs/ConfigScreen.tsx`

**Step 1:** Wire "What GUAPPA Can Do" section: show tool categories with toggle switches per tool, permission status pills next to tools that need specific permissions.

**Step 2: Commit**

```bash
git commit -m "feat(config): wire tool toggles in 'What GUAPPA Can Do' section"
```

### Task M3.7: M3 Emulator Validation

- [ ] Fresh install: onboarding wizard appears
- [ ] Cloud provider path: enter API key → model list loads → chat works
- [ ] On-device path: download local model → chat works (slower)
- [ ] Permissions: grant each → status pills turn green
- [ ] Chat: "Set an alarm for 7 AM" → alarm set → confirmation in chat
- [ ] Chat: "Search the web for weather in Moscow" → web results in chat
- [ ] Config: toggle tools on/off → reflected in next chat tool availability
- [ ] No model set → config screen prompts to set one

```bash
git commit -m "milestone(M3): tool engine + onboarding complete"
git tag m3-tools-onboarding
```

---

## M4: Voice Pipeline + Voice Screen

**Goal:** Full STT/TTS pipeline with wake word, and the plasma orb voice visualization.

**Worktree:** `git worktree add ../guappa-m4 -b milestone/m4-voice`

### Task M4.1: STT Integration (whisper.rn)

Already has `whisper.rn` in dependencies. Wire STT pipeline:
- Audio capture via `react-native-live-audio-stream`
- VAD (voice activity detection) to know when user stops speaking
- whisper.rn transcription → text → send to agent via MessageBus

### Task M4.2: TTS Integration

- Implement TTS engine selection (Picovoice Orca primary, Android TTS fallback)
- Streaming TTS: buffer LLM output by sentence → start TTS on each sentence
- Audio focus management (pause on phone call)

### Task M4.3: Plasma Orb (Skia Shader)

**Files:**
- Create: `mobile-app/src/components/plasma/PlasmaOrb.tsx`
- Create: `mobile-app/src/components/plasma/shaders/plasma.glsl`
- Create: `mobile-app/src/components/plasma/VoiceWaveformRing.tsx`

**Step 1:** Install `@shopify/react-native-skia`.

**Step 2:** Implement plasma orb GLSL shader with 3 layered simplex noise fields, time-varying colors, and state-dependent behavior (idle/listening/processing/speaking/error per Phase 12 spec).

**Step 3:** Implement VoiceWaveformRing — Skia Path wrapping the orb, deformed by audio amplitude.

**Step 4:** Wire orb state to voice pipeline state machine.

### Task M4.4: Voice Screen (Full Implementation)

**Files:**
- Rewrite: `mobile-app/src/screens/tabs/VoiceScreen.tsx`

Implement the full Voice screen per Phase 12 spec:
- Fullscreen, no header
- Plasma orb centered (45% width)
- Waveform ring around orb
- Status elements (GUAPPA wordmark, connection dot, state label, interim transcript)
- Tap to talk, long-press for continuous mode, swipe up for mini-chat preview

### Task M4.5: Voice-to-Chat Sync

- Voice conversations appear in Chat tab with microphone/speaker badges
- Tapping speaker badge replays TTS

### Task M4.6: Config — Voice Section

Wire "How GUAPPA Speaks & Listens" in ConfigScreen:
- STT engine selector + model download
- TTS engine selector + voice preview
- Wake word toggle + sensitivity slider

### Task M4.7: M4 Emulator Validation

- [ ] Voice screen: orb renders at 60fps
- [ ] Tap orb → listening state (orb contracts, cyan)
- [ ] Speak → interim transcript appears
- [ ] Processing → violet orb with orbiting dots
- [ ] Response → orb expands, TTS plays, orb animates with audio
- [ ] Voice messages sync to Chat tab with badges
- [ ] Config: change STT/TTS engine → next interaction uses new engine

```bash
git commit -m "milestone(M4): voice pipeline + plasma orb voice screen complete"
git tag m4-voice
```

---

## M5: Memory & Context + Command Center

**Goal:** 5-tier memory system with auto-summarization, and the Command Center screen.

**Worktree:** `git worktree add ../guappa-m5 -b milestone/m5-memory-command`

### Task M5.1: Room Database Setup

**Files:**
- Create: `mobile-app/android/app/src/main/java/com/guappa/app/memory/GuappaDatabase.kt`
- Create: entities for Sessions, Messages, Tasks, MemoryFacts, Episodes

### Task M5.2: Memory Tiers (Working → Short-Term → Long-Term → Episodic → Semantic)

Implement per Phase 7 spec.

### Task M5.3: Auto-Summarization

Implement incremental summarization triggered at 80% context usage. Use cheap model (Gemini Flash, GPT-4o-mini). Non-blocking (runs in background coroutine).

### Task M5.4: Embedding & Vector Search (RAG)

- all-MiniLM-L6-v2 via ONNX Runtime Mobile
- Store embeddings as BLOBs in SQLite
- Cosine similarity search for top-K retrieval
- Inject retrieved memories into LLM context

### Task M5.5: Command Center Screen

**Files:**
- Rewrite: `mobile-app/src/screens/tabs/CommandScreen.tsx`
- Create: `mobile-app/src/components/command/CollapsibleSection.tsx` (reuse from glass lib)
- Create: `mobile-app/src/components/command/TaskCard.tsx`
- Create: `mobile-app/src/components/command/ScheduleTimeline.tsx`
- Create: `mobile-app/src/components/command/TriggerGrid.tsx`
- Create: `mobile-app/src/components/command/MemoryList.tsx`
- Create: `mobile-app/src/components/command/SessionInfo.tsx`
- Create: `mobile-app/src/components/command/ContextBudgetChart.tsx`

Implement per Phase 12 spec:
- Vertical feed (phone) / mission control grid (tablet)
- 5 collapsible sections: Active Tasks, Scheduled, Triggers, Memory, Sessions
- Skia StatusRing for task progress
- Context budget arc chart (Skia)

### Task M5.6: Config — Memory Section

Wire "What GUAPPA Remembers" in ConfigScreen.

### Task M5.7: M5 Emulator Validation

- [ ] Tell agent "Remember that my dog's name is Rex" → fact appears in Memory section
- [ ] Long conversation (30+ messages) → context compaction triggers → chat continues normally
- [ ] Command Center: tasks show with status rings
- [ ] Memory section: search, filter by category, delete facts
- [ ] Sessions section: shows active sessions with context budget chart

```bash
git commit -m "milestone(M5): memory system + command center complete"
git tag m5-memory-command
```

---

## M6: Proactive Agent + Push Notifications

**Goal:** Agent-initiated messages, event triggers, push notifications with notification channels.

**Worktree:** `git worktree add ../guappa-m6 -b milestone/m6-proactive`

### Tasks:
- M6.1: Create 6 notification channels (guappa_chat, guappa_tasks, guappa_questions, guappa_alerts, guappa_proactive, guappa_service)
- M6.2: Implement ProactiveEngine — event triggers (SMS, missed call, calendar, email, low battery, morning briefing, evening summary)
- M6.3: Implement push notification delivery (MessagingStyle for chat, BigTextStyle for tasks, inline reply via RemoteInput)
- M6.4: Smart timing (quiet hours, DND, cooldown)
- M6.5: Wire triggers to Command Center triggers section (toggle/configure)
- M6.6: Config — Proactive section

```bash
git commit -m "milestone(M6): proactive agent + push notifications complete"
git tag m6-proactive
```

---

## M7: Channel Hub + Config Channels

**Goal:** 8 messenger channels: Telegram, Discord, Slack, WhatsApp, Signal, Matrix, Email, SMS.

**Worktree:** `git worktree add ../guappa-m7 -b milestone/m7-channels`

### Tasks:
- M7.1: Channel interface + factory
- M7.2: Telegram channel (long polling, inline keyboards, file support)
- M7.3: Discord channel (Gateway WebSocket, slash commands)
- M7.4: Slack, WhatsApp, Signal, Matrix, Email, SMS channels
- M7.5: Health check + auto-reconnect
- M7.6: Config — Channels section (tile grid, enable/disable, token input, health check button)
- M7.7: Allowlist per channel (deny by default)

```bash
git commit -m "milestone(M7): channel hub with 8 messenger integrations complete"
git tag m7-channels
```

---

## M8: Live Config (Hot-Reload)

**Goal:** Every setting change applies immediately without restarting the service.

**Worktree:** `git worktree add ../guappa-m8 -b milestone/m8-live-config`

### Tasks:
- M8.1: TurboModule interface (migrate from NativeModules to TurboModules via Codegen)
- M8.2: StateFlow config propagation (provider/model → next request, channel → reconnect, tool → re-filter, voice → swap engine)
- M8.3: Config change events flow back to UI
- M8.4: Verify all settings apply without restart

```bash
git commit -m "milestone(M8): live config with TurboModules + StateFlow complete"
git tag m8-live-config
```

---

## M9: World Wide Swarm + Swarm Screen

**Goal:** WWSP connector integration and the Swarm screen with live feed.

**Worktree:** `git worktree add ../guappa-m9 -b milestone/m9-swarm`

### Tasks:
- M9.1: SwarmConnector (HTTP REST + SSE for remote mode)
- M9.2: Agent identity (Ed25519 keypair in Android Keystore, DID generation)
- M9.3: Registration flow with anti-bot challenge solving
- M9.4: Task polling/receiving + execution
- M9.5: Peer messaging
- M9.6: Reputation tracking
- M9.7: Swarm Screen UI (status bar, connection toggle, live feed, filter pills)
- M9.8: Topology graph (Skia, tablet only)
- M9.9: Statistics modal

```bash
git commit -m "milestone(M9): World Wide Swarm connector + swarm screen complete"
git tag m9-swarm
```

---

## M10: Glass Design System + Final UI Polish

**Goal:** Apply liquid glass treatment consistently, add all animations, responsive layouts, final polish.

**Worktree:** `git worktree add ../guappa-m10 -b milestone/m10-polish`

### Tasks:
- M10.1: Install and configure custom fonts (Orbitron, Exo 2 — download from Google Fonts, add to assets)
- M10.2: Glass material Skia shader (noise grain overlay, refraction)
- M10.3: Dock morphing animation (glow blob slides between tabs)
- M10.4: Screen transition animations (shared element morph, orb scale-to-dock)
- M10.5: Parallax effects (gyroscope via expo-sensors on glass surfaces)
- M10.6: Glass Toast component (success/error/warning)
- M10.7: Tablet/automotive responsive pass — verify all 5 screens adapt (side rail, grid layouts, wider bubbles, topology graph)
- M10.8: Performance optimization — verify 60fps on all animations, add quality tiers for low-end devices
- M10.9: Accessibility pass — testIDs, accessibilityLabels, reduced motion, contrast
- M10.10: Empty states for all screens (per Phase 12 spec)

```bash
git commit -m "milestone(M10): glass design system + UI polish complete"
git tag m10-polish
```

---

## M11: Full Maestro Test Suite

**Goal:** 122 E2E flows covering all screens, backend integration, resilience, performance.

**Worktree:** `git worktree add ../guappa-m11 -b milestone/m11-testing`

### Tasks:
- M11.1: Navigation tests (6 flows)
- M11.2: Voice screen tests (12 flows)
- M11.3: Chat screen tests (16 flows)
- M11.4: Command Center tests (18 flows)
- M11.5: Swarm screen tests (14 flows)
- M11.6: Configuration screen tests (22 flows)
- M11.7: Backend integration tests (12 flows)
- M11.8: Resilience tests (10 flows)
- M11.9: Performance tests (6 flows)
- M11.10: Tablet/automotive tests (6 flows)
- M11.11: CI pipeline (GitHub Actions workflow)

All test flows per Phase 12 section 14 spec. Tests go in `maestro/flows/` directory (new location, organized by category).

```bash
git commit -m "milestone(M11): 122 Maestro E2E test flows + CI pipeline complete"
git tag m11-testing
```

---

## M12: Documentation

**Goal:** Complete Guappa documentation replacing all legacy ZeroClaw/MobileClaw docs.

**Worktree:** `git worktree add ../guappa-m12 -b milestone/m12-docs`

### Tasks:
- M12.1: Delete all legacy docs (per Phase 13 plan — 167 files)
- M12.2: Write new README.md (product overview, quick start, screenshots)
- M12.3: Write CLAUDE.md (updated for Guappa agent protocol)
- M12.4: Write CONTRIBUTING.md
- M12.5: Write docs/getting-started/ (installation, first-setup, quick-tour, FAQ)
- M12.6: Write docs/guides/ (15 files — model selection, voice setup, channel setup, etc.)
- M12.7: Write docs/reference/ (13 files — providers, tools, channels, permissions, etc.)
- M12.8: Write docs/architecture/ (8 files — agent core, provider router, tool engine, etc.)
- M12.9: Write docs/development/ (5 files — setup, building, testing, maestro, contributing)
- M12.10: Final review pass — all links work, no broken references, no ZeroClaw mentions

```bash
git commit -m "milestone(M12): complete Guappa documentation"
git tag m12-docs
```

---

## Runtime Configuration (NOT committed to git)

The following values are entered by users at runtime via the Config screen and stored in Android Keystore / EncryptedSharedPreferences:

- Provider API keys (OpenAI, Anthropic, Google, etc.)
- Telegram bot token (user-provided)
- Brave Search API key (user-provided)
- Discord bot token
- Slack bot token
- WhatsApp Cloud API token
- Email credentials (IMAP/SMTP)
- WWSP connector URL and keypair

**These values NEVER appear in source code, git history, documentation, or debug exports (always redacted).**

---

## Subagent Dispatch Strategy

For milestones with independent tasks, dispatch parallel subagents:

| Milestone | Parallel Opportunities |
|-----------|----------------------|
| M0 | M0.1-M0.3 can run in parallel (Kotlin, TypeScript, Maestro renames) |
| M1 | M1.1-M1.3 (backend) parallel with M1.4-M1.5 (UI) |
| M2 | M2.1-M2.2 (providers) parallel with M2.4 (glass components) |
| M3 | M3.2-M3.3 (tools) parallel with M3.5 (onboarding) |
| M4 | M4.1-M4.2 (STT/TTS) parallel with M4.3 (plasma orb shader) |
| M5 | M5.1-M5.4 (memory backend) parallel with M5.5 (command center UI) |
| M7 | M7.2-M7.4 (individual channels) can parallelize |
| M11 | M11.1-M11.10 (test categories) can parallelize |
| M12 | M12.5-M12.9 (doc sections) can parallelize |

---

## Risk Mitigations

| Risk | Mitigation |
|------|------------|
| Skia shader perf on low-end | Quality tiers: high/medium/low. Test on API 30 emulator. |
| 69 tools too many for initial release | MVP: ship with 15 core tools (M3.2), add rest incrementally |
| Onboarding drop-off | Minimize steps, smart defaults, allow skip with gentle nudge |
| Local model too large for some devices | Recommend smallest viable model per RAM tier. < 3GB RAM → cloud only. |
| Token/key leak in debug export | All secrets redacted in collectDebugInfo. Automated test verifies. |
| Maestro tests flaky on CI | Run critical 30 on every PR, full suite nightly. Retry once on failure. |

---

## Definition of Done (Full Product)

The product is ready for Play Store when ALL milestones pass:

1. M0: Zero ZeroClaw/MobileClaw references in codebase
2. M1-M10: All features working on emulator (API 34 + API 30)
3. M11: 122/122 Maestro tests pass
4. M12: Documentation complete and all links valid
5. Performance: cold start < 2s, orb 60fps, tab switch < 300ms
6. Security: no tokens in git history, all secrets in Keystore
7. Accessibility: all screens navigable with TalkBack
8. First-run: non-technical user can set up and chat within 3 minutes
