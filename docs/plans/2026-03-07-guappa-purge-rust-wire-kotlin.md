# GUAPPA: Purge Rust, Wire Kotlin Backend End-to-End

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove all Rust/JNI/ZeroClaw/MobileClaw artifacts and wire the existing Kotlin backend (GuappaOrchestrator + ProviderRouter + ToolEngine) directly to the React Native UI so every feature works for real with a real LLM.

**Architecture:** Pure Kotlin backend accessed via NativeModule bridge. No Rust daemon, no localhost gateway, no JNI. GuappaAgentModule exposes `sendMessage()` which calls GuappaOrchestrator → ProviderRouter → real LLM API. Tools execute on-device via ToolEngine. Events flow back to JS via React Native EventEmitter.

**Tech Stack:** Kotlin (Android), React Native 0.81, Expo 54, OkHttp, NativeModules

---

## Task 1: Delete all Rust/JNI/ZeroClaw files

**Files to delete:**
- `mobile-app/android/app/src/main/jniLibs/` (entire directory — all libzeroclaw.so binaries)
- `mobile-app/android/app/src/main/java/com/guappa/app/ZeroClawBackend.kt`
- `mobile-app/android/app/src/main/java/com/guappa/app/ZeroClawDaemonService.kt`
- `mobile-app/android/app/src/main/java/com/guappa/app/ZeroClawDaemonModule.kt`
- `mobile-app/android/app/src/main/java/com/guappa/app/ZeroClawDaemonPackage.kt`
- `mobile-app/android/build-backend.sh`
- `mobile-app/android/app/build.gradle` — remove jniLibs task references (lines referencing buildGuappaAndroidBinaries, prepareGuappaAndroidAssets)

**Files to modify:**
- `mobile-app/android/app/src/main/AndroidManifest.xml` — Remove ZeroClawDaemonService entry entirely
- `mobile-app/android/app/src/main/java/com/guappa/app/MainApplication.kt` — Remove ZeroClawDaemonPackage if referenced

**Step 1:** Delete the files listed above.

**Step 2:** Remove ZeroClawDaemonService from AndroidManifest.xml (the `<service android:name=".ZeroClawDaemonService" .../>` block).

**Step 3:** In `MainApplication.kt`, remove any import/reference to ZeroClawDaemonPackage.

**Step 4:** In `app/build.gradle`, remove any Gradle task definitions that reference jni, rust, cargo, backend binaries, or zeroclaw. Keep the standard Android build config.

**Step 5:** Run `./gradlew compileDebugKotlin` — fix any compile errors from missing ZeroClaw references.

**Step 6:** Commit: `chore: delete all Rust/JNI/ZeroClaw files and references`

---

## Task 2: Rewrite GuappaAgentModule as pure Kotlin NativeModule

**Files to modify:**
- `mobile-app/android/app/src/main/java/com/guappa/app/GuappaAgentModule.kt`

**Current state:** GuappaAgentModule delegates to ZeroClawDaemonService via JNI. All methods (startDaemon, processMessage, executeTool) go through the Rust daemon.

**New design:** GuappaAgentModule talks directly to GuappaAgentService's singleton orchestrator. No daemon, no JNI, no gateway URL.

**Step 1:** Rewrite `GuappaAgentModule.kt` to:

