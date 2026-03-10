/**
 * Voice Engine Manager — centralized STT/TTS engine selection.
 *
 * Supports:
 * - STT: Deepgram Nova-3 (cloud), Deepgram Flux (voice agent), Whisper (on-device), Android SpeechRecognizer (free fallback)
 * - TTS: Android built-in (expo-speech), Deepgram Aura-2 (cloud)
 */

import AsyncStorage from "@react-native-async-storage/async-storage";
import { log } from "../logger";

// ---- STT Engines ----

export type STTEngine =
  | "deepgram_nova3"    // Cloud: best accuracy, 50+ languages
  | "deepgram_flux"     // Cloud: voice agent mode with turn detection
  | "whisper_local"     // On-device: whisper.rn, offline
  | "android_builtin";  // On-device: SpeechRecognizer, zero download, free

// ---- TTS Engines ----

export type TTSEngine =
  | "android_builtin"   // expo-speech → Android TextToSpeech
  | "deepgram_aura2";   // Cloud: Deepgram Aura-2, 40+ voices

// ---- Configuration ----

export interface VoiceEngineConfig {
  sttEngine: STTEngine;
  ttsEngine: TTSEngine;
  deepgramApiKey?: string;
  /** Deepgram Aura-2 voice selection */
  aura2Voice?: string;
  /** Whisper model variant */
  whisperModel?: "tiny" | "base" | "small";
  /** Android SpeechRecognizer: prefer offline mode */
  androidSttOffline?: boolean;
}

const STORAGE_KEY = "guappa_voice_engine_config";

const DEFAULT_CONFIG: VoiceEngineConfig = {
  sttEngine: "android_builtin",
  ttsEngine: "android_builtin",
  whisperModel: "tiny",
  androidSttOffline: false,
  aura2Voice: "thalia",
};

let cachedConfig: VoiceEngineConfig | null = null;

/**
 * Load voice engine configuration from storage.
 */
export async function getVoiceEngineConfig(): Promise<VoiceEngineConfig> {
  if (cachedConfig) return cachedConfig;
  try {
    const raw = await AsyncStorage.getItem(STORAGE_KEY);
    if (raw) {
      cachedConfig = { ...DEFAULT_CONFIG, ...JSON.parse(raw) };
      return cachedConfig!;
    }
  } catch (e) {
    log("warn", "Failed to load voice engine config", { error: String(e) });
  }
  cachedConfig = { ...DEFAULT_CONFIG };
  return cachedConfig;
}

/**
 * Save voice engine configuration.
 */
export async function setVoiceEngineConfig(config: Partial<VoiceEngineConfig>): Promise<void> {
  const current = await getVoiceEngineConfig();
  const updated = { ...current, ...config };
  cachedConfig = updated;
  try {
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
    log("debug", "Voice engine config saved", { stt: updated.sttEngine, tts: updated.ttsEngine });
  } catch (e) {
    log("warn", "Failed to save voice engine config", { error: String(e) });
  }
}

/**
 * Auto-select best STT engine based on available credentials and device.
 */
export async function autoSelectSTTEngine(deepgramKey?: string): Promise<STTEngine> {
  if (deepgramKey && deepgramKey.trim().length > 0) {
    return "deepgram_nova3"; // Best cloud STT if key available
  }
  // Try whisper if model is downloaded — otherwise fall back to Android built-in
  // For now, default to Android built-in (zero config required)
  return "android_builtin";
}

/**
 * Auto-select best TTS engine based on available credentials.
 */
export async function autoSelectTTSEngine(deepgramKey?: string): Promise<TTSEngine> {
  if (deepgramKey && deepgramKey.trim().length > 0) {
    return "deepgram_aura2";
  }
  return "android_builtin";
}

/**
 * Get human-readable engine name.
 */
export function getSTTEngineName(engine: STTEngine): string {
  switch (engine) {
    case "deepgram_nova3": return "Deepgram Nova-3 (Cloud)";
    case "deepgram_flux": return "Deepgram Flux (Voice Agent)";
    case "whisper_local": return "Whisper (On-Device)";
    case "android_builtin": return "Android Built-in (Free)";
  }
}

export function getTTSEngineName(engine: TTSEngine): string {
  switch (engine) {
    case "android_builtin": return "Android Built-in";
    case "deepgram_aura2": return "Deepgram Aura-2 (Cloud)";
  }
}
