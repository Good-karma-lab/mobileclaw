import React, { useEffect, useRef } from "react";
import { View, Animated, StyleSheet } from "react-native";
import { colors } from "../../theme/colors";

interface Props {
  progress: number; // 0-1
  color?: string;
  height?: number;
  animated?: boolean;
}

export function GlassProgressBar({
  progress,
  color = colors.accent.cyan,
  height = 6,
  animated = true,
}: Props) {
  const widthAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (animated) {
      Animated.spring(widthAnim, {
        toValue: Math.max(0, Math.min(1, progress)),
        tension: 40,
        friction: 10,
        useNativeDriver: false,
      }).start();
    } else {
      widthAnim.setValue(progress);
    }
  }, [progress, animated, widthAnim]);

  return (
    <View style={[styles.track, { height, borderRadius: height / 2 }]}>
      <Animated.View
        style={[
          styles.fill,
          {
            height,
            borderRadius: height / 2,
            backgroundColor: color,
            width: widthAnim.interpolate({
              inputRange: [0, 1],
              outputRange: ["0%", "100%"],
            }),
          },
        ]}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  track: {
    width: "100%",
    backgroundColor: "rgba(255,255,255,0.06)",
    overflow: "hidden",
  },
  fill: {},
});
