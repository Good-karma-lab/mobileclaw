package com.guappa.app.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class UninstallAppTool : Tool {
    override val name = "uninstall_app"
    override val description = "Prompt the user to uninstall an application via the system uninstall dialog"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "package_name": {
                    "type": "string",
                    "description": "The package name of the app to uninstall (e.g. com.example.app)"
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

        // Verify the app is actually installed
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)

            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("Uninstall dialog opened for $packageName")
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            ToolResult.Error("App not found: $packageName", "APP_NOT_FOUND")
        } catch (e: Exception) {
            ToolResult.Error("Failed to open uninstall dialog: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
