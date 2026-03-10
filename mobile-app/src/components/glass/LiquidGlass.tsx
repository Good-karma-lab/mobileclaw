/**
 * LiquidGlass — Skia-powered glass morphism component.
 *
 * Renders a Skia Canvas as background with:
 * - Rounded rect with inner blur/glow
 * - Glass refraction highlight along top edge
 * - Subtle animated shimmer
 * - Edge border glow
 *
 * RN children render on top of the glass surface.
 */
import React, { useState, useMemo, useEffect } from "react";
import {
  View,
  StyleSheet,
  type ViewStyle,
  type StyleProp,
  type LayoutChangeEvent,
} from "react-native";
import {
  Canvas,
  RoundedRect,
  LinearGradient as SkLinearGradient,
  vec,
  Group,
  Skia,
} from "@shopify/react-native-skia";
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withRepeat,
  withTiming,
  Easing,
} from "react-native-reanimated";

interface Props {
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  borderRadius?: number;
  intensity?: number; // 0-1, glass brightness
  testID?: string;
  padding?: number;
}

export function LiquidGlassView({
  children,
  style,
  borderRadius = 16,
  intensity = 0.5,
  testID,
  padding = 16,
}: Props) {
  const [size, setSize] = useState({ w: 0, h: 0 });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (Math.abs(width - size.w) > 1 || Math.abs(height - size.h) > 1) {
      setSize({ w: width, h: height });
    }
  };

  // Subtle shimmer animation
  const shimmer = useSharedValue(0);
  useEffect(() => {
    shimmer.value = withRepeat(
      withTiming(1, { duration: 6000, easing: Easing.inOut(Easing.sin) }),
      -1,
      true,
    );
  }, []);

  const shimmerStyle = useAnimatedStyle(() => ({
    opacity: 0.02 + shimmer.value * 0.03 * intensity,
  }));

  // Glass fill — brighter tint, more transparent (dark glass with shine)
  const fillAlpha = (0.25 + intensity * 0.15).toFixed(3);
  const borderAlpha = (0.10 + intensity * 0.12).toFixed(3);
  const highlightAlpha = 0.06 + intensity * 0.10;

  const { w, h } = size;

  return (
    <View style={[styles.wrapper, { borderRadius }, style]} testID={testID} onLayout={onLayout}>
      {/* Skia glass background */}
      {w > 0 && h > 0 && (
        <Canvas style={[StyleSheet.absoluteFill, { borderRadius }]} pointerEvents="none">
          {/* Dark glass fill — slightly brighter base */}
          <RoundedRect
            x={0} y={0} width={w} height={h}
            r={borderRadius}
            color={`rgba(18, 40, 55, ${fillAlpha})`}
          />

          {/* Top edge highlight — glass refraction line */}
          <RoundedRect
            x={1} y={1} width={w - 2} height={h - 2}
            r={borderRadius - 1}
          >
            <SkLinearGradient
              start={vec(0, 0)}
              end={vec(0, Math.min(60, h * 0.35))}
              colors={[
                `rgba(140, 200, 220, ${highlightAlpha.toFixed(3)})`,
                `rgba(80, 140, 160, ${(highlightAlpha * 0.35).toFixed(3)})`,
                "rgba(0, 0, 0, 0)",
              ]}
            />
          </RoundedRect>

          {/* Left edge subtle highlight */}
          <RoundedRect
            x={0} y={0} width={w} height={h}
            r={borderRadius}
          >
            <SkLinearGradient
              start={vec(0, 0)}
              end={vec(Math.min(30, w * 0.15), h)}
              colors={[
                `rgba(120, 180, 200, ${(highlightAlpha * 0.35).toFixed(3)})`,
                "rgba(0, 0, 0, 0)",
              ]}
            />
          </RoundedRect>

          {/* Bottom-right subtle dark shadow */}
          <RoundedRect
            x={0} y={0} width={w} height={h}
            r={borderRadius}
          >
            <SkLinearGradient
              start={vec(w * 0.5, h * 0.6)}
              end={vec(w, h)}
              colors={[
                "rgba(0, 0, 0, 0)",
                `rgba(0, 0, 0, ${(0.02 + intensity * 0.03).toFixed(3)})`,
              ]}
            />
          </RoundedRect>

          {/* Outer border glow — visible glass edge */}
          <RoundedRect
            x={0.5} y={0.5} width={w - 1} height={h - 1}
            r={borderRadius}
            style="stroke"
            strokeWidth={0.75}
            color={`rgba(100, 190, 220, ${borderAlpha})`}
          />
        </Canvas>
      )}

      {/* Animated shimmer overlay */}
      <Animated.View
        style={[StyleSheet.absoluteFill, { borderRadius, overflow: "hidden" }, shimmerStyle]}
        pointerEvents="none"
      >
        <View style={[styles.shimmerGradient, { borderRadius }]} />
      </Animated.View>

      {/* RN children on top */}
      <View style={[styles.content, { padding }]}>
        {children}
      </View>
    </View>
  );
}

/**
 * LiquidGlassPanel — a flat liquid glass surface (no padding, for custom layouts).
 */
export function LiquidGlassPanel({
  children,
  style,
  borderRadius = 16,
  intensity = 0.5,
  testID,
}: Omit<Props, "padding">) {
  return (
    <LiquidGlassView
      style={style}
      borderRadius={borderRadius}
      intensity={intensity}
      testID={testID}
      padding={0}
    >
      {children}
    </LiquidGlassView>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    overflow: "hidden",
    marginBottom: 12,
  },
  content: {
    zIndex: 1,
  },
  shimmerGradient: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "rgba(100, 140, 160, 0.02)",
  },
});
