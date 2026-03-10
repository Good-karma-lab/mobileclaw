import { sendMessage, sendMessageStream, subscribeToAgentEvents, type AgentEvent } from "../native/guappaAgent";
import type { AgentTurnResult, ToolExecutionEvent } from "./types";

type RunAgentTurnStreamArgs = {
  userPrompt: string;
  sessionId?: string;
  /** File URIs of images attached by the user. */
  imageUris?: string[];
  onSession?: (sessionId: string) => void;
  onDelta?: (partialText: string, delta: string) => void;
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
          contextLength: agentCfg.contextLength ?? 2048,
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
  onToolEvent,
  onAgentImages,
}: RunAgentTurnStreamArgs): Promise<AgentTurnResult> {
  return new Promise<AgentTurnResult>(async (resolve, reject) => {
    const toolEvents: ToolExecutionEvent[] = [];
    let partialText = "";
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

      if (event.type === "agent_chunk") {
        const delta = event.text || "";
        partialText += delta;
        onDelta?.(partialText, delta);
        return;
      }

      if (event.type === "agent_complete") {
        partialText = event.text || partialText || "(empty response)";
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
