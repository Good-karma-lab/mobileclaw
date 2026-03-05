# llama.rn + whisper.rn Local Inference — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace candle with llama.rn for on-device LLM inference (mmap, OpenCL GPU on Adreno, Qwen3.5 support), add whisper.rn for local voice transcription, and wire it all through ZeroClaw agent via localhost API.

**Architecture:** React Native runs llama.rn for LLM inference with OpenCL GPU acceleration and whisper.rn for speech-to-text. A thin local HTTP server in RN exposes an OpenAI-compatible `/v1/chat/completions` endpoint on `localhost:8888`. The ZeroClaw Rust agent connects to this endpoint using the existing Ollama provider (pointed at localhost). No Rust-side inference code needed — candle and llama-cpp-2 are both removed.

**Tech Stack:** llama.rn (npm), whisper.rn (npm), @react-native-library/webserver (npm), React Native, existing Ollama provider in Rust

---

## Phase 1: Remove Candle from Rust Backend

### Task 1: Remove candle dependencies and local provider

**Files:**
- Modify: `Cargo.toml:120-123` (remove candle deps)
- Modify: `Cargo.toml:206` (remove local-inference feature)
- Delete: `src/providers/local.rs`
- Modify: `src/providers/mod.rs` (remove local module + factory arm)
- Modify: `src/config/schema.rs` (remove local_model_path, local_thinking_mode fields)
- Modify: `src/jni_bridge.rs` (remove local model path handling, remove fallback to openrouter)

**Step 1: Remove candle deps from Cargo.toml**

In `Cargo.toml`, remove lines 120-123:
```toml
# DELETE these lines:
candle-core = { version = "0.9", optional = true, default-features = false }
candle-transformers = { version = "0.9", optional = true, default-features = false }
tokenizers = { version = "0.22", optional = true, default-features = false, features = ["onig"] }
```

Change line 205-206:
```toml
# BEFORE:
android-jni = ["dep:jni", "local-inference"]
local-inference = ["dep:candle-core", "dep:candle-transformers", "dep:tokenizers"]

# AFTER:
android-jni = ["dep:jni"]
# local-inference feature removed — inference runs in RN via llama.rn
```

**Step 2: Remove local.rs provider**

Delete `src/providers/local.rs`.

In `src/providers/mod.rs`:
- Remove `pub mod local;`
- Remove the `"local"` match arm in the factory function (around line 979)

**Step 3: Simplify JNI bridge**

In `src/jni_bridge.rs`:
- Remove the `localModelPath` parameter handling (lines ~242-256)
- Remove the `thinkingMode` parameter handling
- Remove the fallback-to-openrouter logic
- The "local" provider is now just Ollama pointed at localhost — handled by RN setting `apiUrl` to `http://127.0.0.1:8888`

In `src/config/schema.rs`:
- Remove `local_model_path` field
- Remove `local_thinking_mode` field

**Step 4: Update build script**

In `build_android_jni.sh`, change:
```bash
# BEFORE:
cargo build --lib --target aarch64-linux-android --release --features local-inference
# AFTER:
cargo build --lib --target aarch64-linux-android --release
```
Same for the armv7 build line.

**Step 5: Verify compilation**

Run: `cargo check`
Expected: PASS — no candle references remain

**Step 6: Commit**

```bash
git add -A
git commit -m "refactor: remove candle and local provider — inference moves to llama.rn in RN layer"
```

---

## Phase 2: Add llama.rn + Local HTTP Server in React Native

### Task 2: Install llama.rn and HTTP server dependencies

**Step 1: Install npm packages**

```bash
cd /Users/aostapenko/Work/mobileclaw/mobile-app
npm install llama.rn
npm install @react-native-library/webserver
```

**Step 2: Add OpenCL permission to AndroidManifest.xml**

In `mobile-app/android/app/src/main/AndroidManifest.xml`, add inside `<application>`:
```xml
<!-- OpenCL GPU acceleration for llama.rn on Qualcomm Adreno -->
<uses-native-library android:name="libOpenCL.so" android:required="false" />
```

**Step 3: Commit**

```bash
git add mobile-app/package.json mobile-app/package-lock.json mobile-app/android/app/src/main/AndroidManifest.xml
git commit -m "feat(deps): add llama.rn and webserver for local inference"
```

---

### Task 3: Create local LLM inference service module

**Files:**
- Create: `mobile-app/src/native/localLlmServer.ts`

