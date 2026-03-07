import React from "react";
import { View, TextInput, Pressable, Text, StyleSheet } from "react-native";
import { BlurView } from "expo-blur";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing, radius } from "../../theme/spacing";

type Props = {
  value: string;
  onChangeText: (text: string) => void;
  onSend: () => void;
  onMicPress?: () => void;
  editable?: boolean;
};

export function ChatInputBar({
  value,
  onChangeText,
  onSend,
  onMicPress,
  editable = true,
}: Props) {
  const hasText = value.trim().length > 0;

  return (
    <BlurView
      intensity={28}
      tint="dark"
      style={styles.container}
    >
      <View style={styles.inner}>
        <TextInput
          testID="chat-input"
          value={value}
          onChangeText={onChangeText}
          placeholder="Message GUAPPA..."
          placeholderTextColor={colors.text.tertiary}
          editable={editable}
          multiline
          style={styles.input}
        />
        {hasText ? (
          <Pressable
            testID="chat-send-button"
            onPress={onSend}
            style={({ pressed }) => [
              styles.actionButton,
              { opacity: pressed ? 0.7 : 1 },
            ]}
          >
            <Text style={styles.actionIcon}>{">"}</Text>
          </Pressable>
        ) : (
          <Pressable
            testID="chat-mic-button"
            onPress={onMicPress}
            style={({ pressed }) => [
              styles.actionButton,
              { opacity: pressed ? 0.7 : 1 },
            ]}
          >
            <Text style={styles.actionIcon}>{"mic"}</Text>
          </Pressable>
        )}
      </View>
    </BlurView>
  );
}

const styles = StyleSheet.create({
  container: {
    borderRadius: radius.lg,
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
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.sm,
    color: colors.text.primary,
    fontFamily: typography.body.fontFamily,
    fontSize: 15,
    backgroundColor: "transparent",
  },
  actionButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.accent.cyan,
  },
  actionIcon: {
    color: colors.base.spaceBlack,
    fontSize: 16,
    fontWeight: "bold",
  },
});
