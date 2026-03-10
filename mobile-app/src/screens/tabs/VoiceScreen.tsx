/**
 * VoiceScreen — the primary AI interface. Always listening.
 *
 * The neural swarm fills the background (rendered by RootNavigator).
 * Mic is always on. When the user speaks, VAD detects speech → STT →
 * agent → TTS → back to listening. No manual tap needed.
 *
 * Subtle overlays show transcript/response text that fades in/out.
 * This screen is transparent — the swarm shows through from the root.
 */
import React, { useState, useCallback, useRef, useEffect } from "react";
import { View, StyleSheet, Pressable } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withTiming,
  withRepeat,
  withSequence,
  Easing,
  FadeIn,
  FadeOut,
} from "react-native-reanimated";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { swarmStore } from "../../swarm/SwarmController";
import { swarmDirector } from "../../swarm/SwarmDirector";
import { voiceAmplitude } from "../../swarm/audio/VoiceAmplitude";
import { useVAD } from "../../hooks/useVAD";
import { useTTS } from "../../hooks/useTTS";
import { useWakeWord } from "../../hooks/useWakeWord";
import { useVoiceRecording } from "../../hooks/useVoiceRecording";
import { sendMessage } from "../../native/guappaAgent";
import { getModelPath, isModelDownloaded } from "../../voice/whisperModelManager";
import type { SwarmState } from "../../swarm/neurons/NeuronSystem";

