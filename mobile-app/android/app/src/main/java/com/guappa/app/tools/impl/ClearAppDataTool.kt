package com.guappa.app.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class ClearAppDataTool : Tool {
    override val name = "clear_app_data"
    override val description = "Open the system app settings page where the user can clear cache or data for a specific app"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "package_name": {
                    "type": "string",
                    "description": "The package name of the app whose data/cache to clear (e.g. com.android.chrome)"
                }
            },
            "required": ["package_name"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val packageName = params.optString("package_name", "")
        if (packageName.isEmpty()) {
            return ToolResult.Error("Package name is required.", "INVALID_PARAMS")
        }

        // Verify the app exists
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)

            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success(
                "Opened app settings for $packageName. The user can clear cache or data from there."
            )
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            ToolResult.Error("App not found: $packageName", "APP_NOT_FOUND")
        } catch (e: Exception) {
            ToolResult.Error("Failed to open app settings: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
