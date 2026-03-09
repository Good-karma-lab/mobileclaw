package com.guappa.app.tools.impl

import android.content.Context
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

class ListFilesTool : Tool {
    override val name = "list_files"
    override val description = "List files and directories in a given path. Returns names, sizes, types, and modification dates."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "path": {
                    "type": "string",
                    "description": "Absolute path to the directory to list"
                },
                "recursive": {
                    "type": "boolean",
                    "description": "If true, list files recursively (default: false)"
                },
                "max_depth": {
                    "type": "integer",
                    "description": "Maximum depth for recursive listing (default: 3)"
                },
                "pattern": {
                    "type": "string",
                    "description": "File name pattern filter (e.g. '*.txt', '*.jpg')"
                }
            },
            "required": ["path"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val path = params.optString("path", "")
        if (path.isEmpty()) {
            return ToolResult.Error("Directory path is required.", "INVALID_PARAMS")
        }

        val recursive = params.optBoolean("recursive", false)
        val maxDepth = params.optInt("max_depth", 3).coerceIn(1, 10)
        val pattern = params.optString("pattern", "")

        return try {
            val dir = File(path)
            if (!dir.exists()) {
                return ToolResult.Error("Directory not found: $path", "FILE_NOT_FOUND")
            }
            if (!dir.isDirectory) {
                return ToolResult.Error("Path is not a directory: $path", "INVALID_PATH")
            }
            if (!dir.canRead()) {
                return ToolResult.Error("Permission denied: cannot read $path", "PERMISSION_DENIED")
            }

            val entries = withContext(Dispatchers.IO) {
                if (recursive) {
                    listRecursive(dir, 0, maxDepth, pattern)
                } else {
                    listFlat(dir, pattern)
                }
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val filesArray = JSONArray()
            val listing = StringBuilder()

            for (entry in entries.take(500)) {
                val obj = JSONObject().apply {
                    put("name", entry.name)
                    put("path", entry.absolutePath)
                    put("is_directory", entry.isDirectory)
                    put("size_bytes", if (entry.isFile) entry.length() else 0)
                    put("modified", dateFormat.format(Date(entry.lastModified())))
                }
                filesArray.put(obj)

                val typeIndicator = if (entry.isDirectory) "[DIR]" else ""
                val sizeStr = if (entry.isFile) formatSize(entry.length()) else ""
                listing.appendLine("$typeIndicator ${entry.name}  $sizeStr")
            }

            val data = JSONObject().apply {
                put("path", path)
                put("entry_count", entries.size)
                put("entries", filesArray)
            }

            ToolResult.Success(
                content = "Contents of $path (${entries.size} entries):\n$listing",
                data = data
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to list files: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun listFlat(dir: File, pattern: String): List<File> {
        val files = dir.listFiles() ?: return emptyList()
        return if (pattern.isNotEmpty()) {
            val regex = globToRegex(pattern)
            files.filter { regex.matches(it.name) }.sortedBy { it.name }
        } else {
            files.sortedBy { it.name }
        }
    }

    private fun listRecursive(dir: File, depth: Int, maxDepth: Int, pattern: String): List<File> {
        if (depth >= maxDepth) return emptyList()
        val files = dir.listFiles() ?: return emptyList()
        val result = mutableListOf<File>()
        val regex = if (pattern.isNotEmpty()) globToRegex(pattern) else null

        for (file in files.sortedBy { it.name }) {
            if (regex == null || regex.matches(file.name) || file.isDirectory) {
                if (regex == null || regex.matches(file.name)) {
                    result.add(file)
                }
            }
            if (file.isDirectory) {
                result.addAll(listRecursive(file, depth + 1, maxDepth, pattern))
            }
        }
        return result
    }

    private fun globToRegex(glob: String): Regex {
        val regex = buildString {
            append("^")
            for (c in glob) {
                when (c) {
                    '*' -> append(".*")
                    '?' -> append(".")
                    '.' -> append("\\.")
                    else -> append(c)
                }
            }
            append("$")
        }
        return Regex(regex, RegexOption.IGNORE_CASE)
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
            else -> "${bytes / (1024 * 1024 * 1024)}GB"
        }
    }
}
