package com.guappa.app.tools.impl

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class StorageInfoTool : Tool {
    override val name = "storage_info"
    override val description = "Get detailed storage usage breakdown: internal storage, external storage, app data, cache, and common directories."
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
            // Internal storage
            val internalStat = StatFs(Environment.getDataDirectory().path)
            val internalTotal = internalStat.totalBytes
            val internalAvailable = internalStat.availableBytes
            val internalUsed = internalTotal - internalAvailable

            // App-specific storage
            val appDataDir = context.filesDir
            val appCacheDir = context.cacheDir
            val appDataSize = dirSize(appDataDir)
            val appCacheSize = dirSize(appCacheDir)

            // Common directories
            val directories = JSONArray()
            val dirEntries = listOf(
                "Downloads" to Environment.DIRECTORY_DOWNLOADS,
                "Pictures" to Environment.DIRECTORY_PICTURES,
                "DCIM" to Environment.DIRECTORY_DCIM,
                "Music" to Environment.DIRECTORY_MUSIC,
                "Movies" to Environment.DIRECTORY_MOVIES,
                "Documents" to Environment.DIRECTORY_DOCUMENTS
            )

            val summary = StringBuilder()
            summary.appendLine("=== Storage Overview ===")
            summary.appendLine("Internal: ${formatSize(internalUsed)} used / ${formatSize(internalTotal)} total (${formatSize(internalAvailable)} free)")
            summary.appendLine("Usage: ${(internalUsed * 100 / internalTotal)}%")

            // External storage (if available)
            val externalDirs = context.getExternalFilesDirs(null)
            var externalTotal = 0L
            var externalAvailable = 0L
            if (externalDirs.size > 1 && externalDirs[1] != null) {
                val extStat = StatFs(externalDirs[1]!!.path)
                externalTotal = extStat.totalBytes
                externalAvailable = extStat.availableBytes
                summary.appendLine("External SD: ${formatSize(externalTotal - externalAvailable)} used / ${formatSize(externalTotal)} total (${formatSize(externalAvailable)} free)")
            }

            summary.appendLine()
            summary.appendLine("=== App Storage ===")
            summary.appendLine("App Data: ${formatSize(appDataSize)}")
            summary.appendLine("App Cache: ${formatSize(appCacheSize)}")

            summary.appendLine()
            summary.appendLine("=== Common Directories ===")
            for ((label, envDir) in dirEntries) {
                val dir = Environment.getExternalStoragePublicDirectory(envDir)
                if (dir.exists()) {
                    val size = dirSize(dir)
                    val fileCount = dir.listFiles()?.size ?: 0
                    summary.appendLine("$label: ${formatSize(size)} ($fileCount items)")
                    directories.put(JSONObject().apply {
                        put("name", label)
                        put("path", dir.absolutePath)
                        put("size_bytes", size)
                        put("size_human", formatSize(size))
                        put("file_count", fileCount)
                    })
                }
            }

            val data = JSONObject().apply {
                put("internal_total_bytes", internalTotal)
                put("internal_available_bytes", internalAvailable)
                put("internal_used_bytes", internalUsed)
                put("internal_usage_percent", (internalUsed * 100 / internalTotal))
                put("external_total_bytes", externalTotal)
                put("external_available_bytes", externalAvailable)
                put("app_data_bytes", appDataSize)
                put("app_cache_bytes", appCacheSize)
                put("directories", directories)
            }

            ToolResult.Success(content = summary.toString().trim(), data = data)
        } catch (e: Exception) {
            ToolResult.Error("Failed to get storage info: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0
        var size = 0L
        try {
            val files = dir.listFiles() ?: return 0
            for (file in files) {
                size += if (file.isDirectory) {
                    dirSize(file)
                } else {
                    file.length()
                }
            }
        } catch (_: SecurityException) {
            // Skip directories we cannot read
        }
        return size
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
