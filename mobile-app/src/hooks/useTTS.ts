/**
 * useTTS — Text-to-Speech hook for GUAPPA.
 *
 * Provides a clean interface around expo-speech (or platform TTS fallback)
 * with integration into the SwarmController for visual state management.
 *
 * Features:
 *   - speak(text, options) — start speaking
 *   - stop() — interrupt speech
 *   - Voice selection (language, pitch, rate)
 *   - Callbacks: onStart, onDone, onError
 *   - SwarmController integration (sets state to 'speaking' during TTS)
 *   - Sentence-level streaming support for incremental LLM output
 *   - Queue management for sequential utterances
 */
import { useCallback, useEffect, useRef, useState } from "react";
import { log } from "../logger";
import { swarmStore } from "../swarm/SwarmController";

export type TTSState = "idle" | "speaking" | "paused" | "error";

export interface TTSVoice {
  identifier: string;
  name: string;
  language: string;
  quality?: "default" | "enhanced";
}

export interface TTSOptions {
  /** Language code (e.g. "en-US", "es-ES"). Default "en-US" */
  language?: string;
  /** Speaking rate. 0.5 = half speed, 1.0 = normal, 2.0 = double. Default 1.0 */
  rate?: number;
  /** Voice pitch. 0.5 = low, 1.0 = normal, 2.0 = high. Default 1.0 */
  pitch?: number;
  /** Specific voice identifier (platform-dependent) */
  voice?: string;
  /** Callback when speech starts */
  onStart?: () => void;
  /** Callback when speech completes */
  onDone?: () => void;
  /** Callback on error */
  onError?: (error: string) => void;
}

export interface UseTTSConfig {
  /** Whether to update SwarmController state during speech. Default true */
  syncWithSwarm?: boolean;
  /** Default language. Default "en-US" */
  defaultLanguage?: string;
  /** Default speaking rate. Default 1.0 */
  defaultRate?: number;
  /** Default pitch. Default 1.0 */
  defaultPitch?: number;
}

// Lazy-load expo-speech to avoid hard crash if not installed
let Speech: any = null;
function getSpeech(): any {
  if (Speech) return Speech;
  try {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    Speech = require("expo-speech");
    return Speech;
  } catch {
    log("warn", "expo-speech not available, TTS will use platform fallback");
    return null;
  }
}

/**
 * Splits text into sentence-sized chunks for streaming TTS.
 * Preserves sentence boundaries for natural pauses.
 */
function splitIntoSentences(text: string): string[] {
  const parts = text.match(/[^.!?]+[.!?]+[\s]*/g);
  if (!parts) return [text];
  return parts.map((s) => s.trim()).filter(Boolean);
}

