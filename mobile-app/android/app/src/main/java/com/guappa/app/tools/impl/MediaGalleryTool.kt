package com.guappa.app.tools.impl

import android.content.Context
import android.provider.MediaStore
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Browse photos and videos from the device's MediaStore.
 * Queries the MediaStore ContentProvider for images, videos, or both.
 */
class MediaGalleryTool : Tool {
    override val name = "media_gallery"
    override val description =
        "Browse photos and videos from the device's media gallery. " +
        "Returns a list of media items with path, date, size, and duration (for videos)."
    override val requiredPermissions = listOf("READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO")
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "media_type": {
                    "type": "string",
                    "enum": ["images", "videos", "both"],
                    "description": "Type of media to browse. Default: 'both'."
                },
                "limit": {
                    "type": "integer",
                    "description": "Maximum number of items to return (default: 20, max: 100)"
                },
                "sort_by": {
                    "type": "string",
                    "enum": ["date", "size", "name"],
                    "description": "Sort order. Default: 'date' (newest first)."
                }
            },
            "required": []
        }
    """.trimIndent())

    override fun isAvailable(context: Context): Boolean {
        // On Android 13+ (API 33) we need READ_MEDIA_IMAGES / READ_MEDIA_VIDEO
        // On older versions, READ_EXTERNAL_STORAGE is needed
        val sdkInt = android.os.Build.VERSION.SDK_INT
        return if (sdkInt >= 33) {
            val imgPerm = context.checkSelfPermission("android.permission.READ_MEDIA_IMAGES")
            val vidPerm = context.checkSelfPermission("android.permission.READ_MEDIA_VIDEO")
            imgPerm == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                    vidPerm == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            val readPerm = context.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE")
            readPerm == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val mediaType = params.optString("media_type", "both")
        if (mediaType !in listOf("images", "videos", "both")) {
            return ToolResult.Error(
                "media_type must be 'images', 'videos', or 'both'.",
                "INVALID_PARAMS"
            )
        }

        val limit = params.optInt("limit", 20).coerceIn(1, 100)
        val sortBy = params.optString("sort_by", "date")

        return try {
            val items = withContext(Dispatchers.IO) {
                val results = mutableListOf<JSONObject>()

                if (mediaType == "images" || mediaType == "both") {
                    results.addAll(queryMedia(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, sortBy, limit, false))
                }
                if (mediaType == "videos" || mediaType == "both") {
                    results.addAll(queryMedia(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, sortBy, limit, true))
                }

                // Re-sort combined results
                val sortedResults = when (sortBy) {
                    "date" -> results.sortedByDescending { it.optLong("date_taken_epoch", 0) }
                    "size" -> results.sortedByDescending { it.optLong("size_bytes", 0) }
                    "name" -> results.sortedBy { it.optString("name", "") }
                    else -> results
                }

                sortedResults.take(limit)
            }

            val itemsArray = JSONArray()
            val summary = StringBuilder()
            summary.appendLine("Media gallery ($mediaType, ${items.size} items, sorted by $sortBy):")
            summary.appendLine()

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

            for ((index, item) in items.withIndex()) {
                itemsArray.put(item)
                summary.appendLine("${index + 1}. ${item.optString("name", "")}")
                summary.appendLine("   Path: ${item.optString("path", "")}")
                summary.appendLine("   Size: ${formatSize(item.optLong("size_bytes", 0))}")
                val dateEpoch = item.optLong("date_taken_epoch", 0)
                if (dateEpoch > 0) {
                    summary.appendLine("   Date: ${dateFormat.format(Date(dateEpoch))}")
                }
                val duration = item.optLong("duration_ms", 0)
                if (duration > 0) {
                    summary.appendLine("   Duration: ${formatDuration(duration)}")
                }
                summary.appendLine()
            }

            if (items.isEmpty()) {
                summary.clear()
                summary.append("No media items found.")
            }

            val data = JSONObject().apply {
                put("media_type", mediaType)
                put("item_count", items.size)
                put("sort_by", sortBy)
                put("items", itemsArray)
            }

            ToolResult.Success(content = summary.toString().trim(), data = data)
        } catch (e: SecurityException) {
            ToolResult.Error(
                "Permission denied: cannot access media gallery. Required permissions: READ_MEDIA_IMAGES, READ_MEDIA_VIDEO.",
                "PERMISSION_DENIED"
            )
        } catch (e: Exception) {
            ToolResult.Error("Media gallery query failed: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun queryMedia(
        context: Context,
        contentUri: android.net.Uri,
        sortBy: String,
        limit: Int,
        isVideo: Boolean
    ): List<JSONObject> {
        val results = mutableListOf<JSONObject>()

        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT
        )
        if (isVideo) {
            projection.add(MediaStore.Video.Media.DURATION)
        }

        val sortOrder = when (sortBy) {
            "date" -> "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            "size" -> "${MediaStore.MediaColumns.SIZE} DESC"
            "name" -> "${MediaStore.MediaColumns.DISPLAY_NAME} ASC"
            else -> "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        }

        val cursor = context.contentResolver.query(
            contentUri,
            projection.toTypedArray(),
            null,
            null,
            "$sortOrder LIMIT $limit"
        )

        cursor?.use {
            val nameIdx = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathIdx = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val sizeIdx = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateIdx = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val mimeIdx = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val widthIdx = it.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightIdx = it.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val durationIdx = if (isVideo) {
                it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            } else -1

            while (it.moveToNext()) {
                val obj = JSONObject().apply {
                    put("name", it.getString(nameIdx) ?: "")
                    put("path", it.getString(pathIdx) ?: "")
                    put("size_bytes", it.getLong(sizeIdx))
                    val dateAdded = it.getLong(dateIdx) * 1000 // seconds to milliseconds
                    put("date_taken_epoch", dateAdded)
                    put("mime_type", it.getString(mimeIdx) ?: "")
                    put("width", it.getInt(widthIdx))
                    put("height", it.getInt(heightIdx))
                    put("type", if (isVideo) "video" else "image")
                    if (isVideo && durationIdx >= 0) {
                        put("duration_ms", it.getLong(durationIdx))
                    }
                }
                results.add(obj)
            }
        }

        return results
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)}KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))}MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))}GB"
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