```kotlin
package com.guappa.app

import android.content.Intent
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.guappa.app.agent.*
import com.guappa.app.providers.*
import kotlinx.coroutines.*

class GuappaAgentModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun getName() = "GuappaAgent"

    @ReactMethod
    fun startAgent(config: ReadableMap, promise: Promise) {
        try {
            // Start the foreground service (creates orchestrator singleton)
            val intent = Intent(reactApplicationContext, GuappaAgentService::class.java)
            reactApplicationContext.startService(intent)

            // Configure provider from JS config
            val orchestrator = GuappaAgentService.orchestrator
            if (orchestrator != null) {
                val router = orchestrator.providerRouter
                    ?: ProviderRouter()

                // Register providers from config
                val provider = config.getString("provider") ?: "openai"
                val apiKey = config.getString("apiKey") ?: ""
                val apiUrl = config.getString("apiUrl") ?: ""
                val model = config.getString("model") ?: ""

                if (apiKey.isNotEmpty()) {
                    registerProvider(router, provider, apiKey, apiUrl, model)
                }

                // Inject router + context into orchestrator
                orchestrator.configure(router, reactApplicationContext)
            }

            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("START_FAILED", e.message)
        }
    }

    @ReactMethod
    fun sendMessage(text: String, sessionId: String?, promise: Promise) {
        val orchestrator = GuappaAgentService.orchestrator
        if (orchestrator == null) {
            promise.reject("AGENT_NOT_RUNNING", "Agent not started")
            return
        }

        scope.launch {
            try {
                val bus = GuappaAgentService.messageBus ?: throw Exception("No message bus")

                // Collect the response from MessageBus
                val responseDeferred = CompletableDeferred<String>()

                val job = launch {
                    bus.messages.collect { msg ->
                        if (msg is BusMessage.AgentMessage && msg.isComplete) {
                            responseDeferred.complete(msg.text)
                            cancel()
                        }
                    }
                }

                // Publish user message to trigger orchestrator
                bus.publish(BusMessage.UserMessage(
                    text = text,
                    sessionId = sessionId ?: ""
                ))

                // Wait for response with timeout
                val response = withTimeout(120_000) {
                    responseDeferred.await()
                }

                job.cancel()
                promise.resolve(response)
            } catch (e: Exception) {
                promise.reject("SEND_FAILED", e.message)
            }
        }
    }

    @ReactMethod
    fun isAgentRunning(promise: Promise) {
        promise.resolve(GuappaAgentService.isRunning())
    }

    @ReactMethod
    fun stopAgent(promise: Promise) {
        val intent = Intent(reactApplicationContext, GuappaAgentService::class.java)
        reactApplicationContext.stopService(intent)
        promise.resolve(true)
    }

    @ReactMethod
    fun collectDebugInfo(promise: Promise) {
        // ... keep existing debug info collection ...
    }

    private fun registerProvider(
        router: ProviderRouter,
        providerName: String,
        apiKey: String,
        apiUrl: String,
        model: String
    ) {
        val provider: Provider = when (providerName) {
            "anthropic" -> AnthropicProvider(apiKey)
            "gemini" -> GoogleGeminiProvider(apiKey)
            else -> OpenAICompatibleProvider(
                name = providerName,
                apiKey = apiKey,
                baseUrl = apiUrl.ifEmpty { "https://openrouter.ai/api/v1" }
            )
        }
        router.registerProvider(providerName, provider, model)
    }
}
```

**Step 2:** Update `GuappaOrchestrator.kt` to add a `configure()` method:

```kotlin
fun configure(router: ProviderRouter, ctx: Context) {
    this.providerRouter = router
    this.context = ctx
    toolRegistry.registerCoreTools()
}
```

Make `providerRouter` and `context` vars instead of vals.

**Step 3:** Run `./gradlew compileDebugKotlin` — fix errors.

**Step 4:** Commit: `feat: rewrite GuappaAgentModule as pure Kotlin NativeModule`

---

## Task 3: Rewrite GuappaAgentPackage (remove ZeroClaw references)

**Files to modify:**
- `mobile-app/android/app/src/main/java/com/guappa/app/GuappaAgentPackage.kt`

**Step 1:** Ensure it only creates GuappaAgentModule (no ZeroClaw modules).

**Step 2:** Verify MainApplication.kt `getPackages()` only references GuappaAgentPackage, ConfigBridgePackage, and existing non-ZeroClaw packages. Remove any ZeroClaw package references.

**Step 3:** Compile. Commit: `chore: clean GuappaAgentPackage, remove ZeroClaw module refs`

---

## Task 4: Remove Rust daemon from TypeScript layer

**Files to modify:**
- `mobile-app/src/native/guappaAgent.ts` — Rewrite: remove daemon URL, gateway, health polling. Expose `sendMessage()`, `startAgent()`, `stopAgent()`, `isAgentRunning()`.
- `mobile-app/src/runtime/session.ts` — Rewrite: remove gateway mode, remove `runAgentTurnWithGateway()`, remove all `127.0.0.1:8000` references. New `runAgentTurn()` calls `NativeModules.GuappaAgent.sendMessage()`.
- `mobile-app/src/api/guappa.ts` — Remove `runGuappaAgent()`, `runGuappaAgentStream()`, all gateway HTTP calls. Keep only direct provider API calls if needed for non-agent features.
- `mobile-app/App.tsx` — Remove daemon startup logic (lines 80-200 that call startDaemon, waitForDaemonReady, isDaemonRunning, restartDaemon). Replace with `GuappaAgent.startAgent(config)`. Remove all `127.0.0.1:8000` references.
- `mobile-app/src/config.ts` — Remove `platformUrl`, `DEFAULT_GATEWAY_URL`, any port 8000 references.

**Step 1:** Rewrite `src/native/guappaAgent.ts`:
```typescript
import { NativeModules } from "react-native";
const { GuappaAgent } = NativeModules;

export async function startAgent(config: Record<string, string>): Promise<boolean> {
  return GuappaAgent.startAgent(config);
}
export async function sendMessage(text: string, sessionId?: string): Promise<string> {
  return GuappaAgent.sendMessage(text, sessionId ?? null);
}
export async function stopAgent(): Promise<boolean> {
  return GuappaAgent.stopAgent();
}
export async function isAgentRunning(): Promise<boolean> {
  return GuappaAgent.isAgentRunning();
}
export async function collectDebugInfo(): Promise<string> {
  return GuappaAgent.collectDebugInfo();
}
```

