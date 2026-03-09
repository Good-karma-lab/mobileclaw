package com.guappa.app.tools.impl

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONArray
import org.json.JSONObject

class ListAppsTool : Tool {
    override val name = "list_apps"
    override val description = "List installed applications on the device, optionally filtered by query"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "Optional search query to filter apps by name"
                },
                "include_system": {
                    "type": "boolean",
                    "description": "Include system apps in the list (default: false)"
                },
                "limit": {
                    "type": "integer",
                    "description": "Maximum number of apps to return (default: 50, max: 200)"
                }
            },
            "required": []
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val query = params.optString("query", "").lowercase()
        val includeSystem = params.optBoolean("include_system", false)
        val limit = params.optInt("limit", 50).coerceIn(1, 200)

        return try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            val filtered = installedApps.filter { appInfo ->
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!includeSystem && isSystem) return@filter false
                if (query.isNotEmpty()) {
                    val appName = pm.getApplicationLabel(appInfo).toString().lowercase()
                    val packageName = appInfo.packageName.lowercase()
                    appName.contains(query) || packageName.contains(query)
                } else {
                    true
                }
            }.take(limit)

            val apps = JSONArray()
            for (appInfo in filtered) {
                val app = JSONObject().apply {
                    put("package_name", appInfo.packageName)
                    put("name", pm.getApplicationLabel(appInfo).toString())
                    put("is_system", (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
                    put("enabled", appInfo.enabled)
                }
                apps.put(app)
            }

            val data = JSONObject().apply {
                put("apps", apps)
                put("count", apps.length())
                put("total_installed", installedApps.size)
            }

            ToolResult.Success(
                content = "Found ${apps.length()} app(s)" +
                    if (query.isNotEmpty()) " matching \"$query\"" else "",
                data = data
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to list apps: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