**Step 1: Write the local LLM server module**

This module:
1. Loads a GGUF model via llama.rn with OpenCL GPU layers
2. Starts a local HTTP server on port 8888
3. Handles OpenAI-compatible `/v1/chat/completions` requests
4. Routes them to llama.rn `context.completion()`
5. Returns the response in OpenAI format

```typescript
import { initLlama, LlamaContext } from "llama.rn";
import HttpServer from "@react-native-library/webserver";
import { log } from "../logger";

const LOCAL_PORT = 8888;

let llamaContext: LlamaContext | null = null;
let serverRunning = false;

export type LocalLlmConfig = {
  modelPath: string;
  gpuLayers: number;   // 0 = CPU-only, >0 = OpenCL offload
  cpuThreads: number;  // default 4
  contextLength: number; // default 2048
  thinkingMode: boolean;
};

export async function startLocalLlmServer(config: LocalLlmConfig): Promise<void> {
  if (serverRunning) {
    log("[LocalLLM] Server already running");
    return;
  }

  // Load model via llama.rn
  log(`[LocalLLM] Loading model: ${config.modelPath} (gpu_layers=${config.gpuLayers})`);
  llamaContext = await initLlama({
    model: config.modelPath,
    n_ctx: config.contextLength,
    n_gpu_layers: config.gpuLayers,
    n_threads: config.cpuThreads,
    use_mmap: true,
  });
  log("[LocalLLM] Model loaded successfully");

  // Start HTTP server
  HttpServer.start(LOCAL_PORT, "localhost", async (request: any) => {
    try {
      if (request.url === "/v1/chat/completions" && request.type === "POST") {
        const body = JSON.parse(request.postData || "{}");
        const messages = body.messages || [];
        const temperature = body.temperature ?? 0.7;
        const maxTokens = body.max_tokens ?? 2048;

        if (!llamaContext) {
          HttpServer.respond(request.requestId, 503, "application/json",
            JSON.stringify({ error: { message: "Model not loaded" } }));
          return;
        }

        const result = await llamaContext.completion({
          messages,
          n_predict: maxTokens,
          temperature,
          top_k: 20,
          top_p: body.top_p ?? 0.95,
          stop: ["<|im_end|>", "<|endoftext|>"],
        });

        const response = {
          id: `chatcmpl-local-${Date.now()}`,
          object: "chat.completion",
          created: Math.floor(Date.now() / 1000),
          model: "local",
          choices: [{
            index: 0,
            message: { role: "assistant", content: result.text },
            finish_reason: result.text ? "stop" : "length",
          }],
          usage: {
            prompt_tokens: result.timings?.prompt_n ?? 0,
            completion_tokens: result.timings?.predicted_n ?? 0,
            total_tokens: (result.timings?.prompt_n ?? 0) + (result.timings?.predicted_n ?? 0),
          },
        };

        HttpServer.respond(request.requestId, 200, "application/json",
          JSON.stringify(response));
      } else if (request.url === "/v1/models") {
        // Model listing endpoint
        HttpServer.respond(request.requestId, 200, "application/json",
          JSON.stringify({ data: [{ id: "local", object: "model" }] }));
      } else {
        HttpServer.respond(request.requestId, 404, "application/json",
          JSON.stringify({ error: { message: "Not found" } }));
      }
    } catch (err: any) {
      log(`[LocalLLM] Request error: ${err.message}`);
      HttpServer.respond(request.requestId, 500, "application/json",
        JSON.stringify({ error: { message: err.message } }));
    }
  });

  serverRunning = true;
  log(`[LocalLLM] Server started on http://127.0.0.1:${LOCAL_PORT}`);
}

export async function stopLocalLlmServer(): Promise<void> {
  if (serverRunning) {
    HttpServer.stop();
    serverRunning = false;
  }
  if (llamaContext) {
    await llamaContext.release();
    llamaContext = null;
  }
  log("[LocalLLM] Server stopped");
}

export function isLocalLlmRunning(): boolean {
  return serverRunning && llamaContext !== null;
}

