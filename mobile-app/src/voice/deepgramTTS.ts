/**
 * Deepgram Aura-2 TTS Engine.
 *
 * Streams high-quality text-to-speech via Deepgram's Aura-2 API.
 * Uses the same Deepgram API key already configured for STT.
 *
 * API: POST https://api.deepgram.com/v1/speak?model=aura-2-{voice}-{lang}
 * Body: {"text": "..."}
 * Response: Audio stream (MP3 or linear16 PCM)
 */

import { Audio } from "expo-av";
import * as FileSystem from "expo-file-system";
import { log } from "../logger";

export type Aura2Voice =
  | "thalia"   // F, American, Clear/Confident
  | "apollo"   // M, American, Confident/Casual
  | "draco"    // M, British, Warm/Approachable
  | "pandora"  // F, British, Smooth/Calm
  | "luna"     // F, American, Friendly/Natural
  | "hyperion" // M, Australian, Caring/Warm
  | "helena"   // F, American, Friendly/Articulate
  | "orion"    // M, American, Clear/Professional
  | "athena"   // F, British, Refined/Polished
  | "perseus"; // M, American, Deep/Authoritative

export type Aura2Lang = "en" | "es" | "de" | "fr" | "it" | "nl" | "ja";

export interface DeepgramTTSOptions {
  voice?: Aura2Voice;
  lang?: Aura2Lang;
  /** Audio encoding: "mp3" (default) or "linear16". */
  encoding?: "mp3" | "linear16";
  /** Container format: "mp3", "wav", "ogg" */
  container?: "mp3" | "wav" | "ogg";
  /** Sample rate for linear16. Default 24000 */
  sampleRate?: number;
}

const DEFAULT_VOICE: Aura2Voice = "thalia";
const DEFAULT_LANG: Aura2Lang = "en";
const TTS_BASE_URL = "https://api.deepgram.com/v1/speak";

/**
 * Synthesize speech using Deepgram Aura-2 and return the audio file URI.
 */
export async function synthesizeSpeech(
  text: string,
  apiKey: string,
  options: DeepgramTTSOptions = {}
): Promise<string> {
  const {
    voice = DEFAULT_VOICE,
    lang = DEFAULT_LANG,
    encoding = "mp3",
    container = "mp3",
    sampleRate = 24000,
  } = options;

  const model = `aura-2-${voice}-${lang}`;
  const params = new URLSearchParams({
    model,
    encoding,
    container,
    sample_rate: sampleRate.toString(),
  });

  const url = `${TTS_BASE_URL}?${params.toString()}`;

  log("debug", `DeepgramTTS: synthesizing with model=${model}`, { textLength: text.length });

  const outputFile = `${FileSystem.cacheDirectory}deepgram_tts_${Date.now()}.${container}`;

  const response = await FileSystem.downloadAsync(url, outputFile, {
    headers: {
      Authorization: `Token ${apiKey}`,
      "Content-Type": "application/json",
    },
    httpMethod: "POST",
    sessionType: FileSystem.FileSystemSessionType.FOREGROUND,
  });

  if (response.status !== 200) {
    throw new Error(`Deepgram TTS failed: HTTP ${response.status}`);
  }

  log("debug", "DeepgramTTS: audio file ready", { uri: outputFile });
  return outputFile;
}

/**
 * Synthesize and immediately play speech using Deepgram Aura-2.
 * Returns a control object to stop playback.
 */
export async function speakWithDeepgram(
  text: string,
  apiKey: string,
  options: DeepgramTTSOptions = {},
  callbacks?: {
    onStart?: () => void;
    onDone?: () => void;
    onError?: (error: string) => void;
  }
): Promise<{ stop: () => Promise<void> }> {
  let sound: Audio.Sound | null = null;

  try {
    await Audio.setAudioModeAsync({
      playsInSilentModeIOS: true,
      staysActiveInBackground: true,
      shouldDuckAndroid: true,
    });

    const audioUri = await synthesizeSpeech(text, apiKey, options);

    const { sound: loadedSound } = await Audio.Sound.createAsync(
      { uri: audioUri },
      { shouldPlay: true },
      (status) => {
        if (status.isLoaded && status.didJustFinish) {
          callbacks?.onDone?.();
          loadedSound.unloadAsync().catch(() => {});
          // Clean up temp file
          FileSystem.deleteAsync(audioUri, { idempotent: true }).catch(() => {});
        }
      }
    );

    sound = loadedSound;
    callbacks?.onStart?.();

    return {
      stop: async () => {
        if (sound) {
          await sound.stopAsync().catch(() => {});
          await sound.unloadAsync().catch(() => {});
          sound = null;
        }
      },
    };
  } catch (error: any) {
    const msg = error?.message || "Unknown Deepgram TTS error";
    log("error", "DeepgramTTS: playback error", { error: msg });
    callbacks?.onError?.(msg);
    return { stop: async () => {} };
  }
}

/** Available Aura-2 voices with metadata. */
export const AURA2_VOICES: Array<{
  id: Aura2Voice;
  name: string;
  gender: "F" | "M";
  accent: string;
  description: string;
}> = [
  { id: "thalia", name: "Thalia", gender: "F", accent: "American", description: "Clear, Confident, Energetic" },
  { id: "apollo", name: "Apollo", gender: "M", accent: "American", description: "Confident, Comfortable, Casual" },
  { id: "draco", name: "Draco", gender: "M", accent: "British", description: "Warm, Approachable, Baritone" },
  { id: "pandora", name: "Pandora", gender: "F", accent: "British", description: "Smooth, Calm, Melodic" },
  { id: "luna", name: "Luna", gender: "F", accent: "American", description: "Friendly, Natural, Engaging" },
  { id: "hyperion", name: "Hyperion", gender: "M", accent: "Australian", description: "Caring, Warm, Empathetic" },
  { id: "helena", name: "Helena", gender: "F", accent: "American", description: "Friendly, Articulate" },
  { id: "orion", name: "Orion", gender: "M", accent: "American", description: "Clear, Professional" },
  { id: "athena", name: "Athena", gender: "F", accent: "British", description: "Refined, Polished" },
  { id: "perseus", name: "Perseus", gender: "M", accent: "American", description: "Deep, Authoritative" },
];
