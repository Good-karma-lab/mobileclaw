package com.guappa.app.tools.impl

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class VibrateTool : Tool {
    override val name = "vibrate"
    override val description = "Vibrate the device with a specified duration or pattern"
    override val requiredPermissions = listOf("VIBRATE")
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "duration_ms": {
                    "type": "integer",
                    "description": "Vibration duration in milliseconds (default 500). Used for single vibration."
                },
                "pattern": {
                    "type": "array",
                    "items": { "type": "integer" },
                    "description": "Vibration pattern as alternating [wait, vibrate, wait, vibrate, ...] durations in ms. Overrides duration_ms."
                },
                "repeat": {
                    "type": "boolean",
                    "description": "Whether to repeat the pattern. Default false. Only used with 'pattern'."
                }
            },
            "required": []
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val vibrator = getVibrator(context)
            ?: return ToolResult.Error("Vibrator not available on this device.", "EXECUTION_ERROR")

        @Suppress("DEPRECATION")
        if (!vibrator.hasVibrator()) {
            return ToolResult.Error("Device does not support vibration.", "EXECUTION_ERROR")
        }

        return try {
            val patternArray = params.optJSONArray("pattern")

            if (patternArray != null && patternArray.length() > 0) {
                val pattern = LongArray(patternArray.length()) { patternArray.getLong(it) }
                val repeat = params.optBoolean("repeat", false)
                val repeatIndex = if (repeat) 0 else -1

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(pattern, repeatIndex)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, repeatIndex)
                }

                val patternStr = pattern.joinToString(", ")
                val repeatText = if (repeat) " (repeating)" else ""
                ToolResult.Success(content = "Vibrating with pattern [$patternStr]ms$repeatText")
            } else {
                val durationMs = params.optLong("duration_ms", 500L)
                if (durationMs <= 0) {
                    return ToolResult.Error("Duration must be positive.", "INVALID_PARAMS")
                }
                if (durationMs > 10_000) {
                    return ToolResult.Error("Duration must be 10000ms or less.", "INVALID_PARAMS")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }

                ToolResult.Success(content = "Vibrating for ${durationMs}ms")
            }
        } catch (e: Exception) {
            ToolResult.Error("Vibration failed: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
