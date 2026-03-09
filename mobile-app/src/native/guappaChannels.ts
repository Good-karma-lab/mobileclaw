/**
 * React Native bridge to Guappa Channel Hub (Kotlin backend).
 *
 * Manages messaging channels (Telegram, Discord, Slack, WhatsApp,
 * Signal, Matrix, Email, SMS) — configuration, health checks,
 * and send operations.
 */
import { NativeModules, Platform } from "react-native";

type NativeGuappaChannels = {
  listChannels(): Promise<string>;
  configureChannel(channelId: string, config: string): Promise<boolean>;
  removeChannel(channelId: string): Promise<boolean>;
  testChannel(channelId: string): Promise<string>;
  sendMessage(channelId: string, message: string): Promise<boolean>;
  broadcastMessage(message: string): Promise<string>;
  getChannelStatus(channelId: string): Promise<string>;
  setAllowlist(channelId: string, allowedIds: string): Promise<boolean>;
  getAllowlist(channelId: string): Promise<string>;
};

const moduleRef = (NativeModules.GuappaChannels || null) as NativeGuappaChannels | null;

// --- Types ---

export type ChannelType =
  | "telegram"
  | "discord"
  | "slack"
  | "email"
  | "whatsapp"
  | "signal"
  | "matrix"
  | "sms";

export interface ChannelInfo {
  id: ChannelType;
  name: string;
  isConfigured: boolean;
  isConnected: boolean;
  lastHealthCheck: number | null;
  healthStatus: "healthy" | "unhealthy" | "unknown";
}

export interface ChannelConfig {
  // Telegram
  botToken?: string;
  chatId?: string;
  // Discord
  webhookUrl?: string;
  // Slack
  slackWebhookUrl?: string;
  // Email
  recipientEmail?: string;
  // WhatsApp
  phoneNumberId?: string;
  accessToken?: string;
  recipientPhone?: string;
  // Signal
  signalApiUrl?: string;
  senderNumber?: string;
  signalRecipient?: string;
  // Matrix
  homeserverUrl?: string;
  matrixAccessToken?: string;
  roomId?: string;
  // SMS
  smsRecipient?: string;
}

export interface HealthCheckResult {
  channelId: ChannelType;
  healthy: boolean;
  latencyMs: number;
  error: string | null;
}

export interface BroadcastResult {
  sent: ChannelType[];
  failed: Array<{ channelId: ChannelType; error: string }>;
}

// --- API ---

export async function listChannels(): Promise<ChannelInfo[]> {
  if (Platform.OS !== "android" || !moduleRef) return [];
  try {
    const json = await moduleRef.listChannels();
    return JSON.parse(json) as ChannelInfo[];
  } catch {
    return [];
  }
}

export async function configureChannel(
  channelId: ChannelType,
  config: ChannelConfig
): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.configureChannel(channelId, JSON.stringify(config));
}

export async function removeChannel(channelId: ChannelType): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.removeChannel(channelId);
}

export async function testChannel(
  channelId: ChannelType
): Promise<HealthCheckResult> {
  if (Platform.OS !== "android" || !moduleRef) {
    return { channelId, healthy: false, latencyMs: 0, error: "Not available" };
  }
  try {
    const json = await moduleRef.testChannel(channelId);
    return JSON.parse(json) as HealthCheckResult;
  } catch (e) {
    return {
      channelId,
      healthy: false,
      latencyMs: 0,
      error: e instanceof Error ? e.message : "Unknown error",
    };
  }
}

export async function sendChannelMessage(
  channelId: ChannelType,
  message: string
): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.sendMessage(channelId, message);
}

export async function broadcastMessage(
  message: string
): Promise<BroadcastResult> {
  if (Platform.OS !== "android" || !moduleRef) {
    return { sent: [], failed: [] };
  }
  try {
    const json = await moduleRef.broadcastMessage(message);
    return JSON.parse(json) as BroadcastResult;
  } catch {
    return { sent: [], failed: [] };
  }
}

export async function getChannelStatus(
  channelId: ChannelType
): Promise<ChannelInfo | null> {
  if (Platform.OS !== "android" || !moduleRef) return null;
  try {
    const json = await moduleRef.getChannelStatus(channelId);
    return JSON.parse(json) as ChannelInfo;
  } catch {
    return null;
  }
}

export async function setAllowlist(
  channelId: ChannelType,
  allowedIds: string[]
): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.setAllowlist(channelId, JSON.stringify(allowedIds));
}

export async function getAllowlist(
  channelId: ChannelType
): Promise<string[]> {
  if (Platform.OS !== "android" || !moduleRef) return [];
  try {
    const json = await moduleRef.getAllowlist(channelId);
    return JSON.parse(json) as string[];
  } catch {
    return [];
  }
}
