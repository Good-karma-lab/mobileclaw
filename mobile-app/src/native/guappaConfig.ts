import { NativeModules, Platform } from "react-native";

type NativeGuappaConfig = {
  getProviderModels(config: Record<string, unknown>): Promise<Array<Record<string, unknown>>>;
  getProviderHealth(config: Record<string, unknown>): Promise<boolean>;
  getSecureString(key: string): Promise<string | null>;
  setSecureString(key: string, value: string): Promise<boolean>;
  removeSecureString(key: string): Promise<boolean>;
};

export type ProviderModelInfo = {
  id: string;
  name: string;
};

export type ProviderProbeConfig = {
  provider: string;
  apiKey?: string;
  oauthAccessToken?: string;
  apiUrl?: string;
};

const moduleRef = (NativeModules.GuappaConfig || null) as NativeGuappaConfig | null;

function toNativeProbeConfig(config: ProviderProbeConfig): Record<string, unknown> {
  return {
    provider: config.provider,
    apiKey: config.oauthAccessToken || config.apiKey || "",
    apiUrl: config.apiUrl || "",
  };
}

export async function getProviderModels(config: ProviderProbeConfig): Promise<ProviderModelInfo[]> {
  if (Platform.OS !== "android" || !moduleRef?.getProviderModels) {
    return [];
  }

  const raw = await moduleRef.getProviderModels(toNativeProbeConfig(config));
  if (!Array.isArray(raw)) {
    return [];
  }

  return raw
    .map((entry) => ({
      id: String(entry.id || ""),
      name: String(entry.name || entry.id || ""),
    }))
    .filter((entry) => entry.id.length > 0);
}

export async function getProviderHealth(config: ProviderProbeConfig): Promise<boolean> {
  if (Platform.OS !== "android" || !moduleRef?.getProviderHealth) {
    return false;
  }
  return Boolean(await moduleRef.getProviderHealth(toNativeProbeConfig(config)));
}

export async function getSecureString(key: string): Promise<string | null> {
  if (Platform.OS !== "android" || !moduleRef?.getSecureString) {
    return null;
  }
  const value = await moduleRef.getSecureString(key);
  return typeof value === "string" ? value : null;
}

export async function setSecureString(key: string, value: string): Promise<void> {
  if (Platform.OS !== "android" || !moduleRef?.setSecureString) {
    return;
  }
  await moduleRef.setSecureString(key, value);
}

export async function removeSecureString(key: string): Promise<void> {
  if (Platform.OS !== "android" || !moduleRef?.removeSecureString) {
    return;
  }
  await moduleRef.removeSecureString(key);
}
