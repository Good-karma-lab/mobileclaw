import React from "react";
import { View, Pressable, Text, StyleSheet } from "react-native";
import { BlurView } from "expo-blur";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { colors } from "../../theme/colors";
import type { DockTab } from "./FloatingDock";

type Props = {
  tabs: DockTab[];
  activeTab: string;
  onTabPress: (key: string) => void;
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
              <Text
                style={[
                  styles.tabIcon,
                  { opacity: isActive ? 1 : 0.5 },
                ]}
              >
                {tab.icon}
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
    backgroundColor: colors.glass.fill,
    borderRightWidth: 1,
    borderRightColor: colors.glass.border,
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
  },
  tabButton: {
    width: RAIL_WIDTH,
    height: 56,
    alignItems: "center",
    justifyContent: "center",
    position: "relative",
  },
  activeBar: {
    position: "absolute",
    left: 0,
    top: 10,
    bottom: 10,
    width: 3,
    borderRadius: 2,
    backgroundColor: colors.accent.cyan,
  },
  tabIcon: {
    fontSize: 22,
  },
});
