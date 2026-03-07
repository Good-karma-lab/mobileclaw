package com.guappa.app.tools.impl

import android.content.Context
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class LaunchAppTool : Tool {
    override val name = "launch_app"
    override val description = "Launch an installed application by its package name"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "package_name": {
                    "type": "string",
                    "description": "The package name of the app to launch (e.g. com.android.chrome)"
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

        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                return ToolResult.Error(
                    "App not found: $packageName",
                    "APP_NOT_FOUND"
                )
            }
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult.Success("Launched app: $packageName")
        } catch (e: Exception) {
            ToolResult.Error("Failed to launch app: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
