package com.guappa.app.tools.impl

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PackageInfoTool : Tool {
    override val name = "package_info"
    override val description = "Get detailed information about an installed app: version, size, install date, permissions, target SDK, and more"
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

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val packageName = params.optString("package_name", "")
        if (packageName.isEmpty()) {
            return ToolResult.Error("package_name is required.", "INVALID_PARAMS")
        }

        return try {
            withContext(Dispatchers.IO) {
                getPackageDetails(packageName, context)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            ToolResult.Error("Package not found: $packageName", "PACKAGE_NOT_FOUND")
        } catch (e: Exception) {
            ToolResult.Error("Failed to get package info: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun getPackageDetails(packageName: String, context: Context): ToolResult {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val packageInfo = pm.getPackageInfo(
            packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES
        )

        val appName = pm.getApplicationLabel(appInfo).toString()
        val versionName = packageInfo.versionName ?: "unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isDebuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val isExternal = (appInfo.flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0
        val isLargeHeap = (appInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP) != 0

        val firstInstalled = packageInfo.firstInstallTime
        val lastUpdated = packageInfo.lastUpdateTime

        // APK size
        val apkFile = File(appInfo.sourceDir)
        val apkSizeBytes = apkFile.length()

        // Gather declared permissions
        val declaredPermissions = JSONArray()
        packageInfo.requestedPermissions?.forEachIndexed { index, perm ->
            val permJson = JSONObject()
            permJson.put("name", perm)
            // Check if the permission is granted
            val flags = packageInfo.requestedPermissionsFlags
            if (flags != null && index < flags.size) {
                val granted = context.packageManager.checkPermission(perm, packageName) == PackageManager.PERMISSION_GRANTED
                permJson.put("granted", granted)
            }
            declaredPermissions.put(permJson)
        }

        // Activities, services, receivers counts
        val activitiesCount = try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES).activities?.size ?: 0
        } catch (_: Exception) { 0 }

        val servicesCount = try {
            pm.getPackageInfo(packageName, PackageManager.GET_SERVICES).services?.size ?: 0
        } catch (_: Exception) { 0 }

        val receiversCount = try {
            pm.getPackageInfo(packageName, PackageManager.GET_RECEIVERS).receivers?.size ?: 0
        } catch (_: Exception) { 0 }

        val providersCount = try {
            pm.getPackageInfo(packageName, PackageManager.GET_PROVIDERS).providers?.size ?: 0
        } catch (_: Exception) { 0 }

        val data = JSONObject().apply {
            put("package_name", packageName)
            put("app_name", appName)
            put("version_name", versionName)
            put("version_code", versionCode)
            put("target_sdk", appInfo.targetSdkVersion)
            put("min_sdk", appInfo.minSdkVersion)
            put("is_system_app", isSystem)
            put("is_debuggable", isDebuggable)
            put("is_external_storage", isExternal)
            put("is_large_heap", isLargeHeap)
            put("enabled", appInfo.enabled)
            put("apk_path", appInfo.sourceDir)
            put("apk_size_bytes", apkSizeBytes)
            put("apk_size_mb", String.format("%.2f", apkSizeBytes / (1024.0 * 1024.0)))
            put("data_dir", appInfo.dataDir)
            put("first_installed", firstInstalled)
            put("first_installed_date", dateFormat.format(Date(firstInstalled)))
            put("last_updated", lastUpdated)
            put("last_updated_date", dateFormat.format(Date(lastUpdated)))
            put("uid", appInfo.uid)
            put("permissions", declaredPermissions)
            put("permission_count", declaredPermissions.length())
            put("activities_count", activitiesCount)
            put("services_count", servicesCount)
            put("receivers_count", receiversCount)
            put("providers_count", providersCount)
            if (appInfo.processName != null) put("process_name", appInfo.processName)
        }

        val sizeMb = String.format("%.2f", apkSizeBytes / (1024.0 * 1024.0))
        val summary = buildString {
            appendLine("$appName ($packageName)")
            appendLine("Version: $versionName (code $versionCode)")
            appendLine("Target SDK: ${appInfo.targetSdkVersion} | Min SDK: ${appInfo.minSdkVersion}")
            appendLine("APK size: $sizeMb MB")
            appendLine("Installed: ${dateFormat.format(Date(firstInstalled))}")
            appendLine("Updated: ${dateFormat.format(Date(lastUpdated))}")
            appendLine("Permissions: ${declaredPermissions.length()}")
            appendLine("Components: $activitiesCount activities, $servicesCount services, $receiversCount receivers, $providersCount providers")
            appendLine("System: $isSystem | Debuggable: $isDebuggable | External: $isExternal")
        }

        return ToolResult.Success(content = summary.trim(), data = data)
    }
}
