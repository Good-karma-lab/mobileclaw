package com.guappa.app.tools.impl

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class SetAlarmTool : Tool {
    override val name = "set_alarm"
    override val description = "Set an alarm on the device at a specific time"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "hour": {
                    "type": "integer",
                    "description": "Hour in 24-hour format (0-23)"
                },
                "minute": {
                    "type": "integer",
                    "description": "Minute (0-59)"
                },
                "message": {
                    "type": "string",
                    "description": "Label for the alarm"
                }
            },
            "required": ["hour", "minute"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val hour = params.optInt("hour", -1)
        val minute = params.optInt("minute", -1)

        if (hour < 0 || hour > 23) {
            return ToolResult.Error("Invalid hour: $hour. Must be 0-23.", "INVALID_PARAMS")
        }
        if (minute < 0 || minute > 59) {
            return ToolResult.Error("Invalid minute: $minute. Must be 0-59.", "INVALID_PARAMS")
        }

        val message = params.optString("message", "")

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (message.isNotEmpty()) {
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
            }
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            val label = if (message.isNotEmpty()) " with label '$message'" else ""
            ToolResult.Success("Alarm set for %02d:%02d$label".format(hour, minute))
        } catch (e: Exception) {
            ToolResult.Error("Failed to set alarm: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