**Step 2:** Rewrite `src/runtime/session.ts` — strip to essentials:
```typescript
import { sendMessage } from "../native/guappaAgent";

export async function runAgentTurn(userPrompt: string, sessionId?: string): Promise<{ assistantText: string }> {
  const response = await sendMessage(userPrompt, sessionId);
  return { assistantText: response };
}
```

**Step 3:** Clean `App.tsx` — replace daemon startup with `startAgent(config)`.

**Step 4:** Remove gateway references from `src/api/guappa.ts`.

**Step 5:** Remove `platformUrl` from config.ts and state/guappa.ts.

**Step 6:** Compile TS: `npx tsc --noEmit`. Fix errors.

**Step 7:** Commit: `feat: remove Rust daemon from TS layer, wire to Kotlin NativeModule`

---

## Task 5: Wire ChatScreen to real backend

**Files to modify:**
- `mobile-app/src/screens/tabs/ChatScreen.tsx`

**Current state:** ChatScreen adds user message to state but never calls the backend. Pure UI stub.

**New design:** On send, call `runAgentTurn()` which calls NativeModules.GuappaAgent.sendMessage() → Kotlin orchestrator → real LLM.

**Step 1:** Add agent call to `handleSend`:
```typescript
import { runAgentTurn } from "../../runtime/session";

const handleSend = useCallback(async () => {
  const trimmed = draft.trim();
  if (!trimmed) return;
  setDraft("");

  const userMessage: Message = {
    id: `m_${Date.now()}`,
    role: "user",
    content: trimmed,
    timestamp: Date.now(),
  };

  setMessages(prev => {
    const next = [...prev, userMessage];
    saveMessages(next);
    return next;
  });

  // Call real agent
  try {
    const { assistantText } = await runAgentTurn(trimmed);
    const agentMessage: Message = {
      id: `m_${Date.now()}`,
      role: "assistant",
      content: assistantText,
      timestamp: Date.now(),
    };
    setMessages(prev => {
      const next = [...prev, agentMessage];
      saveMessages(next);
      return next;
    });
  } catch (e: any) {
    const errorMessage: Message = {
      id: `m_${Date.now()}`,
      role: "assistant",
      content: `Error: ${e.message}`,
      timestamp: Date.now(),
    };
    setMessages(prev => [...prev, errorMessage]);
  }
}, [draft, saveMessages]);
```

**Step 2:** Rebuild JS bundle + APK. Install on emulator.

**Step 3:** E2E test: Open chat, type "what is 2+2", verify real LLM response appears.

**Step 4:** Commit: `feat: wire ChatScreen to real Kotlin agent backend`

---

## Task 6: Clean remaining MobileClaw/ZeroClaw references in Kotlin

**Files to modify (search and replace):**
- `AndroidAgentToolsModule.kt` — Replace "MobileClaw" → "Guappa" in notification text
- `RuntimeAlwaysOnService.kt` — Replace "MobileClaw Runtime" → "Guappa Runtime"
- `RuntimeBridge.kt` — Replace "MobileClaw" → "Guappa" in channel names
- `ModelDownloaderModule.kt` — Remove ZeroClaw references
- Any remaining `.kt` files with "zeroclaw" or "mobileclaw" strings

**Step 1:** Search and replace across all Kotlin files.

**Step 2:** Compile: `./gradlew compileDebugKotlin`.

**Step 3:** Commit: `chore: replace all MobileClaw/ZeroClaw strings in Kotlin with Guappa`

---

## Task 7: Clean remaining references in TypeScript

**Files to modify:**
- All `.ts`/`.tsx` files containing "zeroclaw", "ZeroClaw", "mobileclaw", "MobileClaw", "daemon", "gateway", "8000"
- `src/state/guappa.ts` — Remove any gateway/daemon config keys
- `src/screens/tabs/IntegrationsScreen.tsx` — Remove ZeroClaw references
- `src/screens/tabs/ScheduledTasksScreen.tsx` — Remove gateway health checks
- Old Maestro tests that reference `com.mobileclaw.app` or gateway endpoints

**Step 1:** Global search-replace in `mobile-app/src/`:
- "ZeroClaw" → "Guappa"
- "zeroclaw" → "guappa"
- "MobileClaw" → "Guappa"
- "mobileclaw" → "guappa"
- Remove all "127.0.0.1:8000", "localhost:8000", "platformUrl" references

