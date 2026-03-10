import { NativeModules, NativeEventEmitter } from "react-native";
import { initLlama, LlamaContext } from "llama.rn";
import { logger } from "../logger";

const { LocalLlmServer } = NativeModules;
const eventEmitter = new NativeEventEmitter(LocalLlmServer);

const LOCAL_PORT = 8888;

let llamaContext: LlamaContext | null = null;
let serverRunning = false;
let serverStarting = false;
let requestSubscription: any = null;

// Serial queue for inference — llama.rn only supports one completion at a time
let inferenceQueue: Promise<void> = Promise.resolve();

export type LocalLlmConfig = {
  modelPath: string;
  gpuLayers: number;
  cpuThreads: number;
  contextLength: number;
  thinkingMode: boolean;
};

export async function startLocalLlmServer(config: LocalLlmConfig): Promise<void> {
  if (serverRunning || serverStarting) {
    logger.info("[LocalLLM] Server already running or starting");
    return;
  }
  serverStarting = true;

  // Load model via llama.rn
  logger.info(`[LocalLLM] Loading model: ${config.modelPath} (gpu_layers=${config.gpuLayers})`);
  llamaContext = await initLlama({
    model: config.modelPath,
    n_ctx: config.contextLength,
    n_gpu_layers: config.gpuLayers,
    n_threads: config.cpuThreads,
    use_mmap: true,
  });
  logger.info("[LocalLLM] Model loaded successfully");

  // Listen for HTTP requests from the native server
  requestSubscription = eventEmitter.addListener("LocalLlmRequest", (event) => {
    const { requestId, body } = event;
    // Serialize all inference requests through the queue
    inferenceQueue = inferenceQueue.then(() => handleInferenceRequest(requestId, body)).catch(() => {});
  });

  async function handleInferenceRequest(requestId: string, body: string) {
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

      // Build ChatML prompt manually for Qwen models
      const prompt = messages.map((m: any) =>
        `<|im_start|>${m.role}\n${m.content}<|im_end|>`
      ).join("\n") + "\n<|im_start|>assistant\n";

      logger.info(`[LocalLLM] Running inference, prompt length=${prompt.length}`);

      const result = await llamaContext.completion({
        prompt,
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
      const errMsg = err?.message || err?.toString() || "unknown error";
      const errStack = err?.stack || "";
      logger.error(`[LocalLLM] Inference error: ${errMsg}`);
      logger.error(`[LocalLLM] Stack: ${errStack}`);
      LocalLlmServer.respondToRequest(requestId, 500,
        JSON.stringify({ error: { message: errMsg } }));
    }
  }

  // Start the native HTTP server
  await LocalLlmServer.start(LOCAL_PORT);
  serverRunning = true;
  serverStarting = false;
  logger.info(`[LocalLLM] Server started on http://127.0.0.1:${LOCAL_PORT}`);
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
  logger.info("[LocalLLM] Server stopped");
}

export function isLocalLlmRunning(): boolean {
  return serverRunning && llamaContext !== null;
}

export const LOCAL_LLM_URL = `http://127.0.0.1:${LOCAL_PORT}`;
