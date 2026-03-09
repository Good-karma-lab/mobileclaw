import React, { useMemo } from "react";
import { View, StyleSheet, Linking, Text } from "react-native";
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

export type MessageRole = "user" | "assistant";

export type Message = {
  id: string;
  role: MessageRole;
  content: string;
  timestamp: number;
  isStreaming?: boolean;
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

  const bubbleContent = (
    <View
      testID={`chat-message-${message.role}-${index}`}
      style={[
        styles.bubble,
        isUser ? styles.userBubble : styles.assistantBubble,
      ]}
    >
      <BlurView
        intensity={isUser ? 8 : 12}
        tint="dark"
        style={styles.blurFill}
      />
      <View style={styles.bubbleContent}>
        {message.isStreaming && !isUser ? (
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
        )}
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
    backgroundColor: "rgba(255, 170, 51, 0.10)",
    borderColor: "rgba(255, 170, 51, 0.15)",
    borderTopRightRadius: 4,
  },
  assistantBubble: {
    alignSelf: "flex-start",
    backgroundColor: "rgba(139, 92, 246, 0.08)",
    borderColor: "rgba(139, 92, 246, 0.12)",
    borderTopLeftRadius: 4,
  },
});
