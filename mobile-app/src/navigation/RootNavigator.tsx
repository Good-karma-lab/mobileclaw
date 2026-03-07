import React, { useState, useEffect, useCallback } from "react";
import { View, StatusBar, ActivityIndicator, StyleSheet } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import AsyncStorage from "@react-native-async-storage/async-storage";

import { LayoutProvider, useLayoutContext } from "../state/layout";
import { FloatingDock, type DockTab } from "../components/dock/FloatingDock";
import { SideRail } from "../components/dock/SideRail";

import { VoiceScreen } from "../screens/tabs/VoiceScreen";
import { ChatScreen } from "../screens/tabs/ChatScreen";
import { CommandScreen } from "../screens/tabs/CommandScreen";
import { SwarmScreen } from "../screens/tabs/SwarmScreen";
import { ConfigScreen } from "../screens/tabs/ConfigScreen";
import {
  OnboardingScreen,
  ONBOARDING_COMPLETE_KEY,
} from "../screens/OnboardingScreen";

import { colors } from "../theme/colors";

const TABS: DockTab[] = [
  { key: "voice", icon: "\uD83C\uDF99", label: "Voice" },
  { key: "chat", icon: "\uD83D\uDCAC", label: "Chat" },
  { key: "command", icon: "\u26A1", label: "Command" },
  { key: "swarm", icon: "\uD83C\uDF10", label: "Swarm" },
  { key: "config", icon: "\u2699", label: "Config" },
];

const SCREENS: Record<string, React.ComponentType> = {
  voice: VoiceScreen,
  chat: ChatScreen,
  command: CommandScreen,
  swarm: SwarmScreen,
  config: ConfigScreen,
};

const RAIL_WIDTH = 72;

function NavigatorContent() {
  const { useSidebar } = useLayoutContext();
  const [activeTab, setActiveTab] = useState("voice");

  const ActiveScreen = SCREENS[activeTab] ?? VoiceScreen;

  return (
    <LinearGradient
      colors={[colors.base.spaceBlack, colors.base.midnightBlue]}
      style={styles.container}
    >
      <StatusBar
        barStyle="light-content"
        translucent
        backgroundColor="transparent"
      />

      {/* Screen content */}
      <View
        style={[
          styles.content,
          useSidebar && { paddingLeft: RAIL_WIDTH },
        ]}
      >
        <ActiveScreen />
      </View>

      {/* Navigation */}
      {useSidebar ? (
        <SideRail
          tabs={TABS}
          activeTab={activeTab}
          onTabPress={setActiveTab}
        />
      ) : (
        <FloatingDock
          tabs={TABS}
          activeTab={activeTab}
          onTabPress={setActiveTab}
        />
      )}
    </LinearGradient>
  );
}

export function RootNavigator() {
  const [loading, setLoading] = useState(true);
  const [onboardingComplete, setOnboardingComplete] = useState(false);

  useEffect(() => {
    AsyncStorage.getItem(ONBOARDING_COMPLETE_KEY).then((value) => {
      setOnboardingComplete(value === "true");
      setLoading(false);
    });
  }, []);

  const handleOnboardingComplete = useCallback(() => {
    setOnboardingComplete(true);
  }, []);

  if (loading) {
    return (
      <LinearGradient
        colors={[colors.base.spaceBlack, colors.base.midnightBlue]}
        style={styles.loading}
      >
        <StatusBar
          barStyle="light-content"
          translucent
          backgroundColor="transparent"
        />
        <ActivityIndicator size="large" color={colors.accent.cyan} />
      </LinearGradient>
    );
  }

  if (!onboardingComplete) {
    return <OnboardingScreen onComplete={handleOnboardingComplete} />;
  }

  return (
    <LayoutProvider>
      <NavigatorContent />
    </LayoutProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    flex: 1,
  },
  loading: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
});
