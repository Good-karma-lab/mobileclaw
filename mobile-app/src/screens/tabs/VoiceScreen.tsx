import React, { useState, useCallback, useRef, useEffect } from "react";
import { View, StyleSheet, Pressable } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withTiming,
  withRepeat,
  withSequence,
  FadeIn,
  FadeOut,
  Easing,
} from "react-native-reanimated";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { springs } from "../../theme/animations";
import { PlasmaOrb } from "../../components/plasma/PlasmaOrb";

type VoiceState = "idle" | "listening" | "thinking" | "speaking";

const STATE_LABELS: Record<VoiceState, string> = {
  idle: "Tap to speak",
  listening: "Listening...",
  thinking: "Thinking...",
  speaking: "Speaking...",
};

const AUTO_HIDE_DELAY = 3000;

export function VoiceScreen() {
  const insets = useSafeAreaInsets();
  const [voiceState, setVoiceState] = useState<VoiceState>("idle");
  const [transcript, setTranscript] = useState("");
  const [response, setResponse] = useState("");
  const [chromeVisible, setChromeVisible] = useState(false);
  const autoHideTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // -- Reanimated shared values --
  const chromeOpacity = useSharedValue(0);
  const glowScale = useSharedValue(1);
  const glowOpacity = useSharedValue(0.05);

  // Breathing radial glow animation
  useEffect(() => {
    glowScale.value = withRepeat(
      withSequence(
        withTiming(1.15, {
          duration: 3000,
          easing: Easing.inOut(Easing.sin),
        }),
        withTiming(1.0, {
          duration: 3000,
          easing: Easing.inOut(Easing.sin),
        })
      ),
      -1,
      true
    );
  }, [glowScale]);

  // Update glow intensity based on voice state
  useEffect(() => {
    const glowLevels: Record<VoiceState, number> = {
      idle: 0.05,
      listening: 0.1,
      thinking: 0.08,
      speaking: 0.12,
    };
    glowOpacity.value = withTiming(glowLevels[voiceState], { duration: 600 });
  }, [voiceState, glowOpacity]);

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

  // Cleanup timer on unmount
  useEffect(() => {
    return () => {
      if (autoHideTimer.current) {
        clearTimeout(autoHideTimer.current);
      }
    };
  }, []);

  const handleOrbPress = useCallback(() => {
    // Also show chrome on tap
    showChrome();

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
      setVoiceState("thinking");
    } else if (voiceState === "speaking") {
      setVoiceState("idle");
    }
  }, [voiceState, showChrome]);

  const handleBackgroundTap = useCallback(() => {
    showChrome();
  }, [showChrome]);

  // Connection status
  const connectionColor =
    voiceState === "idle"
      ? colors.semantic.success
      : voiceState === "listening" || voiceState === "speaking"
        ? colors.semantic.success
        : colors.accent.amber;

  // -- Animated styles --
  const chromeAnimatedStyle = useAnimatedStyle(() => ({
    opacity: chromeOpacity.value,
  }));

  const glowAnimatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: glowScale.value }],
    opacity: glowOpacity.value,
  }));

  return (
    <LinearGradient
      colors={[colors.base.spaceBlack, colors.base.midnightBlue]}
      style={styles.container}
      testID="voice-screen"
    >
      <Pressable style={styles.fill} onPress={handleBackgroundTap}>
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

        {/* Orb area with breathing glow */}
        <View style={styles.orbContainer}>
          {/* Breathing radial glow behind the orb */}
          <Animated.View style={[styles.radialGlow, glowAnimatedStyle]} />

          <Pressable onPress={handleOrbPress}>
            <PlasmaOrb
              size={300}
              state={voiceState}
              audioLevel={voiceState === "listening" ? 0.5 : 0}
            />
          </Pressable>
        </View>

        {/* State label + transcript area */}
        <View
          style={[
            styles.transcriptArea,
            { paddingBottom: insets.bottom + 80 },
          ]}
        >
          {/* State label with cross-fade */}
          <Animated.Text
            key={voiceState}
            entering={FadeIn.duration(300)}
            exiting={FadeOut.duration(200)}
            style={styles.stateLabel}
          >
            {STATE_LABELS[voiceState]}
          </Animated.Text>

          {/* Transcript text */}
          {transcript !== "" && (
            <Animated.Text
              entering={FadeIn.duration(400)}
              exiting={FadeOut.duration(300)}
              style={styles.transcriptText}
            >
              {transcript}
            </Animated.Text>
          )}

          {/* Response text */}
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
      </Pressable>
    </LinearGradient>
  );
}

const styles = StyleSheet.create({
  fill: {
    flex: 1,
  },
  container: {
    flex: 1,
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
  orbContainer: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  radialGlow: {
    position: "absolute",
    width: 400,
    height: 400,
    borderRadius: 200,
    backgroundColor: colors.accent.cyan,
  },
  transcriptArea: {
    paddingHorizontal: 24,
    paddingBottom: 16,
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
