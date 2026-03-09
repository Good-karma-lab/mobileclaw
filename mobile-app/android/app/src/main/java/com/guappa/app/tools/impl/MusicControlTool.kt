package com.guappa.app.tools.impl

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class MusicControlTool : Tool {
    override val name = "music_control"
    override val description = "Control media playback (play, pause, next, previous, volume up/down) via media key events"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "description": "Playback action to perform",
                    "enum": ["play", "pause", "next", "previous", "volume_up", "volume_down"]
                }
            },
            "required": ["action"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val action = params.optString("action", "")
        if (action.isEmpty()) {
            return ToolResult.Error("Action is required.", "INVALID_PARAMS")
        }

        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            when (action) {
                "play" -> {
                    dispatchMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PLAY)
                    ToolResult.Success("Media play command sent.")
                }
                "pause" -> {
                    dispatchMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PAUSE)
                    ToolResult.Success("Media pause command sent.")
                }
                "next" -> {
                    dispatchMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_NEXT)
                    ToolResult.Success("Media next track command sent.")
                }
                "previous" -> {
                    dispatchMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    ToolResult.Success("Media previous track command sent.")
                }
                "volume_up" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    ToolResult.Success("Volume raised to $current/$max.")
                }
                "volume_down" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                    )
                    val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    ToolResult.Success("Volume lowered to $current/$max.")
                }
                else -> ToolResult.Error(
                    "Invalid action: $action. Use play, pause, next, previous, volume_up, or volume_down.",
                    "INVALID_PARAMS"
                )
            }
        } catch (e: Exception) {
            ToolResult.Error("Failed to control media: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun dispatchMediaKey(audioManager: AudioManager, keyCode: Int) {
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        audioManager.dispatchMediaKeyEvent(downEvent)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }
}
