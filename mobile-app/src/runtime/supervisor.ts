import AsyncStorage from "@react-native-async-storage/async-storage";
import { Platform } from "react-native";

import { configureAndroidRuntimeBridge, getAndroidRuntimeBridgeStatus } from "../native/androidAgentBridge";
import { isAgentRunning, startAgent, stopAgent } from "../native/guappaAgent";
import { addActivity } from "../state/activity";
import {
  loadAgentConfig,
  loadDeviceToolsConfig,
  loadIntegrationsConfig,
  loadSecurityConfig,
  type AgentRuntimeConfig,
  type IntegrationsConfig,
  type MobileToolCapability,
  type SecurityConfig,
} from "../state/guappa";
import { startLocalLlmServer, isLocalLlmRunning, LOCAL_LLM_URL } from "../native/localLlmServer";

export type RuntimeSupervisorState = {
  status: "stopped" | "starting" | "healthy" | "degraded";
  degradeReason: "none" | "missing_config" | "platform_unreachable" | "mixed";
  startedAtMs: number | null;
  lastTransitionMs: number;
  restartCount: number;
  components: string[];
  missingConfig: string[];
  lastError: string | null;
  configHash: string;
};

const KEY = "guappa:runtime-supervisor:v1";

const DEFAULT_STATE: RuntimeSupervisorState = {
  status: "stopped",
  degradeReason: "none",
  startedAtMs: null,
  lastTransitionMs: Date.now(),
  restartCount: 0,
  components: [],
  missingConfig: [],
  lastError: null,
  configHash: "",
};

function hashText(input: string): string {
  let hash = 2166136261;
  for (let i = 0; i < input.length; i++) {
    hash ^= input.charCodeAt(i);
    hash +=
      (hash << 1) +
      (hash << 4) +
      (hash << 7) +
      (hash << 8) +
      (hash << 24);
  }
  return (hash >>> 0).toString(16).padStart(8, "0");
}

function effectiveRuntimeApiKey(runtime: AgentRuntimeConfig): string {
  return runtime.authMode === "oauth_token" ? runtime.oauthAccessToken : runtime.apiKey;
}

function signature(
  runtime: AgentRuntimeConfig,
  integrations: IntegrationsConfig,
  security: SecurityConfig,
  enabledToolIds: string[],
): string {
  return JSON.stringify({
    runtime: {
      provider: runtime.provider,
      model: runtime.model,
      apiUrl: runtime.apiUrl,
      temperature: runtime.temperature,
      apiKeyHash: hashText(effectiveRuntimeApiKey(runtime).trim()),
      braveKeyHash: hashText(runtime.braveApiKey.trim()),
      localModelPath: runtime.localModelPath || "",
      thinkingMode: runtime.thinkingMode || false,
    },
    integrations: {
      telegramEnabled: integrations.telegramEnabled,
      telegramTokenHash: hashText(integrations.telegramBotToken.trim()),
      telegramChatId: integrations.telegramChatId.trim(),
      discordEnabled: integrations.discordEnabled,
      discordTokenHash: hashText(integrations.discordBotToken.trim()),
      slackEnabled: integrations.slackEnabled,
      slackTokenHash: hashText(integrations.slackBotToken.trim()),
      whatsappEnabled: integrations.whatsappEnabled,
      whatsappTokenHash: hashText(integrations.whatsappAccessToken.trim()),
      composioEnabled: integrations.composioEnabled,
      composioKeyHash: hashText(integrations.composioApiKey.trim()),
    },
    security: {
      incomingCallHooks: security.incomingCallHooks,
      incomingSmsHooks: security.incomingSmsHooks,
      includeCallerNumber: security.includeCallerNumber,
      alwaysOnRuntime: security.alwaysOnRuntime,
    },
    enabledToolIds: [...enabledToolIds].sort(),
  });
}

