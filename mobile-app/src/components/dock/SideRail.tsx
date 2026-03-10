import React from "react";
import { View, Pressable, Text, StyleSheet } from "react-native";
import { BlurView } from "expo-blur";
import { Ionicons } from "@expo/vector-icons";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import type { DockTab } from "./FloatingDock";

type Props = {
  tabs: DockTab[];
  activeTab: string;
  onTabPress: (key: string) => void;
};

const ICON_MAP: Record<string, keyof typeof Ionicons.glyphMap> = {
  voice: "mic",
  chat: "chatbubble",
  command: "flash",
  swarm: "globe",
  config: "options",
};

const ICON_MAP_OUTLINE: Record<string, keyof typeof Ionicons.glyphMap> = {
  voice: "mic-outline",
  chat: "chatbubble-outline",
  command: "flash-outline",
  swarm: "globe-outline",
  config: "options-outline",
};

export function SideRail({ tabs, activeTab, onTabPress }: Props) {
  const insets = useSafeAreaInsets();

  return (
    <View testID="side-rail" style={styles.container}>
      <BlurView
        intensity={28}
        tint="dark"
        style={[
          styles.blurWrap,
          { paddingTop: insets.top + 12, paddingBottom: insets.bottom + 12 },
        ]}
      >
        {tabs.map((tab) => {
          const isActive = activeTab === tab.key;
          const iconName = isActive
            ? (ICON_MAP[tab.key] || "ellipse")
            : (ICON_MAP_OUTLINE[tab.key] || "ellipse-outline");

          return (
            <Pressable
              key={tab.key}
              testID={`rail-tab-${tab.key}`}
              accessibilityRole="button"
              accessibilityState={isActive ? { selected: true } : {}}
              accessibilityLabel={tab.label}
              onPress={() => onTabPress(tab.key)}
              style={({ pressed }) => [
                styles.tabButton,
                { opacity: pressed ? 0.7 : 1 },
              ]}
            >
              {isActive && <View style={styles.activeBar} />}
              <Ionicons
                name={iconName as any}
                size={22}
                color={isActive ? colors.accent.cyan : colors.text.secondary}
              />
              <Text
                style={[
                  styles.tabLabel,
                  isActive && styles.tabLabelActive,
                ]}
              >
                {tab.label}
              </Text>
            </Pressable>
          );
        })}
      </BlurView>
    </View>
  );
}

const RAIL_WIDTH = 72;

const styles = StyleSheet.create({
  container: {
    position: "absolute",
    left: 0,
    top: 0,
    bottom: 0,
    width: RAIL_WIDTH,
    zIndex: 100,
  },
  blurWrap: {
    flex: 1,
    backgroundColor: "rgba(15, 35, 50, 0.60)",
    borderRightWidth: 1,
    borderRightColor: colors.glass.borderSubtle,
    alignItems: "center",
    justifyContent: "center",
    gap: 4,
  },
  tabButton: {
    width: RAIL_WIDTH,
    height: 60,
    alignItems: "center",
    justifyContent: "center",
    position: "relative",
    gap: 4,
  },
  activeBar: {
    position: "absolute",
    left: 0,
    top: 12,
    bottom: 12,
    width: 3,
    borderRadius: 2,
    backgroundColor: colors.accent.cyan,
  },
  tabLabel: {
    fontSize: 10,
    fontFamily: typography.body.fontFamily,
    color: colors.text.tertiary,
  },
  tabLabelActive: {
    color: colors.accent.cyan,
  },
});
