import React, { useEffect, useRef } from "react";
import { View, Animated, StyleSheet } from "react-native";

const DOT_SIZE = 8;
const DOT_COUNT = 3;
const ANIMATION_DURATION = 600;

export function TypingIndicator() {
  const dots = useRef(
    Array.from({ length: DOT_COUNT }, () => new Animated.Value(0.3))
  ).current;

  useEffect(() => {
    const animations = dots.map((dot, index) =>
      Animated.loop(
        Animated.sequence([
          Animated.delay(index * 200),
          Animated.timing(dot, {
            toValue: 1,
            duration: ANIMATION_DURATION,
            useNativeDriver: true,
          }),
          Animated.timing(dot, {
            toValue: 0.3,
            duration: ANIMATION_DURATION,
            useNativeDriver: true,
          }),
        ])
      )
    );

    animations.forEach((a) => a.start());

    return () => {
      animations.forEach((a) => a.stop());
    };
  }, [dots]);

  return (
    <View style={styles.container}>
      {dots.map((dot, i) => (
        <Animated.View
          key={i}
          style={[
            styles.dot,
            {
              opacity: dot,
              transform: [
                {
                  scale: dot.interpolate({
                    inputRange: [0.3, 1],
                    outputRange: [0.8, 1.2],
                  }),
                },
              ],
            },
          ]}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingHorizontal: 16,
    paddingVertical: 12,
    alignSelf: "flex-start",
    backgroundColor: "rgba(255,255,255,0.05)",
    borderRadius: 16,
    marginLeft: 12,
    marginVertical: 4,
  },
  dot: {
    width: DOT_SIZE,
    height: DOT_SIZE,
    borderRadius: DOT_SIZE / 2,
    backgroundColor: "#5CC8FF",
  },
});
