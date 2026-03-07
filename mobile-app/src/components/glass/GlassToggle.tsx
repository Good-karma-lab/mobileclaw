import React, { useCallback } from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
} from "react-native-reanimated";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing } from "../../theme/spacing";
import { springs } from "../../theme/animations";

type Props = {
  value: boolean;
  onValueChange: (val: boolean) => void;
  label?: string;
  description?: string;
};

const TRACK_W = 48;
const TRACK_H = 28;
const THUMB_SIZE = 22;
const THUMB_MARGIN = 3;
const TRAVEL = TRACK_W - THUMB_SIZE - THUMB_MARGIN * 2;

export function GlassToggle({ value, onValueChange, label, description }: Props) {
  const offset = useSharedValue(value ? TRAVEL : 0);

  React.useEffect(() => {
    offset.value = withSpring(value ? TRAVEL : 0, springs.snappy);
  }, [value, offset]);

  const thumbStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: offset.value }],
  }));

  const handlePress = useCallback(() => {
    onValueChange(!value);
  }, [value, onValueChange]);

  return (
    <Pressable onPress={handlePress} style={styles.row}>
      <View style={styles.labelColumn}>
        {label && <Text style={styles.label}>{label}</Text>}
        {description && <Text style={styles.description}>{description}</Text>}
      </View>
      <View
        style={[
          styles.track,
          value ? styles.trackOn : styles.trackOff,
        ]}
      >
        <Animated.View style={[styles.thumb, thumbStyle]} />
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: spacing.sm,
  },
  labelColumn: {
    flex: 1,
    marginRight: spacing.md,
  },
  label: {
    color: colors.text.primary,
    fontSize: 15,
    fontFamily: typography.body.fontFamily,
  },
  description: {
    color: colors.text.tertiary,
    fontSize: 12,
    fontFamily: typography.body.fontFamily,
    marginTop: 2,
  },
  track: {
    width: TRACK_W,
    height: TRACK_H,
    borderRadius: TRACK_H / 2,
    justifyContent: "center",
    paddingHorizontal: THUMB_MARGIN,
  },
  trackOff: {
    backgroundColor: "rgba(255, 255, 255, 0.08)",
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.12)",
  },
  trackOn: {
    backgroundColor: "rgba(0, 240, 255, 0.25)",
    borderWidth: 1,
    borderColor: "rgba(0, 240, 255, 0.40)",
  },
  thumb: {
    width: THUMB_SIZE,
    height: THUMB_SIZE,
    borderRadius: THUMB_SIZE / 2,
    backgroundColor: "#FFFFFF",
  },
});
