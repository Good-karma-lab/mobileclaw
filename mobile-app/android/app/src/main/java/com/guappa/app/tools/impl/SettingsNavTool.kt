package com.guappa.app.tools.impl

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class SettingsNavTool : Tool {
    override val name = "open_settings"
    override val description = "Open a specific Android settings page (wifi, bluetooth, display, sound, apps, battery, location, etc.)"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "page": {
                    "type": "string",
                    "description": "Settings page to open",
                    "enum": ["wifi", "bluetooth", "display", "sound", "apps", "battery", "location", "date", "developer", "accessibility", "notification"]
                }
            },
            "required": ["page"]
        }
    """.trimIndent())

    private val settingsMap = mapOf(
        "wifi" to Settings.ACTION_WIFI_SETTINGS,
        "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "display" to Settings.ACTION_DISPLAY_SETTINGS,
        "sound" to Settings.ACTION_SOUND_SETTINGS,
        "apps" to Settings.ACTION_APPLICATION_SETTINGS,
        "battery" to Intent.ACTION_POWER_USAGE_SUMMARY,
        "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "date" to Settings.ACTION_DATE_SETTINGS,
        "developer" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        "accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
        "notification" to Settings.ACTION_APP_NOTIFICATION_SETTINGS
    )

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val page = params.optString("page", "")
        if (page.isEmpty()) {
            return ToolResult.Error("Settings page is required.", "INVALID_PARAMS")
        }

        val action = settingsMap[page]
            ?: return ToolResult.Error(
                "Unknown settings page: $page. Valid pages: ${settingsMap.keys.joinToString(", ")}",
                "INVALID_PARAMS"
            )

        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("Opened $page settings.")
        } catch (e: Exception) {
            ToolResult.Error("Failed to open $page settings: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
