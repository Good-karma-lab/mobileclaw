package com.guappa.app.tools.impl

import android.content.Context
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/**
 * Call REST APIs with configurable HTTP method, headers, and JSON body.
 * Returns the response body and status code.
 */
class WebApiTool : Tool {
    override val name = "web_api"
    override val description =
        "Call a REST API endpoint with configurable HTTP method, headers, and JSON body. " +
        "Supports GET, POST, PUT, DELETE, and PATCH. Returns response body and status code."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "url": {
                    "type": "string",
                    "description": "The API endpoint URL"
                },
                "method": {
                    "type": "string",
                    "enum": ["GET", "POST", "PUT", "DELETE", "PATCH"],
                    "description": "HTTP method. Default: 'GET'."
                },
                "headers": {
                    "type": "object",
                    "description": "Map of HTTP headers to include in the request"
                },
                "body": {
                    "type": "string",
                    "description": "Request body as a JSON string (for POST, PUT, PATCH)"
                },
                "timeout_ms": {
                    "type": "integer",
                    "description": "Request timeout in milliseconds (default: 30000, max: 120000)"
                }
            },
            "required": ["url"]
        }
    """.trimIndent())

    companion object {
        private val ALLOWED_METHODS = setOf("GET", "POST", "PUT", "DELETE", "PATCH")
        private const val MAX_RESPONSE_LENGTH = 100000
    }

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        var url = params.optString("url", "")
        if (url.isEmpty()) {
            return ToolResult.Error("URL is required.", "INVALID_PARAMS")
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }

        val method = params.optString("method", "GET").uppercase()
        if (method !in ALLOWED_METHODS) {
            return ToolResult.Error(
                "Unsupported HTTP method: $method. Use GET, POST, PUT, DELETE, or PATCH.",
                "INVALID_PARAMS"
            )
        }

        val timeoutMs = params.optInt("timeout_ms", 30000).coerceIn(1000, 120000).toLong()
        val headersObj = params.optJSONObject("headers")
        val bodyStr = params.optString("body", "")

        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .build()

            val requestBody = if (method in setOf("POST", "PUT", "PATCH") && bodyStr.isNotEmpty()) {
                bodyStr.toRequestBody("application/json".toMediaTypeOrNull())
            } else if (method in setOf("POST", "PUT", "PATCH")) {
                "".toRequestBody("application/json".toMediaTypeOrNull())
            } else {
                null
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "GuappaAgent/1.0")

            // Apply custom headers
            if (headersObj != null) {
                val keys = headersObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    requestBuilder.header(key, headersObj.getString(key))
                }
            }

            // Set method with body
            when (method) {
                "GET" -> requestBuilder.get()
                "DELETE" -> {
                    if (requestBody != null) requestBuilder.delete(requestBody)
                    else requestBuilder.delete()
                }
                "POST" -> requestBuilder.post(requestBody!!)
                "PUT" -> requestBuilder.put(requestBody!!)
                "PATCH" -> requestBuilder.patch(requestBody!!)
            }

            val request = requestBuilder.build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).await()
            }

            response.use { resp ->
                val responseBody = resp.body?.string() ?: ""
                val truncated = responseBody.length > MAX_RESPONSE_LENGTH
                val content = if (truncated) {
                    responseBody.take(MAX_RESPONSE_LENGTH) + "\n...[truncated at $MAX_RESPONSE_LENGTH chars]"
                } else {
                    responseBody
                }

                val data = JSONObject().apply {
                    put("url", url)
                    put("method", method)
                    put("status_code", resp.code)
                    put("content_type", resp.header("Content-Type", "unknown"))
                    put("content_length", responseBody.length)
                    put("truncated", truncated)

                    // Include response headers
                    val respHeaders = JSONObject()
                    for (name in resp.headers.names()) {
                        respHeaders.put(name, resp.header(name))
                    }
                    put("response_headers", respHeaders)
                }

                if (resp.isSuccessful) {
                    ToolResult.Success(content = content, data = data)
                } else {
                    ToolResult.Error(
                        "API returned HTTP ${resp.code}: $content",
                        "HTTP_ERROR",
                        retryable = resp.code in 500..599
                    )
                }
            }
        } catch (e: Exception) {
            ToolResult.Error(
                "API call failed: ${e.message}",
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
