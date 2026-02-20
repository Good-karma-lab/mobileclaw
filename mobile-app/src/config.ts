import Constants from "expo-constants";
import { Platform } from "react-native";

const getRuntimeExtra = (): Record<string, unknown> => {
  const expoConfigExtra = (Constants.expoConfig?.extra ?? {}) as Record<string, unknown>;
  const manifestExtra = (Constants.manifest as any)?.extra as Record<string, unknown> | undefined;
  const manifest2Extra = (Constants.manifest2 as any)?.extra as Record<string, unknown> | undefined;

  return {
    ...expoConfigExtra,
    ...(manifestExtra ?? {}),
    ...(manifest2Extra ?? {})
  };
};

const requireEnv = (key: string, fallback?: string) => {
  const value = process.env[key] ?? (getRuntimeExtra()[key] as string | undefined);
  if (!value) {
    if (fallback !== undefined) {
      console.warn(`[config] Missing env var: ${key}, using fallback: ${fallback}`);
      return fallback;
    }
    throw new Error(`Missing env var: ${key}`);
  }
  return String(value);
};

const optionalEnv = (key: string) => {
  const value = process.env[key] ?? (getRuntimeExtra()[key] as string | undefined);
  return value === undefined ? undefined : String(value);
};

const normalizeDevHost = (url: string) => {
  // Android emulator cannot reach host services via localhost.
  // Real devices must use LAN/tunnel URL and should not be rewritten.
  if (Platform.OS !== "android") return url;
  if (Constants.isDevice) return url;
  return url
    .replace("http://localhost", "http://10.0.2.2")
    .replace("http://127.0.0.1", "http://10.0.2.2")
    .replace("https://localhost", "https://10.0.2.2")
    .replace("https://127.0.0.1", "https://10.0.2.2");
};

const derivedWsUrl = (httpUrl: string) => httpUrl.replace(/^http/, "ws");

export const config = {
  platformUrl: normalizeDevHost(requireEnv("EXPO_PUBLIC_PLATFORM_URL", "http://10.0.2.2:8000")),
  wsUrl: derivedWsUrl(normalizeDevHost(requireEnv("EXPO_PUBLIC_PLATFORM_URL", "http://10.0.2.2:8000"))),
  logLevel: requireEnv("EXPO_PUBLIC_LOG_LEVEL", "info"),
  demoMode: optionalEnv("EXPO_PUBLIC_DEMO_MODE") === "true",
  theme: {
    primary: requireEnv("EXPO_PUBLIC_THEME_PRIMARY", "#D4F49C"),
    secondary: requireEnv("EXPO_PUBLIC_THEME_SECONDARY", "#C69CF4"),
    accent: requireEnv("EXPO_PUBLIC_THEME_ACCENT", "#8B5CF6"),
    background: requireEnv("EXPO_PUBLIC_THEME_BG", "#05050A"),
    text: requireEnv("EXPO_PUBLIC_THEME_TEXT", "#F5F0E6"),
    border: requireEnv("EXPO_PUBLIC_THEME_BORDER", "#FFFFFF"),
    textMuted: requireEnv("EXPO_PUBLIC_THEME_TEXT_MUTED", "#A3A3B2")
  }
};
