import { useCallback, useRef, useState } from "react";
import { Audio } from "expo-av";
import { Buffer } from "buffer";
import LiveAudioStream from "react-native-live-audio-stream";

import { log } from "../logger";

type DeepgramResult = {
  type?: string;
  is_final?: boolean;
  speech_final?: boolean;
  channel?: { alternatives?: Array<{ transcript?: string }> };
};

export type VoiceState = "idle" | "recording" | "transcribing";

function meteringTo01(metering?: number | null): number {
  // expo-av metering is typically in dBFS: 0 = max, negative = quieter.
  if (typeof metering !== "number" || Number.isNaN(metering)) return 0;
  const clamped = Math.max(-60, Math.min(0, metering));
  const t = (clamped + 60) / 60; // 0..1
  return Math.max(0, Math.min(1, Math.pow(t, 1.6)));
}

export function useVoiceRecording(deepgramApiKey?: string) {
  const [state, setState] = useState<VoiceState>("idle");
  const [volume, setVolume] = useState(0);
  const [transcript, setTranscript] = useState("");
  const [interimText, setInterimText] = useState("");

  const wsRef = useRef<WebSocket | null>(null);
  const streamSubRef = useRef<{ remove?: () => void } | null>(null);
  const finalChunksRef = useRef<string[]>([]);
  const interimChunkRef = useRef("");
  const resolveStopRef = useRef<(() => void) | null>(null);
  const startedRef = useRef(false);

  const composeText = () => {
    const finalText = finalChunksRef.current.join(" ").trim();
    const interim = interimChunkRef.current.trim();
    return `${finalText} ${interim}`.trim();
  };

  const cleanupStream = () => {
    try {
      LiveAudioStream.stop();
    } catch {
      // ignore
    }
    try {
      streamSubRef.current?.remove?.();
    } catch {
      // ignore
    }
    streamSubRef.current = null;
  };

  const start = useCallback(async (): Promise<boolean> => {
    setTranscript("");
    setInterimText("");
    setVolume(0);
    startedRef.current = true;
    finalChunksRef.current = [];
    interimChunkRef.current = "";

    const key = String(deepgramApiKey || "").trim();
    if (!key) {
      setInterimText("Add Deepgram API key in Settings");
      setState("idle");
      startedRef.current = false;
      return false;
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

    try {
      cleanupStream();
      wsRef.current?.close();

      setInterimText("Connecting live transcription…");

      const wsUrl =
        "wss://api.deepgram.com/v1/listen" +
        "?model=nova-2&language=en&interim_results=true&endpointing=300&punctuate=true&smart_format=true" +
        "&encoding=linear16&sample_rate=16000&channels=1";

      const WebSocketAny = WebSocket as any;
      const ws: WebSocket = new WebSocketAny(wsUrl, null, {
        headers: {
          Authorization: `Token ${key}`,
        },
      });

      ws.onopen = () => {
        if (!startedRef.current) return;
        setInterimText("Listening…");
        setState("recording");
        LiveAudioStream.init({
          sampleRate: 16000,
          channels: 1,
          bitsPerSample: 16,
          audioSource: 6,
          bufferSize: 4096,
        } as any);

        streamSubRef.current = (LiveAudioStream as any).on("data", (base64Chunk: string) => {
          if (!startedRef.current) return;
          if (ws.readyState !== 1) return;
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

          if (json.speech_final) {
            setInterimText(composeText());
          }
        } catch (error) {
          log("error", "Deepgram message parse error", { error: String(error) });
        }
      };

      ws.onerror = (event: any) => {
        log("error", "Deepgram websocket error", { error: String(event?.message || "ws error") });
        setInterimText("Live transcription connection failed");
      };

      ws.onclose = () => {
        cleanupStream();
        resolveStopRef.current?.();
        resolveStopRef.current = null;
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
  }, [deepgramApiKey]);

  const stop = useCallback(async (): Promise<string> => {
    setState("transcribing");
    startedRef.current = false;
    const ws = wsRef.current;
    wsRef.current = null;

    try {
      setInterimText("Finalizing…");
      cleanupStream();

      if (ws && ws.readyState === 1) {
        ws.send(JSON.stringify({ type: "CloseStream" }));
        const done = new Promise<void>((resolve) => {
          resolveStopRef.current = resolve;
          setTimeout(resolve, 900);
        });
        ws.close();
        await done;
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
  }, []);

  const cancel = useCallback(async () => {
    startedRef.current = false;
    cleanupStream();
    try {
      wsRef.current?.close();
    } catch {
      // ignore
    }
    wsRef.current = null;
    setState("idle");
    setVolume(0);
    setInterimText("");
    setTranscript("");
  }, []);

  return { state, volume, transcript, interimText, start, stop, cancel } as const;
}
