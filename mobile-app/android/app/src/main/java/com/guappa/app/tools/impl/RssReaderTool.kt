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
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/**
 * Parse RSS 2.0 and Atom feeds from a URL and return structured items.
 * Uses XmlPullParser (built into Android) for XML parsing.
 */
class RssReaderTool : Tool {
    override val name = "rss_read"
    override val description =
        "Parse an RSS or Atom feed from a URL and return structured items with title, link, description, and publication date."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "url": {
                    "type": "string",
                    "description": "The URL of the RSS or Atom feed"
                },
                "limit": {
                    "type": "integer",
                    "description": "Maximum number of feed items to return (default: 10, max: 50)"
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
        val limit = params.optInt("limit", 10).coerceIn(1, 50)

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "GuappaAgent/1.0")
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
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

                val xml = resp.body?.string() ?: ""
                if (xml.isEmpty()) {
                    return ToolResult.Error("Empty response body.", "EMPTY_RESPONSE")
                }

                val items = withContext(Dispatchers.IO) {
                    parseFeed(xml, limit)
                }

                val itemsArray = JSONArray()
                val summary = StringBuilder()
                summary.appendLine("Feed: $url (${items.size} items)")
                summary.appendLine()

                for ((index, item) in items.withIndex()) {
                    val obj = JSONObject().apply {
                        put("title", item.title)
                        put("link", item.link)
                        put("description", item.description)
                        put("pub_date", item.pubDate)
                    }
                    itemsArray.put(obj)

                    summary.appendLine("${index + 1}. ${item.title}")
                    if (item.link.isNotEmpty()) summary.appendLine("   ${item.link}")
                    if (item.pubDate.isNotEmpty()) summary.appendLine("   Published: ${item.pubDate}")
                    if (item.description.isNotEmpty()) {
                        val desc = item.description.take(200)
                        summary.appendLine("   $desc")
                    }
                    summary.appendLine()
                }

                val data = JSONObject().apply {
                    put("url", url)
                    put("item_count", items.size)
                    put("items", itemsArray)
                }

                ToolResult.Success(content = summary.toString().trim(), data = data)
            }
        } catch (e: Exception) {
            ToolResult.Error(
                "Failed to read feed: ${e.message}",
                "EXECUTION_ERROR",
                retryable = true
            )
        }
    }

    private data class FeedItem(
        val title: String,
        val link: String,
        val description: String,
        val pubDate: String
    )

    private fun parseFeed(xml: String, limit: Int): List<FeedItem> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        val items = mutableListOf<FeedItem>()
        var isAtom = false
        var inItem = false
        var currentTag = ""
        var title = ""
        var link = ""
        var description = ""
        var pubDate = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name?.lowercase() ?: ""

                    if (currentTag == "feed") {
                        isAtom = true
                    }

                    // RSS uses <item>, Atom uses <entry>
                    if (currentTag == "item" || currentTag == "entry") {
                        inItem = true
                        title = ""
                        link = ""
                        description = ""
                        pubDate = ""
                    }

                    // Atom <link> is self-closing with href attribute
                    if (inItem && currentTag == "link" && isAtom) {
                        val href = parser.getAttributeValue(null, "href")
                        if (href != null && link.isEmpty()) {
                            link = href
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    if (inItem) {
                        val text = parser.text?.trim() ?: ""
                        when (currentTag) {
                            "title" -> title = text
                            "link" -> if (text.isNotEmpty()) link = text
                            "description", "summary", "content" -> description = stripHtml(text)
                            "pubdate", "published", "updated", "dc:date" -> pubDate = text
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val endTag = parser.name?.lowercase() ?: ""
                    if (endTag == "item" || endTag == "entry") {
                        inItem = false
                        items.add(FeedItem(title, link, description, pubDate))
                        if (items.size >= limit) {
                            return items
                        }
                    }
                    if (endTag == currentTag) {
                        currentTag = ""
                    }
                }
            }
            eventType = parser.next()
        }

        return items
    }

    private fun stripHtml(text: String): String {
        return text
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&[a-zA-Z]+;"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
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
