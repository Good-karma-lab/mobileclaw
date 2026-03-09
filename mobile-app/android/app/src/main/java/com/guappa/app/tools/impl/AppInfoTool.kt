package com.guappa.app.tools.impl

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AppInfoTool : Tool {
    override val name = "app_info"
    override val description = "Get detailed information about an installed app including version, size, and permissions"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "package_name": {
                    "type": "string",
                    "description": "The package name of the app (e.g. com.android.chrome)"
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
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)

            val appName = pm.getApplicationLabel(appInfo).toString()
            val versionName = packageInfo.versionName ?: "unknown"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val firstInstalled = packageInfo.firstInstallTime
            val lastUpdated = packageInfo.lastUpdateTime

            // Calculate app size from APK
            val apkFile = File(appInfo.sourceDir)
            val apkSizeBytes = apkFile.length()

            // Gather declared permissions
            val permissions = JSONArray()
            packageInfo.requestedPermissions?.forEach { perm ->
                permissions.put(perm)
            }

            val data = JSONObject().apply {
                put("package_name", packageName)
                put("app_name", appName)
                put("version_name", versionName)
                put("version_code", versionCode)
                put("is_system_app", isSystem)
                put("enabled", appInfo.enabled)
                put("apk_size_bytes", apkSizeBytes)
                put("apk_size_mb", String.format("%.1f", apkSizeBytes / (1024.0 * 1024.0)))
                put("first_installed", firstInstalled)
                put("last_updated", lastUpdated)
                put("target_sdk", appInfo.targetSdkVersion)
                put("min_sdk", appInfo.minSdkVersion)
                put("permissions", permissions)
                put("permission_count", permissions.length())
            }

            val sizeMb = String.format("%.1f", apkSizeBytes / (1024.0 * 1024.0))
            ToolResult.Success(
                content = "$appName ($packageName)\nVersion: $versionName ($versionCode)\n" +
                    "APK size: ${sizeMb} MB\nPermissions: ${permissions.length()}\n" +
                    "System app: $isSystem",
                data = data
            )
        } catch (e: PackageManager.NameNotFoundException) {
            ToolResult.Error("App not found: $packageName", "APP_NOT_FOUND")
        } catch (e: Exception) {
            ToolResult.Error("Failed to get app info: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