export const LOCAL_LLM_URL = `http://127.0.0.1:${LOCAL_PORT}`;
```

**Step 2: Commit**

```bash
git add mobile-app/src/native/localLlmServer.ts
git commit -m "feat(local-llm): add llama.rn-based local LLM HTTP server on localhost:8888"
```

---

### Task 4: Wire local LLM server into app lifecycle

**Files:**
- Modify: `mobile-app/App.tsx` (or wherever daemon config is assembled)
- Modify: `mobile-app/src/runtime/supervisor.ts` (if daemon startup orchestration lives here)

**Step 1: Start local LLM server when provider is "local"**

When the user selects provider "local":
1. Start the local LLM server with the downloaded model path
2. Set the daemon's `apiUrl` to `http://127.0.0.1:8888`
3. Set the daemon's `provider` to `"ollama"` (or `"openai"` — both speak OpenAI format)

The key insight: from the Rust agent's perspective, "local" inference is just Ollama running on localhost. The RN layer handles model loading and GPU acceleration transparently.

In the app config assembly (likely `App.tsx` around lines 97-98):
```typescript
import { startLocalLlmServer, stopLocalLlmServer, LOCAL_LLM_URL } from "./native/localLlmServer";

// When building daemonConfig:
if (agentConfig.provider === "local" && agentConfig.localModelPath) {
  await startLocalLlmServer({
    modelPath: agentConfig.localModelPath,
    gpuLayers: agentConfig.gpuLayers ?? 0,
    cpuThreads: agentConfig.cpuThreads ?? 4,
    contextLength: agentConfig.contextLength ?? 2048,
    thinkingMode: agentConfig.thinkingMode ?? true,
  });
  // Tell the Rust daemon to connect to local server as Ollama
  daemonConfig.provider = "ollama";
  daemonConfig.apiUrl = LOCAL_LLM_URL;
  daemonConfig.model = "local";
}
```

When provider changes away from "local":
```typescript
await stopLocalLlmServer();
```

**Step 2: Commit**

```bash
git add mobile-app/App.tsx mobile-app/src/runtime/supervisor.ts
git commit -m "feat(app): wire local LLM server startup into app lifecycle"
```

---

### Task 5: Update model downloader for Qwen3.5

**Files:**
- Modify: `mobile-app/src/native/modelDownloader.ts`

**Step 1: Update model URLs to Qwen3.5**

Replace the model URL records:
```typescript
const MODEL_URLS: Record<string, string> = {
  "Qwen/Qwen3.5-0.8B":
    "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf",
  "Qwen/Qwen3.5-2B":
    "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q4_K_M.gguf",
};

const MODEL_FILENAMES: Record<string, string> = {
  "Qwen/Qwen3.5-0.8B": "Qwen3.5-0.8B-Q4_K_M.gguf",
  "Qwen/Qwen3.5-2B": "Qwen3.5-2B-Q4_K_M.gguf",
};
```

Remove the `TOKENIZER_URLS` record and tokenizer download logic — llama.rn (llama.cpp) uses the tokenizer embedded in the GGUF file.

**Step 2: Commit**

```bash
git add mobile-app/src/native/modelDownloader.ts
git commit -m "feat(models): update to Qwen3.5 models, remove tokenizer dependency"
```

---

### Task 6: Update state and defaults

**Files:**
- Modify: `mobile-app/src/state/mobileclaw.ts`

**Step 1: Update AgentRuntimeConfig type**

Add new fields:
```typescript
export type AgentRuntimeConfig = {
  // ... existing fields ...
  localModelPath: string;
  thinkingMode: boolean;
  // New fields:
  gpuLayers: number;       // GPU layer offload (0=CPU, >0=OpenCL)
  cpuThreads: number;      // CPU threads for inference
  contextLength: number;   // Context window size
  voiceProvider: "deepgram" | "whisper";
  whisperModel: string;    // whisper-tiny, whisper-base, whisper-small
  whisperModelPath: string;
};
```

Update defaults:
```typescript
export const DEFAULT_AGENT_CONFIG: AgentRuntimeConfig = {
  provider: "local",
  model: "Qwen/Qwen3.5-0.8B",
  // ... existing ...
  localModelPath: "",
  thinkingMode: true,
  gpuLayers: 0,
  cpuThreads: 4,
  contextLength: 2048,
  voiceProvider: "deepgram",
  whisperModel: "whisper-base",
  whisperModelPath: "",
};
```

**Step 2: Commit**

```bash
git add mobile-app/src/state/mobileclaw.ts
git commit -m "feat(state): add GPU, voice, and performance config fields"
```

---

### Task 7: Update Settings UI — model list + performance settings

**Files:**
- Modify: `mobile-app/src/screens/tabs/SettingsScreen.tsx`

