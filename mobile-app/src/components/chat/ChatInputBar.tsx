import React from "react";
import { View, TextInput, Pressable, StyleSheet, Image, ScrollView } from "react-native";
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
  onPickImage?: () => void;
  onTakePhoto?: () => void;
  onPickFile?: () => void;
  /** Currently attached image URIs (shown as thumbnails above the input). */
  attachedImages?: string[];
  onRemoveImage?: (index: number) => void;
  editable?: boolean;
  isThinking?: boolean;
};

export function ChatInputBar({
  value,
  onChangeText,
  onSend,
  onMicPress,
  onPickImage,
  onTakePhoto,
  onPickFile,
  attachedImages,
  onRemoveImage,
  editable = true,
  isThinking = false,
}: Props) {
  const hasText = value.trim().length > 0;
  const hasAttachments = attachedImages && attachedImages.length > 0;
  const canSend = (hasText || hasAttachments) && !isThinking;
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
      {/* Image attachment preview strip */}
      {hasAttachments && (
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          style={styles.attachmentStrip}
          contentContainerStyle={styles.attachmentStripContent}
        >
          {attachedImages!.map((uri, idx) => (
            <View key={`att-${idx}`} style={styles.thumbnailContainer}>
              <Image
                source={{ uri: uri.startsWith("/") ? `file://${uri}` : uri }}
                style={styles.thumbnail}
                resizeMode="cover"
              />
              <Pressable
                testID={`remove-image-${idx}`}
                onPress={() => onRemoveImage?.(idx)}
                style={styles.thumbnailRemove}
              >
                <Ionicons name="close-circle" size={20} color="rgba(255,100,100,0.9)" />
              </Pressable>
            </View>
          ))}
        </ScrollView>
      )}
      <View style={styles.inner}>
        {/* Attachment action buttons */}
        <View style={styles.attachActions}>
          {onPickImage && (
            <Pressable
              testID="chat-pick-image"
              onPress={onPickImage}
              disabled={isThinking}
              style={styles.attachButton}
            >
              <Ionicons name="image-outline" size={22} color={colors.accent.cyan} />
            </Pressable>
          )}
          {onTakePhoto && (
            <Pressable
              testID="chat-take-photo"
              onPress={onTakePhoto}
              disabled={isThinking}
              style={styles.attachButton}
            >
              <Ionicons name="camera-outline" size={22} color={colors.accent.cyan} />
            </Pressable>
          )}
          {onPickFile && (
            <Pressable
              testID="chat-pick-file"
              onPress={onPickFile}
              disabled={isThinking}
              style={styles.attachButton}
            >
              <Ionicons name="document-outline" size={20} color={colors.accent.cyan} />
            </Pressable>
          )}
        </View>
        <TextInput
          testID="chat-input"
          value={value}
          onChangeText={onChangeText}
          onSubmitEditing={() => {
            if (canSend) onSend();
          }}
          returnKeyType="send"
          blurOnSubmit={false}
          placeholder={isThinking ? "GUAPPA is thinking..." : "Message GUAPPA..."}
          placeholderTextColor={
            isThinking ? colors.accent.violet : colors.text.tertiary
          }
          editable={editable && !isThinking}
          multiline={false}
          maxLength={4000}
          style={styles.input}
        />
        {canSend ? (
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
  attachmentStrip: {
    maxHeight: 72,
    borderBottomWidth: 0.5,
    borderBottomColor: "rgba(26, 92, 106, 0.1)",
  },
  attachmentStripContent: {
    paddingHorizontal: spacing.sm,
    paddingVertical: 6,
    gap: 8,
  },
  thumbnailContainer: {
    position: "relative",
  },
  thumbnail: {
    width: 56,
    height: 56,
    borderRadius: 10,
    backgroundColor: "rgba(255, 255, 255, 0.09)",
  },
  thumbnailRemove: {
    position: "absolute",
    top: -6,
    right: -6,
    backgroundColor: "rgba(0, 0, 0, 0.6)",
    borderRadius: 10,
  },
  inner: {
    flexDirection: "row",
    alignItems: "flex-end",
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
    gap: 4,
  },
  attachActions: {
    flexDirection: "row",
    alignItems: "center",
    gap: 2,
    paddingBottom: 8,
  },
  attachButton: {
    width: 32,
    height: 32,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 16,
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
    backgroundColor: "rgba(20, 60, 80, 0.15)",
    borderWidth: 1,
    borderColor: "rgba(25, 80, 100, 0.22)",
  },
});
