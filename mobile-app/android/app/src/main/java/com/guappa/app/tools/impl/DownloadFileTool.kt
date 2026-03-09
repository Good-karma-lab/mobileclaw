package com.guappa.app.tools.impl

import android.content.Context
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

class DownloadFileTool : Tool {
    override val name = "download_file"
    override val description = "Download a file from a URL and save it to the device. Returns the local file path on success."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "url": {
                    "type": "string",
                    "description": "The URL of the file to download"
                },
                "destination": {
                    "type": "string",
                    "description": "Destination file path. If not provided, saves to app's downloads directory."
                },
                "filename": {
                    "type": "string",
                    "description": "Override filename (used when destination is a directory or not provided)"
                }
            },
            "required": ["url"]
        }
    """.trimIndent())

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    companion object {
        private const val MAX_FILE_SIZE = 100L * 1024 * 1024 // 100 MB
    }

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        var url = params.optString("url", "")
        if (url.isEmpty()) {
            return ToolResult.Error("URL is required.", "INVALID_PARAMS")
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }

        val customFilename = params.optString("filename", "")
        val destination = params.optString("destination", "")

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "GuappaAgent/1.0")
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).await()
            }

            response.use { resp ->
                if (!resp.isSuccessful) {
                    return ToolResult.Error(
                        "Download failed: HTTP ${resp.code}",
                        "HTTP_ERROR",
                        retryable = resp.code in 500..599
                    )
                }

                val contentLength = resp.header("Content-Length")?.toLongOrNull() ?: -1
                if (contentLength > MAX_FILE_SIZE) {
                    return ToolResult.Error(
                        "File too large: ${contentLength / (1024 * 1024)}MB exceeds 100MB limit.",
                        "FILE_TOO_LARGE"
                    )
                }

                // Determine filename
                val resolvedFilename = when {
                    customFilename.isNotEmpty() -> customFilename
                    else -> filenameFromUrl(url, resp.header("Content-Disposition"))
                }

                // Determine destination path
                val destFile = when {
                    destination.isNotEmpty() -> {
                        val destPath = File(destination)
                        if (destPath.isDirectory || destination.endsWith("/")) {
                            File(destPath, resolvedFilename)
                        } else {
                            destPath
                        }
                    }
                    else -> {
                        val downloadsDir = File(context.getExternalFilesDir(null), "downloads")
                        downloadsDir.mkdirs()
                        File(downloadsDir, resolvedFilename)
                    }
                }

                // Ensure parent directory exists
                destFile.parentFile?.mkdirs()

                // Write to file
                val bytesWritten = withContext(Dispatchers.IO) {
                    val body = resp.body ?: throw IOException("Empty response body")
                    destFile.outputStream().use { output ->
                        body.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    destFile.length()
                }

                val contentType = resp.header("Content-Type") ?: "unknown"
                val data = JSONObject().apply {
                    put("url", url)
                    put("path", destFile.absolutePath)
                    put("filename", destFile.name)
                    put("size_bytes", bytesWritten)
                    put("content_type", contentType)
                }

                ToolResult.Success(
                    content = "Downloaded ${destFile.name} (${formatSize(bytesWritten)}) to ${destFile.absolutePath}",
                    data = data
                )
            }
        } catch (e: Exception) {
            ToolResult.Error(
                "Download failed: ${e.message}",
                "EXECUTION_ERROR",
                retryable = true
            )
        }
    }

    private fun filenameFromUrl(url: String, contentDisposition: String?): String {
        // Try Content-Disposition header
        if (!contentDisposition.isNullOrEmpty()) {
            val match = Regex("filename=\"?([^\"\\s;]+)\"?").find(contentDisposition)
            if (match != null) return match.groupValues[1]
        }
        // Fall back to URL path
        val urlPath = url.substringBefore("?").substringAfterLast("/")
        return if (urlPath.isNotEmpty() && urlPath.contains(".")) {
            urlPath
        } else {
            "download_${System.currentTimeMillis()}"
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)}KB"
            else -> "${"%.1f".format(bytes / (1024.0 * 1024))}MB"
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) {
                continuation.resumeWithException(e)
            }
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, _, _ ->
                response.close()
            }
        }
    })
    continuation.invokeOnCancellation {
        try {
            cancel()
        } catch (_: Exception) { }
    }
}
