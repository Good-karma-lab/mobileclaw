import React, { useEffect } from "react";
import { View, Pressable, Text, StyleSheet, Platform } from "react-native";
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withRepeat,
  withSequence,
  withTiming,
  Easing,
} from "react-native-reanimated";
import { BlurView } from "expo-blur";
import { Ionicons } from "@expo/vector-icons";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { springs } from "../../theme/animations";

export type DockTab = {
  key: string;
  icon: string;
  label: string;
};

type Props = {
  tabs: DockTab[];
  activeTab: string;
  onTabPress: (key: string) => void;
};

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

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

function DockTabItem({
  tab,
  isActive,
  onPress,
}: {
  tab: DockTab;
  isActive: boolean;
  onPress: () => void;
}) {
  const scale = useSharedValue(isActive ? 1.1 : 1);
  const glowOpacity = useSharedValue(isActive ? 0.35 : 0);

  useEffect(() => {
    scale.value = withSpring(isActive ? 1.1 : 1, springs.snappy);
    glowOpacity.value = withSpring(isActive ? 0.35 : 0, springs.gentle);
  }, [isActive, scale, glowOpacity]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  const glowStyle = useAnimatedStyle(() => ({
    opacity: glowOpacity.value,
  }));

  const iconName = isActive
    ? (ICON_MAP[tab.key] || "ellipse")
    : (ICON_MAP_OUTLINE[tab.key] || "ellipse-outline");

  return (
    <AnimatedPressable
      testID={`dock-tab-${tab.key}`}
      accessibilityRole="button"
      accessibilityState={isActive ? { selected: true } : {}}
      accessibilityLabel={tab.label}
      onPress={onPress}
      style={[styles.tabButton, animatedStyle]}
    >
      {/* Glow ring behind active icon */}
      <Animated.View style={[styles.glowRing, glowStyle]} />
      <Ionicons
        name={iconName as any}
        size={20}
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
    </AnimatedPressable>
  );
}

export function FloatingDock({ tabs, activeTab, onTabPress }: Props) {
  const insets = useSafeAreaInsets();
  const bottomOffset = Math.max(16, insets.bottom + 16);

  // Subtle breathing animation on the dock
  const breathe = useSharedValue(1);
  useEffect(() => {
    breathe.value = withRepeat(
      withSequence(
        withTiming(1.005, { duration: 4000, easing: Easing.inOut(Easing.sin) }),
        withTiming(0.995, { duration: 4000, easing: Easing.inOut(Easing.sin) })
      ),
      -1,
      false
    );
  }, []);

  const dockBreathStyle = useAnimatedStyle(() => ({
    transform: [{ scale: breathe.value }],
  }));

  return (
    <View
      testID="floating-dock"
      style={[styles.container, { bottom: bottomOffset }]}
    >
      <Animated.View style={[styles.dockWrapper, dockBreathStyle]}>
        <BlurView intensity={40} tint="dark" style={styles.blurWrap}>
          <View style={styles.innerRow}>
            {tabs.map((tab) => (
              <DockTabItem
                key={tab.key}
                tab={tab}
                isActive={activeTab === tab.key}
                onPress={() => onTabPress(tab.key)}
              />
            ))}
          </View>
        </BlurView>
      </Animated.View>
    </View>
  );
}

const DOCK_HEIGHT = 68;

const styles = StyleSheet.create({
  container: {
    position: "absolute",
    left: 20,
    right: 20,
    alignItems: "center",
    zIndex: 999,
    elevation: 20,
  },
  dockWrapper: {
    width: "100%",
    // Shadow for depth on both platforms
    ...Platform.select({
      ios: {
        shadowColor: "#000",
        shadowOffset: { width: 0, height: -4 },
        shadowOpacity: 0.4,
        shadowRadius: 16,
      },
      android: {
        elevation: 20,
      },
    }),
  },
  blurWrap: {
    width: "100%",
    height: DOCK_HEIGHT,
    borderRadius: DOCK_HEIGHT / 2,
    overflow: "hidden",
    backgroundColor: "rgba(15, 15, 40, 0.92)",
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.22)",
  },
  innerRow: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-around",
    paddingHorizontal: 4,
  },
  tabButton: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    height: DOCK_HEIGHT,
    gap: 3,
  },
  tabLabel: {
    fontSize: 9,
    fontFamily: typography.body.fontFamily,
    color: colors.text.tertiary,
    letterSpacing: 0.3,
  },
  tabLabelActive: {
    color: colors.accent.cyan,
  },
  glowRing: {
    position: "absolute",
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: colors.accent.cyan,
  },
});
