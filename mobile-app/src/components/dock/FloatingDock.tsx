import React, { useEffect } from "react";
import { View, Pressable, StyleSheet, Platform } from "react-native";
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

import { springs } from "../../theme/animations";
import { colors } from "../../theme/colors";

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
  const scale = useSharedValue(isActive ? 1.25 : 1);
  const glowOpacity = useSharedValue(isActive ? 1 : 0);

  useEffect(() => {
    scale.value = withSpring(isActive ? 1.25 : 1, springs.snappy);
    glowOpacity.value = withSpring(isActive ? 1 : 0, springs.gentle);
  }, [isActive, scale, glowOpacity]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  // Very subtle dark glow behind active icon
  const glowStyle = useAnimatedStyle(() => ({
    opacity: glowOpacity.value * 0.35,
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
      {/* Subtle dark glow behind active icon */}
      <Animated.View style={[styles.activeGlow, glowStyle]} />
      <Ionicons
        name={iconName as any}
        size={24}
        color={isActive ? colors.accent.cyan : colors.text.secondary}
      />
    </AnimatedPressable>
  );
}

export function FloatingDock({ tabs, activeTab, onTabPress }: Props) {
  const insets = useSafeAreaInsets();
  const bottomOffset = Math.max(16, insets.bottom + 12);

  const breathe = useSharedValue(1);
  useEffect(() => {
    breathe.value = withRepeat(
      withSequence(
        withTiming(1.003, { duration: 4000, easing: Easing.inOut(Easing.sin) }),
        withTiming(0.997, { duration: 4000, easing: Easing.inOut(Easing.sin) })
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
        <BlurView intensity={50} tint="dark" style={styles.blurWrap}>
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

const DOCK_HEIGHT = 56;

const styles = StyleSheet.create({
  container: {
    position: "absolute",
    left: 40,
    right: 40,
    alignItems: "center",
    zIndex: 999,
    elevation: 20,
  },
  dockWrapper: {
    width: "100%",
    ...Platform.select({
      ios: {
        shadowColor: "#000",
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.3,
        shadowRadius: 12,
      },
      android: {
        elevation: 16,
      },
    }),
  },
  blurWrap: {
    width: "100%",
    height: DOCK_HEIGHT,
    borderRadius: DOCK_HEIGHT / 2,
    overflow: "hidden",
    backgroundColor: "rgba(6, 13, 20, 0.94)",
    borderWidth: 1,
    borderColor: colors.glass.borderSubtle,
  },
  innerRow: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-around",
    paddingHorizontal: 8,
  },
  tabButton: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    height: DOCK_HEIGHT,
  },
  activeGlow: {
    position: "absolute",
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: "rgba(93, 212, 232, 0.15)",
  },
});
