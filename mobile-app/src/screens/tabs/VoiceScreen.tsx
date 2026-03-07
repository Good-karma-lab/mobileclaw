import React, { useState, useCallback } from "react";
import { View, Text, StyleSheet, Pressable } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { Ionicons } from "@expo/vector-icons";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { PlasmaOrb } from "../../components/plasma/PlasmaOrb";

type VoiceState = "idle" | "listening" | "thinking" | "speaking";

const STATE_LABELS: Record<VoiceState, string> = {
  idle: "Tap to speak",
  listening: "Listening...",
  thinking: "Thinking...",
  speaking: "Speaking...",
};

export function VoiceScreen() {
  const insets = useSafeAreaInsets();
  const [voiceState, setVoiceState] = useState<VoiceState>("idle");
  const [transcript, setTranscript] = useState("");
  const [response, setResponse] = useState("");

  const handleMicPress = useCallback(() => {
    if (voiceState === "idle") {
      setVoiceState("listening");
      setTranscript("");
      setResponse("");
      // STT will be wired in M4
      // For now, simulate after 2s
      setTimeout(() => {
        setTranscript("Hello, GUAPPA");
        setVoiceState("thinking");
        setTimeout(() => {
          setResponse("Hello! How can I help you today?");
          setVoiceState("speaking");
          setTimeout(() => setVoiceState("idle"), 3000);
        }, 1500);
      }, 2000);
    } else if (voiceState === "listening") {
      // Stop listening
      setVoiceState("thinking");
    } else if (voiceState === "speaking") {
      // Interrupt
      setVoiceState("idle");
    }
  }, [voiceState]);

  return (
    <LinearGradient
      colors={[colors.base.spaceBlack, colors.base.midnightBlue]}
      style={styles.container}
      testID="voice-screen"
    >
      {/* Status bar */}
      <View style={[styles.statusBar, { paddingTop: insets.top + 8 }]}>
        <Text style={styles.brandText}>GUAPPA</Text>
        <View style={styles.statusDot}>
          <View
            style={[
              styles.dot,
              voiceState !== "idle" && styles.dotActive,
            ]}
          />
          <Text style={styles.statusText}>
            {voiceState === "idle" ? "Ready" : "Active"}
          </Text>
        </View>
      </View>

      {/* Orb area */}
      <View style={styles.orbContainer}>
        <Pressable onPress={handleMicPress}>
          <PlasmaOrb
            size={300}
            state={voiceState}
            audioLevel={voiceState === "listening" ? 0.5 : 0}
          />
        </Pressable>
      </View>

      {/* Transcript area */}
      <View style={styles.transcriptArea}>
        {transcript !== "" && (
          <Text style={styles.transcriptText}>{transcript}</Text>
        )}
        {response !== "" && (
          <Text style={styles.responseText}>{response}</Text>
        )}
        <Text style={styles.stateLabel}>
          {STATE_LABELS[voiceState]}
        </Text>
      </View>

      {/* Bottom controls */}
      <View style={[styles.controls, { paddingBottom: insets.bottom + 80 }]}>
        <Pressable
          style={({ pressed }) => [
            styles.micButton,
            voiceState === "listening" && styles.micButtonActive,
            pressed && styles.micButtonPressed,
          ]}
          onPress={handleMicPress}
        >
          <Ionicons
            name={voiceState === "listening" ? "stop" : "mic"}
            size={32}
            color={
              voiceState === "listening"
                ? colors.base.spaceBlack
                : colors.accent.cyan
            }
          />
        </Pressable>

        <View style={styles.secondaryControls}>
          <Pressable style={styles.secondaryButton}>
            <Ionicons
              name="chatbubble-outline"
              size={24}
              color={colors.text.secondary}
            />
          </Pressable>
          <Pressable style={styles.secondaryButton}>
            <Ionicons
              name="settings-outline"
              size={24}
              color={colors.text.secondary}
            />
          </Pressable>
        </View>
      </View>
    </LinearGradient>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  statusBar: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 20,
    paddingBottom: 8,
  },
  brandText: {
    color: colors.text.primary,
    fontSize: 18,
    fontFamily: typography.display?.fontFamily,
    letterSpacing: 3,
    fontWeight: "700",
  },
  statusDot: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: colors.text.tertiary,
  },
  dotActive: {
    backgroundColor: colors.accent.lime,
  },
  statusText: {
    color: colors.text.secondary,
    fontSize: 13,
  },
  orbContainer: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  transcriptArea: {
    paddingHorizontal: 24,
    paddingBottom: 16,
    alignItems: "center",
    minHeight: 100,
  },
  transcriptText: {
    color: colors.text.secondary,
    fontSize: 16,
    textAlign: "center",
    marginBottom: 8,
    fontStyle: "italic",
  },
  responseText: {
    color: colors.text.primary,
    fontSize: 18,
    textAlign: "center",
    marginBottom: 12,
    lineHeight: 26,
  },
  stateLabel: {
    color: colors.text.tertiary,
    fontSize: 14,
    letterSpacing: 1,
    textTransform: "uppercase",
  },
  controls: {
    alignItems: "center",
    gap: 16,
  },
  micButton: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: "rgba(0, 240, 255, 0.1)",
    borderWidth: 2,
    borderColor: "rgba(0, 240, 255, 0.3)",
    alignItems: "center",
    justifyContent: "center",
  },
  micButtonActive: {
    backgroundColor: colors.accent.cyan,
    borderColor: colors.accent.cyan,
  },
  micButtonPressed: {
    opacity: 0.8,
    transform: [{ scale: 0.95 }],
  },
  secondaryControls: {
    flexDirection: "row",
    gap: 24,
  },
  secondaryButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: "rgba(255, 255, 255, 0.05)",
    alignItems: "center",
    justifyContent: "center",
  },
});
