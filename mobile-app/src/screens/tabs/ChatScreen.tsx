import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { View, FlatList, KeyboardAvoidingView, Platform, StyleSheet } from "react-native";
import Animated, { FadeIn } from "react-native-reanimated";
import { BlurView } from "expo-blur";
import { LinearGradient } from "expo-linear-gradient";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import AsyncStorage from "@react-native-async-storage/async-storage";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing } from "../../theme/spacing";
import { MessageBubble, type Message } from "../../components/chat/MessageBubble";
import { ChatInputBar } from "../../components/chat/ChatInputBar";
import { runAgentTurn } from "../../runtime/session";

const STORAGE_KEY = "guappa:chat:messages:v1";
const DEBOUNCE_MS = 300;

export function ChatScreen() {
  const insets = useSafeAreaInsets();
  const [messages, setMessages] = useState<Message[]>([]);
  const [draft, setDraft] = useState("");
  const [loaded, setLoaded] = useState(false);
  const flatListRef = useRef<FlatList>(null);
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Load messages from AsyncStorage on mount
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const raw = await AsyncStorage.getItem(STORAGE_KEY);
        if (cancelled) return;
        if (raw) {
          const parsed = JSON.parse(raw) as Message[];
          if (Array.isArray(parsed)) {
            setMessages(parsed);
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
  const saveMessages = useCallback((msgs: Message[]) => {
    if (saveTimerRef.current) {
      clearTimeout(saveTimerRef.current);
    }
    saveTimerRef.current = setTimeout(() => {
      void AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(msgs));
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

  const [isThinking, setIsThinking] = useState(false);

  const handleSend = useCallback(async () => {
    const trimmed = draft.trim();
    if (!trimmed || isThinking) return;

    const userMessage: Message = {
      id: `m_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      role: "user",
      content: trimmed,
      timestamp: Date.now(),
    };

    setDraft("");
    setMessages((prev) => {
      const next = [...prev, userMessage];
      saveMessages(next);
      return next;
    });

    setTimeout(() => {
      flatListRef.current?.scrollToEnd({ animated: true });
    }, 100);

    // Call real agent backend
    setIsThinking(true);
    try {
      const { assistantText } = await runAgentTurn(trimmed);
      const agentMessage: Message = {
        id: `m_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
        role: "assistant",
        content: assistantText,
        timestamp: Date.now(),
      };
      setMessages((prev) => {
        const next = [...prev, agentMessage];
        saveMessages(next);
        return next;
      });
    } catch (e: any) {
      const errorMessage: Message = {
        id: `m_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
        role: "assistant",
        content: `Error: ${e?.message || "Unknown error"}`,
        timestamp: Date.now(),
      };
      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setIsThinking(false);
    }
  }, [draft, saveMessages, isThinking]);

  const renderItem = useCallback(
    ({ item, index }: { item: Message; index: number }) => (
      <MessageBubble
        message={item}
        index={index}
        animate={loaded}
      />
    ),
    [loaded],
  );

  const keyExtractor = useCallback((item: Message) => item.id, []);

  const emptyComponent = useMemo(
    () => (
      <View style={styles.emptyContainer}>
        <Animated.Text
          entering={FadeIn.duration(400)}
          style={styles.emptyText}
        >
          Start a conversation
        </Animated.Text>
      </View>
    ),
    [],
  );

  return (
    <LinearGradient
      colors={[colors.base.spaceBlack, colors.base.midnightBlue]}
      style={styles.screen}
      testID="chat-screen"
    >
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === "ios" ? "padding" : "height"}
        keyboardVerticalOffset={Platform.OS === "ios" ? 0 : 0}
      >
        {/* Glass header */}
        <BlurView
          intensity={20}
          tint="dark"
          style={[styles.header, { paddingTop: insets.top + spacing.sm }]}
        >
          <View style={styles.headerInner}>
            <Animated.Text style={styles.headerTitle}>Chat</Animated.Text>
          </View>
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
        <View style={[styles.inputContainer, { paddingBottom: insets.bottom + spacing.md }]}>
          <ChatInputBar
            value={draft}
            onChangeText={setDraft}
            onSend={handleSend}
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
    borderBottomWidth: 1,
    borderBottomColor: colors.glass.border,
  },
  headerInner: {
    paddingHorizontal: spacing.md,
    paddingBottom: spacing.sm,
  },
  headerTitle: {
    color: colors.text.primary,
    fontFamily: typography.bodySemiBold.fontFamily,
    fontSize: 18,
    letterSpacing: 0.3,
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
  emptyContainer: {
    alignItems: "center",
    justifyContent: "center",
  },
  emptyText: {
    color: colors.text.primary,
    fontFamily: typography.body.fontFamily,
    fontSize: 16,
    opacity: 0.4,
  },
  inputContainer: {
    paddingHorizontal: spacing.md,
    paddingTop: spacing.xs,
  },
});
