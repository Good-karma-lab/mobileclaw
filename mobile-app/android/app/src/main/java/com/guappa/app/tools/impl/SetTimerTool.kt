package com.guappa.app.tools.impl

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class SetTimerTool : Tool {
    override val name = "set_timer"
    override val description = "Set a countdown timer for a specified duration"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "seconds": {
                    "type": "integer",
                    "description": "Duration in seconds for the timer"
                },
                "message": {
                    "type": "string",
                    "description": "Label for the timer"
                }
            },
            "required": ["seconds"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val seconds = params.optInt("seconds", -1)
        if (seconds <= 0) {
            return ToolResult.Error("Seconds must be a positive integer.", "INVALID_PARAMS")
        }

        val message = params.optString("message", "")

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            if (message.isNotEmpty()) {
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
            }
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            val mins = seconds / 60
            val secs = seconds % 60
            val durationText = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
            val label = if (message.isNotEmpty()) " with label '$message'" else ""
            ToolResult.Success("Timer set for $durationText$label")
        } catch (e: Exception) {
            ToolResult.Error("Failed to set timer: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
