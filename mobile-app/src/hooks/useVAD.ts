/**
 * useVAD — Voice Activity Detection hook.
 *
 * Monitors microphone audio energy to detect speech start and end:
 *   - Speech start: energy above threshold for >200ms (configurable)
 *   - Speech end: energy below threshold for >1500ms (configurable)
 *   - Auto-stop recording after silence detected
 *
 * This is designed to work alongside useVoiceRecording — when VAD detects
 * that the user has stopped speaking, it can automatically trigger
 * transcription without requiring a manual "stop" button press.
 */
import { useCallback, useEffect, useRef, useState } from "react";
import { Audio } from "expo-av";
import { log } from "../logger";

export type VADState = "inactive" | "monitoring" | "speech" | "silence";

export interface UseVADConfig {
  /** Callback when speech starts (energy above threshold sustained) */
  onSpeechStart?: () => void;
  /** Callback when speech ends (energy below threshold sustained) */
  onSpeechEnd?: () => void;
  /** Continuous energy level callback (0-1), called at poll rate */
  onEnergyChange?: (energy: number) => void;
  /** Energy threshold (0-1) to distinguish speech from silence. Default 0.12 */
  energyThreshold?: number;
  /** Duration (ms) energy must stay above threshold to detect speech start. Default 200 */
  speechStartMs?: number;
  /** Duration (ms) energy must stay below threshold to detect speech end. Default 1500 */
  speechEndMs?: number;
  /** Polling interval for metering (ms). Default 50 (20Hz) */
  pollIntervalMs?: number;
  /** Maximum recording duration (ms) before auto-stop. Default 60000 (1 min) */
  maxDurationMs?: number;
}

export function useVAD(config: UseVADConfig = {}) {
  const {
    onSpeechStart,
    onSpeechEnd,
    onEnergyChange,
    energyThreshold = 0.12,
    speechStartMs = 200,
    speechEndMs = 1500,
    pollIntervalMs = 50,
    maxDurationMs = 60_000,
  } = config;

  const [vadState, setVadState] = useState<VADState>("inactive");
  const [energy, setEnergy] = useState(0);
  const [isSpeaking, setIsSpeaking] = useState(false);

  const recordingRef = useRef<Audio.Recording | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const monitorStartRef = useRef<number>(0);

  // Timestamps for speech/silence detection
  const aboveThresholdSinceRef = useRef<number | null>(null);
  const belowThresholdSinceRef = useRef<number | null>(null);
  const speechDetectedRef = useRef(false);

  // Keep callbacks in refs to avoid stale closures
  const onSpeechStartRef = useRef(onSpeechStart);
  const onSpeechEndRef = useRef(onSpeechEnd);
  const onEnergyChangeRef = useRef(onEnergyChange);

  useEffect(() => {
    onSpeechStartRef.current = onSpeechStart;
  }, [onSpeechStart]);
  useEffect(() => {
    onSpeechEndRef.current = onSpeechEnd;
  }, [onSpeechEnd]);
  useEffect(() => {
    onEnergyChangeRef.current = onEnergyChange;
  }, [onEnergyChange]);

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
    aboveThresholdSinceRef.current = null;
    belowThresholdSinceRef.current = null;
    speechDetectedRef.current = false;
  }, []);

  /**
   * Start monitoring audio energy for voice activity.
   */
  const start = useCallback(async (): Promise<boolean> => {
    if (vadState === "monitoring" || vadState === "speech") {
      return true; // Already running
    }

    try {
      const { granted } = await Audio.requestPermissionsAsync();
      if (!granted) {
        log("warn", "VAD: microphone permission denied");
        return false;
      }

      await Audio.setAudioModeAsync({
        allowsRecordingIOS: true,
        playsInSilentModeIOS: true,
        staysActiveInBackground: false,
      });

      const recording = new Audio.Recording();
      await recording.prepareToRecordAsync({
        ...Audio.RecordingOptionsPresets.HIGH_QUALITY,
        isMeteringEnabled: true,
      });
      await recording.startAsync();
      recordingRef.current = recording;
      monitorStartRef.current = Date.now();
      speechDetectedRef.current = false;
      aboveThresholdSinceRef.current = null;
      belowThresholdSinceRef.current = null;

      setVadState("monitoring");
      setIsSpeaking(false);

      // Poll metering
      intervalRef.current = setInterval(async () => {
        if (!recordingRef.current) return;

        // Auto-stop after max duration
        if (Date.now() - monitorStartRef.current > maxDurationMs) {
          log("debug", "VAD: max duration reached, auto-stopping");
          if (speechDetectedRef.current) {
            onSpeechEndRef.current?.();
          }
          await cleanup();
          setVadState("inactive");
          setIsSpeaking(false);
          setEnergy(0);
          return;
        }

        try {
          const status = await recordingRef.current.getStatusAsync();
          if (!status.isRecording || status.metering == null) return;

          // Convert dBFS to 0-1
          const db = status.metering;
          const raw = Math.max(0, Math.min(1, (db + 60) / 60));

          setEnergy(raw);
          onEnergyChangeRef.current?.(raw);

          const now = Date.now();

          if (raw >= energyThreshold) {
            // Above threshold
            belowThresholdSinceRef.current = null;

            if (aboveThresholdSinceRef.current === null) {
              aboveThresholdSinceRef.current = now;
            }

            // Detect speech start
            if (
              !speechDetectedRef.current &&
              now - aboveThresholdSinceRef.current >= speechStartMs
            ) {
              speechDetectedRef.current = true;
              setIsSpeaking(true);
              setVadState("speech");
              log("debug", "VAD: speech started");
              onSpeechStartRef.current?.();
            }
          } else {
            // Below threshold
            aboveThresholdSinceRef.current = null;

            if (belowThresholdSinceRef.current === null) {
              belowThresholdSinceRef.current = now;
            }

            // Detect speech end (only if speech was previously detected)
            if (
              speechDetectedRef.current &&
              now - belowThresholdSinceRef.current >= speechEndMs
            ) {
              speechDetectedRef.current = false;
              setIsSpeaking(false);
              setVadState("silence");
              log("debug", "VAD: speech ended (silence detected)");
              onSpeechEndRef.current?.();
            }
          }
        } catch {
          // Ignore polling errors
        }
      }, pollIntervalMs);

      log("debug", "VAD monitoring started");
      return true;
    } catch (err) {
      log("error", "VAD: failed to start", { error: String(err) });
      setVadState("inactive");
      return false;
    }
  }, [
    vadState,
    energyThreshold,
    speechStartMs,
    speechEndMs,
    pollIntervalMs,
    maxDurationMs,
    cleanup,
  ]);

  /**
   * Stop monitoring audio energy.
   */
  const stop = useCallback(async () => {
    await cleanup();
    setVadState("inactive");
    setIsSpeaking(false);
    setEnergy(0);
    log("debug", "VAD monitoring stopped");
  }, [cleanup]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      cleanup();
    };
  }, [cleanup]);

  return {
    /** Current VAD state */
    vadState,
    /** Current energy level (0-1) */
    energy,
    /** Whether speech is currently detected */
    isSpeaking,
    /** Start VAD monitoring */
    start,
    /** Stop VAD monitoring */
    stop,
  } as const;
}
