import { sendMessage, sendMessageStream, subscribeToAgentEvents, type AgentEvent } from "../native/guappaAgent";
import type { AgentTurnResult, ToolExecutionEvent } from "./types";

/** Strip <think>...</think> tags from Qwen3.5 thinking mode output */
function stripThinkingTags(text: string): string {
  return text.replace(/<think>[\s\S]*?<\/think>\s*/g, "").trim();
}

type RunAgentTurnStreamArgs = {
  userPrompt: string;
  sessionId?: string;
  /** File URIs of images attached by the user. */
  imageUris?: string[];
  onSession?: (sessionId: string) => void;
  onDelta?: (partialText: string, delta: string) => void;
  /** Called with thinking/reasoning tokens as they stream in. */
  onThinking?: (partialThinking: string, delta: string) => void;
  /** Called when a tool call starts or completes. contentType is "tool_call" or "tool_result". */
  onToolCallStream?: (text: string, contentType: "tool_call" | "tool_result") => void;
  onToolEvent?: (event: ToolExecutionEvent) => void;
  /** Called when the agent emits images (from camera, screenshots, generated images). */
  onAgentImages?: (imagePaths: string[]) => void;
};

let agentBootstrapPromise: Promise<void> | null = null;

async function ensureAgentReady(): Promise<void> {
  const agentModule = await import("../native/guappaAgent");
  if (await agentModule.isAgentRunning()) return;

  if (!agentBootstrapPromise) {
    agentBootstrapPromise = (async () => {
      const [{ loadAgentConfig, loadIntegrationsConfig }, localLlmModule] = await Promise.all([
        import("../state/guappa"),
        import("../native/localLlmServer"),
      ]);
      const agentCfg = await loadAgentConfig();
      const integCfg = await loadIntegrationsConfig();
      const runtimeApiKey =
        agentCfg.authMode === "oauth_token" ? agentCfg.oauthAccessToken : agentCfg.apiKey;

      const agentConfig = {
        apiKey: runtimeApiKey,
        provider: agentCfg.provider,
        model: agentCfg.model,
        apiUrl: agentCfg.apiUrl,
        temperature: agentCfg.temperature,
        telegramToken: integCfg.telegramEnabled ? integCfg.telegramBotToken : "",
        telegramChatId: integCfg.telegramEnabled ? integCfg.telegramChatId : "",
        discordBotToken: integCfg.discordEnabled ? integCfg.discordBotToken : "",
        slackBotToken: integCfg.slackEnabled ? integCfg.slackBotToken : "",
        composioApiKey: integCfg.composioEnabled ? integCfg.composioApiKey : "",
        braveApiKey: agentCfg.braveApiKey || "",
        localModelPath: agentCfg.localModelPath || "",
        thinkingMode: agentCfg.thinkingMode ?? false,
      };

      if (agentConfig.provider === "local" && agentConfig.localModelPath) {
        await localLlmModule.startLocalLlmServer({
          modelPath: agentConfig.localModelPath,
          gpuLayers: agentCfg.gpuLayers ?? 0,
          cpuThreads: agentCfg.cpuThreads ?? 4,
          contextLength: (agentCfg.contextLength && agentCfg.contextLength > 4096) ? agentCfg.contextLength : 32768,
          thinkingMode: agentCfg.thinkingMode ?? true,
        });
        agentConfig.provider = "openai";
        agentConfig.apiUrl = `${localLlmModule.LOCAL_LLM_URL}/v1`;
        agentConfig.apiKey = "local";
        agentConfig.model = "local";
      }

      await agentModule.startAgent(agentConfig);
    })().finally(() => {
      agentBootstrapPromise = null;
    });
  }

  await agentBootstrapPromise;
}

/**
 * Run a single agent turn: send user prompt to Kotlin orchestrator,
 * get LLM response back (with tool execution if needed).
 */
export async function runAgentTurn(userPrompt: string, sessionId?: string): Promise<AgentTurnResult> {
  try {
    await ensureAgentReady();
    const response = await sendMessage(userPrompt, sessionId);
    return {
      assistantText: response || "(empty response)",
      toolEvents: [],
      sessionId,
    };
  } catch (error) {
    return {
      assistantText:
        error instanceof Error
          ? `Agent error: ${error.message}`
          : "Agent error. Please try again.",
      toolEvents: [
        {
          tool: "agent",
          status: "failed",
          detail: error instanceof Error ? error.message : "Unknown error",
        },
      ],
      sessionId,
    };
  }
}

export async function runAgentTurnStream({
  userPrompt,
  sessionId,
  imageUris,
  onSession,
  onDelta,
  onThinking,
  onToolCallStream,
  onToolEvent,
  onAgentImages,
}: RunAgentTurnStreamArgs): Promise<AgentTurnResult> {
  return new Promise<AgentTurnResult>(async (resolve, reject) => {
    const toolEvents: ToolExecutionEvent[] = [];
    let partialText = "";
    let partialThinking = "";
    let activeSessionId = sessionId || "";

    const finish = (result: AgentTurnResult) => {
      unsubscribe();
      resolve(result);
    };

    const fail = (error: unknown) => {
      unsubscribe();
      reject(error);
    };

    const handleToolEvent = (event: AgentEvent) => {
      const toolEvent: ToolExecutionEvent = {
        tool: event.tool || event.eventType || "agent",
        status: event.success === false ? "failed" : "executed",
        detail: event.detail || event.eventType || "tool event",
      };
      toolEvents.push(toolEvent);
      onToolEvent?.(toolEvent);
    };

    const unsubscribe = subscribeToAgentEvents((event) => {
      if (activeSessionId && event.sessionId && event.sessionId !== activeSessionId) {
        return;
      }

      if (!activeSessionId && event.sessionId) {
        activeSessionId = event.sessionId;
      }

      if (event.type === "tool_event") {
        handleToolEvent(event);
        return;
      }

      // Handle agent image attachments (from camera tool, screenshot, etc.)
      if (event.imageAttachments && event.imageAttachments.length > 0) {
        onAgentImages?.(event.imageAttachments);
      }

      const contentType = (event as any).contentType || "text";

      if (event.type === "agent_chunk") {
        const text = event.text || "";

        if (contentType === "thinking") {
          // Orchestrator sends accumulated thinking text
          partialThinking = text;
          onThinking?.(stripThinkingTags(partialThinking), text);
          return;
        }

        if (contentType === "tool_call" || contentType === "tool_result") {
          onToolCallStream?.(text, contentType);
          return;
        }

        // Orchestrator sends accumulated text (full text so far)
        partialText = text;
        onDelta?.(stripThinkingTags(partialText), text);
        return;
      }

      if (event.type === "agent_complete") {
        partialText = stripThinkingTags(event.text || partialText || "(empty response)");
        onDelta?.(partialText, "");
        finish({
          assistantText: partialText,
          toolEvents,
          sessionId: activeSessionId || event.sessionId,
        });
      }
    });

    try {
      console.log("[session] ensureAgentReady...");
      await ensureAgentReady();
      console.log("[session] agent ready, sending message:", userPrompt);
      activeSessionId = await sendMessageStream(userPrompt, sessionId, imageUris);
      console.log("[session] sendMessageStream returned sessionId:", activeSessionId);
      onSession?.(activeSessionId);
    } catch (error) {
      fail(error);
    }
  });
}
