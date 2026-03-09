package com.guappa.app.tools.impl

import android.content.Context
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class ReadFileTool : Tool {
    override val name = "read_file"
    override val description = "Read the contents of a file on the device. Returns the text content for text files, or metadata for binary files."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "path": {
                    "type": "string",
                    "description": "Absolute path to the file to read"
                },
                "max_length": {
                    "type": "integer",
                    "description": "Maximum characters to return (default: 10000)"
                },
                "offset": {
                    "type": "integer",
                    "description": "Character offset to start reading from (default: 0)"
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

        val maxLength = params.optInt("max_length", 10000).coerceIn(1, 100000)
        val offset = params.optInt("offset", 0).coerceAtLeast(0)

        return try {
            val file = File(path)
            if (!file.exists()) {
                return ToolResult.Error("File not found: $path", "FILE_NOT_FOUND")
            }
            if (!file.isFile) {
                return ToolResult.Error("Path is not a file: $path", "INVALID_PATH")
            }
            if (!file.canRead()) {
                return ToolResult.Error("Permission denied: cannot read $path", "PERMISSION_DENIED")
            }

            val content = withContext(Dispatchers.IO) {
                val text = file.readText()
                val startIndex = offset.coerceAtMost(text.length)
                val endIndex = (startIndex + maxLength).coerceAtMost(text.length)
                text.substring(startIndex, endIndex)
            }

            val fileSize = file.length()
            val truncated = fileSize > (offset + maxLength)

            val data = JSONObject().apply {
                put("path", path)
                put("size_bytes", fileSize)
                put("offset", offset)
                put("chars_returned", content.length)
                put("truncated", truncated)
            }

            val truncNote = if (truncated) "\n...[truncated at $maxLength chars]" else ""
            ToolResult.Success(content = content + truncNote, data = data)
        } catch (e: Exception) {
            ToolResult.Error("Failed to read file: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