**Step 1: Update MODELS_BY_PROVIDER**

```typescript
local: ["Qwen/Qwen3.5-0.8B", "Qwen/Qwen3.5-2B"],
```

**Step 2: Add performance settings section**

When provider is "local", show below the model selector:
```tsx
{form.provider === "local" && (
  <View>
    <Text style={styles.sectionTitle}>Performance</Text>

    {/* GPU Layers */}
    <Text>GPU Layers: {form.gpuLayers}</Text>
    <Slider min={0} max={99} value={form.gpuLayers}
      onValueChange={(v) => setForm({...form, gpuLayers: v})} />
    <Text style={styles.hint}>
      OpenCL on Adreno GPU. 0 = CPU only. Increase for faster inference.
    </Text>

    {/* CPU Threads */}
    <Text>CPU Threads: {form.cpuThreads}</Text>
    <Slider min={1} max={8} value={form.cpuThreads}
      onValueChange={(v) => setForm({...form, cpuThreads: v})} />

    {/* Context Length */}
    <Picker selectedValue={form.contextLength}
      onValueChange={(v) => setForm({...form, contextLength: v})}>
      <Picker.Item label="512 (short)" value={512} />
      <Picker.Item label="2048 (default)" value={2048} />
      <Picker.Item label="4096 (long)" value={4096} />
    </Picker>
  </View>
)}
```

**Step 3: Commit**

```bash
git add mobile-app/src/screens/tabs/SettingsScreen.tsx
git commit -m "feat(ui): add Qwen3.5 models and performance settings"
```

---

## Phase 3: Add RAM Checks

### Task 8: Add device memory check utility

**Files:**
- Create: `mobile-app/src/native/deviceMemory.ts`

**Step 1: Create RAM check module**

```typescript
export const MODEL_SIZES: Record<string, number> = {
  "Qwen/Qwen3.5-0.8B": 530_000_000,
  "Qwen/Qwen3.5-2B": 1_500_000_000,
  "whisper-tiny": 75_000_000,
  "whisper-base": 142_000_000,
  "whisper-small": 466_000_000,
};

const RAM_MULTIPLIER = 1.5;
const WARN_THRESHOLD = 0.50;
const BLOCK_THRESHOLD = 0.60;

export type RamCheckResult = {
  status: "ok" | "warning" | "blocked";
  requiredMB: number;
  limitMB: number;
  message?: string;
};

export function checkModelRam(modelId: string, deviceRamMB: number): RamCheckResult {
  const fileSize = MODEL_SIZES[modelId];
  if (!fileSize) return { status: "ok", requiredMB: 0, limitMB: deviceRamMB };

  const requiredMB = Math.round((fileSize * RAM_MULTIPLIER) / (1024 * 1024));
  const warnLimit = Math.round(deviceRamMB * WARN_THRESHOLD);
  const blockLimit = Math.round(deviceRamMB * BLOCK_THRESHOLD);

  if (requiredMB > blockLimit) {
    return {
      status: "blocked", requiredMB, limitMB: blockLimit,
      message: `Cannot load ${modelId} (~${requiredMB}MB required) — exceeds safe limit of ${blockLimit}MB.`,
    };
  }
  if (requiredMB > warnLimit) {
    return {
      status: "warning", requiredMB, limitMB: blockLimit,
      message: `${modelId} uses ${requiredMB}MB — close to device limit.`,
    };
  }
  return { status: "ok", requiredMB, limitMB: blockLimit };
}
```

**Step 2: Integrate into Settings UI**

Show RAM status badges next to each model in the picker. Block download if `status === "blocked"`.

**Step 3: Commit**

```bash
git add mobile-app/src/native/deviceMemory.ts mobile-app/src/screens/tabs/SettingsScreen.tsx
git commit -m "feat(memory): add RAM pre-check with warning/block thresholds"
```

---

## Phase 4: Add Whisper.rn Voice Transcription

### Task 9: Install whisper.rn

**Step 1: Install**

```bash
cd /Users/aostapenko/Work/mobileclaw/mobile-app
npm install whisper.rn
```

**Step 2: Add proguard rules if needed**

Check `android/app/proguard-rules.pro`.

**Step 3: Commit**

```bash
git add mobile-app/package.json mobile-app/package-lock.json
git commit -m "feat(deps): add whisper.rn for local speech-to-text"
```

---

### Task 10: Add whisper model downloads

