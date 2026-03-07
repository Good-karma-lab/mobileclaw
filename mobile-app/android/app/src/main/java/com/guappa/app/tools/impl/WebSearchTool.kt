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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

class WebSearchTool : Tool {
    override val name = "web_search"
    override val description = "Search the web using Brave Search API and return structured results"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "The search query"
                },
                "count": {
                    "type": "integer",
                    "description": "Number of results to return (default: 5, max: 20)"
                }
            },
            "required": ["query"]
        }
    """.trimIndent())

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val query = params.optString("query", "")
        if (query.isEmpty()) {
            return ToolResult.Error("Search query is required.", "INVALID_PARAMS")
        }
        val count = params.optInt("count", 5).coerceIn(1, 20)

        val prefs = context.getSharedPreferences("guappa_config", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("brave_search_api_key", "") ?: ""
        if (apiKey.isEmpty()) {
            return ToolResult.Error(
                "Brave Search API key not configured. Set 'brave_search_api_key' in app config.",
                "CONFIG_MISSING"
            )
        }

        return try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://api.search.brave.com/res/v1/web/search?q=$encodedQuery&count=$count"

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("X-Subscription-Token", apiKey)
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).await()
            }

            response.use { resp ->
                if (!resp.isSuccessful) {
                    return ToolResult.Error(
                        "Brave Search API error: HTTP ${resp.code}",
                        "HTTP_ERROR",
                        retryable = resp.code in 500..599
                    )
                }

                val body = resp.body?.string() ?: "{}"
                val json = JSONObject(body)
                val webResults = json.optJSONObject("web")?.optJSONArray("results") ?: JSONArray()

                val results = JSONArray()
                for (i in 0 until webResults.length()) {
                    val item = webResults.getJSONObject(i)
                    val result = JSONObject().apply {
                        put("title", item.optString("title", ""))
                        put("url", item.optString("url", ""))
                        put("description", item.optString("description", ""))
                    }
                    results.put(result)
                }

                val data = JSONObject().apply {
                    put("query", query)
                    put("results", results)
                    put("result_count", results.length())
                }

                val summary = buildString {
                    append("Search results for \"$query\":\n")
                    for (i in 0 until results.length()) {
                        val r = results.getJSONObject(i)
                        append("${i + 1}. ${r.getString("title")}\n")
                        append("   ${r.getString("url")}\n")
                        append("   ${r.getString("description")}\n\n")
                    }
                }

                ToolResult.Success(content = summary.trim(), data = data)
            }
        } catch (e: Exception) {
            ToolResult.Error(
                "Web search failed: ${e.message}",
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
