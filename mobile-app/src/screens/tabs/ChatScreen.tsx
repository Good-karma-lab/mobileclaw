import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { View, ScrollView, TextInput, Pressable, Linking, KeyboardAvoidingView, Platform, Keyboard } from "react-native";
import Animated, { FadeIn, SlideInLeft, SlideInRight } from "react-native-reanimated";
import { useFocusEffect, useNavigation } from "@react-navigation/native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import Markdown from "react-native-markdown-display";
import MarkdownIt from "markdown-it";
import { Audio } from "expo-av";

import { Screen } from "../../../ui/primitives/Screen";
import { Text } from "../../../ui/primitives/Text";
import { theme } from "../../../ui/theme";
import { VoiceRecordButton } from "../../../ui/voice/VoiceRecordButton";
import { useVoiceRecording } from "../../hooks/useVoiceRecording";
import { useToast } from "../../state/toast";
import { appendChat, loadChat, sanitizeAssistantArtifacts, type ChatMessage } from "../../state/chat";
import { addActivity } from "../../state/activity";
import { loadAgentConfig } from "../../state/mobileclaw";
import { runAgentTurnWithGateway } from "../../runtime/session";
import { pairTelegramIdentity, synthesizeSpeechWithDeepgram, runZeroClawAgentStream } from "../../api/mobileclaw";
import { useLayoutContext } from "../../state/layout";
import { restartDaemon } from "../../native/zeroClawDaemon";

const BUBBLE_USER = SlideInRight.duration(280).springify().damping(18).stiffness(180);
const BUBBLE_ASSISTANT = SlideInLeft.duration(280).springify().damping(18).stiffness(180);
const MARKDOWN_NO_TABLES = new MarkdownIt({ breaks: true, linkify: true, typographer: true }).disable(["table"]);
const CHAT_SESSION_ID = "mobileclaw-chat-main";

