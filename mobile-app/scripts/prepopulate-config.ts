/**
 * Development helper script to prepopulate AsyncStorage with credentials.
 * Run this script in the app via developer menu or automatically on dev builds.
 *
 * IMPORTANT: These credentials are for development only and should NEVER be committed to git.
 */

import AsyncStorage from "@react-native-async-storage/async-storage";
import type { AgentRuntimeConfig, IntegrationsConfig } from "../src/state/mobileclaw";

const DEV_CREDENTIALS = {
  // OpenRouter configuration
  openRouterApiKey: "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",
  openRouterModel: "minimax/minimax-m2.5",

  // Deepgram configuration
  deepgramApiKey: "45958109dcef69e4c524498de3a8bdc8cdf82317",

  // Telegram configuration
  telegramBotToken: "8353127948:AAH5Dyuc1ydsTDzwbydobbRYndoqpXXUPEc",

  // EAS Token (for Expo builds)
  easToken: "4_EE5-6yFLCi3aEWfA8-hiBv-bXX5yZiotE7HHN2",
};

export async function prepopulateDevCredentials(): Promise<void> {
  console.log("[prepopulate] Starting credential prepopulation for development...");

  try {
    // Load existing agent config
    const agentConfigKey = "mobileclaw:agent-config:v1";
    const existingAgentRaw = await AsyncStorage.getItem(agentConfigKey);
    const existingAgent: Partial<AgentRuntimeConfig> = existingAgentRaw ? JSON.parse(existingAgentRaw) : {};

    // Merge with dev credentials
    const agentConfig: AgentRuntimeConfig = {
      provider: "openrouter",
      model: DEV_CREDENTIALS.openRouterModel,
      apiUrl: "https://openrouter.ai/api/v1",
      apiKey: DEV_CREDENTIALS.openRouterApiKey,
      authMode: "api_key",
      oauthAccessToken: "",
      oauthRefreshToken: "",
      oauthExpiresAtMs: 0,
      accountId: "",
      enterpriseUrl: "",
      temperature: 0.1,
      deepgramApiKey: DEV_CREDENTIALS.deepgramApiKey,
      ...existingAgent,
    };

    await AsyncStorage.setItem(agentConfigKey, JSON.stringify(agentConfig));
    console.log("[prepopulate] ✓ Agent config saved with OpenRouter and Deepgram credentials");

    // Load existing integrations config
    const integrationsKey = "mobileclaw:integrations-config:v2";
    const existingIntegrationsRaw = await AsyncStorage.getItem(integrationsKey);
    const existingIntegrations: Partial<IntegrationsConfig> = existingIntegrationsRaw
      ? JSON.parse(existingIntegrationsRaw)
      : {};

    // Merge with dev credentials
    const integrationsConfig: IntegrationsConfig = {
      telegramEnabled: true,
      telegramBotToken: DEV_CREDENTIALS.telegramBotToken,
      telegramChatId: "", // User needs to detect this via the app
      discordEnabled: false,
      discordBotToken: "",
      slackEnabled: false,
      slackBotToken: "",
      whatsappEnabled: false,
      whatsappAccessToken: "",
      composioEnabled: false,
      composioApiKey: "",
      ...existingIntegrations,
    };

    await AsyncStorage.setItem(integrationsKey, JSON.stringify(integrationsConfig));
    console.log("[prepopulate] ✓ Integrations config saved with Telegram credentials");

    console.log("[prepopulate] ✅ All dev credentials prepopulated successfully!");
    console.log("[prepopulate] Note: You may need to detect Telegram Chat ID via the Integrations screen");

  } catch (error) {
    console.error("[prepopulate] ❌ Failed to prepopulate credentials:", error);
    throw error;
  }
}

// Export for manual invocation if needed
export const DEV_CREDENTIALS_INFO = {
  openRouter: {
    model: DEV_CREDENTIALS.openRouterModel,
    apiKey: DEV_CREDENTIALS.openRouterApiKey.slice(0, 20) + "...",
  },
  deepgram: {
    apiKey: DEV_CREDENTIALS.deepgramApiKey.slice(0, 20) + "...",
  },
  telegram: {
    botToken: DEV_CREDENTIALS.telegramBotToken.split(":")[0] + ":...",
  },
  eas: {
    token: DEV_CREDENTIALS.easToken.slice(0, 15) + "...",
  },
};
