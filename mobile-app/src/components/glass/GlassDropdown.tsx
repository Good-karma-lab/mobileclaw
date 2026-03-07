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

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing } from "../../theme/spacing";

if (
  Platform.OS === "android" &&
  UIManager.setLayoutAnimationEnabledExperimental
) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

type Option = {
  label: string;
  value: string;
};

type Props = {
  label?: string;
  value: string;
  options: Option[];
  onValueChange: (value: string) => void;
  placeholder?: string;
  style?: StyleProp<ViewStyle>;
};

export function GlassDropdown({
  label,
  value,
  options,
  onValueChange,
  placeholder = "Select...",
  style,
}: Props) {
  const [expanded, setExpanded] = useState(false);

  const selectedOption = options.find((o) => o.value === value);
  const displayText = selectedOption?.label ?? placeholder;

  const handleToggle = useCallback(() => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    setExpanded((prev) => !prev);
  }, []);

  const handleSelect = useCallback(
    (optionValue: string) => {
      LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
      onValueChange(optionValue);
      setExpanded(false);
    },
    [onValueChange],
  );

  return (
    <View style={[styles.container, style]}>
      {label && <Text style={styles.label}>{label}</Text>}
      <Pressable onPress={handleToggle} style={styles.pill}>
        <Text
          style={[
            styles.pillText,
            !selectedOption && styles.placeholderText,
          ]}
          numberOfLines={1}
        >
          {displayText}
        </Text>
        <Ionicons
          name={expanded ? "chevron-up" : "chevron-down"}
          size={16}
          color={colors.text.secondary}
        />
      </Pressable>
      {expanded && (
        <View style={styles.optionsList}>
          {options.map((option) => {
            const isSelected = option.value === value;
            return (
              <Pressable
                key={option.value}
                onPress={() => handleSelect(option.value)}
                style={[
                  styles.optionItem,
                  isSelected && styles.optionItemSelected,
                ]}
              >
                <Text
                  style={[
                    styles.optionText,
                    isSelected && styles.optionTextSelected,
                  ]}
                >
                  {option.label}
                </Text>
                {isSelected && (
                  <Ionicons
                    name="checkmark"
                    size={16}
                    color={colors.accent.cyan}
                  />
                )}
              </Pressable>
            );
          })}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginBottom: spacing.sm,
  },
  label: {
    color: colors.text.secondary,
    fontSize: 12,
    fontFamily: typography.bodySemiBold.fontFamily,
    marginBottom: spacing.xs,
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },
  pill: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    backgroundColor: "rgba(255, 255, 255, 0.05)",
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.10)",
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  pillText: {
    color: colors.text.primary,
    fontSize: 15,
    fontFamily: typography.body.fontFamily,
    flex: 1,
    marginRight: spacing.sm,
  },
  placeholderText: {
    color: colors.text.tertiary,
  },
  optionsList: {
    marginTop: spacing.xs,
    backgroundColor: "rgba(255, 255, 255, 0.06)",
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.10)",
    borderRadius: 12,
    overflow: "hidden",
  },
  optionItem: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  optionItemSelected: {
    backgroundColor: "rgba(0, 240, 255, 0.08)",
  },
  optionText: {
    color: colors.text.primary,
    fontSize: 15,
    fontFamily: typography.body.fontFamily,
  },
  optionTextSelected: {
    color: colors.accent.cyan,
  },
});
