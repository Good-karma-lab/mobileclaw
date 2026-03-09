package com.guappa.app.tools.impl

import android.content.Context
import android.media.AudioManager
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class VolumeTool : Tool {
    override val name = "volume_control"
    override val description = "Get or set volume levels for media, ringtone, notification, and alarm streams"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "enum": ["get", "set"],
                    "description": "Action to perform: 'get' to read current volumes, 'set' to change a volume"
                },
                "stream": {
                    "type": "string",
                    "enum": ["media", "ring", "notification", "alarm"],
                    "description": "Audio stream to control. Required for 'set' action."
                },
                "level": {
                    "type": "integer",
                    "description": "Volume level to set (0 to max for that stream). Required for 'set' action."
                }
            },
            "required": ["action"]
        }
    """.trimIndent())

    private val streamMap = mapOf(
        "media" to AudioManager.STREAM_MUSIC,
        "ring" to AudioManager.STREAM_RING,
        "notification" to AudioManager.STREAM_NOTIFICATION,
        "alarm" to AudioManager.STREAM_ALARM
    )

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val action = params.optString("action", "")
        if (action.isEmpty()) {
            return ToolResult.Error("Action is required.", "INVALID_PARAMS")
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolResult.Error("AudioManager not available.", "EXECUTION_ERROR")

        return try {
            when (action) {
                "get" -> getVolumes(audioManager)
                "set" -> setVolume(params, audioManager)
                else -> ToolResult.Error("Invalid action: $action. Use 'get' or 'set'.", "INVALID_PARAMS")
            }
        } catch (e: Exception) {
            ToolResult.Error("Volume operation failed: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun getVolumes(audioManager: AudioManager): ToolResult {
        val data = JSONObject()
        val parts = mutableListOf<String>()

        for ((name, streamType) in streamMap) {
            val current = audioManager.getStreamVolume(streamType)
            val max = audioManager.getStreamMaxVolume(streamType)
            val streamData = JSONObject().apply {
                put("current", current)
                put("max", max)
            }
            data.put(name, streamData)
            parts.add("$name: $current/$max")
        }

        val ringerMode = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "silent"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            AudioManager.RINGER_MODE_NORMAL -> "normal"
            else -> "unknown"
        }
        data.put("ringer_mode", ringerMode)

        return ToolResult.Success(
            content = "Volume levels — ${parts.joinToString(", ")}. Ringer mode: $ringerMode",
            data = data
        )
    }

    private fun setVolume(params: JSONObject, audioManager: AudioManager): ToolResult {
        val stream = params.optString("stream", "")
        if (stream.isEmpty()) {
            return ToolResult.Error("Stream is required for set action.", "INVALID_PARAMS")
        }

        val streamType = streamMap[stream]
            ?: return ToolResult.Error(
                "Invalid stream: $stream. Use: media, ring, notification, alarm.",
                "INVALID_PARAMS"
            )

        if (!params.has("level")) {
            return ToolResult.Error("Level is required for set action.", "INVALID_PARAMS")
        }

        val level = params.optInt("level", -1)
        val maxVolume = audioManager.getStreamMaxVolume(streamType)

        if (level < 0 || level > maxVolume) {
            return ToolResult.Error(
                "Level must be 0-$maxVolume for $stream stream.",
                "INVALID_PARAMS"
            )
        }

        audioManager.setStreamVolume(streamType, level, 0)

        return ToolResult.Success(
            content = "Volume for $stream set to $level/$maxVolume"
        )
    }
}
