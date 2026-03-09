import React, { useCallback } from "react";
import {
  View,
  Text,
  StyleSheet,
  PanResponder,
  type LayoutChangeEvent,
  type ViewStyle,
  type StyleProp,
} from "react-native";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing } from "../../theme/spacing";

type Props = {
  label?: string;
  value: number;
  onValueChange: (value: number) => void;
  min: number;
  max: number;
  step?: number;
  unit?: string;
  style?: StyleProp<ViewStyle>;
};

const THUMB_SIZE = 24;

export function GlassSlider({
  label,
  value,
  onValueChange,
  min,
  max,
  step = 1,
  unit = "",
  style,
}: Props) {
  const trackWidthRef = React.useRef(0);
  const trackXRef = React.useRef(0);

  const clampAndStep = useCallback(
    (raw: number): number => {
      const clamped = Math.min(max, Math.max(min, raw));
      return Math.round(clamped / step) * step;
    },
    [min, max, step],
  );

  const fraction = max > min ? (value - min) / (max - min) : 0;

  const panResponder = React.useMemo(
    () =>
      PanResponder.create({
        onStartShouldSetPanResponder: () => true,
        onMoveShouldSetPanResponder: () => true,
        onPanResponderGrant: (evt) => {
          const x = evt.nativeEvent.locationX;
          const ratio = x / trackWidthRef.current;
          onValueChange(clampAndStep(min + ratio * (max - min)));
        },
        onPanResponderMove: (evt) => {
          const pageX = evt.nativeEvent.pageX;
          const x = pageX - trackXRef.current;
          const ratio = x / trackWidthRef.current;
          onValueChange(clampAndStep(min + ratio * (max - min)));
        },
      }),
    [min, max, onValueChange, clampAndStep],
  );

  const handleTrackLayout = useCallback((e: LayoutChangeEvent) => {
    trackWidthRef.current = e.nativeEvent.layout.width;
    trackXRef.current = e.nativeEvent.layout.x;
    // Measure absolute X for pan move
    (e.target as any)?.measureInWindow?.(
      (x: number) => {
        trackXRef.current = x;
      },
    );
  }, []);

  const displayValue =
    step < 1 ? value.toFixed(1) : String(Math.round(value));

  return (
    <View style={[styles.container, style]}>
      <View style={styles.headerRow}>
        {label && <Text style={styles.label}>{label}</Text>}
        <Text style={styles.valueText}>
          {displayValue}
          {unit ? ` ${unit}` : ""}
        </Text>
      </View>
      <View
        style={styles.trackOuter}
        onLayout={handleTrackLayout}
        {...panResponder.panHandlers}
      >
        <View style={styles.track}>
          <View
            style={[styles.fill, { width: `${fraction * 100}%` }]}
          />
        </View>
        <View
          style={[
            styles.thumb,
            {
              left: `${fraction * 100}%`,
              marginLeft: -(THUMB_SIZE / 2),
            },
          ]}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginBottom: spacing.sm,
  },
  headerRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: spacing.sm,
  },
  label: {
    color: colors.text.secondary,
    fontSize: 12,
    fontFamily: typography.bodySemiBold.fontFamily,
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },
  valueText: {
    color: colors.accent.cyan,
    fontSize: 14,
    fontFamily: typography.mono.fontFamily,
  },
  trackOuter: {
    height: THUMB_SIZE + 8,
    justifyContent: "center",
  },
  track: {
    height: 6,
    borderRadius: 3,
    backgroundColor: "rgba(15, 30, 40, 0.4)",
    overflow: "hidden",
  },
  fill: {
    height: "100%",
    backgroundColor: colors.accent.cyan,
    borderRadius: 3,
  },
  thumb: {
    position: "absolute",
    width: THUMB_SIZE,
    height: THUMB_SIZE,
    borderRadius: THUMB_SIZE / 2,
    backgroundColor: "rgba(20, 35, 45, 0.35)",
    borderWidth: 2,
    borderColor: colors.accent.cyan,
  },
});
