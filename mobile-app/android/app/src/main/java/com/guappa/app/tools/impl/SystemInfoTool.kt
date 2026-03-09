package com.guappa.app.tools.impl

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class SystemInfoTool : Tool {
    override val name = "system_info"
    override val description = "Get device system information: model, manufacturer, OS version, CPU architecture, RAM, and storage."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {},
            "required": []
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)

            val totalRam = memInfo.totalMem
            val availableRam = memInfo.availMem
            val usedRam = totalRam - availableRam

            // Internal storage
            val internalStat = StatFs(Environment.getDataDirectory().path)
            val internalTotal = internalStat.totalBytes
            val internalAvailable = internalStat.availableBytes

            val data = JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
                put("brand", Build.BRAND)
                put("device", Build.DEVICE)
                put("product", Build.PRODUCT)
                put("android_version", Build.VERSION.RELEASE)
                put("sdk_version", Build.VERSION.SDK_INT)
                put("security_patch", Build.VERSION.SECURITY_PATCH)
                put("cpu_abi", Build.SUPPORTED_ABIS.joinToString())
                put("hardware", Build.HARDWARE)
                put("ram_total_bytes", totalRam)
                put("ram_available_bytes", availableRam)
                put("ram_used_bytes", usedRam)
                put("ram_total_human", formatSize(totalRam))
                put("ram_available_human", formatSize(availableRam))
                put("storage_total_bytes", internalTotal)
                put("storage_available_bytes", internalAvailable)
                put("storage_total_human", formatSize(internalTotal))
                put("storage_available_human", formatSize(internalAvailable))
                put("low_memory", memInfo.lowMemory)
            }

            val summary = buildString {
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("Security Patch: ${Build.VERSION.SECURITY_PATCH}")
                appendLine("CPU: ${Build.SUPPORTED_ABIS.joinToString()}")
                appendLine("RAM: ${formatSize(usedRam)} used / ${formatSize(totalRam)} total (${formatSize(availableRam)} free)")
                appendLine("Storage: ${formatSize(internalTotal - internalAvailable)} used / ${formatSize(internalTotal)} total (${formatSize(internalAvailable)} free)")
                if (memInfo.lowMemory) appendLine("WARNING: Device is in low memory state")
            }

            ToolResult.Success(content = summary.trim(), data = data)
        } catch (e: Exception) {
            ToolResult.Error("Failed to get system info: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes} B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }
}