export function VoiceScreen({ isActive }: { isActive?: boolean }) {
  const insets = useSafeAreaInsets();
  const [voiceState, setVoiceState] = useState<SwarmState>("idle");
  const [transcript, setTranscript] = useState("");
  const [response, setResponse] = useState("");
  const [modelReady, setModelReady] = useState(false);
  const transcriptRef = useRef("");
  const whisperModelPathRef = useRef<string>("");
  const voiceProviderRef = useRef<"android" | "whisper" | "deepgram">("android");
  const autoListenTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const processTranscriptRef = useRef<(text: string) => void>(() => {});
  const startListeningRef = useRef<() => void>(() => {});

  // Determine voice provider: android built-in (default), whisper if downloaded, deepgram if key
  useEffect(() => {
    (async () => {
      const downloaded = await isModelDownloaded("base.en");
      if (downloaded) {
        whisperModelPathRef.current = getModelPath("base.en");
      }
      // Android built-in is always ready (no model download needed)
      setModelReady(true);
    })();
  }, []);

  // STT engine
  const voice = useVoiceRecording({
    voiceProvider: voiceProviderRef.current,
    whisperModelPath: whisperModelPathRef.current || undefined,
  });

  // VAD: auto-detect silence to stop recording
  const vad = useVAD({
    onSpeechEnd: useCallback(() => {
      if (swarmStore.state.state === "listening") {
        voice.stop().then((finalText) => {
          if (finalText && finalText.trim().length > 0) {
            processTranscriptRef.current(finalText.trim());
          } else {
            // No speech detected — resume listening
            swarmStore.setState("idle");
            voiceAmplitude.stop();
            // Auto-restart listening after brief pause
            autoListenTimerRef.current = setTimeout(() => {
              startListeningRef.current();
            }, 500);
          }
        });
      }
    }, []),
  });

  // TTS
  const tts = useTTS({
    syncWithSwarm: true,
    defaultRate: 1.0,
  });

  // Wake word
  useWakeWord({
    enabled: false, // Mic is always on, no wake word needed
    onWakeWord: useCallback(() => {}, []),
  });

  // Interim transcript → SwarmDirector
  useEffect(() => {
    if (voice.interimText && voice.interimText !== "Listening…") {
      setTranscript(voice.interimText);
      transcriptRef.current = voice.interimText;
      swarmDirector.analyzeTranscript(voice.interimText);
    }
  }, [voice.interimText]);

  // Process final transcript
  const processTranscript = useCallback(async (text: string) => {
    swarmStore.setState("processing");
    voiceAmplitude.stop();
    swarmDirector.analyzeTranscript(text);

    try {
      const agentResponse = await sendMessage(text);
      setResponse(agentResponse);
      swarmDirector.analyzeAgentResponse(agentResponse);
      tts.speak(agentResponse);
    } catch {
      setResponse("I couldn't process that. Let me try again.");
      swarmStore.setState("idle");
      // Resume listening after error
      autoListenTimerRef.current = setTimeout(() => {
        startListeningRef.current();
      }, 2000);
    }
  }, [tts]);

  // Start listening
  const startListening = useCallback(async () => {
    if (!modelReady) return;
    swarmStore.setState("listening");
    setTranscript("");
    transcriptRef.current = "";
    voiceAmplitude.start();
    await voice.start();
  }, [voice, modelReady]);

  // Keep refs in sync
  useEffect(() => { processTranscriptRef.current = processTranscript; }, [processTranscript]);
  useEffect(() => { startListeningRef.current = startListening; }, [startListening]);

  // Sync swarm state
  useEffect(() => {
    const unsub = swarmStore.subscribe((state) => {
      setVoiceState(state.state);
    });
    return unsub;
  }, []);

  // AUTO-START listening when model is ready and screen is active
  useEffect(() => {
    if (modelReady && voiceState === "idle") {
      autoListenTimerRef.current = setTimeout(() => {
        startListeningRef.current();
      }, 800);
    }
    return () => {
      if (autoListenTimerRef.current) {
        clearTimeout(autoListenTimerRef.current);
      }
    };
  }, [modelReady]);

  // When TTS finishes speaking, auto-resume listening
  useEffect(() => {
    if (voiceState === "idle" && modelReady && !tts.isSpeaking) {
      autoListenTimerRef.current = setTimeout(() => {
        startListeningRef.current();
      }, 600);
    }
  }, [voiceState, modelReady, tts.isSpeaking]);

  // Cleanup
  useEffect(() => {
    return () => {
      if (autoListenTimerRef.current) clearTimeout(autoListenTimerRef.current);
    };
  }, []);

  // Tap on screen: if speaking, stop TTS. If idle, force restart.
  const handleTap = useCallback(async () => {
    if (voiceState === "speaking") {
      tts.stop();
      swarmStore.setState("idle");
    } else if (voiceState === "idle") {
      startListening();
    }
  }, [voiceState, tts, startListening]);

  // Breathing pulse for state indicator
  const pulseScale = useSharedValue(1);
  useEffect(() => {
    if (voiceState === "listening") {
      pulseScale.value = withRepeat(
        withSequence(
          withTiming(1.2, { duration: 800, easing: Easing.inOut(Easing.sin) }),
          withTiming(1.0, { duration: 800, easing: Easing.inOut(Easing.sin) }),
        ),
        -1, false
      );
    } else {
      pulseScale.value = withTiming(1, { duration: 300 });
    }
  }, [voiceState]);

  const pulseStyle = useAnimatedStyle(() => ({
    transform: [{ scale: pulseScale.value }],
  }));

  const stateColor = {
    idle: "rgba(26, 92, 106, 0.3)",
    listening: "rgba(26, 122, 106, 0.4)",
    processing: "rgba(90, 58, 138, 0.4)",
    speaking: "rgba(26, 92, 106, 0.4)",
  }[voiceState] ?? "rgba(26, 92, 106, 0.3)";

  return (
    <View style={styles.container} testID="voice-screen">
      {/* Transparent — swarm shows through from RootNavigator */}
      <Pressable style={StyleSheet.absoluteFill} onPress={handleTap} testID="mic-button" />

      {/* Minimal state indicator — small glowing dot at top center */}
      <View style={[styles.stateIndicator, { marginTop: insets.top + 16 }]}>
        <Animated.View style={[styles.stateDot, { backgroundColor: stateColor }, pulseStyle]} />
      </View>

      {/* Transcript + response overlay at bottom */}
      <View
        style={[styles.transcriptArea, { paddingBottom: insets.bottom + 80 }]}
        pointerEvents="none"
        testID="voice-transcript-area"
      >
        {voiceState === "listening" && (
          <Animated.Text
            entering={FadeIn.duration(300)}
            exiting={FadeOut.duration(200)}
            style={styles.listeningLabel}
          >
            ●  Listening
          </Animated.Text>
        )}

        {voiceState === "processing" && (
          <Animated.Text
            entering={FadeIn.duration(300)}
            exiting={FadeOut.duration(200)}
            style={styles.processingLabel}
          >
            Thinking...
          </Animated.Text>
        )}

        {transcript !== "" && (
          <Animated.Text
            entering={FadeIn.duration(400)}
            exiting={FadeOut.duration(300)}
            style={styles.transcriptText}
            numberOfLines={3}
          >
            {transcript}
          </Animated.Text>
        )}

        {response !== "" && voiceState !== "listening" && (
          <Animated.Text
            entering={FadeIn.duration(400)}
            exiting={FadeOut.duration(300)}
            style={styles.responseText}
            numberOfLines={5}
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
    // Transparent — swarm background shows through
    backgroundColor: "transparent",
  },
  stateIndicator: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    alignItems: "center",
    zIndex: 10,
  },
  stateDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  transcriptArea: {
    position: "absolute",
    bottom: 0,
    left: 0,
    right: 0,
    paddingHorizontal: 24,
    alignItems: "center",
    minHeight: 100,
  },
  listeningLabel: {
    color: "rgba(26, 122, 106, 0.5)",
    fontSize: 13,
    fontFamily: "JetBrainsMono_500Medium",
    letterSpacing: 1,
    marginBottom: 8,
  },
  processingLabel: {
    color: "rgba(90, 58, 138, 0.5)",
    fontSize: 13,
    fontFamily: "JetBrainsMono_500Medium",
    letterSpacing: 1,
    marginBottom: 8,
  },
  transcriptText: {
    color: "rgba(255, 255, 255, 0.5)",
    fontSize: 14,
    fontFamily: "Exo2_400Regular",
    textAlign: "center",
    marginBottom: 8,
  },
  responseText: {
    color: "rgba(255, 255, 255, 0.6)",
    fontSize: 14,
    fontFamily: "Exo2_400Regular",
    textAlign: "center",
    lineHeight: 22,
  },
});
