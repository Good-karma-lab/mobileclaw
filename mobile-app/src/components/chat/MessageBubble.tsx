import React, { useMemo } from "react";
import { View, StyleSheet, Linking } from "react-native";
import Animated, { FadeIn, SlideInLeft, SlideInRight } from "react-native-reanimated";
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
};

type Props = {
  message: Message;
  index: number;
  animate?: boolean;
};

const MARKDOWN_PARSER = new MarkdownIt({ breaks: true, linkify: true, typographer: true }).disable([
  "table",
]);

const BUBBLE_USER_ENTERING = SlideInRight.duration(280).springify().damping(18).stiffness(180);
const BUBBLE_ASSISTANT_ENTERING = SlideInLeft.duration(280).springify().damping(18).stiffness(180);

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
        fontFamily: typography.bodyMedium.fontFamily,
        fontSize: 24,
        lineHeight: 30,
        marginBottom: 8,
      },
      heading2: {
        fontFamily: typography.bodyMedium.fontFamily,
        fontSize: 20,
        lineHeight: 28,
        marginBottom: 8,
      },
      heading3: {
        fontFamily: typography.bodyMedium.fontFamily,
        fontSize: 18,
        lineHeight: 24,
        marginBottom: 6,
      },
      strong: { fontFamily: typography.bodyMedium.fontFamily },
      em: { fontStyle: "italic" as const },
      code_inline: {
        fontFamily: typography.mono.fontFamily,
        backgroundColor: "rgba(255, 255, 255, 0.06)",
        paddingHorizontal: 4,
        paddingVertical: 2,
      },
      fence: {
        fontFamily: typography.mono.fontFamily,
        backgroundColor: "rgba(255, 255, 255, 0.06)",
        borderWidth: 1,
        borderColor: colors.glass.border,
        borderRadius: radius.md,
        paddingHorizontal: 10,
        paddingVertical: 8,
      },
      code_block: {
        fontFamily: typography.mono.fontFamily,
        backgroundColor: "rgba(255, 255, 255, 0.06)",
        borderWidth: 1,
        borderColor: colors.glass.border,
        borderRadius: radius.md,
        paddingHorizontal: 10,
        paddingVertical: 8,
      },
      link: { color: colors.accent.cyan },
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
      {isUser ? (
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
    paddingVertical: 10,
    paddingHorizontal: 14,
    borderRadius: 18,
    maxWidth: "85%",
    borderWidth: 1,
  },
  userBubble: {
    alignSelf: "flex-end",
    backgroundColor: "rgba(255, 170, 51, 0.10)",
    borderColor: "rgba(255, 170, 51, 0.20)",
    borderTopRightRadius: 4,
  },
  assistantBubble: {
    alignSelf: "flex-start",
    backgroundColor: "rgba(0, 240, 255, 0.08)",
    borderColor: "rgba(139, 92, 246, 0.15)",
    borderTopLeftRadius: 4,
  },
});