export function ChatScreen() {
  const { useSidebar } = useLayoutContext();
  const navigation = useNavigation<any>();
  const insets = useSafeAreaInsets();
  const toast = useToast();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const [thinkingDots, setThinkingDots] = useState(".");
  const [loadedIds, setLoadedIds] = useState<Set<string>>(new Set());
  const [deepgramApiKey, setDeepgramApiKey] = useState("");
  const [keyboardVisible, setKeyboardVisible] = useState(false);
  const voice = useVoiceRecording(deepgramApiKey);
  const scrollRef = useRef<ScrollView | null>(null);
  const runNonceRef = useRef(0);
  const speechSoundRef = useRef<Audio.Sound | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const [saved, cfg] = await Promise.all([loadChat(), loadAgentConfig()]);
      if (cancelled) return;
      setMessages(saved);
      setLoadedIds(new Set(saved.map((m) => m.id)));
      setDeepgramApiKey(cfg.deepgramApiKey);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    return () => {
      void (async () => {
        try {
          await speechSoundRef.current?.unloadAsync();
        } catch {
          // ignore unload errors
        }
      })();
    };
  }, []);

  useFocusEffect(
    useCallback(() => {
      let cancelled = false;
      (async () => {
        const cfg = await loadAgentConfig();
        if (!cancelled) setDeepgramApiKey(cfg.deepgramApiKey);
      })();
      return () => {
        cancelled = true;
      };
    }, []),
  );

  useEffect(() => {
    if (!scrollRef.current) return;
    scrollRef.current.scrollToEnd({ animated: true });
  }, [messages, busy]);

  useEffect(() => {
    const showSub = Keyboard.addListener("keyboardDidShow", () => setKeyboardVisible(true));
    const hideSub = Keyboard.addListener("keyboardDidHide", () => setKeyboardVisible(false));
    return () => {
      showSub.remove();
      hideSub.remove();
    };
  }, []);

  useEffect(() => {
    if (!busy) return;
    const id = setInterval(() => {
      setThinkingDots((prev) => (prev.length >= 3 ? "." : `${prev}.`));
    }, 420);
    return () => clearInterval(id);
  }, [busy]);

  // Wire interim transcription into the draft field while recording
  useEffect(() => {
    if (voice.state === "recording" || voice.state === "transcribing") {
      setDraft(voice.interimText || voice.transcript || "");
    }
  }, [voice.interimText, voice.transcript, voice.state]);


  const markdownStyles = useMemo(
    () => ({
      body: {
        color: theme.colors.base.text,
        fontFamily: theme.typography.body,
        fontSize: useSidebar ? 18 : 15,
        lineHeight: useSidebar ? 26 : 22,
      },
      paragraph: { marginTop: 0, marginBottom: 8 },
      heading1: { fontFamily: theme.typography.bodyMedium, fontSize: 24, lineHeight: 30, marginBottom: 8 },
      heading2: { fontFamily: theme.typography.bodyMedium, fontSize: 20, lineHeight: 28, marginBottom: 8 },
      heading3: { fontFamily: theme.typography.bodyMedium, fontSize: 18, lineHeight: 24, marginBottom: 6 },
      heading4: { fontFamily: theme.typography.bodyMedium, fontSize: 16, lineHeight: 22, marginBottom: 6 },
      heading5: { fontFamily: theme.typography.bodyMedium, fontSize: 15, lineHeight: 22, marginBottom: 6 },
      heading6: { fontFamily: theme.typography.bodyMedium, fontSize: 14, lineHeight: 20, marginBottom: 6 },
      bullet_list: { marginTop: 0, marginBottom: 8 },
      ordered_list: { marginTop: 0, marginBottom: 8 },
      list_item: { marginBottom: 4 },
      blockquote: {
        marginTop: 0,
        marginBottom: 8,
        paddingVertical: 8,
        paddingHorizontal: 10,
        borderLeftWidth: 3,
        borderColor: theme.colors.stroke.subtle,
        backgroundColor: theme.colors.surface.panel,
      },
      strong: { fontFamily: theme.typography.bodyMedium },
      em: { fontStyle: "italic" as const },
      code_inline: {
        fontFamily: theme.typography.mono,
        backgroundColor: theme.colors.surface.panel,
        paddingHorizontal: 4,
        paddingVertical: 2,
      },
      fence: {
        fontFamily: theme.typography.mono,
        backgroundColor: theme.colors.surface.panel,
        borderWidth: 1,
        borderColor: theme.colors.stroke.subtle,
        borderRadius: 10,
        paddingHorizontal: 10,
        paddingVertical: 8,
      },
      code_block: {
        fontFamily: theme.typography.mono,
        backgroundColor: theme.colors.surface.panel,
        borderWidth: 1,
        borderColor: theme.colors.stroke.subtle,
        borderRadius: 10,
        paddingHorizontal: 10,
        paddingVertical: 8,
      },
      link: { color: theme.colors.base.primary },
      hr: { backgroundColor: theme.colors.stroke.subtle },
    }),
    [useSidebar],
  );

  const renderMessageText = useCallback(
    (message: ChatMessage) => {
      if (message.role !== "assistant") {
        return (
          <Text variant="body" style={{ lineHeight: useSidebar ? 26 : 22, fontSize: useSidebar ? 18 : 15 }}>
            {message.text}
          </Text>
        );
      }
      return (
        <Markdown
          markdownit={MARKDOWN_NO_TABLES}
          style={markdownStyles}
          onLinkPress={(url) => {
            void Linking.openURL(url);
            return false;
          }}
        >
          {message.text}
        </Markdown>
      );
    },
    [markdownStyles, useSidebar],
  );

  const runTurnWithTimeout = useCallback(async (prompt: string, sessionId: string) => {
    const timeoutMs = 180_000; // 3 minutes — allow time for complex tool-using agent turns
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        return await Promise.race([
          runAgentTurnWithGateway(prompt, sessionId),
          new Promise<never>((_, reject) =>
            setTimeout(() => reject(new Error("Agent request timed out. Retrying...")), timeoutMs)
          ),
        ]);
      } catch (err) {
        if (attempt === 0 && err instanceof Error && err.message.includes("timed out")) continue;
        throw err;
      }
    }
    throw new Error("Agent request timed out after retry.");
  }, []);

  const startVoiceRecording = useCallback(async () => {
    const ok = await voice.start();
    if (!ok) {
      toast.show(voice.interimText || "Voice mode is unavailable. Check Deepgram key in Settings.");
    }
    return ok;
  }, [toast, voice]);

  const speechTextFromMarkdown = useCallback((raw: string) => {
    return String(raw || "")
      .replace(/```[\s\S]*?```/g, " ")
      .replace(/`([^`]+)`/g, "$1")
      .replace(/\[([^\]]+)\]\([^\)]+\)/g, "$1")
      .replace(/[>#*_~|]/g, " ")
      .replace(/\s+/g, " ")
      .trim();
  }, []);

  const speakAssistantReply = useCallback(
    async (assistantText: string) => {
      const key = deepgramApiKey.trim();
      if (!key) return;
      const speechText = speechTextFromMarkdown(assistantText).slice(0, 1200);
      if (!speechText) return;
      try {
        const uri = await synthesizeSpeechWithDeepgram(speechText, key);
        if (!uri) return;
        await Audio.setAudioModeAsync({
          allowsRecordingIOS: false,
          playsInSilentModeIOS: true,
          staysActiveInBackground: false,
        });
        try {
          await speechSoundRef.current?.unloadAsync();
        } catch {
          // ignore
        }
        const { sound } = await Audio.Sound.createAsync({ uri }, { shouldPlay: true });
        speechSoundRef.current = sound;
      } catch (error) {
        toast.show(error instanceof Error ? error.message : "Voice playback failed");
      }
    },
    [deepgramApiKey, speechTextFromMarkdown, toast],
  );

  const send = useCallback(
    async (text: string, voiceText?: string | null) => {
      const trimmed = text.trim();
      if (!trimmed && !voiceText) return;

      const userMsg: ChatMessage = {
        id: `m_${Date.now()}`,
        role: "user",
        text: voiceText || trimmed || "(voice)",
        ts: Date.now(),
      };

      setDraft("");
      setMessages((prev) => [...prev, userMsg]);
      await appendChat(userMsg);
      await addActivity({ kind: "message", source: "chat", title: "User message", detail: userMsg.text.slice(0, 120) });

      const runtime = await loadAgentConfig();
      const gatewayUrl = runtime.platformUrl?.trim() || "http://127.0.0.1:8000";
      const pairingMatch = userMsg.text.match(/^mobileclaw\s+telegram\s+bot\s+pairing\s*:\s*(.+)$/i);
      if (pairingMatch?.[1]) {
        const identity = pairingMatch[1].trim();
        const assistantMsg: ChatMessage = {
          id: `a_${Date.now()}_${Math.random()}`,
          role: "assistant",
          text: "",
          ts: Date.now(),
        };
        setBusy(true);
        setMessages((prev) => [...prev, assistantMsg]);
        try {
          const result = await pairTelegramIdentity(gatewayUrl, identity);
          const confirmation = result.paired
            ? `Telegram pairing completed for identity ${identity}. You can now message the bot again in Telegram.`
            : `Telegram pairing did not complete: ${result.error || "unknown error"}`;
          if (result.paired && Platform.OS === "android") {
            try {
              await restartDaemon();
            } catch {
              // ignore restart failures; pairing is persisted on backend config
            }
          }
          setMessages((prev) => prev.map((m) => (m.id === assistantMsg.id ? { ...m, text: confirmation } : m)));
          await appendChat({ ...assistantMsg, text: confirmation });
          await addActivity({ kind: "action", source: "chat", title: "Telegram pairing", detail: confirmation });
        } catch (error) {
          const detail = error instanceof Error ? error.message : "Telegram pairing failed";
          const errorText = `Telegram pairing failed: ${detail}`;
          setMessages((prev) => prev.map((m) => (m.id === assistantMsg.id ? { ...m, text: errorText } : m)));
          await appendChat({ ...assistantMsg, text: errorText });
          toast.show(errorText);
          await addActivity({ kind: "log", source: "chat", title: "Telegram pairing failed", detail });
        } finally {
          setBusy(false);
        }
        return;
      }

      const runNonce = runNonceRef.current + 1;
      runNonceRef.current = runNonce;
      setBusy(true);

      const assistantMsgId = `a_${Date.now()}_${Math.random()}`;
      const assistantMsg: ChatMessage = {
        id: assistantMsgId,
        role: "assistant",
        text: "",
        ts: Date.now(),
      };
      setMessages((prev) => [...prev, assistantMsg]);

      try {
        let fullText = "";

        try {
          for await (const chunk of runZeroClawAgentStream(userMsg.text, gatewayUrl, CHAT_SESSION_ID)) {
            if (runNonceRef.current !== runNonce) return;
            fullText += chunk;
            setMessages((prev) => prev.map((m) => m.id === assistantMsgId ? { ...m, text: fullText } : m));
          }
        } catch {
          // Streaming failed — fall back to non-streaming
          if (runNonceRef.current !== runNonce) return;
          const result = await runTurnWithTimeout(userMsg.text, CHAT_SESSION_ID);
          if (runNonceRef.current !== runNonce) return;
          fullText = result.assistantText || "(empty response)";
          for (const event of result.toolEvents) {
            await addActivity({ kind: "action", source: "chat", title: `Tool ${event.status}`, detail: `${event.tool}: ${event.detail}` });
          }
        }

        if (runNonceRef.current !== runNonce) return;

        const finalText = sanitizeAssistantArtifacts(fullText || "(empty response)");
        setMessages((prev) => prev.map((m) => m.id === assistantMsgId ? { ...m, text: finalText } : m));
        await appendChat({ ...assistantMsg, text: finalText });
        await addActivity({ kind: "action", source: "chat", title: "Agent response", detail: finalText.slice(0, 120) });

        if (voiceText) {
          await speakAssistantReply(finalText);
        }
      } catch (error) {
        if (runNonceRef.current !== runNonce) return;
        let detail = error instanceof Error ? error.message : "Unknown error";
        if (Platform.OS === "android" && /gateway|network|failed to fetch|timeout/i.test(detail)) {
          try {
            await restartDaemon();
            detail = "Agent was restarting. Please retry your message in a few seconds.";
          } catch {
            // keep original detail
          }
        }
        toast.show(detail);
        const errText = sanitizeAssistantArtifacts(`MobileClaw agent error: ${detail}`);
        setMessages((prev) => prev.map((m) => m.id === assistantMsgId ? { ...m, text: errText } : m));
        await appendChat({ ...assistantMsg, text: errText });
        await addActivity({ kind: "log", source: "chat", title: "Agent error", detail });
      } finally {
        if (runNonceRef.current === runNonce) {
          setBusy(false);
        }
      }
    },
    [runTurnWithTimeout, speakAssistantReply, toast],
  );

  const canSend = useMemo(() => !!draft.trim() && !busy, [draft, busy]);
  const hasDraft = useMemo(() => !!draft.trim(), [draft]);
  const dockBottom = Math.max(12, insets.bottom + 10);
  const dockClearance = dockBottom + 56 + theme.spacing.sm;
  const contentBottomPadding = useMemo(() => {
    if (useSidebar) return 24;
    if (keyboardVisible) return Math.max(theme.spacing.sm, insets.bottom + theme.spacing.xs);
    return dockClearance;
  }, [dockClearance, insets.bottom, keyboardVisible, useSidebar]);

  return (
    <Screen testID="screen-chat">
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === "ios" ? "padding" : "height"}
        keyboardVerticalOffset={Platform.OS === "ios" ? 14 : 0}
      >
        <View
          style={{
            flex: 1,
            flexDirection: useSidebar ? "row" : "column",
            paddingHorizontal: theme.spacing.lg,
            paddingTop: theme.spacing.xl,
            paddingBottom: contentBottomPadding,
            gap: useSidebar ? theme.spacing.lg : 0,
          }}
        >

          <View style={{ flex: 1 }}>
            {useSidebar ? (
              // Automotive/tablet layout - no header to save space
              <Text testID="screen-chat" variant="display" style={{ position: "absolute", opacity: 0, height: 0 }}>Chat</Text>
            ) : (
              // Phone layout - show header
              <>
                <View
                  style={{
                    flexDirection: "row",
                    alignItems: "center",
                    flexWrap: "wrap",
                    gap: theme.spacing.sm,
                  }}
                >
                  <Text testID="screen-chat" variant="display" style={{ flexShrink: 1 }}>
                    Chat
                  </Text>
                  <Pressable
                    testID="chat-tasks-hooks"
                    onPress={() => navigation.navigate("Tasks")}
                    style={{
                      marginLeft: "auto",
                      maxWidth: "60%",
                      paddingHorizontal: 10,
                      paddingVertical: 6,
                      borderRadius: theme.radii.md,
                      backgroundColor: theme.colors.surface.panel,
                      borderWidth: 1,
                      borderColor: theme.colors.stroke.subtle,
                    }}
                  >
                    <Text variant="label" numberOfLines={1}>
                      Tasks &amp; Hooks
                    </Text>
                  </Pressable>
                </View>
                <View style={{ marginBottom: theme.spacing.md }} />
              </>
            )}

            <ScrollView
              ref={scrollRef}
              style={{ flex: 1 }}
              contentContainerStyle={{ paddingBottom: 18, gap: theme.spacing.sm }}
              showsVerticalScrollIndicator={false}
              keyboardShouldPersistTaps="handled"
              onContentSizeChange={() => scrollRef.current?.scrollToEnd({ animated: true })}
            >
              {messages.map((m) => {
                const isUser = m.role === "user";
                const isNew = !loadedIds.has(m.id);
                const bubbleContent = (
                  <View
                    style={{
                      alignSelf: isUser ? "flex-end" : "flex-start",
                      maxWidth: isUser ? (useSidebar ? "84%" : "90%") : "100%",
                      paddingVertical: useSidebar ? 14 : 10,
                      paddingHorizontal: useSidebar ? 16 : 12,
                      borderRadius: 18,
                      backgroundColor: isUser ? theme.colors.alpha.userBubbleBg : theme.colors.surface.raised,
                      borderWidth: 1,
                      borderColor: isUser ? theme.colors.alpha.userBubbleBorder : theme.colors.stroke.subtle,
                    }}
                  >
                    {renderMessageText(m)}
                  </View>
                );
                if (isNew) {
                  return (
                    <Animated.View key={m.id} entering={isUser ? BUBBLE_USER : BUBBLE_ASSISTANT}>
                      {bubbleContent}
                    </Animated.View>
                  );
                }
                return <View key={m.id}>{bubbleContent}</View>;
              })}
              {busy && (
                <Animated.View entering={FadeIn}>
                  <Text variant="muted" style={{ alignSelf: "center", color: theme.colors.base.textMuted }}>
                    {`MobileClaw is thinking${thinkingDots}`}
                  </Text>
                </Animated.View>
              )}
            </ScrollView>

            <View style={{ flexDirection: "row", alignItems: "center", gap: theme.spacing.sm, paddingTop: theme.spacing.sm }}>
              <View style={{ flex: 1 }}>
                <TextInput
                  testID="chat-input"
                  value={draft}
                  onChangeText={(text) => {
                    if (!useSidebar && text.endsWith("\n") && !draft.endsWith("\n")) {
                      const trimmed = text.slice(0, -1).trim();
                      if (trimmed && !busy) {
                        setDraft("");
                        void send(trimmed);
                      }
                      return;
                    }
                    setDraft(text);
                  }}
                  onSubmitEditing={useSidebar ? () => { if (canSend) void send(draft); } : undefined}
                  returnKeyType={useSidebar ? "send" : "default"}
                  blurOnSubmit={useSidebar}
                  placeholder={
                    busy
                      ? "Thinking..."
                      : voice.state === "recording"
                      ? "Listening..."
                      : "Tell agent what to do..."
                  }
                  placeholderTextColor={theme.colors.alpha.textPlaceholder}
                  editable={!busy && voice.state !== "recording"}
                  multiline={!useSidebar}
                  style={{
                    minHeight: useSidebar ? 56 : 56,
                    maxHeight: useSidebar ? 84 : 120,
                    borderRadius: theme.radii.lg,
                    paddingHorizontal: theme.spacing.md,
                    paddingVertical: 14,
                    backgroundColor: theme.colors.surface.raised,
                    borderWidth: 1,
                    borderColor:
                      voice.state === "recording"
                        ? theme.colors.base.primary
                        : theme.colors.stroke.subtle,
                    color: theme.colors.base.text,
                    fontFamily: theme.typography.body,
                    fontSize: useSidebar ? 20 : 16,
                    opacity: busy ? 0.5 : 1,
                  }}
                />
              </View>

              <VoiceRecordButton
                testID="chat-send-or-voice"
                size={useSidebar ? 80 : 56}
                style={{ alignSelf: "center" }}
                mode={hasDraft ? "send" : "voice"}
                disabled={busy || (hasDraft && !canSend)}
                onPress={hasDraft ? () => (canSend ? send(draft) : undefined) : undefined}
                onRecordStart={hasDraft ? undefined : startVoiceRecording}
                onRecordEnd={hasDraft ? undefined : voice.stop}
                volume={voice.volume}
                onVoiceResult={hasDraft ? undefined : (t) => { setDraft(t); }}
              />
            </View>
            {voice.state === "idle" && voice.interimText ? (
              <Text variant="muted" style={{ marginTop: theme.spacing.xs }}>
                {voice.interimText}
              </Text>
            ) : null}
          </View>
        </View>
      </KeyboardAvoidingView>
    </Screen>
  );
}
