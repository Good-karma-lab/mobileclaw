import { initLlama, LlamaContext } from "llama.rn";
import HttpServer from "@react-native-library/webserver";
import { log } from "../logger";

const LOCAL_PORT = 8888;

let llamaContext: LlamaContext | null = null;
let serverRunning = false;

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

  log(`[LocalLLM] Loading model: ${config.modelPath} (gpu_layers=${config.gpuLayers})`);
  llamaContext = await initLlama({
    model: config.modelPath,
    n_ctx: config.contextLength,
    n_gpu_layers: config.gpuLayers,
    n_threads: config.cpuThreads,
    use_mmap: true,
  });
  log("[LocalLLM] Model loaded successfully");

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
