package com.guappa.app.tools.impl

import android.content.Context
import android.hardware.camera2.CameraManager
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class FlashlightTool : Tool {
    override val name = "flashlight"
    override val description = "Toggle the device flashlight (camera torch) on or off"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "enum": ["on", "off", "toggle"],
                    "description": "Action: 'on' to enable, 'off' to disable, 'toggle' to switch state"
                }
            },
            "required": ["action"]
        }
    """.trimIndent())

    @Volatile
    private var isOn = false

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val action = params.optString("action", "")
        if (action.isEmpty()) {
            return ToolResult.Error("Action is required.", "INVALID_PARAMS")
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ToolResult.Error("Camera service not available.", "EXECUTION_ERROR")

        return try {
            val cameraId = findFlashCameraId(cameraManager)
                ?: return ToolResult.Error("No flashlight (torch) found on this device.", "EXECUTION_ERROR")

            val targetState = when (action) {
                "on" -> true
                "off" -> false
                "toggle" -> !isOn
                else -> return ToolResult.Error("Invalid action: $action. Use 'on', 'off', or 'toggle'.", "INVALID_PARAMS")
            }

            cameraManager.setTorchMode(cameraId, targetState)
            isOn = targetState

            val stateText = if (targetState) "on" else "off"
            ToolResult.Success(content = "Flashlight turned $stateText")
        } catch (e: Exception) {
            ToolResult.Error("Flashlight operation failed: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun findFlashCameraId(cameraManager: CameraManager): String? {
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val hasFlash = characteristics.get(
                android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE
            )
            if (hasFlash == true) return id
        }
        return null
    }
}
