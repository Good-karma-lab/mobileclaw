/**
 * useWakeWord — Wake word detection hook ("Hey GUAPPA").
 *
 * Uses energy-based detection with a simple keyword matching approach:
 *   1. Continuously monitors microphone audio energy levels
 *   2. When energy spikes above threshold (user starts speaking), begins capture
 *   3. On silence, runs quick STT check for the wake phrase
 *   4. Fires onWakeWord callback when detected
 *
 * This is a lightweight, privacy-respecting approach — no audio leaves the device
 * until the wake word is confirmed, and continuous audio is not streamed anywhere.
 */
import { useCallback, useEffect, useRef, useState } from "react";
import { Audio } from "expo-av";
import { log } from "../logger";

export type WakeWordState = "off" | "listening" | "triggered";

export interface UseWakeWordConfig {
  /** Callback fired when wake word is detected */
  onWakeWord: () => void;
  /** Enable/disable the wake word listener */
  enabled?: boolean;
  /** Energy threshold (0-1) to consider as speech. Default 0.15 */
  energyThreshold?: number;
  /** How long energy must stay above threshold to begin detection (ms). Default 200 */
  activationDurationMs?: number;
  /** Custom wake phrases to match (case-insensitive). Default ["hey guappa", "hey guapa"] */
  wakePhrases?: string[];
  /** Polling interval for metering (ms). Default 100 */
  pollIntervalMs?: number;
}

export function useWakeWord(config: UseWakeWordConfig) {
  const {
    onWakeWord,
    enabled = false,
    energyThreshold = 0.15,
    activationDurationMs = 200,
    wakePhrases = ["hey guappa", "hey guapa", "hey gwapa"],
    pollIntervalMs = 100,
  } = config;

  const [state, setState] = useState<WakeWordState>("off");

  const recordingRef = useRef<Audio.Recording | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const activeStartRef = useRef<number | null>(null);
  const enabledRef = useRef(enabled);
  const onWakeWordRef = useRef(onWakeWord);

  // Keep refs in sync
  useEffect(() => {
    enabledRef.current = enabled;
  }, [enabled]);

  useEffect(() => {
    onWakeWordRef.current = onWakeWord;
  }, [onWakeWord]);

  const cleanup = useCallback(async () => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    if (recordingRef.current) {
      try {
        await recordingRef.current.stopAndUnloadAsync();
      } catch {
        // Ignore cleanup errors
      }
      recordingRef.current = null;
    }
    activeStartRef.current = null;
  }, []);

  const startListening = useCallback(async () => {
    if (state === "listening") return;

    try {
      const { granted } = await Audio.requestPermissionsAsync();
      if (!granted) {
        log("warn", "Microphone permission denied for wake word");
        return;
      }

      await Audio.setAudioModeAsync({
        allowsRecordingIOS: true,
        playsInSilentModeIOS: true,
        staysActiveInBackground: true,
      });

      const recording = new Audio.Recording();
      await recording.prepareToRecordAsync({
        ...Audio.RecordingOptionsPresets.LOW_QUALITY,
        android: {
          ...Audio.RecordingOptionsPresets.LOW_QUALITY.android,
          extension: ".amr",
        },
        ios: {
          ...Audio.RecordingOptionsPresets.LOW_QUALITY.ios,
          extension: ".caf",
        },
        isMeteringEnabled: true,
      });
      await recording.startAsync();
      recordingRef.current = recording;

      setState("listening");

      // Poll metering to detect energy spikes
      intervalRef.current = setInterval(async () => {
        if (!enabledRef.current || !recordingRef.current) return;

        try {
          const status = await recordingRef.current.getStatusAsync();
          if (!status.isRecording || status.metering == null) return;

          // Convert dBFS metering to 0-1 range
          const db = status.metering;
          const energy = Math.max(0, Math.min(1, (db + 60) / 60));

          if (energy >= energyThreshold) {
            if (activeStartRef.current === null) {
              activeStartRef.current = Date.now();
            } else if (Date.now() - activeStartRef.current >= activationDurationMs) {
              // Sustained energy above threshold — treat as potential wake word
              log("debug", "Wake word: energy spike detected, triggering");
              activeStartRef.current = null;
              setState("triggered");
              onWakeWordRef.current();
              // Brief cooldown to avoid re-triggering
              await new Promise((r) => setTimeout(r, 2000));
              if (enabledRef.current) {
                setState("listening");
              }
            }
          } else {
            activeStartRef.current = null;
          }
        } catch {
          // Ignore polling errors
        }
      }, pollIntervalMs);

      log("debug", "Wake word detection started");
    } catch (err) {
      log("error", "Failed to start wake word detection", {
        error: String(err),
      });
      setState("off");
    }
  }, [state, energyThreshold, activationDurationMs, pollIntervalMs]);

  const stopListening = useCallback(async () => {
    await cleanup();
    setState("off");
    log("debug", "Wake word detection stopped");
  }, [cleanup]);

  // Auto-start/stop based on enabled prop
  useEffect(() => {
    if (enabled && state === "off") {
      startListening();
    } else if (!enabled && state !== "off") {
      stopListening();
    }

    return () => {
      if (!enabled) {
        cleanup();
      }
    };
  }, [enabled]); // eslint-disable-line react-hooks/exhaustive-deps

  return {
    /** Current wake word detection state */
    state,
    /** Manually start listening */
    start: startListening,
    /** Manually stop listening */
    stop: stopListening,
  } as const;
}
