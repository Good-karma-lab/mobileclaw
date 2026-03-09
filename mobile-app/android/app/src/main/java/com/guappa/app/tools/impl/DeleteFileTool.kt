package com.guappa.app.tools.impl

import android.content.Context
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class DeleteFileTool : Tool {
    override val name = "delete_file"
    override val description = "Delete a file from the device. Cannot delete directories or system files."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "path": {
                    "type": "string",
                    "description": "Absolute path to the file to delete"
                }
            },
            "required": ["path"]
        }
    """.trimIndent())

    companion object {
        private val BLOCKED_PATHS = listOf(
            "/system", "/proc", "/sys", "/dev", "/vendor",
            "/data/data", "/data/app", "/data/system"
        )
    }

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val path = params.optString("path", "")
        if (path.isEmpty()) {
            return ToolResult.Error("File path is required.", "INVALID_PARAMS")
        }

        // Block deletes in sensitive system paths
        for (blocked in BLOCKED_PATHS) {
            if (path.startsWith(blocked)) {
                return ToolResult.Error(
                    "Deleting files in '$blocked' is not allowed for security reasons.",
                    "PERMISSION_DENIED"
                )
            }
        }

        return try {
            val file = File(path)
            if (!file.exists()) {
                return ToolResult.Error("File not found: $path", "FILE_NOT_FOUND")
            }
            if (file.isDirectory) {
                return ToolResult.Error(
                    "Cannot delete directories. Use this tool only for individual files.",
                    "INVALID_PARAMS"
                )
            }
            if (!file.canWrite()) {
                return ToolResult.Error("Permission denied: cannot delete $path", "PERMISSION_DENIED")
            }

            val sizeBytes = file.length()
            val deleted = withContext(Dispatchers.IO) { file.delete() }

            if (deleted) {
                val data = JSONObject().apply {
                    put("path", path)
                    put("size_bytes", sizeBytes)
                    put("deleted", true)
                }
                ToolResult.Success(content = "File deleted: $path ($sizeBytes bytes)", data = data)
            } else {
                ToolResult.Error("Failed to delete file: $path", "EXECUTION_ERROR")
            }
        } catch (e: Exception) {
            ToolResult.Error("Failed to delete file: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
