package com.guappa.app.providers

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Detects device hardware capabilities for optimal model selection.
 * Identifies SoC, available RAM, GPU, and NPU support.
 */
object HardwareProbe {
    private const val TAG = "HardwareProbe"

    data class DeviceCapabilities(
        val socName: String,
        val chipset: String?,
        val totalRamMb: Long,
        val availableRamMb: Long,
        val cpuCores: Int,
        val supportedAbis: List<String>,
        val hasNpu: Boolean,
        val npuGeneration: String?,
        val gpuRenderer: String?,
        val maxModelSizeMb: Long, // Recommended max model size
        val recommendedQuantization: String
    )

    fun probe(context: Context): DeviceCapabilities {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val abis = Build.SUPPORTED_ABIS.toList()

        val socName = detectSoC()
        val chipset = readChipset()
        val npuInfo = detectNpu(socName)

        // Recommend model size based on available RAM (use ~40% of total)
        val maxModelMb = (totalRamMb * 0.4).toLong()
        val quantization = when {
            totalRamMb >= 12_000 -> "Q6_K"   // 12GB+ → high quality
            totalRamMb >= 8_000 -> "Q4_K_M"  // 8GB → balanced
            totalRamMb >= 6_000 -> "Q4_K_S"  // 6GB → smaller
            totalRamMb >= 4_000 -> "Q3_K_M"  // 4GB → compact
            else -> "Q2_K"                     // <4GB → minimum
        }

        val caps = DeviceCapabilities(
            socName = socName,
            chipset = chipset,
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            cpuCores = cpuCores,
            supportedAbis = abis,
            hasNpu = npuInfo.first,
            npuGeneration = npuInfo.second,
            gpuRenderer = null, // Requires GL context
            maxModelSizeMb = maxModelMb,
            recommendedQuantization = quantization
        )

        Log.i(TAG, "Device: $socName, RAM: ${totalRamMb}MB, cores: $cpuCores, NPU: ${npuInfo.first}, recommend: $quantization up to ${maxModelMb}MB")
        return caps
    }

    private fun detectSoC(): String {
        return try {
            val board = Build.BOARD ?: ""
            val hardware = Build.HARDWARE ?: ""
            val soc = Build.SOC_MODEL ?: ""

            when {
                soc.isNotBlank() -> soc
                hardware.isNotBlank() -> hardware
                board.isNotBlank() -> board
                else -> "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun readChipset(): String? {
        return try {
            val cpuinfo = File("/proc/cpuinfo").readText()
            val hardware = cpuinfo.lines().find { it.startsWith("Hardware") }
            hardware?.substringAfter(":")?.trim()
        } catch (e: Exception) {
            null
        }
    }

    private fun detectNpu(socName: String): Pair<Boolean, String?> {
        val soc = socName.uppercase()
        return when {
            // Qualcomm Snapdragon — Hexagon DSP/HTP
            soc.contains("SM8850") || soc.contains("8GEN5") -> true to "Hexagon HTP (8 Gen 5)"
            soc.contains("SM8750") || soc.contains("8GEN4") -> true to "Hexagon HTP (8 Gen 4)"
            soc.contains("SM8650") || soc.contains("8GEN3") -> true to "Hexagon HTP (8 Gen 3)"
            soc.contains("SM8550") || soc.contains("8GEN2") -> true to "Hexagon HTP (8 Gen 2)"
            soc.contains("SM8450") || soc.contains("8GEN1") -> true to "Hexagon HTP (8 Gen 1)"
            // Samsung Exynos
            soc.contains("EXYNOS") && soc.contains("2400") -> true to "Samsung NPU"
            soc.contains("EXYNOS") && soc.contains("2500") -> true to "Samsung NPU"
            // Google Tensor
            soc.contains("TENSOR") || soc.contains("GS") -> true to "Google TPU (Edge)"
            // MediaTek Dimensity
            soc.contains("MT6989") || soc.contains("9300") -> true to "MediaTek APU"
            soc.contains("MT6985") || soc.contains("9200") -> true to "MediaTek APU"
            else -> false to null
        }
    }

    /**
     * Recommend model size for this device.
     */
    fun recommendModel(context: Context): ModelRecommendation {
        val caps = probe(context)
        return when {
            caps.totalRamMb >= 12_000 -> ModelRecommendation(
                modelName = "Qwen3.5-4B",
                quantization = "Q4_K_M",
                estimatedSizeMb = 2700,
                reason = "Flagship device with ${caps.totalRamMb}MB RAM — 4B model for best quality"
            )
            caps.totalRamMb >= 8_000 -> ModelRecommendation(
                modelName = "Qwen3.5-2B",
                quantization = "Q4_K_M",
                estimatedSizeMb = 1300,
                reason = "High-end device with ${caps.totalRamMb}MB RAM — 2B model for quality"
            )
            caps.totalRamMb >= 4_000 -> ModelRecommendation(
                modelName = "Qwen3.5-0.8B",
                quantization = "Q4_K_M",
                estimatedSizeMb = 533,
                reason = "Standard device with ${caps.totalRamMb}MB RAM — 0.8B model for reliability"
            )
            else -> ModelRecommendation(
                modelName = "Qwen3.5-0.8B",
                quantization = "Q3_K_M",
                estimatedSizeMb = 430,
                reason = "Budget device with ${caps.totalRamMb}MB RAM — smallest model"
            )
        }
    }

    data class ModelRecommendation(
        val modelName: String,
        val quantization: String,
        val estimatedSizeMb: Long,
        val reason: String
    )
}
