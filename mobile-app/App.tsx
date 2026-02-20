import "react-native-gesture-handler";

import React, { useEffect, useRef, useState } from "react";
import { Platform, View, ActivityIndicator, Text } from "react-native";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { NavigationContainer } from "@react-navigation/native";
import { StatusBar } from "expo-status-bar";
import * as Font from "expo-font";
import { SafeAreaProvider } from "react-native-safe-area-context";

import { Inter_400Regular, Inter_500Medium } from "@expo-google-fonts/inter";
import { SpaceGrotesk_600SemiBold } from "@expo-google-fonts/space-grotesk";
import { JetBrainsMono_500Medium } from "@expo-google-fonts/jetbrains-mono";

import { theme } from "./ui/theme";
import { ToastProvider } from "./src/state/toast";
import { ActivityProvider } from "./src/state/activity";
import { RootNavigator } from "./src/navigation/RootNavigator";
import { log } from "./src/logger";
import { ErrorBoundary } from "./src/state/ErrorBoundary";
import { addActivity } from "./src/state/activity";
import { loadSecurityConfig } from "./src/state/mobileclaw";
import { subscribeIncomingDeviceEvents } from "./src/native/incomingCalls";
import { getAndroidRuntimeBridgeStatus } from "./src/native/androidAgentBridge";
import { applyRuntimeSupervisorConfig, reportRuntimeHookEvent, startRuntimeSupervisor } from "./src/runtime/supervisor";
import { prepopulateDevCredentials } from "./scripts/prepopulate-config";

export default function App() {
  const lastTelegramSeenRef = useRef(0);
  const lastWebhookSuccessRef = useRef(0);
  const lastWebhookFailRef = useRef(0);
  const [initError, setInitError] = useState<string | null>(null);
  const [isReady, setIsReady] = useState(false);
  const [fontsLoaded, fontError] = Font.useFonts({
    Inter_400Regular,
    Inter_500Medium,
    SpaceGrotesk_600SemiBold,
    JetBrainsMono_500Medium
  });

  useEffect(() => {
    if (!fontsLoaded && !fontError) return;

    if (fontError) {
      console.error("[app] font loading error", fontError);
      setInitError(`Font loading failed: ${fontError.message}`);
      return;
    }

    // Initialize app
    (async () => {
      try {
        console.log("[app] initializing...");
        log("info", "app started", { platform: Platform.OS });

        // Prepopulate dev credentials automatically in dev mode
        if (__DEV__) {
          console.log("[app] prepopulating dev credentials...");
          await prepopulateDevCredentials();
          log("info", "dev credentials prepopulated");
        }

        console.log("[app] initialization complete");
        setIsReady(true);
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
        const suffix = security.includeCallerNumber && event.phone.trim() ? event.phone.trim() : "redacted";
        await reportRuntimeHookEvent("incoming_call", `${event.state} from ${suffix}`);
        await addActivity({
          kind: "action",
          source: "device",
          title: "Incoming call hook",
          detail: `${event.state} from ${suffix}`,
        });
      })();
    }, (event) => {
      void (async () => {
        const security = await loadSecurityConfig();
        if (!security.incomingSmsHooks) return;
        await reportRuntimeHookEvent("incoming_sms", `from ${event.address || "unknown"}`);
        await addActivity({
          kind: "action",
          source: "device",
          title: "Incoming SMS hook",
          detail: `from ${event.address || "unknown"}`,
        });
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
