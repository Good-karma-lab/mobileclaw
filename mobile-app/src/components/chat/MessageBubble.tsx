import React, { useMemo } from "react";
import { View, StyleSheet, Linking, Text, Image, Pressable, Dimensions } from "react-native";
import Animated, {
  FadeIn,
  SlideInLeft,
  SlideInRight,
} from "react-native-reanimated";
import { BlurView } from "expo-blur";
import Markdown from "react-native-markdown-display";
import MarkdownIt from "markdown-it";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { radius } from "../../theme/spacing";

export type MessageRole = "user" | "assistant" | "thinking" | "tool_call";

export type Message = {
  id: string;
  role: MessageRole;
  content: string;
  timestamp: number;
  isStreaming?: boolean;
  isError?: boolean;
  /** Image URIs attached to this message (user photos, agent outputs). */
  imageUris?: string[];
  /** Content type for differentiated rendering */
  contentType?: "text" | "thinking" | "tool_call" | "tool_result";
};

type Props = {
  message: Message;
  index: number;
  animate?: boolean;
};

const MARKDOWN_PARSER = new MarkdownIt({
  breaks: true,
  linkify: true,
  typographer: true,
}).disable(["table"]);

const BUBBLE_USER_ENTERING = SlideInRight.duration(280)
  .springify()
  .damping(12)
  .stiffness(100);
const BUBBLE_ASSISTANT_ENTERING = SlideInLeft.duration(280)
  .springify()
  .damping(12)
  .stiffness(100);

