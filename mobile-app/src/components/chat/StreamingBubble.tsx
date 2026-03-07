import React, { useEffect, useRef } from "react";
import { View, Text, Animated, StyleSheet } from "react-native";

interface StreamingBubbleProps {
  text: string;
  isStreaming: boolean;
  isUser?: boolean;
}

export function StreamingBubble({
  text,
  isStreaming,
  isUser = false,
}: StreamingBubbleProps) {
  const fadeAnim = useRef(new Animated.Value(0)).current;
  const cursorOpacity = useRef(new Animated.Value(1)).current;

  useEffect(() => {
    Animated.spring(fadeAnim, {
      toValue: 1,
      tension: 80,
      friction: 12,
      useNativeDriver: true,
    }).start();
  }, [fadeAnim]);

  useEffect(() => {
    if (isStreaming) {
      const pulse = Animated.loop(
        Animated.sequence([
          Animated.timing(cursorOpacity, {
            toValue: 0.2,
            duration: 500,
            useNativeDriver: true,
          }),
          Animated.timing(cursorOpacity, {
            toValue: 1,
            duration: 500,
            useNativeDriver: true,
          }),
        ])
      );
      pulse.start();
      return () => pulse.stop();
    } else {
      Animated.timing(cursorOpacity, {
        toValue: 0,
        duration: 300,
        useNativeDriver: true,
      }).start();
    }
  }, [isStreaming, cursorOpacity]);

  return (
    <Animated.View
      style={[
        styles.bubble,
        isUser ? styles.userBubble : styles.assistantBubble,
        {
          opacity: fadeAnim,
          transform: [
            {
              scale: fadeAnim.interpolate({
                inputRange: [0, 1],
                outputRange: [0.95, 1],
              }),
            },
          ],
        },
      ]}
    >
      <Text style={[styles.text, isUser && styles.userText]}>{text}</Text>
      {isStreaming && (
        <Animated.View
          style={[styles.cursor, { opacity: cursorOpacity }]}
        />
      )}
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  bubble: {
    maxWidth: "85%",
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 20,
    marginVertical: 2,
    marginHorizontal: 12,
    flexDirection: "row",
    alignItems: "flex-end",
  },
  assistantBubble: {
    alignSelf: "flex-start",
    backgroundColor: "rgba(255,255,255,0.06)",
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.08)",
  },
  userBubble: {
    alignSelf: "flex-end",
    backgroundColor: "rgba(212,244,156,0.15)",
    borderWidth: 1,
    borderColor: "rgba(212,244,156,0.2)",
  },
  text: {
    fontSize: 15,
    lineHeight: 22,
    color: "#F5F0E6",
    flex: 1,
  },
  userText: {
    color: "#D4F49C",
  },
  cursor: {
    width: 2,
    height: 18,
    backgroundColor: "#5CC8FF",
    borderRadius: 1,
    marginLeft: 2,
  },
});
