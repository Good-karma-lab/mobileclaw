package com.guappa.app.tools.impl

import android.content.Context
import android.provider.Settings
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class BrightnessTool : Tool {
    override val name = "screen_brightness"
    override val description = "Get or set the screen brightness level. Can also toggle auto-brightness."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "enum": ["get", "set"],
                    "description": "Action to perform: 'get' to read current brightness, 'set' to change it"
                },
                "level": {
                    "type": "integer",
                    "description": "Brightness level (0-255). Only used with action 'set'."
                },
                "auto": {
                    "type": "boolean",
                    "description": "Enable or disable auto-brightness. Only used with action 'set'."
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
            when (action) {
                "get" -> getBrightness(context)
                "set" -> setBrightness(params, context)
                else -> ToolResult.Error("Invalid action: $action. Use 'get' or 'set'.", "INVALID_PARAMS")
            }
        } catch (e: Exception) {
            ToolResult.Error("Brightness operation failed: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun getBrightness(context: Context): ToolResult {
        val brightness = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            -1
        )
        val autoMode = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        val isAuto = autoMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        val percentage = (brightness * 100) / 255

        val data = JSONObject().apply {
            put("brightness", brightness)
            put("percentage", percentage)
            put("auto_brightness", isAuto)
        }

        val autoText = if (isAuto) " (auto-brightness on)" else ""
        return ToolResult.Success(
            content = "Screen brightness: $percentage%$autoText",
            data = data
        )
    }

    private fun setBrightness(params: JSONObject, context: Context): ToolResult {
        if (!Settings.System.canWrite(context)) {
            return ToolResult.Error(
                "App does not have WRITE_SETTINGS permission. User must grant it in system settings.",
                "PERMISSION_DENIED"
            )
        }

        val hasAuto = params.has("auto")
        val hasLevel = params.has("level")

        if (!hasAuto && !hasLevel) {
            return ToolResult.Error(
                "Either 'level' or 'auto' parameter is required for set action.",
                "INVALID_PARAMS"
            )
        }

        val messages = mutableListOf<String>()

        if (hasAuto) {
            val auto = params.optBoolean("auto", false)
            val mode = if (auto) {
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            } else {
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            }
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                mode
            )
            messages.add("Auto-brightness ${if (auto) "enabled" else "disabled"}")
        }

        if (hasLevel) {
            val level = params.optInt("level", -1)
            if (level < 0 || level > 255) {
                return ToolResult.Error("Brightness level must be 0-255.", "INVALID_PARAMS")
            }
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                level
            )
            val percentage = (level * 100) / 255
            messages.add("Brightness set to $percentage% ($level/255)")
        }

        return ToolResult.Success(content = messages.joinToString(". "))
    }
}
