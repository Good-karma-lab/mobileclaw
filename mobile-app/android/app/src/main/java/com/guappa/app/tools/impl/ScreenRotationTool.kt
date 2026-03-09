package com.guappa.app.tools.impl

import android.content.Context
import android.provider.Settings
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class ScreenRotationTool : Tool {
    override val name = "screen_rotation"
    override val description = "Get or set the screen auto-rotation lock setting"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "enum": ["get", "set"],
                    "description": "Action: 'get' to read rotation lock state, 'set' to change it"
                },
                "auto_rotate": {
                    "type": "boolean",
                    "description": "Enable (true) or disable (false) auto-rotation. Required for 'set' action."
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
                "get" -> getRotation(context)
                "set" -> setRotation(params, context)
                else -> ToolResult.Error("Invalid action: $action. Use 'get' or 'set'.", "INVALID_PARAMS")
            }
        } catch (e: Exception) {
            ToolResult.Error("Screen rotation operation failed: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun getRotation(context: Context): ToolResult {
        val accelerometerRotation = Settings.System.getInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0
        )
        val isAutoRotate = accelerometerRotation == 1

        val data = JSONObject().apply {
            put("auto_rotate", isAutoRotate)
            put("rotation_locked", !isAutoRotate)
        }

        val stateText = if (isAutoRotate) "Auto-rotation is enabled" else "Auto-rotation is disabled (rotation locked)"
        return ToolResult.Success(
            content = stateText,
            data = data
        )
    }

    private fun setRotation(params: JSONObject, context: Context): ToolResult {
        if (!Settings.System.canWrite(context)) {
            return ToolResult.Error(
                "App does not have WRITE_SETTINGS permission. User must grant it in system settings.",
                "PERMISSION_DENIED"
            )
        }

        if (!params.has("auto_rotate")) {
            return ToolResult.Error("'auto_rotate' parameter is required for set action.", "INVALID_PARAMS")
        }

        val autoRotate = params.optBoolean("auto_rotate", false)
        val value = if (autoRotate) 1 else 0

        Settings.System.putInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            value
        )

        val stateText = if (autoRotate) "Auto-rotation enabled" else "Auto-rotation disabled (rotation locked)"
        return ToolResult.Success(content = stateText)
    }
}