function deriveComponents(integrations: IntegrationsConfig, security: SecurityConfig) {
  const components = ["agent:guappa_agent"];
  const missing: string[] = [];

  if (integrations.telegramEnabled) {
    components.push("channel:telegram");
    if (!integrations.telegramBotToken.trim()) missing.push("telegram.bot_token");
  }
  if (integrations.discordEnabled) {
    components.push("channel:discord");
    if (!integrations.discordBotToken.trim()) missing.push("discord.bot_token");
  }
  if (integrations.slackEnabled) {
    components.push("channel:slack");
    if (!integrations.slackBotToken.trim()) missing.push("slack.bot_token");
  }
  if (integrations.whatsappEnabled) {
    components.push("channel:whatsapp");
    if (!integrations.whatsappAccessToken.trim()) missing.push("whatsapp.access_token");
  }
  if (integrations.composioEnabled) {
    components.push("tool:composio");
    if (!integrations.composioApiKey.trim()) missing.push("composio.api_key");
  }

  if (security.incomingCallHooks) {
    components.push("hook:incoming_call");
  }
  if (security.incomingSmsHooks) {
    components.push("hook:incoming_sms");
  }

  return { components, missing };
}

async function buildAgentConfig(runtime: AgentRuntimeConfig, integrations: IntegrationsConfig) {
  const config = {
    apiKey: effectiveRuntimeApiKey(runtime),
    provider: runtime.provider,
    model: runtime.model,
    apiUrl: runtime.apiUrl,
    temperature: runtime.temperature,
    telegramToken: integrations.telegramEnabled ? integrations.telegramBotToken : "",
    telegramChatId: integrations.telegramEnabled ? integrations.telegramChatId : "",
    discordBotToken: integrations.discordEnabled ? integrations.discordBotToken : "",
    slackBotToken: integrations.slackEnabled ? integrations.slackBotToken : "",
    composioApiKey: integrations.composioEnabled ? integrations.composioApiKey : "",
    braveApiKey: runtime.braveApiKey || "",
    localModelPath: runtime.localModelPath || "",
    thinkingMode: runtime.thinkingMode || false,
  };

  // If provider is "local", start the on-device LLM server and remap to Ollama
  if (config.provider === "local" && config.localModelPath) {
    if (!isLocalLlmRunning()) {
      await startLocalLlmServer({
        modelPath: config.localModelPath,
        gpuLayers: runtime.gpuLayers ?? 0,
        cpuThreads: runtime.cpuThreads ?? 4,
        contextLength: runtime.contextLength ?? 65536,
        thinkingMode: runtime.thinkingMode ?? true,
      });
    }
    config.provider = "openai";
    config.apiUrl = `${LOCAL_LLM_URL}/v1`;
    config.apiKey = "local";
    config.model = "local";
  }

  return config;
}

async function readState(): Promise<RuntimeSupervisorState> {
  const raw = await AsyncStorage.getItem(KEY);
  if (!raw) return DEFAULT_STATE;
  try {
    return { ...DEFAULT_STATE, ...(JSON.parse(raw) as Partial<RuntimeSupervisorState>) };
  } catch {
    return DEFAULT_STATE;
  }
}

async function writeState(state: RuntimeSupervisorState): Promise<void> {
  await AsyncStorage.setItem(KEY, JSON.stringify(state));
}

async function fetchHealthSnapshot(): Promise<{ ok: boolean; detail?: string }> {
  if (Platform.OS !== "android") return { ok: false, detail: "android_only" };
  const bridge = await getAndroidRuntimeBridgeStatus();
  if (!bridge) return { ok: false, detail: "native_runtime_bridge_unavailable" };
  if (!bridge.runtimeReady) return { ok: false, detail: "runtime_not_configured" };
  if (!bridge.daemonUp) return { ok: false, detail: "agent_down" };
  return { ok: true };
}

async function waitForAgentReady(timeoutMs: number): Promise<void> {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    if (await isAgentRunning()) return;
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error("agent_start_timeout");
}

export async function getRuntimeSupervisorState(): Promise<RuntimeSupervisorState> {
  return readState();
}

export async function startRuntimeSupervisor(reason: string): Promise<RuntimeSupervisorState> {
  const state = await readState();
  if (state.status !== "stopped") {
    return applyRuntimeSupervisorConfig(`resume:${reason}`);
  }

  const next: RuntimeSupervisorState = {
    ...state,
    status: "starting",
    startedAtMs: Date.now(),
    lastTransitionMs: Date.now(),
    lastError: null,
  };
  await writeState(next);
  await addActivity({
    kind: "action",
    source: "runtime",
    title: "Guappa agent starting",
    detail: reason,
  });

  return applyRuntimeSupervisorConfig(`start:${reason}`);
}

