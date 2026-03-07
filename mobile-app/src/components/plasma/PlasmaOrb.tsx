import React, { useEffect, useRef } from "react";
import { View, Animated, StyleSheet, Easing } from "react-native";
import { LinearGradient } from "expo-linear-gradient";

interface PlasmaOrbProps {
  size?: number;
  state?: "idle" | "listening" | "thinking" | "speaking";
  audioLevel?: number; // 0-1
}

/**
 * Animated plasma orb visualization.
 * Uses Animated API with gradient overlay.
 * Will be upgraded to Skia GLSL shader when @shopify/react-native-skia is installed.
 */
export function PlasmaOrb({
  size = 280,
  state = "idle",
  audioLevel = 0,
}: PlasmaOrbProps) {
  const pulseAnim = useRef(new Animated.Value(1)).current;
  const rotateAnim = useRef(new Animated.Value(0)).current;
  const glowAnim = useRef(new Animated.Value(0.3)).current;
  const breatheAnim = useRef(new Animated.Value(0.95)).current;

  // Continuous slow rotation
  useEffect(() => {
    const rotation = Animated.loop(
      Animated.timing(rotateAnim, {
        toValue: 1,
        duration: 8000,
        easing: Easing.linear,
        useNativeDriver: true,
      })
    );
    rotation.start();
    return () => rotation.stop();
  }, [rotateAnim]);

  // Breathing pulse (idle)
  useEffect(() => {
    const breathe = Animated.loop(
      Animated.sequence([
        Animated.timing(breatheAnim, {
          toValue: 1.05,
          duration: 3000,
          easing: Easing.inOut(Easing.sin),
          useNativeDriver: true,
        }),
        Animated.timing(breatheAnim, {
          toValue: 0.95,
          duration: 3000,
          easing: Easing.inOut(Easing.sin),
          useNativeDriver: true,
        }),
      ])
    );
    breathe.start();
    return () => breathe.stop();
  }, [breatheAnim]);

  // State-based animations
  useEffect(() => {
    const configs: Record<string, { scale: number; glow: number; duration: number }> = {
      idle: { scale: 1, glow: 0.3, duration: 1000 },
      listening: { scale: 1.1, glow: 0.7, duration: 300 },
      thinking: { scale: 0.95, glow: 0.5, duration: 600 },
      speaking: { scale: 1.15, glow: 0.9, duration: 200 },
    };

    const config = configs[state] || configs.idle;

    Animated.parallel([
      Animated.spring(pulseAnim, {
        toValue: config.scale + audioLevel * 0.2,
        tension: 40,
        friction: 7,
        useNativeDriver: true,
      }),
      Animated.timing(glowAnim, {
        toValue: config.glow + audioLevel * 0.3,
        duration: config.duration,
        useNativeDriver: true,
      }),
    ]).start();
  }, [state, audioLevel, pulseAnim, glowAnim]);

  const rotate = rotateAnim.interpolate({
    inputRange: [0, 1],
    outputRange: ["0deg", "360deg"],
  });

  const orbSize = size * 0.7;
  const glowSize = size;

  return (
    <View style={[styles.container, { width: size, height: size }]}>
      {/* Outer glow */}
      <Animated.View
        style={[
          styles.glow,
          {
            width: glowSize,
            height: glowSize,
            borderRadius: glowSize / 2,
            opacity: glowAnim,
            transform: [{ scale: breatheAnim }],
          },
        ]}
      />

      {/* Main orb */}
      <Animated.View
        style={[
          {
            width: orbSize,
            height: orbSize,
            borderRadius: orbSize / 2,
            overflow: "hidden",
            transform: [{ scale: pulseAnim }, { rotate }],
          },
        ]}
      >
        <LinearGradient
          colors={[
            "#00F0FF",
            "#8B5CF6",
            "#D4F49C",
            "#5CC8FF",
          ]}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
          style={StyleSheet.absoluteFill}
        />
        {/* Inner glass overlay */}
        <View style={styles.innerGlass} />
      </Animated.View>

      {/* Inner glow ring */}
      <Animated.View
        style={[
          styles.innerRing,
          {
            width: orbSize + 20,
            height: orbSize + 20,
            borderRadius: (orbSize + 20) / 2,
            opacity: Animated.multiply(glowAnim, new Animated.Value(0.5)),
            transform: [{ scale: pulseAnim }],
          },
        ]}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: "center",
    justifyContent: "center",
  },
  glow: {
    position: "absolute",
    backgroundColor: "rgba(0, 240, 255, 0.15)",
  },
  innerGlass: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "rgba(255, 255, 255, 0.08)",
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.15)",
  },
  innerRing: {
    position: "absolute",
    borderWidth: 2,
    borderColor: "rgba(0, 240, 255, 0.3)",
  },
});
