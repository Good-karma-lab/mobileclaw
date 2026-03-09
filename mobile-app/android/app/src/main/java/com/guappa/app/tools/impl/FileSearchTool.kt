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

/**
 * Search files by name pattern or content within a directory.
 * Recursively walks the directory tree with glob pattern matching
 * and optional content search.
 */
class FileSearchTool : Tool {
    override val name = "file_search"
    override val description =
        "Search for files by name pattern (glob) or by content within files. " +
        "Recursively searches directories and returns matching file paths with metadata."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "directory": {
                    "type": "string",
                    "description": "Absolute path to the directory to search in"
                },
                "pattern": {
                    "type": "string",
                    "description": "Glob pattern for file name matching (e.g. '*.txt', '*.jpg', 'report_*')"
                },
                "content_search": {
                    "type": "string",
                    "description": "Optional text to search for within file contents (only searches text files)"
                },
                "max_results": {
                    "type": "integer",
                    "description": "Maximum number of results to return (default: 50, max: 500)"
                },
                "max_depth": {
                    "type": "integer",
                    "description": "Maximum directory depth to search (default: 10, max: 20)"
                }
            },
            "required": ["directory"]
        }
    """.trimIndent())

    companion object {
        private const val MAX_FILE_SIZE_FOR_CONTENT_SEARCH = 5L * 1024 * 1024 // 5 MB
        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "json", "xml", "csv", "html", "htm", "css", "js",
            "ts", "kt", "java", "py", "rb", "rs", "go", "c", "cpp", "h",
            "yaml", "yml", "toml", "ini", "conf", "cfg", "log", "sh",
            "bat", "ps1", "sql", "r", "m", "swift", "dart", "lua",
            "properties", "gradle", "pro", "env", "gitignore"
        )
    }

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val directory = params.optString("directory", "")
        if (directory.isEmpty()) {
            return ToolResult.Error("directory is required.", "INVALID_PARAMS")
        }

        val pattern = params.optString("pattern", "")
        val contentSearch = params.optString("content_search", "")
        val maxResults = params.optInt("max_results", 50).coerceIn(1, 500)
        val maxDepth = params.optInt("max_depth", 10).coerceIn(1, 20)

        if (pattern.isEmpty() && contentSearch.isEmpty()) {
            return ToolResult.Error(
                "At least one of 'pattern' or 'content_search' must be provided.",
                "INVALID_PARAMS"
            )
        }

        val dir = File(directory)
        if (!dir.exists()) {
            return ToolResult.Error("Directory not found: $directory", "FILE_NOT_FOUND")
        }
        if (!dir.isDirectory) {
            return ToolResult.Error("Path is not a directory: $directory", "INVALID_PATH")
        }
        if (!dir.canRead()) {
            return ToolResult.Error("Permission denied: cannot read $directory", "PERMISSION_DENIED")
        }

        return try {
            val results = withContext(Dispatchers.IO) {
                searchFiles(dir, pattern, contentSearch, maxResults, maxDepth)
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val matchesArray = JSONArray()
            val summary = StringBuilder()
            summary.appendLine("Search results in $directory (${results.size} matches):")
            summary.appendLine()

            for ((index, match) in results.withIndex()) {
                val obj = JSONObject().apply {
                    put("path", match.file.absolutePath)
                    put("name", match.file.name)
                    put("size_bytes", match.file.length())
                    put("modified", dateFormat.format(Date(match.file.lastModified())))
                    put("is_directory", match.file.isDirectory)
                    if (match.matchedLine != null) {
                        put("matched_line", match.matchedLine)
                        put("line_number", match.lineNumber)
                    }
                }
                matchesArray.put(obj)

                summary.appendLine("${index + 1}. ${match.file.absolutePath}")
                summary.appendLine("   Size: ${formatSize(match.file.length())} | Modified: ${dateFormat.format(Date(match.file.lastModified()))}")
                if (match.matchedLine != null) {
                    val preview = match.matchedLine.take(120)
                    summary.appendLine("   Line ${match.lineNumber}: $preview")
                }
                summary.appendLine()
            }

            if (results.isEmpty()) {
                summary.clear()
                summary.append("No files found matching the search criteria.")
            }

            val data = JSONObject().apply {
                put("directory", directory)
                put("pattern", pattern)
                put("content_search", contentSearch)
                put("match_count", results.size)
                put("matches", matchesArray)
            }

            ToolResult.Success(content = summary.toString().trim(), data = data)
        } catch (e: Exception) {
            ToolResult.Error("File search failed: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private data class SearchMatch(
        val file: File,
        val matchedLine: String? = null,
        val lineNumber: Int = 0
    )

    private fun searchFiles(
        dir: File,
        pattern: String,
        contentSearch: String,
        maxResults: Int,
        maxDepth: Int
    ): List<SearchMatch> {
        val results = mutableListOf<SearchMatch>()
        val nameRegex = if (pattern.isNotEmpty()) globToRegex(pattern) else null

        searchRecursive(dir, nameRegex, contentSearch, maxResults, maxDepth, 0, results)
        return results
    }

    private fun searchRecursive(
        dir: File,
        nameRegex: Regex?,
        contentSearch: String,
        maxResults: Int,
        maxDepth: Int,
        currentDepth: Int,
        results: MutableList<SearchMatch>
    ) {
        if (currentDepth > maxDepth || results.size >= maxResults) return

        val files = dir.listFiles() ?: return

        for (file in files.sortedBy { it.name }) {
            if (results.size >= maxResults) return

            if (file.isDirectory) {
                // Skip hidden directories and common non-useful directories
                if (file.name.startsWith(".") || file.name == "node_modules" || file.name == "build") {
                    continue
                }
                searchRecursive(file, nameRegex, contentSearch, maxResults, maxDepth, currentDepth + 1, results)
                continue
            }

            // Check name pattern
            val nameMatch = nameRegex == null || nameRegex.matches(file.name)
            if (!nameMatch) continue

            // Check content search
            if (contentSearch.isNotEmpty()) {
                if (!isTextFile(file) || file.length() > MAX_FILE_SIZE_FOR_CONTENT_SEARCH) continue

                val contentMatch = searchInFile(file, contentSearch)
                if (contentMatch != null) {
                    results.add(SearchMatch(file, contentMatch.first, contentMatch.second))
                }
            } else {
                results.add(SearchMatch(file))
            }
        }
    }

    private fun searchInFile(file: File, searchText: String): Pair<String, Int>? {
        try {
            file.bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, line ->
                    if (line.contains(searchText, ignoreCase = true)) {
                        return Pair(line.trim(), index + 1)
                    }
                }
            }
        } catch (_: Exception) {
            // Skip files that can't be read as text
        }
        return null
    }

    private fun isTextFile(file: File): Boolean {
        return file.extension.lowercase() in TEXT_EXTENSIONS
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
