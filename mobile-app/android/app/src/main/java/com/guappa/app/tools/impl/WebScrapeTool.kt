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
import java.util.regex.Pattern
import kotlin.coroutines.resumeWithException

/**
 * Extract structured data from a web page using CSS-selector-like patterns.
 * Uses OkHttp to fetch HTML and a lightweight built-in CSS selector engine
 * (regex-based tag/class/id matching) to avoid requiring Jsoup as an extra dependency.
 */
class WebScrapeTool : Tool {
    override val name = "web_scrape"
    override val description =
        "Extract structured data from a web page using CSS selectors. " +
        "Fetches the page and applies selectors to extract text, HTML fragments, or attributes."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "url": {
                    "type": "string",
                    "description": "The URL of the web page to scrape"
                },
                "selectors": {
                    "type": "object",
                    "description": "JSON object mapping field names to CSS selectors (e.g. {\"title\": \"h1\", \"links\": \"a.nav\"})"
                },
                "extract": {
                    "type": "string",
                    "enum": ["text", "html", "attr"],
                    "description": "What to extract from matched elements: 'text' (inner text), 'html' (outer HTML), or 'attr' (attribute value). Default: 'text'."
                },
                "attr_name": {
                    "type": "string",
                    "description": "Attribute name to extract when extract='attr' (e.g. 'href', 'src'). Required if extract='attr'."
                },
                "list": {
                    "type": "boolean",
                    "description": "If true, return all matches as an array. If false, return only the first match. Default: false."
                }
            },
            "required": ["url", "selectors"]
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

        val selectors = params.optJSONObject("selectors")
        if (selectors == null || selectors.length() == 0) {
            return ToolResult.Error("At least one selector is required in 'selectors'.", "INVALID_PARAMS")
        }

        val extractMode = params.optString("extract", "text")
        if (extractMode !in listOf("text", "html", "attr")) {
            return ToolResult.Error("extract must be 'text', 'html', or 'attr'.", "INVALID_PARAMS")
        }
        val attrName = params.optString("attr_name", "")
        if (extractMode == "attr" && attrName.isEmpty()) {
            return ToolResult.Error("attr_name is required when extract='attr'.", "INVALID_PARAMS")
        }
        val listMode = params.optBoolean("list", false)

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

                val html = resp.body?.string() ?: ""
                val result = JSONObject()

                val keys = selectors.keys()
                while (keys.hasNext()) {
                    val fieldName = keys.next()
                    val selector = selectors.getString(fieldName)
                    val matches = selectElements(html, selector)

                    if (listMode) {
                        val arr = JSONArray()
                        for (element in matches) {
                            arr.put(extractValue(element, extractMode, attrName))
                        }
                        result.put(fieldName, arr)
                    } else {
                        val first = matches.firstOrNull()
                        result.put(fieldName, if (first != null) extractValue(first, extractMode, attrName) else JSONObject.NULL)
                    }
                }

                val data = JSONObject().apply {
                    put("url", url)
                    put("extracted", result)
                    put("selector_count", selectors.length())
                }

                ToolResult.Success(content = result.toString(2), data = data)
            }
        } catch (e: Exception) {
            ToolResult.Error(
                "Failed to scrape URL: ${e.message}",
                "EXECUTION_ERROR",
                retryable = true
            )
        }
    }

    /**
     * Lightweight CSS selector matching on raw HTML.
     * Supports: tag, tag.class, tag#id, .class, #id, tag[attr], tag[attr=value].
     * Returns list of matched outer-HTML fragments.
     */
    private fun selectElements(html: String, selector: String): List<String> {
        val parsed = parseSelector(selector)
        val tag = parsed.tag ?: "[a-zA-Z][a-zA-Z0-9]*"
        val results = mutableListOf<String>()

        // Build regex to find opening tags matching criteria
        val tagPattern = Pattern.compile(
            "<($tag)(\\s[^>]*)?>",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )
        val matcher = tagPattern.matcher(html)

        while (matcher.find()) {
            val tagName = matcher.group(1) ?: continue
            val attrs = matcher.group(2) ?: ""
            val startPos = matcher.start()

            // Check class constraint
            if (parsed.className != null) {
                val classMatch = Pattern.compile(
                    "class\\s*=\\s*[\"']([^\"']*)[\"']",
                    Pattern.CASE_INSENSITIVE
                ).matcher(attrs)
                val classes = if (classMatch.find()) classMatch.group(1) ?: "" else ""
                if (!classes.split("\\s+".toRegex()).contains(parsed.className)) continue
            }

            // Check id constraint
            if (parsed.id != null) {
                val idMatch = Pattern.compile(
                    "id\\s*=\\s*[\"']([^\"']*)[\"']",
                    Pattern.CASE_INSENSITIVE
                ).matcher(attrs)
                val idValue = if (idMatch.find()) idMatch.group(1) ?: "" else ""
                if (idValue != parsed.id) continue
            }

            // Check attribute constraint
            if (parsed.attrName != null) {
                val attrPat = if (parsed.attrValue != null) {
                    Pattern.compile(
                        "${Pattern.quote(parsed.attrName)}\\s*=\\s*[\"']${Pattern.quote(parsed.attrValue)}[\"']",
                        Pattern.CASE_INSENSITIVE
                    )
                } else {
                    Pattern.compile(
                        "${Pattern.quote(parsed.attrName)}\\s*=",
                        Pattern.CASE_INSENSITIVE
                    )
                }
                if (!attrPat.matcher(attrs).find()) continue
            }

            // Extract the full element (opening tag through closing tag)
            val outerHtml = extractOuterHtml(html, startPos, tagName)
            if (outerHtml != null) {
                results.add(outerHtml)
            }
        }
        return results
    }

    private fun extractOuterHtml(html: String, startPos: Int, tagName: String): String? {
        // For self-closing or void elements
        val voidElements = setOf(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr"
        )
        if (tagName.lowercase() in voidElements) {
            val endIdx = html.indexOf('>', startPos)
            return if (endIdx >= 0) html.substring(startPos, endIdx + 1) else null
        }

        // Find matching closing tag, handling nesting
        var depth = 1
        var searchPos = html.indexOf('>', startPos) + 1
        if (searchPos <= 0) return null

        val openPattern = Pattern.compile(
            "<$tagName(\\s|>|/>)",
            Pattern.CASE_INSENSITIVE
        )
        val closePattern = Pattern.compile(
            "</$tagName\\s*>",
            Pattern.CASE_INSENSITIVE
        )

        while (depth > 0 && searchPos < html.length) {
            val nextOpen = openPattern.matcher(html).let { m ->
                if (m.find(searchPos)) m.start() else Int.MAX_VALUE
            }
            val closeMatcher = closePattern.matcher(html)
            val nextClose = if (closeMatcher.find(searchPos)) closeMatcher.start() else -1

            if (nextClose < 0) break

            if (nextOpen < nextClose) {
                depth++
                searchPos = nextOpen + tagName.length + 1
            } else {
                depth--
                if (depth == 0) {
                    val endPos = closeMatcher.end()
                    return html.substring(startPos, endPos)
                }
                searchPos = closeMatcher.end()
            }
        }

        // Fallback: return up to a reasonable length
        val maxLen = (startPos + 2000).coerceAtMost(html.length)
        return html.substring(startPos, maxLen)
    }

    private data class SelectorParts(
        val tag: String?,
        val className: String?,
        val id: String?,
        val attrName: String?,
        val attrValue: String?
    )

    private fun parseSelector(selector: String): SelectorParts {
        var tag: String? = null
        var className: String? = null
        var id: String? = null
        var attrName: String? = null
        var attrValue: String? = null

        var s = selector.trim()

        // Extract [attr] or [attr=value]
        val attrMatch = Regex("\\[([^\\]=]+)(?:=([^\\]]+))?\\]").find(s)
        if (attrMatch != null) {
            attrName = attrMatch.groupValues[1].trim()
            val raw = attrMatch.groupValues[2].trim()
            if (raw.isNotEmpty()) {
                attrValue = raw.removeSurrounding("\"").removeSurrounding("'")
            }
            s = s.replace(attrMatch.value, "")
        }

        // Extract #id
        val idMatch = Regex("#([a-zA-Z0-9_-]+)").find(s)
        if (idMatch != null) {
            id = idMatch.groupValues[1]
            s = s.replace(idMatch.value, "")
        }

        // Extract .class
        val classMatch = Regex("\\.([a-zA-Z0-9_-]+)").find(s)
        if (classMatch != null) {
            className = classMatch.groupValues[1]
            s = s.replace(classMatch.value, "")
        }

        // Remaining is the tag name
        s = s.trim()
        if (s.isNotEmpty() && s.matches(Regex("[a-zA-Z][a-zA-Z0-9]*"))) {
            tag = s
        }

        return SelectorParts(tag, className, id, attrName, attrValue)
    }

    private fun extractValue(outerHtml: String, mode: String, attrName: String): String {
        return when (mode) {
            "html" -> outerHtml
            "attr" -> {
                val attrPattern = Pattern.compile(
                    "${Pattern.quote(attrName)}\\s*=\\s*[\"']([^\"']*)[\"']",
                    Pattern.CASE_INSENSITIVE
                )
                val m = attrPattern.matcher(outerHtml)
                if (m.find()) m.group(1) ?: "" else ""
            }
            else -> { // "text"
                // Strip all HTML tags to get inner text
                outerHtml
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
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
