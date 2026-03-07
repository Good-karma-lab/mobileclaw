import React from "react";
import { Text, Pressable, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "../../theme/colors";

interface Props {
  label: string;
  icon?: keyof typeof Ionicons.glyphMap;
  onPress?: () => void;
  selected?: boolean;
  color?: string;
}

export function GlassChip({ label, icon, onPress, selected, color = colors.accent.cyan }: Props) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.chip,
        selected && { backgroundColor: `${color}15`, borderColor: `${color}40` },
        pressed && { opacity: 0.8, transform: [{ scale: 0.96 }] },
      ]}
    >
      {icon && <Ionicons name={icon} size={14} color={selected ? color : colors.text.tertiary} />}
      <Text style={[styles.label, selected && { color }]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  chip: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    backgroundColor: "rgba(255,255,255,0.05)",
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.1)",
  },
  label: { color: colors.text.secondary, fontSize: 13, fontWeight: "500" },
});