**Files:**
- Modify: `mobile-app/src/native/modelDownloader.ts`

**Step 1: Add whisper URLs and download function**

```typescript
const WHISPER_MODEL_URLS: Record<string, string> = {
  "whisper-tiny": "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
  "whisper-base": "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
  "whisper-small": "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
};

const WHISPER_MODEL_FILENAMES: Record<string, string> = {
  "whisper-tiny": "ggml-tiny.bin",
  "whisper-base": "ggml-base.bin",
  "whisper-small": "ggml-small.bin",
};

export async function downloadWhisperModel(
  modelId: string,
  onProgress?: (progress: number) => void,
): Promise<string> {
  const url = WHISPER_MODEL_URLS[modelId];
  if (!url) throw new Error(`Unknown whisper model: ${modelId}`);
  const filename = WHISPER_MODEL_FILENAMES[modelId];
  if (!filename) throw new Error(`No filename: ${modelId}`);

  const dir = getModelDir();
  if (!dir.exists) dir.create({ intermediates: true });
  const destFile = new File(dir, filename);
  onProgress?.(0);
  const downloaded = await File.downloadFileAsync(url, destFile, { idempotent: true });
  onProgress?.(100);
  return downloaded.uri.replace(/^file:\/\//, "");
}

export async function checkWhisperModelExists(
  modelId: string,
): Promise<{ exists: boolean; path: string }> {
  const filename = WHISPER_MODEL_FILENAMES[modelId];
  if (!filename) return { exists: false, path: "" };
  const file = new File(getModelDir(), filename);
  try {
    return { exists: file.exists, path: file.exists ? file.uri.replace(/^file:\/\//, "") : "" };
  } catch { return { exists: false, path: "" }; }
}
```

**Step 2: Commit**

```bash
git add mobile-app/src/native/modelDownloader.ts
git commit -m "feat(whisper): add whisper model download support"
```

---

### Task 11: Integrate whisper.rn into voice recording hook

**Files:**
- Modify: `mobile-app/src/hooks/useVoiceRecording.ts`

**Step 1: Extend hook to support local whisper transcription**

Change hook signature:
```typescript
export function useVoiceRecording(config: {
  voiceProvider: "deepgram" | "whisper";
  deepgramApiKey?: string;
  whisperModelPath?: string;
})
```

When `voiceProvider === "whisper"`:
- Record audio to a WAV file using expo-av (not streaming to WebSocket)
- After recording stops, call `whisperContext.transcribe(audioUri)`
- Return transcript

When `voiceProvider === "deepgram"`:
- Use existing Deepgram WebSocket streaming (unchanged)

```typescript
import { initWhisper, WhisperContext } from "whisper.rn";

// Inside the hook:
const whisperCtxRef = useRef<WhisperContext | null>(null);

const transcribeWithWhisper = useCallback(async (audioUri: string) => {
  if (!whisperCtxRef.current && config.whisperModelPath) {
    whisperCtxRef.current = await initWhisper({
      filePath: config.whisperModelPath,
    });
  }
  if (!whisperCtxRef.current) throw new Error("Whisper model not loaded");
  const { promise } = whisperCtxRef.current.transcribe(audioUri, { language: "en" });
  const result = await promise;
  return result.result || "";
}, [config.whisperModelPath]);
```

**Step 2: Update callers**

Update all call sites of `useVoiceRecording()` to pass the new config object instead of just `deepgramApiKey`.

**Step 3: Commit**

```bash
git add mobile-app/src/hooks/useVoiceRecording.ts
git commit -m "feat(voice): add whisper.rn local transcription path"
```

---

### Task 12: Add voice provider toggle to Settings UI

**Files:**
- Modify: `mobile-app/src/screens/tabs/SettingsScreen.tsx`

**Step 1: Replace Deepgram-only section with voice provider toggle**

Replace the Deepgram section (around lines 374-378):
```tsx
<Text style={styles.sectionTitle}>Voice Transcription</Text>

{/* Provider toggle */}
<Picker selectedValue={form.voiceProvider}
  onValueChange={(v) => setForm({...form, voiceProvider: v})}>
  <Picker.Item label="Cloud (Deepgram)" value="deepgram" />
  <Picker.Item label="Local (Whisper)" value="whisper" />
</Picker>

{form.voiceProvider === "deepgram" && (
  <TextInput placeholder="Deepgram API Key" value={form.deepgramApiKey}
    onChangeText={(v) => setForm({...form, deepgramApiKey: v})} secureTextEntry />
)}

{form.voiceProvider === "whisper" && (
  <>
    <Picker selectedValue={form.whisperModel}
      onValueChange={(v) => setForm({...form, whisperModel: v})}>
      <Picker.Item label="Tiny (~75MB, fastest)" value="whisper-tiny" />
      <Picker.Item label="Base (~142MB, balanced)" value="whisper-base" />
      <Picker.Item label="Small (~466MB, best)" value="whisper-small" />
    </Picker>
    {/* Download button + progress for whisper model */}
  </>
)}
```

