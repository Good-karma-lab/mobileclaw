import { sendMessage } from "../native/guappaAgent";
import type { AgentTurnResult } from "./types";

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
    };
  }
}