**Step 2:** Fix any broken imports or logic.

**Step 3:** Compile TS: `npx tsc --noEmit`.

**Step 4:** Commit: `chore: purge all ZeroClaw/MobileClaw/daemon references from TypeScript`

---

## Task 8: Clean Maestro tests

**Files to modify:**
- All `.maestro/*.yaml` files

**Step 1:** Remove all Maestro tests that test daemon health, gateway endpoints, or Rust-specific behavior:
- `embedded_daemon_test.yaml`
- `daemon_health_check.yaml`
- `daemon_context_memory.yaml`
- `gateway_integration_test.yaml`
- `backend_integration_test.yaml`
- `e2e_guappa_backend_flow.yaml`
- Any test referencing `127.0.0.1:8000`

**Step 2:** Update remaining tests: replace `com.mobileclaw.app` → `com.guappa.app` if any missed.

**Step 3:** Commit: `chore: remove Rust daemon Maestro tests, clean app IDs`

---

## Task 9: Clean GitHub Actions workflows

**Files to modify:**
- `.github/workflows/ci-run.yml` — Remove cargo build/test/clippy steps, remove build_android_jni.sh
- `.github/workflows/release.yml` — Remove Rust binary build, Docker, zeroclaw refs
- `.github/workflows/pub-docker-img.yml` — Remove or repurpose (no more Rust Docker)
- `.github/workflows/sec-audit.yml` — Remove cargo audit
- `.github/workflows/sec-codeql.yml` — Remove Rust CodeQL
- `.github/workflows/test-benchmarks.yml` — Remove cargo bench
- `.github/workflows/test-e2e.yml` — Remove build_android_jni.sh step
- `.github/workflows/feature-matrix.yml` — Remove Rust refs

**Step 1:** Edit each workflow: remove Rust/cargo/JNI steps. Keep Android build + Maestro test steps. Update naming from ZeroClaw to Guappa.

**Step 2:** Commit: `chore: remove Rust/cargo/JNI from all GitHub Actions workflows`

---

## Task 10: Clean root-level Rust artifacts (mobile-app scope only)

**Files to delete from mobile-app/:**
- `mobile-app/android/build-backend.sh` (if not already deleted)

**Files to check at repo root (outside mobile-app — user decides):**
- `build_android_jni.sh`
- `zeroclaw_install.sh`
- `Cargo.toml`, `Cargo.lock`
- `src/` (entire Rust source tree)

**Note:** The Rust source at repo root (`/Users/aostapenko/Work/mobileclaw/src/`) is the ZeroClaw server runtime. The user said to remove all old architecture. Delete root-level Rust build scripts that reference mobile/Android (build_android_jni.sh, zeroclaw_install.sh). Leave the core Rust runtime decision to the user.

**Step 1:** Delete `build_android_jni.sh` and `zeroclaw_install.sh` from repo root.

**Step 2:** Commit: `chore: remove Android JNI build scripts`

---

## Task 11: E2E verification — real agent chat

**Prerequisites:** API key configured in the app (OpenAI, Anthropic, or OpenRouter).

**Step 1:** Build and install: `npx expo export:embed ... && ./gradlew assembleDebug && adb install`

**Step 2:** Launch app, complete onboarding, go to Config, enter an API key.

**Step 3:** Go to Chat. Send: "What is the capital of France?"
- **Expected:** Real LLM response: "Paris" (or similar)
- **Not acceptable:** "Agent not configured", error, empty response, or stub text

**Step 4:** Send: "What is my battery level?"
- **Expected:** Tool call executes, returns real battery percentage
- **Not acceptable:** "I don't have that tool" or stub

**Step 5:** Send: "Set an alarm for 7am tomorrow"
- **Expected:** Tool executes, alarm set confirmation

**Step 6:** Send: "Search the web for latest news about AI"
- **Expected:** Web search tool fires, returns results

**Step 7:** Screenshot each result. Take logcat for errors.

**Step 8:** Kill app, reopen — verify chat history persists (AsyncStorage).

**Step 9:** Commit test fixes if needed.

---

## Task 12: Documentation cleanup

**Files to modify:**
- `mobile-app/README.md` — Remove all Rust/daemon/JNI references
- `docs/plans/` — Plans are historical reference (keep as-is, they document design decisions)
- Root `README.md` — Update if it references mobile JNI build

**Step 1:** Update mobile-app/README.md: remove Rust build pipeline section, update architecture diagram to show pure Kotlin.

**Step 2:** Commit: `docs: update README for pure Kotlin architecture`

---

## Execution Order

Tasks 1-4 are sequential (each depends on prior).
Tasks 5-8 can be partially parallelized.
Task 9-10 are independent cleanup.
Task 11 is final verification.
Task 12 is docs.
