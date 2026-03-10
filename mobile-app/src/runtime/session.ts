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

/**
 * Run a single agent turn: send user prompt to Kotlin orchestrator,
 * get LLM response back (with tool execution if needed).
 */
export async function runAgentTurn(userPrompt: string, sessionId?: string): Promise<AgentTurnResult> {
  try {
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
      activeSessionId = await sendMessageStream(userPrompt, sessionId, imageUris);
      onSession?.(activeSessionId);
    } catch (error) {
      fail(error);
    }
  });
}
