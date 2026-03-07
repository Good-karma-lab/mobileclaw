import React, { useState, useCallback } from "react";
import {
  View,
  Pressable,
  LayoutAnimation,
  Platform,
  UIManager,
  StyleSheet,
  type ViewStyle,
  type StyleProp,
} from "react-native";
import { BlurView } from "expo-blur";
import { Ionicons } from "@expo/vector-icons";

import { colors } from "../../theme/colors";
import { spacing } from "../../theme/spacing";

if (
  Platform.OS === "android" &&
  UIManager.setLayoutAnimationEnabledExperimental
) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

type Props = {
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  onPress?: () => void;
  collapsed?: boolean;
  onToggle?: () => void;
  header?: React.ReactNode;
};

export function GlassCard({
  children,
  style,
  onPress,
  collapsed,
  onToggle,
  header,
}: Props) {
  const isCollapsible = onToggle !== undefined;
  const isCollapsed = collapsed ?? false;

  const handleToggle = useCallback(() => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    onToggle?.();
  }, [onToggle]);

  const content = (
    <BlurView intensity={20} tint="dark" style={[styles.blur, style]}>
      <View style={styles.inner}>
        {isCollapsible && header ? (
          <>
            <Pressable onPress={handleToggle} style={styles.headerRow}>
              {header}
              <Ionicons
                name={isCollapsed ? "chevron-down" : "chevron-up"}
                size={18}
                color={colors.text.secondary}
              />
            </Pressable>
            {!isCollapsed && <View style={styles.body}>{children}</View>}
          </>
        ) : (
          children
        )}
      </View>
    </BlurView>
  );

  if (onPress && !isCollapsible) {
    return (
      <Pressable onPress={onPress} style={styles.wrapper}>
        {content}
      </Pressable>
    );
  }

  return <View style={styles.wrapper}>{content}</View>;
}

const styles = StyleSheet.create({
  wrapper: {
    borderRadius: 20,
    overflow: "hidden",
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.10)",
  },
  blur: {
    backgroundColor: "rgba(255, 255, 255, 0.05)",
  },
  inner: {
    padding: spacing.md,
  },
  headerRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  body: {
    marginTop: spacing.sm,
  },
});
