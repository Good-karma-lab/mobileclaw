package com.guappa.app.tools.impl

import android.content.Context
import android.webkit.MimeTypeMap
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileInfoTool : Tool {
    override val name = "file_info"
    override val description = "Get metadata about a file: size, type, MIME type, modification date, permissions."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "path": {
                    "type": "string",
                    "description": "Absolute path to the file"
                }
            },
            "required": ["path"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val path = params.optString("path", "")
        if (path.isEmpty()) {
            return ToolResult.Error("File path is required.", "INVALID_PARAMS")
        }

        return try {
            val file = File(path)
            if (!file.exists()) {
                return ToolResult.Error("File not found: $path", "FILE_NOT_FOUND")
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val extension = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(extension) ?: "application/octet-stream"

            val data = JSONObject().apply {
                put("path", file.absolutePath)
                put("name", file.name)
                put("extension", extension)
                put("mime_type", mimeType)
                put("is_file", file.isFile)
                put("is_directory", file.isDirectory)
                put("size_bytes", file.length())
                put("size_human", formatSize(file.length()))
                put("last_modified", dateFormat.format(Date(file.lastModified())))
                put("last_modified_epoch", file.lastModified())
                put("can_read", file.canRead())
                put("can_write", file.canWrite())
                put("can_execute", file.canExecute())
                put("is_hidden", file.isHidden)
                if (file.isDirectory) {
                    put("child_count", file.listFiles()?.size ?: 0)
                }
            }

            val typeStr = when {
                file.isDirectory -> "directory"
                else -> "$mimeType ($extension)"
            }

            ToolResult.Success(
                content = buildString {
                    appendLine("File: ${file.name}")
                    appendLine("Path: ${file.absolutePath}")
                    appendLine("Type: $typeStr")
                    appendLine("Size: ${formatSize(file.length())} (${file.length()} bytes)")
                    appendLine("Modified: ${dateFormat.format(Date(file.lastModified()))}")
                    appendLine("Permissions: ${permissionString(file)}")
                },
                data = data
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to get file info: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes} B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }

    private fun permissionString(file: File): String {
        val r = if (file.canRead()) "r" else "-"
        val w = if (file.canWrite()) "w" else "-"
        val x = if (file.canExecute()) "x" else "-"
        return "$r$w$x"
    }
}