**Step 2: Commit**

```bash
git add mobile-app/src/screens/tabs/SettingsScreen.tsx
git commit -m "feat(ui): add voice provider toggle (Deepgram vs Whisper)"
```

---

## Phase 5: Build and Test

### Task 13: Rebuild JNI library (smaller, no candle)

**Step 1: Run build script**

```bash
cd /Users/aostapenko/Work/mobileclaw && ./build_android_jni.sh
```

Expected: Builds successfully. Library should be **smaller** than before since candle is removed.

**Step 2: Verify size**

```bash
ls -lh mobile-app/android/app/src/main/jniLibs/arm64-v8a/libzeroclaw.so
```

**Step 3: Commit**

```bash
git add mobile-app/android/app/src/main/jniLibs/
git commit -m "build(jni): rebuild libzeroclaw.so without candle"
```

---

### Task 14: Build and install APK

**Step 1: Build**

```bash
cd /Users/aostapenko/Work/mobileclaw/mobile-app/android && ./gradlew assembleDebug
```

Note: First build with llama.rn will take longer as it compiles llama.cpp C++ code via CMake.

**Step 2: Install**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

### Task 15: End-to-end test on Samsung S22 Ultra

**Step 1: Download Qwen3.5-0.8B**

Settings -> select Qwen/Qwen3.5-0.8B -> Download. Wait for ~500MB download.

**Step 2: Verify local LLM server starts**

```bash
adb logcat | grep -i "LocalLLM"
```

Expected: "[LocalLLM] Model loaded successfully" and "[LocalLLM] Server started on http://127.0.0.1:8888"

**Step 3: Send chat message**

Chat -> "What is 2+2? Answer briefly." -> send.

Expected: Response within 30-60s. No OOM kill. llama.rn uses mmap so memory stays low.

**Step 4: Test with GPU layers**

Settings -> GPU Layers = 10 -> restart. Chat again.

Expected: Faster response if OpenCL works on Adreno 730. If it crashes, GPU layers auto-fallback to 0.

**Step 5: Test whisper voice**

Settings -> Voice -> Local (Whisper) -> download whisper-base -> record -> verify transcript.

**Step 6: Verify no OOM**

```bash
adb logcat -s ActivityManager:* | grep -i "kill\|oom\|low memory"
```

Expected: No OOM kills. Model stays under 60% RAM budget.

---

## Architecture Summary

```
┌─ React Native Layer ─────────────────────────────┐
│                                                    │
│  llama.rn (GGUF + OpenCL GPU)                     │
│    ↓                                               │
│  localLlmServer.ts (HTTP on :8888)                │
│    ↕ OpenAI-compatible /v1/chat/completions       │
│                                                    │
│  whisper.rn (speech-to-text, local)               │
│  useVoiceRecording.ts (Deepgram or Whisper)       │
│                                                    │
│  modelDownloader.ts (GGUF + whisper models)       │
│  deviceMemory.ts (RAM checks)                     │
│                                                    │
└──────────────┬────────────────────────────────────┘
               │ HTTP localhost:8888
               ↓
┌─ ZeroClaw Rust Agent (libzeroclaw.so) ────────────┐
│                                                    │
│  Ollama Provider → http://127.0.0.1:8888          │
│  (thinks it's talking to Ollama)                  │
│                                                    │
│  Agent loop, tools, memory, channels              │
│  (unchanged — no inference code)                  │
│                                                    │
└────────────────────────────────────────────────────┘
```

**GPU Acceleration Priority:**
1. OpenCL (Adreno GPU) — via llama.rn `n_gpu_layers` parameter
2. CPU + mmap — fallback, still memory-efficient
3. QNN (Snapdragon NPU) — future, requires llama.rn fork or custom build

**Key Constraint:** No fallbacks. No stubs. If model isn't loaded, explicit error in UI.
