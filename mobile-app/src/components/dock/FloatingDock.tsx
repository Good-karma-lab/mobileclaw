import React from "react";
import { View, Pressable, Text, StyleSheet } from "react-native";
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
} from "react-native-reanimated";
import { BlurView } from "expo-blur";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { colors } from "../../theme/colors";
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

function DockTabItem({
  tab,
  isActive,
  onPress,
}: {
  tab: DockTab;
  isActive: boolean;
  onPress: () => void;
}) {
  const scale = useSharedValue(isActive ? 1.15 : 1);

  React.useEffect(() => {
    scale.value = withSpring(isActive ? 1.15 : 1, springs.gentle);
  }, [isActive, scale]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  return (
    <AnimatedPressable
      testID={`dock-tab-${tab.key}`}
      accessibilityRole="button"
      accessibilityState={isActive ? { selected: true } : {}}
      accessibilityLabel={tab.label}
      onPress={onPress}
      style={[styles.tabButton, animatedStyle]}
    >
      <Text
        style={[
          styles.tabIcon,
          { opacity: isActive ? 1 : 0.5 },
        ]}
      >
        {tab.icon}
      </Text>
    </AnimatedPressable>
  );
}

export function FloatingDock({ tabs, activeTab, onTabPress }: Props) {
  const insets = useSafeAreaInsets();
  const bottomOffset = Math.max(16, insets.bottom + 16);

  return (
    <View
      testID="floating-dock"
      pointerEvents="box-none"
      style={[styles.container, { bottom: bottomOffset }]}
    >
      <BlurView
        intensity={28}
        tint="dark"
        style={styles.blurWrap}
      >
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
    </View>
  );
}

const DOCK_HEIGHT = 56;

const styles = StyleSheet.create({
  container: {
    position: "absolute",
    left: 16,
    right: 16,
    alignItems: "center",
  },
  blurWrap: {
    width: "100%",
    height: DOCK_HEIGHT,
    borderRadius: DOCK_HEIGHT / 2,
    overflow: "hidden",
    backgroundColor: colors.glass.fill,
    borderWidth: 1,
    borderColor: colors.glass.border,
  },
  innerRow: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-around",
    paddingHorizontal: 12,
  },
  tabButton: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    height: DOCK_HEIGHT,
  },
  tabIcon: {
    fontSize: 22,
  },
});
