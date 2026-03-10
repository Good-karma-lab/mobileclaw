import { useCallback, useEffect, useRef, useState } from "react";
import { Audio } from "expo-av";
import { NativeModules, NativeEventEmitter, Platform } from "react-native";
import { Buffer } from "buffer";
import LiveAudioStream from "react-native-live-audio-stream";
import { initWhisper, WhisperContext } from "whisper.rn";

import { log } from "../logger";

const { AndroidSTT } = NativeModules;
const androidSTTEmitter = AndroidSTT ? new NativeEventEmitter(AndroidSTT) : null;

type DeepgramResult = {
  type?: string;
  is_final?: boolean;
  speech_final?: boolean;
  channel?: { alternatives?: Array<{ transcript?: string }> };
};

export type VoiceState = "idle" | "recording" | "transcribing";

export function useVoiceRecording(config: {
  voiceProvider: "deepgram" | "whisper" | "android";
  deepgramApiKey?: string;
  whisperModelPath?: string;
}) {
  const [state, setState] = useState<VoiceState>("idle");
  const [volume, setVolume] = useState(0);
  const [transcript, setTranscript] = useState("");
  const [interimText, setInterimText] = useState("");

  const wsRef = useRef<WebSocket | null>(null);
  const streamSubRef = useRef<{ remove?: () => void } | null>(null);
  const finalChunksRef = useRef<string[]>([]);
  const interimChunkRef = useRef("");
  const resolveStopRef = useRef<((text: string) => void) | null>(null);
  const startedRef = useRef(false);
  const whisperCtxRef = useRef<WhisperContext | null>(null);
  const recordingRef = useRef<Audio.Recording | null>(null);
  const androidListenersRef = useRef<Array<{ remove: () => void }>>([]);

  const composeText = () => {
    const finalText = finalChunksRef.current.join(" ").trim();
    const interim = interimChunkRef.current.trim();
    return `${finalText} ${interim}`.trim();
  };

  const cleanupStream = () => {
    try { LiveAudioStream.stop(); } catch { /* ignore */ }
    try { streamSubRef.current?.remove?.(); } catch { /* ignore */ }
    streamSubRef.current = null;
  };

  const cleanupAndroidListeners = () => {
    for (const sub of androidListenersRef.current) {
      try { sub.remove(); } catch { /* ignore */ }
    }
    androidListenersRef.current = [];
  };

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      cleanupAndroidListeners();
      cleanupStream();
    };
  }, []);

  const transcribeWithWhisper = useCallback(async (audioUri: string) => {
    if (!whisperCtxRef.current && config.whisperModelPath) {
      whisperCtxRef.current = await initWhisper({
        filePath: config.whisperModelPath,
      });
    }
    if (!whisperCtxRef.current) throw new Error("Whisper model not loaded");
    const { promise } = whisperCtxRef.current.transcribe(audioUri, { language: "en" });
    const result = await promise;
    return result.result || "";
  }, [config.whisperModelPath]);

  // ── Android Built-in STT ──
  const startAndroidSTT = useCallback(async (): Promise<boolean> => {
    if (!AndroidSTT || !androidSTTEmitter) {
      setInterimText("Android STT not available");
      setState("idle");
      return false;
    }

    try {
      const available = await AndroidSTT.isAvailable();
      if (!available) {
        setInterimText("Speech recognition not available on this device");
        setState("idle");
        return false;
      }
    } catch (err) {
      log("error", "Android STT availability check failed", { error: String(err) });
      setInterimText("Speech recognition not available");
      setState("idle");
      return false;
    }

    cleanupAndroidListeners();

    // Listen for results
    const resultSub = androidSTTEmitter.addListener("androidSTT_result", (event: any) => {
      const text = event.transcript || "";
      if (event.isFinal) {
        if (text) finalChunksRef.current.push(text);
        interimChunkRef.current = "";
      } else {
        interimChunkRef.current = text;
      }
      const live = composeText();
      setTranscript(finalChunksRef.current.join(" ").trim());
      setInterimText(live || "Listening…");

      // Final result = auto-resolve stop
      if (event.isFinal && text) {
        const finalTranscript = composeText();
        resolveStopRef.current?.(finalTranscript);
        resolveStopRef.current = null;
      }
    });

    const readySub = androidSTTEmitter.addListener("androidSTT_ready", () => {
      setInterimText("Listening…");
    });

    const rmsSub = androidSTTEmitter.addListener("androidSTT_rmsChanged", (event: any) => {
      const rms = event.rmsDb ?? -60;
      const normalized = Math.max(0, Math.min(1, (rms + 12) / 22));
      setVolume(normalized);
    });

    const errorSub = androidSTTEmitter.addListener("androidSTT_error", (event: any) => {
      log("warn", "Android STT error", { code: event.errorCode, msg: event.errorMessage });
      // "No speech detected" is not a critical error
      if (event.errorCode === 7) { // ERROR_NO_MATCH
        setInterimText("Didn't catch that. Try again.");
        resolveStopRef.current?.("");
        resolveStopRef.current = null;
      } else if (event.errorCode === 6) { // ERROR_SPEECH_TIMEOUT
        setInterimText("No speech heard. Try again.");
        resolveStopRef.current?.("");
        resolveStopRef.current = null;
      } else {
        setInterimText(event.errorMessage || "Recognition error");
        resolveStopRef.current?.("");
        resolveStopRef.current = null;
      }
      setState("idle");
      setVolume(0);
    });

    const endSub = androidSTTEmitter.addListener("androidSTT_speechEnd", () => {
      // Speech ended, wait for final result
      setInterimText("Processing…");
    });

    androidListenersRef.current = [resultSub, readySub, rmsSub, errorSub, endSub];

    try {
      await AndroidSTT.startListening({ language: "en-US", offline: false });
      setState("recording");
      startedRef.current = true;
      log("debug", "Android STT started");
      return true;
    } catch (err) {
      log("error", "Android STT start failed", { error: String(err) });
      cleanupAndroidListeners();
      setInterimText("Failed to start speech recognition");
      setState("idle");
      return false;
    }
  }, []);

  const stopAndroidSTT = useCallback(async (): Promise<string> => {
    setState("transcribing");
    startedRef.current = false;

    try {
      // Create promise that resolves when final result arrives or timeout
      const resultPromise = new Promise<string>((resolve) => {
        resolveStopRef.current = resolve;
        // If we already have final text, resolve immediately
        const existing = composeText();
        if (existing && finalChunksRef.current.length > 0) {
          resolve(existing);
          resolveStopRef.current = null;
          return;
        }
        // Timeout after 5 seconds
        setTimeout(() => {
          resolve(composeText());
          resolveStopRef.current = null;
        }, 5000);
      });

      await AndroidSTT.stopListening();
      const finalText = await resultPromise;

      cleanupAndroidListeners();
      setTranscript(finalText);
      setInterimText(finalText || "Didn't catch that. Try again.");
      setState("idle");
      setVolume(0);
      return finalText;
    } catch (err) {
      log("error", "Android STT stop failed", { error: String(err) });
      cleanupAndroidListeners();
      setState("idle");
      setVolume(0);
      return composeText();
    }
  }, []);

  // ── Main start/stop dispatch ──
  const start = useCallback(async (): Promise<boolean> => {
    setTranscript("");
    setInterimText("");
    setVolume(0);
    startedRef.current = true;
    finalChunksRef.current = [];
    interimChunkRef.current = "";

    if (config.voiceProvider === "android") {
      return startAndroidSTT();
    }

    try {
      const perm = await Audio.requestPermissionsAsync();
      if (!perm.granted) throw new Error("Microphone permission denied");

      await Audio.setAudioModeAsync({
        allowsRecordingIOS: true,
        playsInSilentModeIOS: true,
        staysActiveInBackground: false
      });
    } catch (err) {
      log("error", "Microphone permission error", { error: String(err) });
      setInterimText("Microphone permission is required");
      setState("idle");
      startedRef.current = false;
      return false;
    }

    if (config.voiceProvider === "whisper") {
      if (!config.whisperModelPath) {
        setInterimText("Whisper model path not configured");
        setState("idle");
        startedRef.current = false;
        return false;
      }

      try {
        const recording = new Audio.Recording();
        await recording.prepareToRecordAsync(Audio.RecordingOptionsPresets.HIGH_QUALITY);
        await recording.startAsync();
        recordingRef.current = recording;
        setState("recording");
        setInterimText("Listening…");
        log("debug", "Whisper voice recording started");
        return true;
      } catch (err) {
        log("error", "Failed to start whisper recording", { error: String(err) });
        setInterimText("Mic failed");
        setState("idle");
        startedRef.current = false;
        return false;
      }
    }

    // Deepgram flow
    const key = String(config.deepgramApiKey || "").trim();
    if (!key) {
      setInterimText("Add Deepgram API key in Settings");
      setState("idle");
      startedRef.current = false;
      return false;
    }

    try {
      cleanupStream();
      wsRef.current?.close();

      setInterimText("Connecting live transcription…");

      const wsUrl =
        "wss://api.deepgram.com/v1/listen" +
        "?model=nova-3&language=en&interim_results=true&endpointing=300&punctuate=true&smart_format=true" +
        "&encoding=linear16&sample_rate=16000&channels=1";

      const WebSocketAny = WebSocket as any;
      const ws: WebSocket = new WebSocketAny(wsUrl, null, {
        headers: { Authorization: `Token ${key}` },
      });

      ws.onopen = () => {
        if (!startedRef.current) return;
        setInterimText("Listening…");
        setState("recording");
        LiveAudioStream.init({
          sampleRate: 16000, channels: 1, bitsPerSample: 16,
          audioSource: 6, bufferSize: 4096,
        } as any);

        streamSubRef.current = (LiveAudioStream as any).on("data", (base64Chunk: string) => {
          if (!startedRef.current || ws.readyState !== 1) return;
          try {
            const chunk = Buffer.from(base64Chunk, "base64");
            ws.send(chunk as any);
            setVolume((prev) => {
              const sample = Math.random() * 0.35 + 0.2;
              return prev + (sample - prev) * 0.35;
            });
          } catch (error) {
            log("error", "Failed sending audio chunk", { error: String(error) });
          }
        });

        LiveAudioStream.start();
      };

      ws.onmessage = (event) => {
        if (!startedRef.current) return;
        try {
          const json = JSON.parse(String(event.data || "{}")) as DeepgramResult;
          const text = String(json.channel?.alternatives?.[0]?.transcript || "").trim();
          if (!text) return;

          if (json.is_final) {
            finalChunksRef.current.push(text);
            interimChunkRef.current = "";
          } else {
            interimChunkRef.current = text;
          }

          const live = composeText();
          setTranscript(finalChunksRef.current.join(" ").trim());
          setInterimText(live || "Listening…");
        } catch (error) {
          log("error", "Deepgram message parse error", { error: String(error) });
        }
      };

      ws.onerror = (event: any) => {
        log("error", "Deepgram websocket error", { error: String(event?.message || "ws error") });
        setInterimText("Voice transcription unavailable. Check Deepgram API key.");
        startedRef.current = false;
        setState("idle");
        cleanupStream();
      };

      ws.onclose = () => {
        cleanupStream();
      };

      wsRef.current = ws;
      log("debug", "Live voice transcription started");
      return true;
    } catch (err) {
      log("error", "Failed to start live transcription", { error: String(err) });
      setInterimText("Mic failed");
      setState("idle");
      startedRef.current = false;
      cleanupStream();
      return false;
    }
  }, [config.voiceProvider, config.deepgramApiKey, config.whisperModelPath, startAndroidSTT, transcribeWithWhisper]);

  const stop = useCallback(async (): Promise<string> => {
    startedRef.current = false;

    if (config.voiceProvider === "android") {
      return stopAndroidSTT();
    }

    setState("transcribing");

    if (config.voiceProvider === "whisper") {
      const recording = recordingRef.current;
      recordingRef.current = null;

      try {
        setInterimText("Transcribing…");
        if (recording) {
          await recording.stopAndUnloadAsync();
          const uri = recording.getURI();
          if (!uri) {
            setTranscript("");
            setInterimText("Didn't catch that. Try again.");
            return "";
          }
          const finalText = await transcribeWithWhisper(uri);
          if (!finalText) {
            setTranscript("");
            setInterimText("Didn't catch that. Try again.");
            return "";
          }
          setTranscript(finalText);
          setInterimText(finalText);
          return finalText;
        }
        return "";
      } catch (err) {
        log("error", "Whisper transcription failed", { error: String(err) });
        setInterimText("Transcription failed");
        return "";
      } finally {
        setVolume(0);
        setState("idle");
      }
    }

    // Deepgram flow
    const ws = wsRef.current;
    wsRef.current = null;

    try {
      setInterimText("Finalizing…");
      cleanupStream();

      if (ws && ws.readyState === 1) {
        ws.send(JSON.stringify({ type: "CloseStream" }));
        await new Promise<void>((resolve) => setTimeout(resolve, 500));
        ws.close();
      }

      const finalText = composeText();
      if (!finalText) {
        setTranscript("");
        setInterimText("Didn't catch that. Try again.");
        return "";
      }
      setTranscript(finalText);
      setInterimText(finalText);
      return finalText;
    } catch (err) {
      log("error", "Live voice transcription failed", { error: String(err) });
      setInterimText("Transcription failed");
      return "";
    } finally {
      setVolume(0);
      setState("idle");
    }
  }, [config.voiceProvider, stopAndroidSTT, transcribeWithWhisper]);

  const cancel = useCallback(async () => {
    startedRef.current = false;

    if (config.voiceProvider === "android") {
      try { await AndroidSTT?.cancel(); } catch { /* ignore */ }
      cleanupAndroidListeners();
    }

    cleanupStream();
    try { wsRef.current?.close(); } catch { /* ignore */ }
    wsRef.current = null;
    try { await recordingRef.current?.stopAndUnloadAsync(); } catch { /* ignore */ }
    recordingRef.current = null;
    setState("idle");
    setVolume(0);
    setInterimText("");
    setTranscript("");
  }, [config.voiceProvider]);

  return { state, volume, transcript, interimText, start, stop, cancel } as const;
}
