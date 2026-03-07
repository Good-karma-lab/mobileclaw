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
import { BlurView } from "expo-blur";
import { Ionicons } from "@expo/vector-icons";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
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
};

export function CollapsibleSection({
  title,
  icon,
  children,
  defaultExpanded = false,
  style,
}: Props) {
  const [expanded, setExpanded] = useState(defaultExpanded);

  const handleToggle = useCallback(() => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    setExpanded((prev) => !prev);
  }, []);

  return (
    <View style={[styles.wrapper, style]}>
      <BlurView intensity={20} tint="dark" style={styles.blur}>
        <Pressable onPress={handleToggle} style={styles.header}>
          <View style={styles.headerLeft}>
            {icon && (
              <Ionicons
                name={icon}
                size={20}
                color={colors.accent.cyan}
                style={styles.icon}
              />
            )}
            <Text style={styles.title}>{title}</Text>
          </View>
          <Ionicons
            name={expanded ? "chevron-up" : "chevron-down"}
            size={18}
            color={colors.text.secondary}
          />
        </Pressable>
        {expanded && <View style={styles.content}>{children}</View>}
      </BlurView>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    borderRadius: 20,
    overflow: "hidden",
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.10)",
    marginBottom: spacing.md,
  },
  blur: {
    backgroundColor: "rgba(255, 255, 255, 0.05)",
  },
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
  icon: {
    marginRight: spacing.sm,
  },
  title: {
    color: colors.text.primary,
    fontSize: 16,
    fontFamily: typography.bodySemiBold.fontFamily,
    flex: 1,
  },
  content: {
    paddingHorizontal: spacing.md,
    paddingBottom: spacing.md,
  },
});
