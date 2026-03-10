import React, { useCallback } from "react";
import {
  Pressable,
  Text,
  ActivityIndicator,
  StyleSheet,
  View,
  type ViewStyle,
  type StyleProp,
} from "react-native";
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
} from "react-native-reanimated";
import { Ionicons } from "@expo/vector-icons";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing } from "../../theme/spacing";
import { springs } from "../../theme/animations";

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

type Variant = "primary" | "secondary" | "ghost";

type Props = {
  title: string;
  onPress: () => void;
  variant?: Variant;
  icon?: keyof typeof Ionicons.glyphMap;
  loading?: boolean;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
  testID?: string;
};

export function GlassButton({
  title,
  onPress,
  variant = "primary",
  icon,
  loading = false,
  disabled = false,
  style,
  testID,
}: Props) {
  const scale = useSharedValue(1);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  const handlePressIn = useCallback(() => {
    scale.value = withSpring(0.96, springs.snappy);
  }, [scale]);

  const handlePressOut = useCallback(() => {
    scale.value = withSpring(1, springs.snappy);
  }, [scale]);

  const variantStyles = variantMap[variant];
  const textColor = variant === "primary"
    ? colors.text.primary
    : colors.text.secondary;

  return (
    <AnimatedPressable
      onPress={onPress}
      onPressIn={handlePressIn}
      onPressOut={handlePressOut}
      disabled={disabled || loading}
      testID={testID}
      style={[
        styles.base,
        variantStyles,
        disabled && styles.disabled,
        animatedStyle,
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator size="small" color={textColor} />
      ) : (
        <View style={styles.content}>
          {icon && (
            <Ionicons
              name={icon}
              size={18}
              color={textColor}
              style={styles.icon}
            />
          )}
          <Text
            style={[
              styles.text,
              { color: textColor },
              { fontFamily: typography.bodySemiBold.fontFamily },
            ]}
          >
            {title}
          </Text>
        </View>
      )}
    </AnimatedPressable>
  );
}

const variantMap: Record<Variant, ViewStyle> = {
  primary: {
    backgroundColor: "rgba(22, 51, 74, 0.50)",
    borderWidth: 0.5,
    borderColor: colors.glass.border,
  },
  secondary: {
    backgroundColor: "rgba(14, 32, 48, 0.40)",
    borderWidth: 0.5,
    borderColor: colors.glass.borderSubtle,
  },
  ghost: {
    backgroundColor: "transparent",
    borderWidth: 0,
    borderColor: "transparent",
  },
};

const styles = StyleSheet.create({
  base: {
    borderRadius: 14,
    paddingVertical: 14,
    paddingHorizontal: spacing.lg,
    alignItems: "center",
    justifyContent: "center",
    minHeight: 48,
  },
  content: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
  },
  icon: {
    marginRight: spacing.sm,
  },
  text: {
    fontSize: 15,
  },
  disabled: {
    opacity: 0.4,
  },
});
