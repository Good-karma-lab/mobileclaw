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
import { Ionicons } from "@expo/vector-icons";

import { LiquidGlassView } from "./LiquidGlass";
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
  testID?: string;
};

export function GlassCard({
  children,
  style,
  onPress,
  collapsed,
  onToggle,
  header,
  testID,
}: Props) {
  const isCollapsible = onToggle !== undefined;
  const isCollapsed = collapsed ?? false;

  const handleToggle = useCallback(() => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    onToggle?.();
  }, [onToggle]);

  const content = (
    <LiquidGlassView style={style} testID={testID} intensity={0.5} borderRadius={16}>
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
    </LiquidGlassView>
  );

  if (onPress && !isCollapsible) {
    return (
      <Pressable onPress={onPress}>
        {content}
      </Pressable>
    );
  }

  return content;
}

const styles = StyleSheet.create({
  headerRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  body: {
    marginTop: spacing.sm,
  },
});
