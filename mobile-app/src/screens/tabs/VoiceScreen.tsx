/**
 * VoiceScreen — fullscreen neural swarm, the primary AI interaction mode.
 *
 * The entire screen is a living neural swarm visualization (SwarmCanvas).
 * State transitions (idle → listening → processing → speaking) happen
 * automatically via the voice pipeline with real STT, VAD, and TTS.
 *
 * Chrome UI (brand text, connection indicator) fades in on tap and
 * auto-hides after 3 seconds to keep the experience immersive.
 */
import React, { useState, useCallback, useRef, useEffect } from "react";
import { View, StyleSheet, Pressable } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withTiming,
  FadeIn,
  FadeOut,
} from "react-native-reanimated";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { SwarmCanvas } from "../../swarm/SwarmCanvas";
import { swarmStore } from "../../swarm/SwarmController";
import { swarmDirector } from "../../swarm/SwarmDirector";
import { voiceAmplitude } from "../../swarm/audio/VoiceAmplitude";
import { useVAD } from "../../hooks/useVAD";
import { useTTS } from "../../hooks/useTTS";
import { useWakeWord } from "../../hooks/useWakeWord";
import { sendMessage } from "../../native/guappaAgent";
import type { SwarmState } from "../../swarm/neurons/NeuronSystem";

const AUTO_HIDE_DELAY = 3000;

