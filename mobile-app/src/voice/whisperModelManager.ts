/**
 * WhisperModelManager — download and manage on-device Whisper GGML models.
 *
 * Downloads models from Hugging Face to the app's document directory.
 * Supports: tiny, base, small (English variants for speed).
 */
import * as FileSystem from "expo-file-system";
import { log } from "../logger";

export type WhisperModelSize = "tiny.en" | "base.en" | "small.en" | "tiny" | "base" | "small";

const MODEL_BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main";

const MODEL_FILES: Record<WhisperModelSize, { file: string; sizeBytes: number }> = {
  "tiny.en": { file: "ggml-tiny.en.bin", sizeBytes: 77_700_000 },
  "base.en": { file: "ggml-base.en.bin", sizeBytes: 148_000_000 },
  "small.en": { file: "ggml-small.en.bin", sizeBytes: 488_000_000 },
  "tiny": { file: "ggml-tiny.bin", sizeBytes: 77_700_000 },
  "base": { file: "ggml-base.bin", sizeBytes: 148_000_000 },
  "small": { file: "ggml-small.bin", sizeBytes: 488_000_000 },
};

const MODELS_DIR = `${FileSystem.documentDirectory}whisper-models/`;

export function getModelPath(size: WhisperModelSize = "base.en"): string {
  const info = MODEL_FILES[size];
  return `${MODELS_DIR}${info.file}`;
}

export async function isModelDownloaded(size: WhisperModelSize = "base.en"): Promise<boolean> {
  const path = getModelPath(size);
  const info = await FileSystem.getInfoAsync(path);
  return info.exists && (info.size ?? 0) > 1_000_000;
}

export async function downloadModel(
  size: WhisperModelSize = "base.en",
  onProgress?: (progress: number) => void,
): Promise<string> {
  const modelInfo = MODEL_FILES[size];
  const destPath = getModelPath(size);

  // Ensure directory exists
  const dirInfo = await FileSystem.getInfoAsync(MODELS_DIR);
  if (!dirInfo.exists) {
    await FileSystem.makeDirectoryAsync(MODELS_DIR, { intermediates: true });
  }

  // Check if already downloaded
  if (await isModelDownloaded(size)) {
    log("debug", `Whisper model ${size} already downloaded`);
    return destPath;
  }

  const url = `${MODEL_BASE_URL}/${modelInfo.file}`;
  log("info", `Downloading Whisper model ${size} from ${url}`);

  const downloadResumable = FileSystem.createDownloadResumable(
    url,
    destPath,
    {},
    (downloadProgress) => {
      const progress =
        downloadProgress.totalBytesWritten / downloadProgress.totalBytesExpectedToWrite;
      onProgress?.(progress);
    },
  );

  const result = await downloadResumable.downloadAsync();
  if (!result || !result.uri) {
    throw new Error(`Failed to download Whisper model ${size}`);
  }

  log("info", `Whisper model ${size} downloaded to ${result.uri}`);
  return destPath;
}

export async function deleteModel(size: WhisperModelSize): Promise<void> {
  const path = getModelPath(size);
  const info = await FileSystem.getInfoAsync(path);
  if (info.exists) {
    await FileSystem.deleteAsync(path);
    log("info", `Deleted Whisper model ${size}`);
  }
}

export async function getDownloadedModels(): Promise<WhisperModelSize[]> {
  const downloaded: WhisperModelSize[] = [];
  for (const size of Object.keys(MODEL_FILES) as WhisperModelSize[]) {
    if (await isModelDownloaded(size)) {
      downloaded.push(size);
    }
  }
  return downloaded;
}
