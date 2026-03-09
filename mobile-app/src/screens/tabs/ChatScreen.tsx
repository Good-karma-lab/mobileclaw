import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { View, FlatList, KeyboardAvoidingView, Platform, StyleSheet } from "react-native";
import Animated, {
  FadeIn,
  FadeOut,
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withTiming,
  withSequence,
  Easing,
  withSpring,
} from "react-native-reanimated";
import { BlurView } from "expo-blur";
import { LinearGradient } from "expo-linear-gradient";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import AsyncStorage from "@react-native-async-storage/async-storage";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing } from "../../theme/spacing";
import { MessageBubble, type Message } from "../../components/chat/MessageBubble";
import { ChatInputBar } from "../../components/chat/ChatInputBar";
import { runAgentTurnStream } from "../../runtime/session";

const STORAGE_KEY = "guappa:chat:messages:v1";
const DEBOUNCE_MS = 300;

type ChatStoragePayload = {
  sessionId: string | null;
  messages: Message[];
};

function ThinkingIndicator() {
  const dot1 = useSharedValue(0.3);
  const dot2 = useSharedValue(0.3);
  const dot3 = useSharedValue(0.3);

  useEffect(() => {
    dot1.value = withRepeat(
      withSequence(
        withTiming(1, { duration: 400, easing: Easing.inOut(Easing.ease) }),
        withTiming(0.3, { duration: 400, easing: Easing.inOut(Easing.ease) })
      ),
      -1,
      false
    );
    setTimeout(() => {
      dot2.value = withRepeat(
        withSequence(
          withTiming(1, { duration: 400, easing: Easing.inOut(Easing.ease) }),
          withTiming(0.3, { duration: 400, easing: Easing.inOut(Easing.ease) })
        ),
        -1,
        false
      );
    }, 150);
    setTimeout(() => {
      dot3.value = withRepeat(
        withSequence(
          withTiming(1, { duration: 400, easing: Easing.inOut(Easing.ease) }),
          withTiming(0.3, { duration: 400, easing: Easing.inOut(Easing.ease) })
        ),
        -1,
        false
      );
    }, 300);
  }, []);

  const dot1Style = useAnimatedStyle(() => ({
    opacity: dot1.value,
    transform: [{ scale: 0.8 + dot1.value * 0.4 }],
  }));
  const dot2Style = useAnimatedStyle(() => ({
    opacity: dot2.value,
    transform: [{ scale: 0.8 + dot2.value * 0.4 }],
  }));
  const dot3Style = useAnimatedStyle(() => ({
    opacity: dot3.value,
    transform: [{ scale: 0.8 + dot3.value * 0.4 }],
  }));

  return (
    <Animated.View
      entering={FadeIn.duration(200)}
      exiting={FadeOut.duration(200)}
      style={thinkingStyles.container}
    >
      <View style={thinkingStyles.bubble}>
        <Animated.View style={[thinkingStyles.dot, dot1Style]} />
        <Animated.View style={[thinkingStyles.dot, dot2Style]} />
        <Animated.View style={[thinkingStyles.dot, dot3Style]} />
      </View>
    </Animated.View>
  );
}

const thinkingStyles = StyleSheet.create({
  container: {
    alignSelf: "flex-start",
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.xs,
  },
  bubble: {
    flexDirection: "row",
    gap: 6,
    paddingVertical: 14,
    paddingHorizontal: 18,
    borderRadius: 18,
    borderTopLeftRadius: 4,
    backgroundColor: "rgba(139, 92, 246, 0.12)",
    borderWidth: 1,
    borderColor: "rgba(139, 92, 246, 0.20)",
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: colors.accent.violet,
  },
});

function EmptyState() {
  const breathe = useSharedValue(0.95);
  const glow = useSharedValue(0.15);

  useEffect(() => {
    breathe.value = withRepeat(
      withSequence(
        withTiming(1.08, { duration: 3000, easing: Easing.inOut(Easing.sin) }),
        withTiming(0.95, { duration: 3000, easing: Easing.inOut(Easing.sin) })
      ),
      -1,
      false
    );
    glow.value = withRepeat(
      withSequence(
        withTiming(0.35, { duration: 3000, easing: Easing.inOut(Easing.sin) }),
        withTiming(0.15, { duration: 3000, easing: Easing.inOut(Easing.sin) })
      ),
      -1,
      false
    );
  }, []);

  const orbStyle = useAnimatedStyle(() => ({
    transform: [{ scale: breathe.value }],
    opacity: glow.value + 0.25,
  }));

  return (
    <View style={emptyStyles.container}>
      <Animated.View style={[emptyStyles.orb, orbStyle]}>
        <LinearGradient
          colors={["#00F0FF", "#8B5CF6", "#D4F49C", "#5CC8FF"]}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
          style={emptyStyles.orbGradient}
        />
      </Animated.View>
      <Animated.Text
        entering={FadeIn.delay(200).duration(600)}
        style={emptyStyles.text}
      >
        Start a conversation
      </Animated.Text>
      <Animated.Text
        entering={FadeIn.delay(400).duration(600)}
        style={emptyStyles.subtext}
      >
        Ask anything — GUAPPA has full access to your device
      </Animated.Text>
    </View>
  );
}

