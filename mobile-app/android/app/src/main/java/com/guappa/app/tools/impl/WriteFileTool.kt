package com.guappa.app.tools.impl

import android.content.Context
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class WriteFileTool : Tool {
    override val name = "write_file"
    override val description = "Write content to a file on the device. Can create new files or overwrite existing ones. Supports append mode."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "path": {
                    "type": "string",
                    "description": "Absolute path to the file to write"
                },
                "content": {
                    "type": "string",
                    "description": "The text content to write to the file"
                },
                "append": {
                    "type": "boolean",
                    "description": "If true, append to existing file instead of overwriting (default: false)"
                },
                "create_dirs": {
                    "type": "boolean",
                    "description": "If true, create parent directories if they do not exist (default: true)"
                }
            },
            "required": ["path", "content"]
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

        val content = params.optString("content", "")
        val append = params.optBoolean("append", false)
        val createDirs = params.optBoolean("create_dirs", true)

        // Block writes to sensitive system paths
        for (blocked in BLOCKED_PATHS) {
            if (path.startsWith(blocked)) {
                return ToolResult.Error(
                    "Writing to '$blocked' is not allowed for security reasons.",
                    "PERMISSION_DENIED"
                )
            }
        }

        return try {
            val file = File(path)

            if (createDirs) {
                file.parentFile?.let { parent ->
                    if (!parent.exists()) {
                        val created = withContext(Dispatchers.IO) { parent.mkdirs() }
                        if (!created) {
                            return ToolResult.Error(
                                "Failed to create parent directories for: $path",
                                "EXECUTION_ERROR"
                            )
                        }
                    }
                }
            }

            withContext(Dispatchers.IO) {
                if (append) {
                    file.appendText(content)
                } else {
                    file.writeText(content)
                }
            }

            val data = JSONObject().apply {
                put("path", path)
                put("bytes_written", content.toByteArray().size)
                put("append", append)
                put("total_size", file.length())
            }

            val mode = if (append) "appended to" else "written to"
            ToolResult.Success(
                content = "Successfully $mode file: $path (${content.length} chars)",
                data = data
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to write file: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
