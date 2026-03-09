/**
 * React Native bridge to Guappa Proactive Agent (Kotlin backend).
 *
 * Manages triggers, smart timing, notification preferences,
 * and scheduled briefings.
 */
import { NativeModules, Platform } from "react-native";

type NativeGuappaProactive = {
  // Triggers
  getTriggers(): Promise<string>;
  addTrigger(triggerJson: string): Promise<boolean>;
  removeTrigger(triggerId: string): Promise<boolean>;
  toggleTrigger(triggerId: string, enabled: boolean): Promise<boolean>;
  evaluateTriggers(): Promise<string>;

  // Smart timing
  setQuietHours(startHour: number, endHour: number): Promise<boolean>;
  getQuietHours(): Promise<string>;
  isInQuietHours(): Promise<boolean>;
  setCooldown(channelId: string, cooldownMs: number): Promise<boolean>;

  // Briefings
  setMorningBriefing(enabled: boolean, hour: number, minute: number): Promise<boolean>;
  getMorningBriefingConfig(): Promise<string>;
  setEveningSummary(enabled: boolean, hour: number, minute: number): Promise<boolean>;
  getEveningSummaryConfig(): Promise<string>;
  generateBriefingNow(): Promise<string>;

  // Notifications
  getNotificationHistory(limit: number): Promise<string>;
  clearNotificationHistory(): Promise<boolean>;
  setNotificationEnabled(channelId: string, enabled: boolean): Promise<boolean>;
};

const moduleRef = (NativeModules.GuappaProactive || null) as NativeGuappaProactive | null;

// --- Types ---

export type TriggerType = "time_based" | "event_based" | "location_based" | "condition_based";

export interface ProactiveTrigger {
  id: string;
  type: TriggerType;
  name: string;
  description: string;
  enabled: boolean;
  config: Record<string, unknown>;
  lastFired: number | null;
  createdAt: number;
}

export interface QuietHoursConfig {
  startHour: number;
  endHour: number;
  enabled: boolean;
}

export interface BriefingConfig {
  enabled: boolean;
  hour: number;
  minute: number;
}

export interface NotificationRecord {
  id: string;
  channelId: string;
  title: string;
  body: string;
  timestamp: number;
  type: string;
}

// --- Triggers ---

export async function getTriggers(): Promise<ProactiveTrigger[]> {
  if (Platform.OS !== "android" || !moduleRef) return [];
  try {
    const json = await moduleRef.getTriggers();
    return JSON.parse(json) as ProactiveTrigger[];
  } catch {
    return [];
  }
}

export async function addTrigger(
  trigger: Omit<ProactiveTrigger, "id" | "lastFired" | "createdAt">
): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.addTrigger(JSON.stringify(trigger));
}

export async function removeTrigger(triggerId: string): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.removeTrigger(triggerId);
}

export async function toggleTrigger(
  triggerId: string,
  enabled: boolean
): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.toggleTrigger(triggerId, enabled);
}

export async function evaluateTriggers(): Promise<string[]> {
  if (Platform.OS !== "android" || !moduleRef) return [];
  try {
    const json = await moduleRef.evaluateTriggers();
    return JSON.parse(json) as string[];
  } catch {
    return [];
  }
}

// --- Smart Timing ---

export async function setQuietHours(
  startHour: number,
  endHour: number
): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.setQuietHours(startHour, endHour);
}

export async function getQuietHours(): Promise<QuietHoursConfig | null> {
  if (Platform.OS !== "android" || !moduleRef) return null;
  try {
    const json = await moduleRef.getQuietHours();
    return JSON.parse(json) as QuietHoursConfig;
  } catch {
    return null;
  }
}

export async function isInQuietHours(): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.isInQuietHours();
}

export async function setCooldown(
  channelId: string,
  cooldownMs: number
): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.setCooldown(channelId, cooldownMs);
}

// --- Briefings ---

export async function setMorningBriefing(
  enabled: boolean,
  hour: number = 7,
  minute: number = 30
): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.setMorningBriefing(enabled, hour, minute);
}

export async function getMorningBriefingConfig(): Promise<BriefingConfig | null> {
  if (Platform.OS !== "android" || !moduleRef) return null;
  try {
    const json = await moduleRef.getMorningBriefingConfig();
    return JSON.parse(json) as BriefingConfig;
  } catch {
    return null;
  }
}

export async function setEveningSummary(
  enabled: boolean,
  hour: number = 21,
  minute: number = 0
): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.setEveningSummary(enabled, hour, minute);
}

export async function getEveningSummaryConfig(): Promise<BriefingConfig | null> {
  if (Platform.OS !== "android" || !moduleRef) return null;
  try {
    const json = await moduleRef.getEveningSummaryConfig();
    return JSON.parse(json) as BriefingConfig;
  } catch {
    return null;
  }
}

export async function generateBriefingNow(): Promise<string> {
  if (Platform.OS !== "android" || !moduleRef) return "";
  return moduleRef.generateBriefingNow();
}

// --- Notifications ---

export async function getNotificationHistory(
  limit: number = 50
): Promise<NotificationRecord[]> {
  if (Platform.OS !== "android" || !moduleRef) return [];
  try {
    const json = await moduleRef.getNotificationHistory(limit);
    return JSON.parse(json) as NotificationRecord[];
  } catch {
    return [];
  }
}

export async function clearNotificationHistory(): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.clearNotificationHistory();
}

export async function setNotificationEnabled(
  channelId: string,
  enabled: boolean
): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef) return false;
  return moduleRef.setNotificationEnabled(channelId, enabled);
}