const emptyStyles = StyleSheet.create({
  container: {
    alignItems: "center",
    justifyContent: "center",
    gap: 16,
  },
  orb: {
    width: 80,
    height: 80,
    borderRadius: 40,
    overflow: "hidden",
    marginBottom: 8,
  },
  orbGradient: {
    ...StyleSheet.absoluteFillObject,
  },
  text: {
    color: colors.text.primary,
    fontFamily: typography.display.fontFamily,
    fontSize: 18,
    letterSpacing: 1,
    opacity: 0.6,
  },
  subtext: {
    color: colors.text.tertiary,
    fontFamily: typography.body.fontFamily,
    fontSize: 14,
    textAlign: "center",
    maxWidth: 260,
  },
});

export function ChatScreen() {
  const insets = useSafeAreaInsets();
  const [messages, setMessages] = useState<Message[]>([]);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const [loaded, setLoaded] = useState(false);
  const [isThinking, setIsThinking] = useState(false);
  const flatListRef = useRef<FlatList>(null);
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const sessionRef = useRef<string | null>(null);

  // Load messages from AsyncStorage on mount
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const raw = await AsyncStorage.getItem(STORAGE_KEY);
        if (cancelled) return;
        if (raw) {
          const parsed = JSON.parse(raw) as Message[] | ChatStoragePayload;
          if (Array.isArray(parsed)) {
            setMessages(parsed.map((message) => ({ ...message, isStreaming: false })));
          } else if (parsed && Array.isArray(parsed.messages)) {
            setMessages(parsed.messages.map((message) => ({ ...message, isStreaming: false })));
            setSessionId(parsed.sessionId || null);
            sessionRef.current = parsed.sessionId || null;
          }
        }
      } catch {
        // ignore load errors
      }
      if (!cancelled) setLoaded(true);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // Save messages with debounce
  const saveMessages = useCallback((msgs: Message[], nextSessionId?: string | null) => {
    if (saveTimerRef.current) {
      clearTimeout(saveTimerRef.current);
    }
    saveTimerRef.current = setTimeout(() => {
      const payload: ChatStoragePayload = {
        sessionId: nextSessionId ?? sessionRef.current,
        messages: msgs.map((message) => ({ ...message, isStreaming: false })),
      };
      void AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
    }, DEBOUNCE_MS);
  }, []);

  // Cleanup timer on unmount
  useEffect(() => {
    return () => {
      if (saveTimerRef.current) {
        clearTimeout(saveTimerRef.current);
      }
    };
  }, []);

  const handleSend = useCallback(async () => {
    const trimmed = draft.trim();
    if (!trimmed || isThinking) return;

    const now = Date.now();
    const userMessage: Message = {
      id: `m_${now}_${Math.random().toString(36).slice(2, 8)}`,
      role: "user",
      content: trimmed,
      timestamp: now,
    };
    const assistantMessageId = `m_${now + 1}_${Math.random().toString(36).slice(2, 8)}`;
    const assistantPlaceholder: Message = {
      id: assistantMessageId,
      role: "assistant",
      content: "",
      timestamp: now + 1,
      isStreaming: true,
    };

    setDraft("");
    setMessages((prev) => {
      const next = [...prev, userMessage, assistantPlaceholder];
      saveMessages(next, sessionRef.current);
      return next;
    });

    setTimeout(() => {
      flatListRef.current?.scrollToEnd({ animated: true });
    }, 100);

    // Call real agent backend
    setIsThinking(true);
    try {
      const { assistantText, sessionId: resolvedSessionId } = await runAgentTurnStream({
        userPrompt: trimmed,
        sessionId: sessionRef.current || undefined,
        onSession: (nextSessionId) => {
          sessionRef.current = nextSessionId;
          setSessionId(nextSessionId);
        },
        onDelta: (partialText) => {
          setMessages((prev) => {
            const next = prev.map((message) =>
              message.id === assistantMessageId
                ? { ...message, content: partialText, isStreaming: true }
                : message,
            );
            saveMessages(next, sessionRef.current);
            return next;
          });
        },
      });

      if (resolvedSessionId) {
        sessionRef.current = resolvedSessionId;
        setSessionId(resolvedSessionId);
      }

      setMessages((prev) => {
        const next = prev.map((message) =>
          message.id === assistantMessageId
            ? { ...message, content: assistantText, isStreaming: false }
            : message,
        );
        saveMessages(next, resolvedSessionId ?? sessionRef.current);
        return next;
      });
    } catch (e: any) {
      setMessages((prev) => {
        const next = prev.map((message) =>
          message.id === assistantMessageId
            ? {
                ...message,
                content: `Error: ${e?.message || "Unknown error"}`,
                isStreaming: false,
              }
            : message,
        );
        saveMessages(next, sessionRef.current);
        return next;
      });
    } finally {
      setIsThinking(false);
    }
  }, [draft, saveMessages, isThinking]);

  const showThinkingIndicator = useMemo(() => {
    if (!isThinking) {
      return false;
    }

    const lastAssistantMessage = [...messages].reverse().find((message) => message.role === "assistant");
    return !lastAssistantMessage || !lastAssistantMessage.content;
  }, [isThinking, messages]);

  const renderItem = useCallback(
    ({ item, index }: { item: Message; index: number }) => (
      <MessageBubble message={item} index={index} animate={loaded} />
    ),
    [loaded],
  );

  const keyExtractor = useCallback((item: Message) => item.id, []);

  const emptyComponent = useMemo(() => <EmptyState />, []);

  return (
    <LinearGradient
      colors={[colors.base.spaceBlack, colors.base.midnightBlue]}
      style={styles.screen}
      testID="chat-screen"
    >
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === "ios" ? "padding" : "height"}
        keyboardVerticalOffset={0}
      >
        {/* Glass header */}
        <BlurView
          intensity={24}
          tint="dark"
          style={[styles.header, { paddingTop: insets.top + spacing.sm }]}
        >
          <View style={styles.headerInner}>
            <Animated.Text style={styles.headerTitle}>Chat</Animated.Text>
            {/* Mini orb indicator */}
            <View style={styles.miniOrbContainer}>
              <LinearGradient
                colors={
                  isThinking
                    ? ["#8B5CF6", "#00F0FF"]
                    : ["#00F0FF", "#8B5CF6"]
                }
                start={{ x: 0, y: 0 }}
                end={{ x: 1, y: 1 }}
                style={styles.miniOrb}
              />
            </View>
          </View>
          {/* Bottom edge glow line */}
          <LinearGradient
            colors={["transparent", "rgba(0, 240, 255, 0.3)", "transparent"]}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 0 }}
            style={styles.headerLine}
          />
        </BlurView>

        {/* Message list */}
        <FlatList
          ref={flatListRef}
          data={messages}
          renderItem={renderItem}
          keyExtractor={keyExtractor}
          contentContainerStyle={[
            styles.listContent,
            messages.length === 0 && styles.listContentEmpty,
          ]}
          ListEmptyComponent={emptyComponent}
          ListFooterComponent={showThinkingIndicator ? <ThinkingIndicator /> : null}
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="handled"
          onContentSizeChange={() => {
            if (messages.length > 0) {
              flatListRef.current?.scrollToEnd({ animated: true });
            }
          }}
          ItemSeparatorComponent={() => <View style={{ height: spacing.sm }} />}
        />

        {/* Input bar */}
        <View
          style={[styles.inputContainer, { paddingBottom: insets.bottom + 80 }]}
        >
          <ChatInputBar
            value={draft}
            onChangeText={setDraft}
            onSend={handleSend}
            isThinking={isThinking}
          />
        </View>
      </KeyboardAvoidingView>
    </LinearGradient>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  flex: {
    flex: 1,
  },
  header: {
    backgroundColor: colors.glass.fill,
    borderBottomWidth: 0,
  },
  headerInner: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: spacing.md,
    paddingBottom: spacing.sm,
  },
  headerTitle: {
    color: colors.text.primary,
    fontFamily: typography.display.fontFamily,
    fontSize: 20,
    letterSpacing: 2,
    fontWeight: "700",
  },
  miniOrbContainer: {
    width: 24,
    height: 24,
    borderRadius: 12,
    overflow: "hidden",
    borderWidth: 1,
    borderColor: "rgba(0, 240, 255, 0.3)",
  },
  miniOrb: {
    ...StyleSheet.absoluteFillObject,
  },
  headerLine: {
    height: 1,
    width: "100%",
  },
  listContent: {
    paddingHorizontal: spacing.md,
    paddingTop: spacing.md,
    paddingBottom: spacing.md,
  },
  listContentEmpty: {
    flex: 1,
    justifyContent: "center",
  },
  inputContainer: {
    paddingHorizontal: spacing.md,
    paddingTop: spacing.xs,
  },
});
