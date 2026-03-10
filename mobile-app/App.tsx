import "react-native-gesture-handler";

import React, { useEffect, useRef, useState } from "react";
import { Platform, View, ActivityIndicator, Text, LogBox } from "react-native";

// Suppress non-critical warnings that create a banner covering the dock navigation
LogBox.ignoreLogs([
  "expo-av",
  "new NativeEventEmitter",
  "setLayoutAnimationEnabledExperimental",
  "Method getInfoAsync",
  "Attempted to import the module",
]);
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { NavigationContainer } from "@react-navigation/native";
import { StatusBar } from "expo-status-bar";
import * as Font from "expo-font";
import { SafeAreaProvider } from "react-native-safe-area-context";

import { Inter_400Regular, Inter_500Medium } from "@expo-google-fonts/inter";
import { SpaceGrotesk_600SemiBold } from "@expo-google-fonts/space-grotesk";
import { JetBrainsMono_500Medium } from "@expo-google-fonts/jetbrains-mono";
import { Orbitron_700Bold } from "@expo-google-fonts/orbitron";
import { Exo2_400Regular, Exo2_500Medium, Exo2_600SemiBold } from "@expo-google-fonts/exo-2";
// Ionicons font must be explicitly loaded for dev-client builds
const ioniconsFont = require("@expo/vector-icons/build/vendor/react-native-vector-icons/Fonts/Ionicons.ttf");

import { theme } from "./ui/theme";
import { ToastProvider } from "./src/state/toast";
import { ActivityProvider } from "./src/state/activity";
import { RootNavigator } from "./src/navigation/RootNavigator";
import { log } from "./src/logger";
import { ErrorBoundary } from "./src/state/ErrorBoundary";
import { addActivity } from "./src/state/activity";
import { loadSecurityConfig, loadAgentConfig, loadIntegrationsConfig } from "./src/state/guappa";
import { subscribeIncomingDeviceEvents } from "./src/native/incomingCalls";
import { getAndroidRuntimeBridgeStatus } from "./src/native/androidAgentBridge";
import { applyRuntimeSupervisorConfig, reportRuntimeHookEvent, startRuntimeSupervisor } from "./src/runtime/supervisor";
import { startAgent, isAgentRunning } from "./src/native/guappaAgent";
import { startLocalLlmServer, stopLocalLlmServer, LOCAL_LLM_URL } from "./src/native/localLlmServer";

// Dev-only: prepopulate credentials for faster local testing
let prepopulateDevCredentials: (() => Promise<void>) | undefined;
if (__DEV__) {
  try {
    prepopulateDevCredentials = require("./scripts/prepopulate-config").prepopulateDevCredentials;
  } catch {
    // File doesn't exist in CI/production builds - that's expected
  }
}

