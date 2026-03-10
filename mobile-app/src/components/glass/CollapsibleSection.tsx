import React, { useState, useCallback } from "react";
import {
  View,
  Text,
  Pressable,
  LayoutAnimation,
  Platform,
  UIManager,
  StyleSheet,
  type ViewStyle,
  type StyleProp,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { LiquidGlassPanel } from "./LiquidGlass";
import { colors } from "../../theme/colors";
import { spacing } from "../../theme/spacing";

if (
  Platform.OS === "android" &&
  UIManager.setLayoutAnimationEnabledExperimental
) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

type Props = {
  title: string;
  icon?: keyof typeof Ionicons.glyphMap;
  children: React.ReactNode;
  defaultExpanded?: boolean;
  style?: StyleProp<ViewStyle>;
  testID?: string;
};

export function CollapsibleSection({
  title,
  icon,
  children,
  defaultExpanded = false,
  style,
  testID,
}: Props) {
  const [expanded, setExpanded] = useState(defaultExpanded);

  const handleToggle = useCallback(() => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    setExpanded((prev) => !prev);
  }, []);

  return (
    <LiquidGlassPanel
      style={style}
      borderRadius={16}
      intensity={expanded ? 0.6 : 0.3}
    >
      <Pressable onPress={handleToggle} style={styles.header} testID={testID}>
        <View style={styles.headerLeft}>
          {icon && (
            <View style={[styles.iconWrap, expanded && styles.iconWrapActive]}>
              <Ionicons
                name={icon}
                size={17}
                color={expanded ? "rgba(160, 195, 210, 0.8)" : "rgba(100, 140, 160, 0.5)"}
              />
            </View>
          )}
          <Text style={[styles.title, expanded && styles.titleExpanded]}>{title}</Text>
        </View>
        <View style={[styles.chevronWrap, expanded && styles.chevronWrapExpanded]}>
          <Ionicons
            name={expanded ? "chevron-up" : "chevron-down"}
            size={15}
            color={expanded ? "rgba(140, 180, 200, 0.6)" : "rgba(80, 110, 130, 0.35)"}
          />
        </View>
      </Pressable>
      {expanded && (
        <>
          <View style={styles.divider} />
          <View style={styles.content}>{children}</View>
        </>
      )}
    </LiquidGlassPanel>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    padding: spacing.md,
  },
  headerLeft: {
    flexDirection: "row",
    alignItems: "center",
    flex: 1,
  },
  iconWrap: {
    width: 30,
    height: 30,
    borderRadius: 8,
    backgroundColor: "rgba(20, 50, 60, 0.3)",
    alignItems: "center",
    justifyContent: "center",
    marginRight: spacing.sm,
  },
  iconWrapActive: {
    backgroundColor: "rgba(25, 60, 70, 0.45)",
  },
  title: {
    color: "rgba(130, 160, 175, 0.55)",
    fontSize: 15,
    fontFamily: "Exo2_500Medium",
    flex: 1,
    letterSpacing: 0.3,
  },
  titleExpanded: {
    color: "rgba(170, 200, 215, 0.8)",
  },
  chevronWrap: {
    width: 26,
    height: 26,
    borderRadius: 13,
    backgroundColor: "rgba(30, 50, 60, 0.25)",
    alignItems: "center",
    justifyContent: "center",
  },
  chevronWrapExpanded: {
    backgroundColor: "rgba(25, 55, 65, 0.35)",
  },
  divider: {
    height: 0.5,
    marginHorizontal: spacing.md,
    backgroundColor: "rgba(60, 90, 110, 0.1)",
  },
  content: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
  },
});
