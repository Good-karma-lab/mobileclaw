/**
 * ChatScreen — transparent chat overlay on the neural swarm.
 *
 * The swarm background is dimmed/receded (via RootNavigator opacity).
 * Chat bubbles float on top with glass-like translucency.
 * All elements feel managed by the swarm — glass panels, no solid fills.
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { View, FlatList, KeyboardAvoidingView, Platform, StyleSheet, Alert } from "react-native";
import Animated, {
  FadeIn,
  FadeOut,
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withTiming,
  withSequence,
  Easing,
} from "react-native-reanimated";
import { BlurView } from "expo-blur";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import AsyncStorage from "@react-native-async-storage/async-storage";
import * as ImagePicker from "expo-image-picker";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing } from "../../theme/spacing";
import { MessageBubble, type Message } from "../../components/chat/MessageBubble";
import { ChatInputBar } from "../../components/chat/ChatInputBar";
import { runAgentTurnStream } from "../../runtime/session";
import { swarmDirector } from "../../swarm/SwarmDirector";
import { swarmStore } from "../../swarm/SwarmController";

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
      ), -1, false
    );
    setTimeout(() => {
      dot2.value = withRepeat(
        withSequence(
          withTiming(1, { duration: 400, easing: Easing.inOut(Easing.ease) }),
          withTiming(0.3, { duration: 400, easing: Easing.inOut(Easing.ease) })
        ), -1, false
      );
    }, 150);
    setTimeout(() => {
      dot3.value = withRepeat(
        withSequence(
          withTiming(1, { duration: 400, easing: Easing.inOut(Easing.ease) }),
          withTiming(0.3, { duration: 400, easing: Easing.inOut(Easing.ease) })
        ), -1, false
      );
    }, 300);
  }, []);

  const d1 = useAnimatedStyle(() => ({ opacity: dot1.value, transform: [{ scale: 0.8 + dot1.value * 0.4 }] }));
  const d2 = useAnimatedStyle(() => ({ opacity: dot2.value, transform: [{ scale: 0.8 + dot2.value * 0.4 }] }));
  const d3 = useAnimatedStyle(() => ({ opacity: dot3.value, transform: [{ scale: 0.8 + dot3.value * 0.4 }] }));

  return (
    <Animated.View entering={FadeIn.duration(200)} exiting={FadeOut.duration(200)} style={thinkingStyles.container} testID="chat-typing-indicator">
      <View style={thinkingStyles.bubble}>
        <Animated.View style={[thinkingStyles.dot, d1]} />
        <Animated.View style={[thinkingStyles.dot, d2]} />
        <Animated.View style={[thinkingStyles.dot, d3]} />
      </View>
    </Animated.View>
  );
}

const thinkingStyles = StyleSheet.create({
  container: { alignSelf: "flex-start", paddingHorizontal: spacing.md, paddingVertical: spacing.xs },
  bubble: {
    flexDirection: "row", gap: 6, paddingVertical: 14, paddingHorizontal: 18,
    borderRadius: 18, borderTopLeftRadius: 4,
    backgroundColor: "rgba(90, 58, 138, 0.06)",
    borderWidth: 0.5, borderColor: "rgba(90, 58, 138, 0.1)",
  },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: colors.accent.violet },
});

export function ChatScreen({ isActive }: { isActive?: boolean }) {
  const insets = useSafeAreaInsets();
  const [messages, setMessages] = useState<Message[]>([]);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const [loaded, setLoaded] = useState(false);
  const [isThinking, setIsThinking] = useState(false);
  const [attachedImages, setAttachedImages] = useState<string[]>([]);
  const flatListRef = useRef<FlatList>(null);
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const sessionRef = useRef<string | null>(null);

  // Load messages
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const raw = await AsyncStorage.getItem(STORAGE_KEY);
        if (cancelled) return;
        if (raw) {
          const parsed = JSON.parse(raw) as Message[] | ChatStoragePayload;
          if (Array.isArray(parsed)) {
            setMessages(parsed.map((m) => ({ ...m, isStreaming: false })));
          } else if (parsed && Array.isArray(parsed.messages)) {
            setMessages(parsed.messages.map((m) => ({ ...m, isStreaming: false })));
            setSessionId(parsed.sessionId || null);
            sessionRef.current = parsed.sessionId || null;
          }
        }
      } catch { /* ignore */ }
      if (!cancelled) setLoaded(true);
    })();
    return () => { cancelled = true; };
  }, []);

  const saveMessages = useCallback((msgs: Message[], nextSessionId?: string | null) => {
    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    saveTimerRef.current = setTimeout(() => {
      const payload: ChatStoragePayload = {
        sessionId: nextSessionId ?? sessionRef.current,
        messages: msgs.map((m) => ({ ...m, isStreaming: false })),
      };
      void AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
    }, DEBOUNCE_MS);
  }, []);

  useEffect(() => () => { if (saveTimerRef.current) clearTimeout(saveTimerRef.current); }, []);

  // ---- Image Picker Handlers ----

  const handlePickImage = useCallback(async () => {
    try {
      const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (status !== "granted") {
        Alert.alert("Permission needed", "Please grant gallery access to attach images.");
        return;
      }
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ["images"],
        allowsMultipleSelection: true,
        selectionLimit: 4,
        quality: 0.8,
      });
      if (!result.canceled && result.assets.length > 0) {
        setAttachedImages((prev) => [...prev, ...result.assets.map((a) => a.uri)].slice(0, 4));
      }
    } catch (e) {
      console.warn("Image picker error:", e);
    }
  }, []);

  const handleTakePhoto = useCallback(async () => {
    try {
      const { status } = await ImagePicker.requestCameraPermissionsAsync();
      if (status !== "granted") {
        Alert.alert("Permission needed", "Please grant camera access to take photos.");
        return;
      }
      const result = await ImagePicker.launchCameraAsync({
        quality: 0.8,
      });
      if (!result.canceled && result.assets.length > 0) {
        setAttachedImages((prev) => [...prev, result.assets[0].uri].slice(0, 4));
      }
    } catch (e) {
      console.warn("Camera error:", e);
    }
  }, []);

  const handleRemoveImage = useCallback((index: number) => {
    setAttachedImages((prev) => prev.filter((_, i) => i !== index));
  }, []);

  // ---- Send Handler ----

  const handleSend = useCallback(async () => {
    const trimmed = draft.trim();
    const hasImages = attachedImages.length > 0;
    if ((!trimmed && !hasImages) || isThinking) return;

    const now = Date.now();
    const userMsg: Message = {
      id: `m_${now}_${Math.random().toString(36).slice(2, 8)}`,
      role: "user",
      content: trimmed,
      timestamp: now,
      imageUris: hasImages ? [...attachedImages] : undefined,
    };
    const assistantId = `m_${now + 1}_${Math.random().toString(36).slice(2, 8)}`;
    const assistantPlaceholder: Message = {
      id: assistantId, role: "assistant", content: "", timestamp: now + 1, isStreaming: true,
    };

    // Capture images before clearing
    const imagesToSend = hasImages ? [...attachedImages] : undefined;

    setDraft("");
    setAttachedImages([]);
    setMessages((prev) => {
      const next = [...prev, userMsg, assistantPlaceholder];
      saveMessages(next, sessionRef.current);
      return next;
    });
    setTimeout(() => flatListRef.current?.scrollToEnd({ animated: true }), 100);

    setIsThinking(true);
    if (trimmed) swarmDirector.analyzeTranscript(trimmed);
    swarmStore.setState("processing");

    try {
      console.log("[chat] runAgentTurnStream starting, prompt:", trimmed);
      const { assistantText, sessionId: resolvedSessionId } = await runAgentTurnStream({
        userPrompt: trimmed || "(user sent image(s) — please describe what you see)",
        sessionId: sessionRef.current || undefined,
        imageUris: imagesToSend,
        onSession: (sid) => { sessionRef.current = sid; setSessionId(sid); },
        onDelta: (partial) => {
          setMessages((prev) => {
            const next = prev.map((m) => m.id === assistantId ? { ...m, content: partial, isStreaming: true } : m);
            saveMessages(next, sessionRef.current);
            return next;
          });
        },
        onAgentImages: (imagePaths) => {
          // Agent sent images (from camera tool, screenshot, etc.) — insert as a new message
          const imageMsg: Message = {
            id: `m_${Date.now()}_img_${Math.random().toString(36).slice(2, 8)}`,
            role: "assistant",
            content: "",
            timestamp: Date.now(),
            imageUris: imagePaths.map((p) => (p.startsWith("/") ? `file://${p}` : p)),
          };
          setMessages((prev) => {
            // Insert image message before the streaming assistant message
            const idx = prev.findIndex((m) => m.id === assistantId);
            if (idx >= 0) {
              const next = [...prev.slice(0, idx), imageMsg, ...prev.slice(idx)];
              saveMessages(next, sessionRef.current);
              return next;
            }
            const next = [...prev, imageMsg];
            saveMessages(next, sessionRef.current);
            return next;
          });
          setTimeout(() => flatListRef.current?.scrollToEnd({ animated: true }), 100);
        },
      });

      if (resolvedSessionId) { sessionRef.current = resolvedSessionId; setSessionId(resolvedSessionId); }

      setMessages((prev) => {
        const next = prev.map((m) => m.id === assistantId ? { ...m, content: assistantText, isStreaming: false } : m);
        saveMessages(next, resolvedSessionId ?? sessionRef.current);
        return next;
      });

      swarmDirector.analyzeAgentResponse(assistantText);
      swarmStore.setState("idle");
    } catch (e: any) {
      console.error("[chat] runAgentTurnStream ERROR:", e?.message || e);
      setMessages((prev) => {
        const next = prev.map((m) =>
          m.id === assistantId
            ? { ...m, content: `I encountered an issue: ${e?.message || "Unknown error"}. I'll try to do better next time.`, isStreaming: false, isError: true }
            : m,
        );
        saveMessages(next, sessionRef.current);
        return next;
      });
    } finally {
      setIsThinking(false);
      if (swarmStore.state.state === "processing") swarmStore.setState("idle");
    }
  }, [draft, attachedImages, saveMessages, isThinking]);

  const showThinking = useMemo(() => {
    if (!isThinking) return false;
    const lastA = [...messages].reverse().find((m) => m.role === "assistant");
    return !lastA || !lastA.content;
  }, [isThinking, messages]);

  const renderItem = useCallback(
    ({ item, index }: { item: Message; index: number }) => (
      <MessageBubble message={item} index={index} animate={loaded} />
    ), [loaded],
  );

  const keyExtractor = useCallback((item: Message) => item.id, []);

  return (
    <View style={styles.screen} testID="chat-screen">
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === "ios" ? "padding" : "height"}
        keyboardVerticalOffset={0}
      >
        {/* Glass header — transparent, blurred */}
        <BlurView
          intensity={30}
          tint="dark"
          style={[styles.header, { paddingTop: insets.top + spacing.sm }]}
        >
          <View style={styles.headerInner}>
            <Animated.Text style={styles.headerTitle}>Chat</Animated.Text>
            {/* Swarm pulse dot */}
            <View style={[styles.swarmDot, isThinking && styles.swarmDotActive]} />
          </View>
          <View style={styles.headerLine} />
        </BlurView>

        {/* Messages */}
        <FlatList
          ref={flatListRef}
          data={messages}
          renderItem={renderItem}
          keyExtractor={keyExtractor}
          contentContainerStyle={[
            styles.listContent,
            messages.length === 0 && styles.listContentEmpty,
          ]}
          ListEmptyComponent={null}
          ListFooterComponent={showThinking ? <ThinkingIndicator /> : null}
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="handled"
          onContentSizeChange={() => {
            if (messages.length > 0) flatListRef.current?.scrollToEnd({ animated: true });
          }}
          ItemSeparatorComponent={() => <View style={{ height: spacing.sm }} />}
        />

        {/* Input */}
        <View style={[styles.inputContainer, { paddingBottom: insets.bottom + 80 }]}>
          <ChatInputBar
            value={draft}
            onChangeText={setDraft}
            onSend={handleSend}
            onPickImage={handlePickImage}
            onTakePhoto={handleTakePhoto}
            attachedImages={attachedImages}
            onRemoveImage={handleRemoveImage}
            isThinking={isThinking}
          />
        </View>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: "transparent",
  },
  flex: { flex: 1 },
  header: {
    backgroundColor: "rgba(15, 30, 45, 0.55)",
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
    color: "rgba(255, 255, 255, 0.7)",
    fontFamily: "Orbitron_700Bold",
    fontSize: 18,
    letterSpacing: 2,
  },
  swarmDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: "rgba(26, 92, 106, 0.3)",
    borderWidth: 1,
    borderColor: "rgba(26, 92, 106, 0.15)",
  },
  swarmDotActive: {
    backgroundColor: "rgba(90, 58, 138, 0.4)",
    borderColor: "rgba(90, 58, 138, 0.3)",
  },
  headerLine: {
    height: 0.5,
    backgroundColor: "rgba(26, 92, 106, 0.08)",
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
    gap: 12,
  },
  emptyText: {
    color: "rgba(255, 255, 255, 0.3)",
    fontFamily: "Exo2_400Regular",
    fontSize: 16,
    letterSpacing: 1,
  },
  inputContainer: {
    paddingHorizontal: spacing.md,
    paddingTop: spacing.xs,
  },
});
