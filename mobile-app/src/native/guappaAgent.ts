/**
 * React Native bridge to Guappa agent (pure Kotlin backend)
 *
 * No Rust daemon, no JNI, no localhost gateway.
 * Calls go directly to the Kotlin NativeModule.
 */

import { NativeEventEmitter, NativeModules, Platform, type EmitterSubscription } from "react-native";

const { GuappaAgent } = NativeModules;
const agentEventEmitter = GuappaAgent ? new NativeEventEmitter(GuappaAgent) : null;

if (!GuappaAgent) {
  console.warn(
    "GuappaAgent native module not found. Agent backend may not be available."
  );
}

export interface AgentStartConfig {
  apiKey?: string;
  provider?: string;
  model?: string;
  apiUrl?: string;
  temperature?: number;
  telegramToken?: string;
  telegramChatId?: string;
  discordBotToken?: string;
  slackBotToken?: string;
  composioApiKey?: string;
  braveApiKey?: string;
  localModelPath?: string;
  thinkingMode?: boolean;
}

export type AgentEvent = {
  type: "agent_chunk" | "agent_complete" | "tool_event" | "system_event";
  sessionId: string;
  text?: string;
  eventType?: string;
  tool?: string;
  detail?: string;
  success?: boolean;
  isStreaming?: boolean;
  isComplete?: boolean;
};

export async function startAgent(config: AgentStartConfig = {}): Promise<boolean> {
  if (Platform.OS !== "android") {
    throw new Error("Guappa agent only available on Android");
  }
  if (!GuappaAgent) {
    throw new Error("GuappaAgent native module not available");
  }
  return GuappaAgent.startAgent({
    apiKey: config.apiKey ?? "",
    provider: config.provider ?? "",
    model: config.model ?? "",
    apiUrl: config.apiUrl ?? "",
    temperature: config.temperature ?? 0.1,
    telegramToken: config.telegramToken ?? "",
    telegramChatId: config.telegramChatId ?? "",
    discordBotToken: config.discordBotToken ?? "",
    slackBotToken: config.slackBotToken ?? "",
    composioApiKey: config.composioApiKey ?? "",
    braveApiKey: config.braveApiKey ?? "",
    localModelPath: config.localModelPath ?? "",
    thinkingMode: config.thinkingMode ?? false,
  });
}

export async function sendMessage(
  text: string,
  sessionId?: string
): Promise<string> {
  if (!GuappaAgent) {
    throw new Error("GuappaAgent native module not available");
  }
  return GuappaAgent.sendMessage(text, sessionId ?? null);
}

export async function sendMessageStream(text: string, sessionId?: string): Promise<string> {
  if (!GuappaAgent) {
    throw new Error("GuappaAgent native module not available");
  }
  return GuappaAgent.sendMessageStream(text, sessionId ?? null);
}

export async function stopAgent(): Promise<boolean> {
  if (!GuappaAgent) {
    throw new Error("GuappaAgent native module not available");
  }
  return GuappaAgent.stopAgent();
}

export async function isAgentRunning(): Promise<boolean> {
  if (!GuappaAgent) return false;
  try {
    return await GuappaAgent.isAgentRunning();
  } catch {
    return false;
  }
}

/**
 * Quick, non-orchestrated LLM call for the Swarm Director.
 * Separate pipeline from the main agent — fast emotion/intent classification.
 */
export async function quickLlmCall(prompt: string, systemPrompt: string): Promise<string> {
  if (!GuappaAgent) {
    throw new Error("GuappaAgent native module not available");
  }
  return GuappaAgent.quickLlmCall(prompt, systemPrompt);
}

export async function collectDebugInfo(): Promise<string> {
  if (!GuappaAgent) {
    throw new Error("GuappaAgent native module not available");
  }
  return GuappaAgent.collectDebugInfo();
}

export function subscribeToAgentEvents(listener: (event: AgentEvent) => void): () => void {
  if (!agentEventEmitter) {
    return () => {};
  }

  const subscription: EmitterSubscription = agentEventEmitter.addListener(
    "guappa_agent_event",
    (event: AgentEvent) => listener(event),
  );

  return () => {
    subscription.remove();
  };
}