export function VoiceScreen() {
  const insets = useSafeAreaInsets();
  const [voiceState, setVoiceState] = useState<SwarmState>("idle");
  const [transcript, setTranscript] = useState("");
  const [response, setResponse] = useState("");
  const [chromeVisible, setChromeVisible] = useState(false);
  const [wakeWordEnabled, setWakeWordEnabled] = useState(true);
  const autoHideTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const transcriptRef = useRef("");

  const chromeOpacity = useSharedValue(0);

  // Use refs for callbacks that need to reference each other
  const processTranscriptRef = useRef<(text: string) => void>(() => {});
  const startListeningRef = useRef<() => void>(() => {});

  // VAD: auto-detect silence to stop recording
  const vad = useVAD({
    onSpeechEnd: useCallback(() => {
      if (swarmStore.state.state === "listening" && transcriptRef.current.length > 0) {
        processTranscriptRef.current(transcriptRef.current);
      }
    }, []),
  });

  // TTS: speak agent responses
  const tts = useTTS({
    updateSwarmState: true,
    onDone: useCallback(() => {
      setTimeout(() => {
        swarmStore.setFormation(null);
        swarmStore.setDisplayText(null);
      }, 3000);
    }, []),
  });

  // Wake word: "Hey GUAPPA" hands-free activation
  useWakeWord({
    enabled: wakeWordEnabled && voiceState === "idle",
    onWakeWord: useCallback(() => {
      startListeningRef.current();
    }, []),
  });

  // Process final transcript through agent
  const processTranscript = useCallback(async (text: string) => {
    swarmStore.setState("processing");
    voiceAmplitude.stop();
    vad.reset();

    swarmDirector.analyzeTranscript(text);

    try {
      const agentResponse = await sendMessage(text);
      setResponse(agentResponse);
      swarmDirector.analyzeAgentResponse(agentResponse);
      tts.speak(agentResponse);
    } catch {
      setResponse("Sorry, I couldn't process that.");
      swarmStore.setState("idle");
    }
  }, [tts, vad]);

  // Start listening mode
  const startListening = useCallback(() => {
    swarmStore.setState("listening");
    setTranscript("");
    setResponse("");
    transcriptRef.current = "";
    voiceAmplitude.start();
    vad.reset();
  }, [vad]);

  // Keep refs in sync
  useEffect(() => {
    processTranscriptRef.current = processTranscript;
  }, [processTranscript]);
  useEffect(() => {
    startListeningRef.current = startListening;
  }, [startListening]);

  // Sync local state with SwarmController
  useEffect(() => {
    const unsub = swarmStore.subscribe((state) => {
      setVoiceState(state.state);
    });
    return unsub;
  }, []);

  // Chrome show/hide
  const showChrome = useCallback(() => {
    if (autoHideTimer.current) {
      clearTimeout(autoHideTimer.current);
      autoHideTimer.current = null;
    }
    setChromeVisible(true);
    chromeOpacity.value = withTiming(1, { duration: 300 });
    autoHideTimer.current = setTimeout(() => {
      chromeOpacity.value = withTiming(0, { duration: 400 });
      setTimeout(() => setChromeVisible(false), 400);
      autoHideTimer.current = null;
    }, AUTO_HIDE_DELAY);
  }, [chromeOpacity]);

  useEffect(() => {
    return () => {
      if (autoHideTimer.current) clearTimeout(autoHideTimer.current);
    };
  }, []);

  const handleTap = useCallback(() => {
    showChrome();

    if (voiceState === "idle") {
      startListening();
    } else if (voiceState === "listening") {
      if (transcriptRef.current.length > 0) {
        processTranscript(transcriptRef.current);
      } else {
        swarmStore.setState("idle");
        voiceAmplitude.stop();
      }
    } else if (voiceState === "speaking") {
      tts.stop();
      swarmStore.setState("idle");
    }
  }, [voiceState, showChrome, startListening, processTranscript, tts]);

  // Connection status color
  const connectionColor =
    voiceState === "idle" || voiceState === "listening" || voiceState === "speaking"
      ? colors.semantic.success
      : colors.accent.amber;

  const stateLabel = {
    idle: "Tap to speak",
    listening: "Listening...",
    processing: "Thinking...",
    speaking: "Speaking...",
  }[voiceState];

  const chromeAnimatedStyle = useAnimatedStyle(() => ({
    opacity: chromeOpacity.value,
  }));

  return (
    <View style={styles.container} testID="voice-screen">
      {/* Neural Swarm Canvas — fills entire screen */}
      <Pressable style={StyleSheet.absoluteFill} onPress={handleTap}>
        <SwarmCanvas />
      </Pressable>

      {/* Status bar chrome — fades in on tap, auto-hides after 3s */}
      {chromeVisible && (
        <Animated.View
          style={[
            styles.statusBar,
            { paddingTop: insets.top + 8 },
            chromeAnimatedStyle,
          ]}
          pointerEvents="none"
        >
          <Animated.Text
            entering={FadeIn.duration(300)}
            exiting={FadeOut.duration(300)}
            style={styles.brandText}
          >
            GUAPPA
          </Animated.Text>
          <View style={styles.connectionIndicator}>
            <View
              style={[
                styles.connectionDot,
                { backgroundColor: connectionColor },
              ]}
            />
            <Animated.Text
              entering={FadeIn.duration(300)}
              exiting={FadeOut.duration(300)}
              style={styles.connectionText}
            >
              Ready
            </Animated.Text>
          </View>
        </Animated.View>
      )}

      {/* Transcript + state label overlay at bottom */}
      <View
        style={[
          styles.transcriptArea,
          { paddingBottom: insets.bottom + 80 },
        ]}
        pointerEvents="none"
      >
        <Animated.Text
          key={voiceState}
          entering={FadeIn.duration(300)}
          exiting={FadeOut.duration(200)}
          style={styles.stateLabel}
        >
          {stateLabel}
        </Animated.Text>

        {transcript !== "" && (
          <Animated.Text
            entering={FadeIn.duration(400)}
            exiting={FadeOut.duration(300)}
            style={styles.transcriptText}
          >
            {transcript}
          </Animated.Text>
        )}

        {response !== "" && (
          <Animated.Text
            entering={FadeIn.duration(400)}
            exiting={FadeOut.duration(300)}
            style={styles.responseText}
          >
            {response}
          </Animated.Text>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#020206",
  },
  statusBar: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 20,
    paddingBottom: 8,
    zIndex: 10,
  },
  brandText: {
    color: colors.text.primary,
    fontSize: 14,
    fontFamily: typography.display.fontFamily,
    letterSpacing: 3,
    opacity: 0.4,
  },
  connectionIndicator: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  connectionDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  connectionText: {
    color: colors.text.secondary,
    fontSize: 11,
    fontFamily: typography.mono.fontFamily,
    opacity: 0.6,
  },
  transcriptArea: {
    position: "absolute",
    bottom: 0,
    left: 0,
    right: 0,
    paddingHorizontal: 24,
    alignItems: "center",
    minHeight: 120,
  },
  stateLabel: {
    color: colors.text.primary,
    fontSize: 16,
    fontFamily: typography.body.fontFamily,
    opacity: 0.6,
    marginBottom: 12,
    letterSpacing: 0.5,
  },
  transcriptText: {
    color: colors.text.primary,
    fontSize: 14,
    fontFamily: typography.body.fontFamily,
    opacity: 0.6,
    textAlign: "center",
    marginBottom: 8,
  },
  responseText: {
    color: colors.text.primary,
    fontSize: 14,
    fontFamily: typography.body.fontFamily,
    opacity: 0.6,
    textAlign: "center",
    lineHeight: 22,
  },
});
