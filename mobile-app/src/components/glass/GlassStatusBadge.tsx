import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { colors } from "../../theme/colors";

type Status = "online" | "offline" | "warning" | "error";

const STATUS_COLORS: Record<Status, string> = {
  online: "#22C55E",
  offline: colors.text.tertiary,
  warning: "#F59E0B",
  error: "#EF4444",
};

interface Props {
  status: Status;
  label?: string;
  size?: "sm" | "md";
}

export function GlassStatusBadge({ status, label, size = "sm" }: Props) {
  const dotSize = size === "sm" ? 8 : 10;

  return (
    <View style={[styles.container, size === "md" && styles.containerMd]}>
      <View
        style={[
          styles.dot,
          { width: dotSize, height: dotSize, borderRadius: dotSize / 2, backgroundColor: STATUS_COLORS[status] },
        ]}
      />
      {label && (
        <Text style={[styles.label, size === "md" && styles.labelMd]}>
          {label}
        </Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    backgroundColor: "rgba(255,255,255,0.05)",
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.08)",
  },
  containerMd: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 14 },
  dot: {},
  label: { color: colors.text.secondary, fontSize: 11, fontWeight: "600" },
  labelMd: { fontSize: 13 },
});
