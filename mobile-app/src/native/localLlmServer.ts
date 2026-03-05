import { NativeModules, NativeEventEmitter } from "react-native";
import { initLlama, LlamaContext } from "llama.rn";
import { log } from "../logger";

const { LocalLlmServer } = NativeModules;
const eventEmitter = new NativeEventEmitter(LocalLlmServer);

const LOCAL_PORT = 8888;

let llamaContext: LlamaContext | null = null;
let serverRunning = false;
let requestSubscription: any = null;

export type LocalLlmConfig = {
  modelPath: string;
  gpuLayers: number;
  cpuThreads: number;
  contextLength: number;
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

  // Listen for HTTP requests from the native server
  requestSubscription = eventEmitter.addListener("LocalLlmRequest", async (event) => {
    const { requestId, body } = event;
    try {
      const parsed = JSON.parse(body || "{}");
      const messages = parsed.messages || [];
      const temperature = parsed.temperature ?? 0.7;
      const maxTokens = parsed.max_tokens ?? 2048;

      if (!llamaContext) {
        LocalLlmServer.respondToRequest(requestId, 503,
          JSON.stringify({ error: { message: "Model not loaded" } }));
        return;
      }

      const result = await llamaContext.completion({
        messages,
        n_predict: maxTokens,
        temperature,
        top_k: 20,
        top_p: parsed.top_p ?? 0.95,
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

      LocalLlmServer.respondToRequest(requestId, 200, JSON.stringify(response));
    } catch (err: any) {
      log(`[LocalLLM] Inference error: ${err.message}`);
      LocalLlmServer.respondToRequest(requestId, 500,
        JSON.stringify({ error: { message: err.message } }));
    }
  });

  // Start the native HTTP server
  await LocalLlmServer.start(LOCAL_PORT);
  serverRunning = true;
  log(`[LocalLLM] Server started on http://127.0.0.1:${LOCAL_PORT}`);
}

export async function stopLocalLlmServer(): Promise<void> {
  if (requestSubscription) {
    requestSubscription.remove();
    requestSubscription = null;
  }
  if (serverRunning) {
    await LocalLlmServer.stop();
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