export async function applyRuntimeSupervisorConfig(reason: string): Promise<RuntimeSupervisorState> {
  const [runtime, deviceTools, integrations, security, previous] = await Promise.all([
    loadAgentConfig(),
    loadDeviceToolsConfig(),
    loadIntegrationsConfig(),
    loadSecurityConfig(),
    readState(),
  ]);

  const enabledToolIds = deviceTools.filter((tool: MobileToolCapability) => tool.enabled).map((tool) => tool.id);
  const { components, missing } = deriveComponents(integrations, security);
  const hash = signature(runtime, integrations, security, enabledToolIds);
  const changed = previous.configHash !== hash;

  await configureAndroidRuntimeBridge({
    telegramEnabled: integrations.telegramEnabled,
    telegramBotToken: integrations.telegramBotToken,
    telegramChatId: integrations.telegramChatId,
    alwaysOnMode: security.alwaysOnRuntime,
    incomingCallHooks: security.incomingCallHooks,
    incomingSmsHooks: security.incomingSmsHooks,
    enabledToolIds,
    runtimeProvider: runtime.provider,
    runtimeModel: runtime.model,
    runtimeApiUrl: runtime.apiUrl,
    runtimeApiKey: effectiveRuntimeApiKey(runtime),
    runtimeTemperature: runtime.temperature,
    runtimeBraveApiKey: runtime.braveApiKey,
  });

  let agentApplyError: string | null = null;
  let agentRestarted = false;

  if (Platform.OS === "android") {
    const running = await isAgentRunning();
    if (changed || !running) {
      try {
        const agentConfig = await buildAgentConfig(runtime, integrations);
        if (running) {
          await stopAgent();
        } else {
          await stopAgent().catch(() => undefined);
        }
        await startAgent(agentConfig);
        await waitForAgentReady(25_000);
        agentRestarted = true;
      } catch (error) {
        agentApplyError = error instanceof Error ? error.message : "agent_restart_failed";
      }
    }
  }

  const health = await fetchHealthSnapshot();
  if (agentApplyError) {
    health.ok = false;
    health.detail = agentApplyError;
  }

  const status: RuntimeSupervisorState["status"] =
    missing.length > 0 || !health.ok ? "degraded" : "healthy";
  const degradeReason: RuntimeSupervisorState["degradeReason"] =
    missing.length > 0 && !health.ok
      ? "mixed"
      : missing.length > 0
        ? "missing_config"
        : !health.ok
          ? "platform_unreachable"
          : "none";

  const next: RuntimeSupervisorState = {
    ...previous,
    status,
    degradeReason,
    components,
    missingConfig: missing,
    lastError: health.ok ? null : health.detail || "health check failed",
    lastTransitionMs: Date.now(),
    restartCount: previous.restartCount + (agentRestarted ? 1 : 0),
    configHash: hash,
  };

  const statusChanged = previous.status !== status;
  const componentsChanged = JSON.stringify(previous.components) !== JSON.stringify(components);

  if (changed || statusChanged || componentsChanged) {
    const detailParts = [
      `reason=${reason}`,
      `status=${status}`,
      `degrade_reason=${degradeReason}`,
      `components=${components.join(", ") || "none"}`,
    ];
    if (missing.length) detailParts.push(`missing=${missing.join(", ")}`);
    if (!health.ok && health.detail) detailParts.push(`health=${health.detail}`);
    if (agentRestarted) detailParts.push("agent=restarted");

    await addActivity({
      kind: "action",
      source: "runtime",
      title: status === "healthy" ? "Guappa agent healthy" : "Guappa agent needs attention",
      detail: detailParts.join(" | "),
    });
  }

  await writeState(next);
  return next;
}

export async function stopRuntimeSupervisor(reason: string): Promise<RuntimeSupervisorState> {
  const previous = await readState();
  const next: RuntimeSupervisorState = {
    ...previous,
    status: "stopped",
    degradeReason: "none",
    components: [],
    missingConfig: [],
    lastError: null,
    lastTransitionMs: Date.now(),
  };
  await writeState(next);
  await addActivity({
    kind: "action",
    source: "runtime",
    title: "Guappa agent stopped",
    detail: reason,
  });
  return next;
}

export async function reportRuntimeHookEvent(kind: "incoming_call" | "incoming_sms", detail: string): Promise<void> {
  await addActivity({
    kind: "action",
    source: "runtime",
    title: `Hook queued: ${kind}`,
    detail: `${detail} | native_runtime_bridge=true`,
  });
}