export default function App() {
  const lastTelegramSeenRef = useRef(0);
  const lastWebhookSuccessRef = useRef(0);
  const lastWebhookFailRef = useRef(0);
  const [initError, setInitError] = useState<string | null>(null);
  const [isReady, setIsReady] = useState(false);
  const [daemonReady, setDaemonReady] = useState(false);
  const [fontsLoaded, fontError] = Font.useFonts({
    Inter_400Regular,
    Inter_500Medium,
    SpaceGrotesk_600SemiBold,
    JetBrainsMono_500Medium,
    Orbitron_700Bold,
    Exo2_400Regular,
    Exo2_500Medium,
    Exo2_600SemiBold,
    Ionicons: ioniconsFont,
    ionicons: ioniconsFont,
  });

  useEffect(() => {
    if (!fontsLoaded && !fontError) return;
    console.log("[app] fonts loaded:", fontsLoaded, "error:", fontError);
    // If font error occurred, continue without custom fonts

    if (fontError) {
      console.warn("[app] font loading error (continuing with system fonts)", fontError);
      // Don't block app startup for font errors — fall through to initialization
    }

    // Initialize app
    (async () => {
      try {
        console.log("[app] initializing...");
        log("info", "app started", { platform: Platform.OS });

        // Prepopulate dev credentials if not already configured (idempotent — preserves existing)
        if (prepopulateDevCredentials) {
          console.log("[app] prepopulating dev credentials...");
          await prepopulateDevCredentials();
          log("info", "dev credentials prepopulated");
        }

        // Never block UI on agent startup.
        setIsReady(true);

        // Start Kotlin agent in background (Android only)
        if (Platform.OS === 'android') {
          void (async () => {
            try {
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
                telegramToken: integCfg.telegramEnabled ? integCfg.telegramBotToken : '',
                telegramChatId: integCfg.telegramEnabled ? integCfg.telegramChatId : '',
                discordBotToken: integCfg.discordEnabled ? integCfg.discordBotToken : '',
                slackBotToken: integCfg.slackEnabled ? integCfg.slackBotToken : '',
                composioApiKey: integCfg.composioEnabled ? integCfg.composioApiKey : '',
                braveApiKey: agentCfg.braveApiKey || '',
                localModelPath: agentCfg.localModelPath || '',
                thinkingMode: agentCfg.thinkingMode ?? false,
              };

              // If provider is "local", start the on-device LLM server first
              if (agentConfig.provider === "local" && agentConfig.localModelPath) {
                console.log("[app] starting local LLM server...");
                await startLocalLlmServer({
                  modelPath: agentConfig.localModelPath,
                  gpuLayers: agentCfg.gpuLayers ?? 0,
                  cpuThreads: agentCfg.cpuThreads ?? 4,
                  contextLength: agentCfg.contextLength ?? 2048,
                  thinkingMode: agentCfg.thinkingMode ?? true,
                });
                agentConfig.provider = "openai";
                agentConfig.apiUrl = `${LOCAL_LLM_URL}/v1`;
                agentConfig.apiKey = "local";
                agentConfig.model = "local";
                console.log("[app] local LLM server started");
              }

              console.log("[app] starting Guappa agent...");
              await startAgent(agentConfig);
              setDaemonReady(true);
              log("info", "Guappa agent started");
            } catch (err) {
              console.warn("[app] agent start failed:", err);
              setDaemonReady(false);
              log("warn", "agent start failed", { error: err instanceof Error ? err.message : String(err) });
            }
          })();
        } else {
          setDaemonReady(true);
        }

        console.log("[app] initialization complete");
      } catch (err) {
        console.error("[app] initialization error", err);
        const errorMsg = err instanceof Error ? err.message : String(err);
        setInitError(errorMsg);
        log("error", "app initialization failed", { error: errorMsg });
      }
    })();
  }, [fontsLoaded, fontError]);

  useEffect(() => {
    if (!isReady) return;

    const timer = setInterval(() => {
      void (async () => {
        const status = await getAndroidRuntimeBridgeStatus();
        if (!status) return;

        if (status.telegramSeenCount > lastTelegramSeenRef.current) {
          lastTelegramSeenRef.current = status.telegramSeenCount;
          await addActivity({
            kind: "message",
            source: "runtime",
            title: "Telegram inbound received",
            detail: status.lastEventNote || "Telegram message queued in native bridge",
          });
        }

        if (status.webhookSuccessCount > lastWebhookSuccessRef.current) {
          lastWebhookSuccessRef.current = status.webhookSuccessCount;
          await addActivity({
            kind: "action",
            source: "runtime",
            title: "Bridge forwarded event",
            detail: status.lastEventNote || "Webhook delivery succeeded",
          });
        }

        if (status.webhookFailCount > lastWebhookFailRef.current) {
          lastWebhookFailRef.current = status.webhookFailCount;
          await addActivity({
            kind: "action",
            source: "runtime",
            title: "Bridge forward retry",
            detail: status.lastEventNote || "Webhook delivery failed and will retry",
          });
        }
      })();
    }, 5000);

    return () => clearInterval(timer);
  }, [isReady]);

  useEffect(() => {
    if (!isReady) return;

    const unsubscribe = subscribeIncomingDeviceEvents((event) => {
      void (async () => {
        const security = await loadSecurityConfig();
        if (!security.incomingCallHooks) return;
        const phone = security.includeCallerNumber && event.phone.trim() ? event.phone.trim() : "redacted";
        await reportRuntimeHookEvent("incoming_call", `${event.state} from ${phone}`);
        await addActivity({
          kind: "action",
          source: "device",
          title: "Incoming call hook",
          detail: `${event.state} from ${phone}`,
        });
        // Call events are handled by native Kotlin BroadcastReceiver directly
      })();
    }, (event) => {
      void (async () => {
        const security = await loadSecurityConfig();
        if (!security.incomingSmsHooks) return;
        const address = event.address || "unknown";
        await reportRuntimeHookEvent("incoming_sms", `from ${address}`);
        await addActivity({
          kind: "action",
          source: "device",
          title: "Incoming SMS hook",
          detail: `from ${address}`,
        });
        // SMS events are handled by native Kotlin BroadcastReceiver directly
      })();
    });

    return () => unsubscribe();
  }, [isReady]);

  useEffect(() => {
    if (!isReady) return;

    void startRuntimeSupervisor("app_start");
    const interval = setInterval(() => {
      void applyRuntimeSupervisorConfig("heartbeat");
    }, 30000);

    return () => {
      clearInterval(interval);
    };
  }, [isReady]);

  // Show error screen if initialization failed
  if (initError) {
    return (
      <View style={{ flex: 1, backgroundColor: "#05050A", justifyContent: "center", alignItems: "center", padding: 20 }}>
        <Text style={{ color: "#FF6B6B", fontSize: 20, fontWeight: "bold", marginBottom: 10 }}>
          Initialization Error
        </Text>
        <Text style={{ color: "#F5F0E6", fontSize: 14, textAlign: "center" }}>
          {initError}
        </Text>
        <Text style={{ color: "#A3A3B2", fontSize: 12, marginTop: 20, textAlign: "center" }}>
          Check the console logs for more details
        </Text>
      </View>
    );
  }

  // Show loading screen while fonts are loading or app is initializing
  if (!fontsLoaded || !isReady) {
    return (
      <View style={{ flex: 1, backgroundColor: "#05050A", justifyContent: "center", alignItems: "center" }}>
        <ActivityIndicator size="large" color="#D4F49C" />
        <Text style={{ color: "#A3A3B2", marginTop: 20, fontSize: 14 }}>
          {!fontsLoaded ? "Loading fonts..." : "Initializing app..."}
        </Text>
      </View>
    );
  }

  return (
    <GestureHandlerRootView style={{ flex: 1, backgroundColor: theme.colors.base.background }}>
      <StatusBar style="light" />
      <SafeAreaProvider>
        <ToastProvider>
          <ActivityProvider>
            <NavigationContainer>
              <ErrorBoundary>
                <RootNavigator />
              </ErrorBoundary>
            </NavigationContainer>
          </ActivityProvider>
        </ToastProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
