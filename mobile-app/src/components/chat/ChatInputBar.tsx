import React from "react";
import { View, TextInput, Pressable, StyleSheet } from "react-native";
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
} from "react-native-reanimated";
import { BlurView } from "expo-blur";
import { Ionicons } from "@expo/vector-icons";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing, radius } from "../../theme/spacing";

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

type Props = {
  value: string;
  onChangeText: (text: string) => void;
  onSend: () => void;
  onMicPress?: () => void;
  editable?: boolean;
  isThinking?: boolean;
};

export function ChatInputBar({
  value,
  onChangeText,
  onSend,
  onMicPress,
  editable = true,
  isThinking = false,
}: Props) {
  const hasText = value.trim().length > 0;
  const buttonScale = useSharedValue(1);

  const buttonAnimStyle = useAnimatedStyle(() => ({
    transform: [{ scale: buttonScale.value }],
  }));

  const handlePressIn = () => {
    buttonScale.value = withSpring(0.85, { damping: 15, stiffness: 200 });
  };

  const handlePressOut = () => {
    buttonScale.value = withSpring(1, { damping: 12, stiffness: 150 });
  };

  return (
    <BlurView intensity={28} tint="dark" style={styles.container}>
      <View style={styles.inner}>
        <TextInput
          testID="chat-input"
          value={value}
          onChangeText={onChangeText}
          placeholder={isThinking ? "GUAPPA is thinking..." : "Message GUAPPA..."}
          placeholderTextColor={
            isThinking ? colors.accent.violet : colors.text.tertiary
          }
          editable={editable && !isThinking}
          multiline
          maxLength={4000}
          style={styles.input}
        />
        {hasText ? (
          <AnimatedPressable
            testID="chat-send-button"
            onPress={onSend}
            onPressIn={handlePressIn}
            onPressOut={handlePressOut}
            disabled={isThinking}
            style={[styles.sendButton, buttonAnimStyle]}
          >
            <Ionicons
              name="arrow-up"
              size={20}
              color={colors.base.spaceBlack}
            />
          </AnimatedPressable>
        ) : (
          <AnimatedPressable
            testID="chat-mic-button"
            onPress={onMicPress}
            onPressIn={handlePressIn}
            onPressOut={handlePressOut}
            style={[styles.micButton, buttonAnimStyle]}
          >
            <Ionicons
              name="mic-outline"
              size={22}
              color={colors.accent.cyan}
            />
          </AnimatedPressable>
        )}
      </View>
    </BlurView>
  );
}

const styles = StyleSheet.create({
  container: {
    borderRadius: radius.xl,
    overflow: "hidden",
    backgroundColor: colors.glass.fill,
    borderWidth: 1,
    borderColor: colors.glass.border,
  },
  inner: {
    flexDirection: "row",
    alignItems: "flex-end",
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
    gap: spacing.sm,
  },
  input: {
    flex: 1,
    minHeight: 40,
    maxHeight: 120,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    color: colors.text.primary,
    fontFamily: typography.body.fontFamily,
    fontSize: 15,
    backgroundColor: "transparent",
  },
  sendButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.accent.cyan,
  },
  micButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "rgba(0, 240, 255, 0.12)",
    borderWidth: 1,
    borderColor: "rgba(0, 240, 255, 0.25)",
  },
});
