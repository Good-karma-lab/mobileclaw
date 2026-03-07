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
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

class WebFetchTool : Tool {
    override val name = "web_fetch"
    override val description = "Fetch the content of a web page via HTTP GET and return the text"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "url": {
                    "type": "string",
                    "description": "The URL to fetch"
                },
                "max_length": {
                    "type": "integer",
                    "description": "Maximum characters to return (default: 10000)"
                }
            },
            "required": ["url"]
        }
    """.trimIndent())

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        var url = params.optString("url", "")
        if (url.isEmpty()) {
            return ToolResult.Error("URL is required.", "INVALID_PARAMS")
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        val maxLength = params.optInt("max_length", 10000).coerceIn(100, 50000)

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
                        "HTTP ${resp.code}: ${resp.message}",
                        "HTTP_ERROR",
                        retryable = resp.code in 500..599
                    )
                }

                val body = resp.body?.string() ?: ""
                val truncated = if (body.length > maxLength) {
                    body.take(maxLength) + "\n...[truncated at $maxLength chars]"
                } else {
                    body
                }

                val data = JSONObject().apply {
                    put("url", url)
                    put("status_code", resp.code)
                    put("content_type", resp.header("Content-Type", "unknown"))
                    put("content_length", body.length)
                    put("truncated", body.length > maxLength)
                }

                ToolResult.Success(content = truncated, data = data)
            }
        } catch (e: Exception) {
            ToolResult.Error(
                "Failed to fetch URL: ${e.message}",
                "EXECUTION_ERROR",
                retryable = true
            )
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
