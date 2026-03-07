import React from "react";
import { View, Text, Image, StyleSheet } from "react-native";
import { colors } from "../../theme/colors";

interface Props {
  name: string;
  imageUri?: string;
  size?: number;
  online?: boolean;
}

const GRADIENT_COLORS = ["#8B5CF6", "#5CC8FF", "#D4F49C", "#EC4899", "#F59E0B"];

export function GlassAvatar({ name, imageUri, size = 44, online }: Props) {
  const initials = name
    .split(" ")
    .map((w) => w[0])
    .join("")
    .toUpperCase()
    .slice(0, 2);
  const colorIndex = name.split("").reduce((a, c) => a + c.charCodeAt(0), 0) % GRADIENT_COLORS.length;

  return (
    <View style={[styles.container, { width: size, height: size, borderRadius: size / 2 }]}>
      {imageUri ? (
        <Image source={{ uri: imageUri }} style={[styles.image, { width: size, height: size, borderRadius: size / 2 }]} />
      ) : (
        <View style={[styles.placeholder, { width: size, height: size, borderRadius: size / 2, backgroundColor: `${GRADIENT_COLORS[colorIndex]}30` }]}>
          <Text style={[styles.initials, { fontSize: size * 0.38, color: GRADIENT_COLORS[colorIndex] }]}>{initials}</Text>
        </View>
      )}
      {online !== undefined && (
        <View style={[styles.indicator, { backgroundColor: online ? "#22C55E" : colors.text.tertiary }]} />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { position: "relative", borderWidth: 1.5, borderColor: "rgba(255,255,255,0.12)" },
  image: {},
  placeholder: { alignItems: "center", justifyContent: "center" },
  initials: { fontWeight: "700" },
  indicator: {
    position: "absolute", bottom: 0, right: 0,
    width: 12, height: 12, borderRadius: 6,
    borderWidth: 2, borderColor: "rgba(5,5,10,1)",
  },
});
