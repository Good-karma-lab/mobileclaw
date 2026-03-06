// Model sizes in bytes (approximate for Q4_K_M quantization)
export const MODEL_SIZES: Record<string, number> = {
  "Qwen/Qwen3.5-0.8B": 530_000_000,
  "Qwen/Qwen3.5-2B": 1_500_000_000,
  "whisper-tiny": 75_000_000,
  "whisper-base": 142_000_000,
  "whisper-small": 466_000_000,
};

const RAM_MULTIPLIER = 1.5; // KV cache + activations overhead
const WARN_THRESHOLD = 0.50; // 50% of device RAM
const BLOCK_THRESHOLD = 0.60; // 60% of device RAM

export type RamCheckResult = {
  status: "ok" | "warning" | "blocked";
  requiredMB: number;
  limitMB: number;
  message?: string;
};

export function checkModelRam(modelId: string, deviceRamMB: number): RamCheckResult {
  const fileSize = MODEL_SIZES[modelId];
  if (!fileSize) return { status: "ok", requiredMB: 0, limitMB: deviceRamMB };

  const requiredMB = Math.round((fileSize * RAM_MULTIPLIER) / (1024 * 1024));
  const warnLimit = Math.round(deviceRamMB * WARN_THRESHOLD);
  const blockLimit = Math.round(deviceRamMB * BLOCK_THRESHOLD);

  if (requiredMB > blockLimit) {
    return {
      status: "blocked",
      requiredMB,
      limitMB: blockLimit,
      message: `Cannot load ${modelId} (~${requiredMB}MB required) — exceeds safe limit of ${blockLimit}MB.`,
    };
  }
  if (requiredMB > warnLimit) {
    return {
      status: "warning",
      requiredMB,
      limitMB: blockLimit,
      message: `${modelId} uses ${requiredMB}MB — close to device limit.`,
    };
  }
  return { status: "ok", requiredMB, limitMB: blockLimit };
}