export function MessageBubble({ message, index, animate = true }: Props) {
  const isUser = message.role === "user";
  const isThinking = message.contentType === "thinking" || message.role === "thinking";
  const isToolCall = message.contentType === "tool_call" || message.contentType === "tool_result" || message.role === "tool_call";

  const markdownStyles = useMemo(
    () => ({
      body: {
        color: colors.text.primary,
        fontFamily: typography.body.fontFamily,
        fontSize: 15,
        lineHeight: 22,
      },
      paragraph: { marginTop: 0, marginBottom: 8 },
      heading1: {
        fontFamily: typography.display.fontFamily,
        fontSize: 22,
        lineHeight: 28,
        marginBottom: 8,
        letterSpacing: 1,
      },
      heading2: {
        fontFamily: typography.display.fontFamily,
        fontSize: 18,
        lineHeight: 24,
        marginBottom: 8,
        letterSpacing: 0.5,
      },
      heading3: {
        fontFamily: typography.bodySemiBold.fontFamily,
        fontSize: 16,
        lineHeight: 22,
        marginBottom: 6,
      },
      strong: { fontFamily: typography.bodySemiBold.fontFamily },
      em: { fontStyle: "italic" as const },
      code_inline: {
        fontFamily: typography.mono.fontFamily,
        backgroundColor: "rgba(255, 255, 255, 0.08)",
        paddingHorizontal: 5,
        paddingVertical: 2,
        borderRadius: 4,
        fontSize: 13,
      },
      fence: {
        fontFamily: typography.mono.fontFamily,
        backgroundColor: "rgba(255, 255, 255, 0.06)",
        borderWidth: 1,
        borderColor: "rgba(255, 255, 255, 0.10)",
        borderRadius: radius.md,
        paddingHorizontal: 12,
        paddingVertical: 10,
        fontSize: 13,
      },
      code_block: {
        fontFamily: typography.mono.fontFamily,
        backgroundColor: "rgba(255, 255, 255, 0.06)",
        borderWidth: 1,
        borderColor: "rgba(255, 255, 255, 0.10)",
        borderRadius: radius.md,
        paddingHorizontal: 12,
        paddingVertical: 10,
        fontSize: 13,
      },
      link: { color: colors.accent.cyan, textDecorationLine: "underline" as const },
    }),
    [],
  );

  const hasImages = message.imageUris && message.imageUris.length > 0;
  const maxImageWidth = Dimensions.get("window").width * 0.65;

  const bubbleContent = (
    <View
      testID={
        message.isError ? "chat-error-message"
        : isThinking ? "chat-bubble-thinking"
        : isToolCall ? "chat-bubble-tool"
        : isUser ? "chat-bubble-user"
        : "chat-bubble-agent"
      }
      style={[
        styles.bubble,
        isUser ? styles.userBubble
          : isThinking ? styles.thinkingBubble
          : isToolCall ? styles.toolCallBubble
          : styles.assistantBubble,
        hasImages && styles.imageBubble,
      ]}
    >
      <BlurView
        intensity={isUser ? 8 : isThinking || isToolCall ? 6 : 12}
        tint="dark"
        style={styles.blurFill}
      />
      <View style={styles.bubbleContent}>
        {/* Thinking header */}
        {isThinking && (
          <View style={styles.metaRow}>
            <Text style={styles.metaIcon}>🧠</Text>
            <Text style={styles.metaLabel}>Thinking</Text>
          </View>
        )}
        {/* Tool call header */}
        {isToolCall && (
          <View style={styles.metaRow}>
            <Text style={styles.metaIcon}>
              {message.contentType === "tool_result" ? "✓" : "⚡"}
            </Text>
            <Text style={[
              styles.metaLabel,
              message.contentType === "tool_result" && styles.metaLabelSuccess,
            ]}>
              {message.content?.replace(/^[⚡✓✗]\s*/, "") || "Tool"}
            </Text>
          </View>
        )}
        {/* Render attached images */}
        {hasImages && (
          <View style={styles.imageContainer}>
            {message.imageUris!.map((uri, idx) => (
              <Pressable
                key={`img-${idx}`}
                testID={`chat-image-${idx}`}
                onPress={() => {
                  // Could open a full-screen image viewer
                }}
              >
                <Image
                  source={{ uri: uri.startsWith("/") ? `file://${uri}` : uri }}
                  style={[styles.attachedImage, { width: maxImageWidth, height: maxImageWidth * 0.75 }]}
                  resizeMode="cover"
                />
              </Pressable>
            ))}
          </View>
        )}
        {/* Render text content — skip for tool calls (shown in header) */}
        {message.content && !isToolCall ? (
          isThinking ? (
            <Text style={styles.thinkingText}>{message.content}</Text>
          ) : message.isStreaming && !isUser ? (
            <Text style={styles.streamingText}>{message.content || "..."}</Text>
          ) : (
            <Markdown
              markdownit={MARKDOWN_PARSER}
              style={markdownStyles}
              onLinkPress={(url) => {
                void Linking.openURL(url);
                return false;
              }}
            >
              {message.content}
            </Markdown>
          )
        ) : null}
      </View>
    </View>
  );

  if (!animate) {
    return <View>{bubbleContent}</View>;
  }

  return (
    <Animated.View
      entering={
        isUser
          ? BUBBLE_USER_ENTERING.withInitialValues({ opacity: 0 })
          : BUBBLE_ASSISTANT_ENTERING.withInitialValues({ opacity: 0 })
      }
    >
      {bubbleContent}
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  bubble: {
    maxWidth: "80%",
    borderRadius: 16,
    borderWidth: 1,
    overflow: "hidden",
  },
  blurFill: {
    ...StyleSheet.absoluteFillObject,
  },
  bubbleContent: {
    paddingVertical: 12,
    paddingHorizontal: 16,
  },
  streamingText: {
    color: colors.text.primary,
    fontFamily: typography.body.fontFamily,
    fontSize: 15,
    lineHeight: 22,
  },
  userBubble: {
    alignSelf: "flex-end",
    backgroundColor: "rgba(90, 70, 30, 0.12)",
    borderColor: "rgba(90, 70, 30, 0.15)",
    borderTopRightRadius: 4,
  },
  assistantBubble: {
    alignSelf: "flex-start",
    backgroundColor: "rgba(30, 45, 60, 0.2)",
    borderColor: "rgba(40, 60, 80, 0.15)",
    borderTopLeftRadius: 4,
  },
  thinkingBubble: {
    alignSelf: "flex-start",
    backgroundColor: "rgba(90, 58, 138, 0.08)",
    borderColor: "rgba(90, 58, 138, 0.12)",
    borderTopLeftRadius: 4,
    borderStyle: "dashed" as any,
  },
  toolCallBubble: {
    alignSelf: "flex-start",
    backgroundColor: "rgba(26, 92, 106, 0.10)",
    borderColor: "rgba(26, 92, 106, 0.18)",
    borderTopLeftRadius: 4,
    maxWidth: "70%",
  },
  metaRow: {
    flexDirection: "row" as const,
    alignItems: "center" as const,
    gap: 6,
    marginBottom: 2,
  },
  metaIcon: {
    fontSize: 13,
  },
  metaLabel: {
    color: colors.text.secondary,
    fontFamily: typography.mono.fontFamily,
    fontSize: 12,
    letterSpacing: 0.5,
    textTransform: "uppercase" as const,
  },
  metaLabelSuccess: {
    color: colors.semantic?.success || "#50D0A0",
  },
  thinkingText: {
    color: colors.text.secondary,
    fontFamily: typography.body.fontFamily,
    fontSize: 13,
    lineHeight: 18,
    fontStyle: "italic" as const,
    opacity: 0.7,
  },
  imageBubble: {
    maxWidth: "85%",
  },
  imageContainer: {
    gap: 6,
    marginBottom: 4,
  },
  attachedImage: {
    borderRadius: 12,
    backgroundColor: "rgba(255, 255, 255, 0.05)",
  },
});
