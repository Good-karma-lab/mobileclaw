import { File, Directory, Paths } from "expo-file-system";

const MODEL_DIR_NAME = ".zeroclaw/models";

const MODEL_URLS: Record<string, string> = {
  "Qwen/Qwen3.5-0.8B":
    "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf",
  "Qwen/Qwen3.5-2B":
    "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q4_K_M.gguf",
};

const MODEL_FILENAMES: Record<string, string> = {
  "Qwen/Qwen3.5-0.8B": "Qwen3.5-0.8B-Q4_K_M.gguf",
  "Qwen/Qwen3.5-2B": "Qwen3.5-2B-Q4_K_M.gguf",
};

const TOKENIZER_URLS: Record<string, string> = {};

const WHISPER_MODEL_URLS: Record<string, string> = {
  "whisper-tiny": "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
  "whisper-base": "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
  "whisper-small": "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
};

const WHISPER_MODEL_FILENAMES: Record<string, string> = {
  "whisper-tiny": "ggml-tiny.bin",
  "whisper-base": "ggml-base.bin",
  "whisper-small": "ggml-small.bin",
};

function getModelDir(): Directory {
  return new Directory(Paths.document, MODEL_DIR_NAME);
}

export function getModelDownloadUrl(modelId: string): string {
  return MODEL_URLS[modelId] || "";
}

export function getModelFileName(modelId: string): string {
  return MODEL_FILENAMES[modelId] || modelId;
}

function getModelFile(modelId: string): File | null {
  const filename = MODEL_FILENAMES[modelId];
  if (!filename) return null;
  return new File(getModelDir(), filename);
}

export async function checkModelExists(modelId: string): Promise<{ exists: boolean; path: string }> {
  const file = getModelFile(modelId);
  if (!file) return { exists: false, path: "" };
  try {
    const exists = file.exists;
    return { exists, path: exists ? file.uri.replace(/^file:\/\//, "") : "" };
  } catch {
    return { exists: false, path: "" };
  }
}

export async function downloadModel(
  modelId: string,
  onProgress?: (progress: number) => void,
): Promise<string> {
  const url = getModelDownloadUrl(modelId);
  if (!url) throw new Error(`Unknown model: ${modelId}`);

  const filename = MODEL_FILENAMES[modelId];
  if (!filename) throw new Error(`No filename for model: ${modelId}`);

  const dir = getModelDir();
  if (!dir.exists) {
    dir.create({ intermediates: true });
  }

  const destFile = new File(dir, filename);

  // Signal download started
  onProgress?.(0);

  const downloaded = await File.downloadFileAsync(url, destFile, { idempotent: true });

  onProgress?.(100);

  // Return plain filesystem path (strip file:// prefix) for JNI compatibility
  return downloaded.uri.replace(/^file:\/\//, "");
}

export async function deleteModel(modelId: string): Promise<void> {
  const file = getModelFile(modelId);
  if (!file) return;
  try {
    if (file.exists) file.delete();
  } catch {}
}

export async function downloadWhisperModel(
  modelId: string,
  onProgress?: (progress: number) => void,
): Promise<string> {
  const url = WHISPER_MODEL_URLS[modelId];
  if (!url) throw new Error(`Unknown whisper model: ${modelId}`);
  const filename = WHISPER_MODEL_FILENAMES[modelId];
  if (!filename) throw new Error(`No filename: ${modelId}`);
  const dir = getModelDir();
  if (!dir.exists) dir.create({ intermediates: true });
  const destFile = new File(dir, filename);
  onProgress?.(0);
  const downloaded = await File.downloadFileAsync(url, destFile, { idempotent: true });
  onProgress?.(100);
  return downloaded.uri.replace(/^file:\/\//, "");
}

export async function checkWhisperModelExists(
  modelId: string,
): Promise<{ exists: boolean; path: string }> {
  const filename = WHISPER_MODEL_FILENAMES[modelId];
  if (!filename) return { exists: false, path: "" };
  const file = new File(getModelDir(), filename);
  try {
    return { exists: file.exists, path: file.exists ? file.uri.replace(/^file:\/\//, "") : "" };
  } catch { return { exists: false, path: "" }; }
}