export function useTTS(config: UseTTSConfig = {}) {
  const {
    syncWithSwarm = true,
    defaultLanguage = "en-US",
    defaultRate = 1.0,
    defaultPitch = 1.0,
  } = config;

  const [ttsState, setTtsState] = useState<TTSState>("idle");
  const [availableVoices, setAvailableVoices] = useState<TTSVoice[]>([]);
  const [currentText, setCurrentText] = useState<string | null>(null);

  const queueRef = useRef<Array<{ text: string; options: TTSOptions }>>([]);
  const isProcessingQueueRef = useRef(false);
  const stoppedRef = useRef(false);
  const streamBufferRef = useRef("");

  // -----------------------------------------------------------------------
  // Load available voices on mount
  // -----------------------------------------------------------------------
  useEffect(() => {
    const loadVoices = async () => {
      const speech = getSpeech();
      if (!speech) return;

      try {
        const voices = await speech.getAvailableVoicesAsync();
        const mapped: TTSVoice[] = voices.map((v: any) => ({
          identifier: v.identifier,
          name: v.name,
          language: v.language,
          quality: v.quality as TTSVoice["quality"],
        }));
        setAvailableVoices(mapped);
        log("debug", `TTS: loaded ${mapped.length} voices`);
      } catch (err) {
        log("warn", "TTS: failed to load voices", { error: String(err) });
      }
    };

    loadVoices();
  }, []);

  // -----------------------------------------------------------------------
  // Core speak function
  // -----------------------------------------------------------------------
  const speakSingle = useCallback(
    async (text: string, options: TTSOptions = {}): Promise<void> => {
      const speech = getSpeech();
      if (!speech) {
        log("error", "TTS: no speech engine available");
        options.onError?.("No speech engine available");
        return;
      }

      if (!text.trim()) return;

      return new Promise<void>((resolve) => {
        setTtsState("speaking");
        setCurrentText(text);

        if (syncWithSwarm) {
          swarmStore.setState("speaking");
        }

        speech.speak(text, {
          language: options.language ?? defaultLanguage,
          rate: options.rate ?? defaultRate,
          pitch: options.pitch ?? defaultPitch,
          voice: options.voice,
          onStart: () => {
            log("debug", "TTS: utterance started");
            options.onStart?.();
          },
          onDone: () => {
            log("debug", "TTS: utterance done");
            setCurrentText(null);
            options.onDone?.();
            resolve();
          },
          onError: (error: any) => {
            const msg = typeof error === "string" ? error : error?.message ?? "unknown";
            log("error", "TTS: utterance error", { error: msg });
            setTtsState("error");
            setCurrentText(null);
            if (syncWithSwarm) {
              swarmStore.setState("idle");
            }
            options.onError?.(msg);
            resolve();
          },
          onStopped: () => {
            log("debug", "TTS: utterance stopped");
            setCurrentText(null);
            resolve();
          },
        });
      });
    },
    [syncWithSwarm, defaultLanguage, defaultRate, defaultPitch],
  );

  // -----------------------------------------------------------------------
  // Queue processing
  // -----------------------------------------------------------------------
  const processQueue = useCallback(async () => {
    if (isProcessingQueueRef.current) return;
    isProcessingQueueRef.current = true;

    while (queueRef.current.length > 0 && !stoppedRef.current) {
      const item = queueRef.current.shift();
      if (!item) break;
      await speakSingle(item.text, item.options);
    }

    isProcessingQueueRef.current = false;

    if (!stoppedRef.current) {
      setTtsState("idle");
      if (syncWithSwarm) {
        swarmStore.setState("idle");
      }
    }
  }, [speakSingle, syncWithSwarm]);

  // -----------------------------------------------------------------------
  // Public API
  // -----------------------------------------------------------------------

  /**
   * Speak text. If text contains multiple sentences, they are queued
   * and spoken sequentially for natural pacing.
   */
  const speak = useCallback(
    (text: string, options: TTSOptions = {}) => {
      stoppedRef.current = false;
      const sentences = splitIntoSentences(text);

      for (const sentence of sentences) {
        queueRef.current.push({ text: sentence, options });
      }

      processQueue();
    },
    [processQueue],
  );

  /**
   * Immediately stop all speech and clear the queue.
   */
  const stop = useCallback(() => {
    stoppedRef.current = true;
    queueRef.current = [];
    streamBufferRef.current = "";

    const speech = getSpeech();
    if (speech) {
      speech.stop();
    }

    setTtsState("idle");
    setCurrentText(null);

    if (syncWithSwarm) {
      swarmStore.setState("idle");
    }

    log("debug", "TTS: stopped");
  }, [syncWithSwarm]);

  /**
   * Check if TTS is currently speaking.
   */
  const isSpeaking = useCallback(async (): Promise<boolean> => {
    const speech = getSpeech();
    if (!speech) return false;
    return speech.isSpeakingAsync();
  }, []);

  /**
   * Speak streaming text — call this repeatedly as LLM tokens arrive.
   * Buffers text and speaks complete sentences as they form.
   */
  const speakStreaming = useCallback(
    (chunk: string, options: TTSOptions = {}) => {
      streamBufferRef.current += chunk;

      // Check for sentence boundaries
      const sentenceEnd = /[.!?]\s*$/;
      if (sentenceEnd.test(streamBufferRef.current)) {
        const text = streamBufferRef.current.trim();
        streamBufferRef.current = "";
        if (text) {
          stoppedRef.current = false;
          queueRef.current.push({ text, options });
          if (!isProcessingQueueRef.current) {
            processQueue();
          }
        }
      }
    },
    [processQueue],
  );

  /**
   * Flush any remaining buffered streaming text.
   * Call this when the LLM response is complete.
   */
  const flushStreaming = useCallback(
    (options: TTSOptions = {}) => {
      const remaining = streamBufferRef.current.trim();
      streamBufferRef.current = "";
      if (remaining) {
        stoppedRef.current = false;
        queueRef.current.push({ text: remaining, options });
        if (!isProcessingQueueRef.current) {
          processQueue();
        }
      }
    },
    [processQueue],
  );

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      stoppedRef.current = true;
      queueRef.current = [];
      streamBufferRef.current = "";
      const speech = getSpeech();
      if (speech) {
        speech.stop();
      }
    };
  }, []);

  return {
    /** Current TTS state */
    ttsState,
    /** Text currently being spoken */
    currentText,
    /** Available TTS voices */
    availableVoices,
    /** Speak text (auto-splits into sentences) */
    speak,
    /** Stop all speech and clear queue */
    stop,
    /** Check if currently speaking */
    isSpeaking,
    /** Incrementally speak streamed LLM output */
    speakStreaming,
    /** Flush remaining streaming buffer */
    flushStreaming,
  } as const;
}
