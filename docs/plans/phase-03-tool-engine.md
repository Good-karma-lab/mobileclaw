# Phase 3: Tool Engine — Device Tools, App Control, Web Tools, and Agent Capabilities

**Date**: 2026-03-06
**Status**: Proposal
**Depends On**: Phase 1 (Foundation)
**Blocks**: Phase 4 (Proactive Agent), Phase 10 (Live Config)

---

## 1. Objective

Build the complete tool execution engine with **65+ tools** covering:
1. **Device hardware** — SMS, calls, contacts, calendar, camera, sensors
2. **App control** — intents, alarms, timers, email, maps, music
3. **Social media** — Twitter, Instagram, Telegram, WhatsApp
4. **App automation** — AppFunctions, UI Automation Framework (NOT Accessibility)
5. **File system** — read, write, search, document picker
6. **Web tools** — `web_fetch`, `web_search`, `web_scrape`, `browser_session` (MANDATORY)
7. **System** — shell, package manager, device info
8. **AI-powered tools** — image analysis, OCR, translation, code interpreter, calculator
9. **Smart home / IoT** — (future, placeholder)

---

## 2. Research Checklist

- [ ] **Android AppFunctions API** (Feb 2026) — structured API for app-to-agent communication
- [ ] **Android UI Automation Framework** (Feb 2026) — sanctioned agent automation (preview)
- [ ] **DroidRun** (MIT, 3.8k stars) — accessibility tree → structured data for development
- [ ] Android Intent system — all common intents, deep links, share targets
- [ ] `AlarmManager` / `AlarmClock` API — SCHEDULE_EXACT_ALARM denied by default on Android 14+
- [ ] `ContentProvider` — contacts, calendar, call log, SMS, media store
- [ ] `MediaProjection` API — screenshots, screen recording
- [ ] `NotificationListenerService` — reading notifications from other apps
- [ ] Social media deep links — Twitter, Instagram, Telegram, WhatsApp URI schemes
- [ ] **Brave Search API** — for web search tool (free tier: 2K queries/month)
- [ ] **Google Custom Search JSON API** — alternative web search
- [ ] **Jina Reader API** — `r.jina.ai` for converting URLs to clean markdown
- [ ] OkHttp / Ktor for HTTP fetching
- [ ] `WebView` for headless browser sessions
- [ ] Google ML Kit — OCR, barcode scanning, face detection
- [ ] `MediaSession` API — controlling media playback
- [ ] Content resolver patterns for email (Gmail, Exchange)

---

## 3. Architecture

### 3.1 Module Structure

```
android/app/src/main/java/com/guappa/app/
└── tools/
    ├── ToolEngine.kt                — tool registry, schema validation, execution dispatch
    ├── ToolInterface.kt             — base tool interface (name, description, schema, execute)
    ├── ToolResult.kt                — structured result (success/error/needs_approval)
    ├── ToolPermissions.kt           — per-tool Android permission checking + request
    ├── ToolSchemaGenerator.kt       — generate JSON Schema for LLM tool calling
    ├── ToolRateLimiter.kt           — prevent tool spam (max N calls per minute)
    ├── ToolAuditLog.kt              — log all tool executions for review
    │
    ├── device/
    │   ├── SmsTool.kt               — send/read SMS
    │   ├── CallTool.kt              — place/end calls, call log
    │   ├── ContactsTool.kt          — CRUD contacts, search
    │   ├── CalendarTool.kt          — CRUD events, query
    │   ├── CameraTool.kt            — capture photo/video
    │   ├── LocationTool.kt          — GPS, geofencing, geocoding
    │   ├── AudioRecordTool.kt       — record ambient audio
    │   ├── SensorTool.kt            — accelerometer, gyro, proximity, light, step counter
    │   ├── BatteryTool.kt           — battery level, charging state, health
    │   ├── NetworkTool.kt           — WiFi/cellular status, SSID, signal, speed test
    │   ├── BluetoothTool.kt         — scan BLE, connect, read characteristics
    │   ├── NFCTool.kt               — read/write NFC tags
    │   ├── ClipboardTool.kt         — get/set clipboard content
    │   ├── VibrateTool.kt           — haptic patterns
    │   ├── FlashlightTool.kt        — toggle flashlight
    │   ├── ScreenBrightnessTool.kt  — adjust brightness
    │   └── VolumeTool.kt            — media/ring/alarm volume
    │
    ├── apps/
    │   ├── AppLaunchTool.kt         — launch any app by package
    │   ├── AppListTool.kt           — list installed apps with metadata
    │   ├── IntentTool.kt            — fire arbitrary Android Intents
    │   ├── AlarmTool.kt             — set/cancel alarms (AlarmClock Intent)
    │   ├── TimerTool.kt             — countdown timers
    │   ├── ReminderTool.kt          — create reminders
    │   ├── EmailComposeTool.kt      — compose email via Intent
    │   ├── EmailReadTool.kt         — read email via content provider
    │   ├── BrowserOpenTool.kt       — open URL in system browser
    │   ├── MapsTool.kt              — open maps, directions, search places
    │   ├── MusicControlTool.kt      — play/pause/skip via MediaSession
    │   ├── SettingsNavTool.kt       — open specific settings pages
    │   ├── ShareTool.kt             — share content via share sheet
    │   └── DownloadTool.kt          — download file from URL
    │
    ├── social/
    │   ├── TwitterPostTool.kt       — compose tweet via deep link / Intent
    │   ├── InstagramShareTool.kt    — share image to Instagram
    │   ├── TelegramSendTool.kt      — send message via deep link
    │   ├── WhatsAppSendTool.kt      — send message via deep link
    │   └── SocialShareTool.kt       — universal social share (chooser)
    │
    ├── automation/
    │   ├── AppFunctionsClient.kt    — Android AppFunctions API (structured app control)
    │   ├── UIAutomationClient.kt    — Android UI Automation Framework (Google-sanctioned)
    │   ├── DroidRunAdapter.kt       — accessibility tree → structured data (DEV ONLY)
    │   ├── UIAutomationTool.kt      — tap, swipe, scroll, type, gesture
    │   ├── ScreenReaderTool.kt      — read current screen structure + text
    │   ├── AppNavigationTool.kt     — back, home, recents, app switch
    │   ├── FormFillerTool.kt        — fill form fields by label/ID
    │   ├── ScreenshotTool.kt        — capture + analyze screenshot (MediaProjection)
    │   └── NotificationReaderTool.kt — read/dismiss/act on notifications
    │
    ├── files/
    │   ├── FileReadTool.kt          — read file content (text, binary info)
    │   ├── FileWriteTool.kt         — write/create files
    │   ├── FileSearchTool.kt        — search files by name/content/type
    │   ├── DocumentPickerTool.kt    — SAF document picker
    │   ├── MediaGalleryTool.kt      — browse photos/videos from MediaStore
    │   └── PdfReaderTool.kt         — extract text from PDF
    │
    ├── web/                         ★ MANDATORY TOOLS ★
    │   ├── WebFetchTool.kt          — HTTP GET/POST, parse HTML → markdown
    │   ├── WebSearchTool.kt         — search engine queries (Brave/Google)
    │   ├── WebScrapeTool.kt         — extract structured data (CSS selectors, XPath)
    │   ├── WebBrowserSessionTool.kt — interactive WebView session (headless)
    │   ├── RssReaderTool.kt         — parse RSS/Atom feeds
    │   └── WebApiTool.kt            — call REST APIs with JSON
    │
    ├── ai/                          ★ AI-POWERED TOOLS ★
    │   ├── ImageAnalysisTool.kt     — analyze image via vision model
    │   ├── OCRTool.kt               — extract text from image (ML Kit)
    │   ├── TranslationTool.kt       — translate text (ML Kit or LLM)
    │   ├── CalculatorTool.kt        — evaluate math expressions safely
    │   ├── CodeInterpreterTool.kt   — execute code snippets (sandboxed)
    │   ├── SummarizeTool.kt         — summarize long text (LLM-powered)
    │   ├── QRCodeTool.kt            — generate / read QR codes
    │   ├── BarcodeScanTool.kt       — scan barcodes (ML Kit)
    │   └── ImageGenerateTool.kt     — generate image via DALL-E/Imagen/SD
    │
    └── system/
        ├── ShellTool.kt             — execute shell commands (HEAVILY sandboxed)
        ├── SystemInfoTool.kt        — device model, OS, storage, RAM, CPU
        ├── PackageInfoTool.kt       — app info (version, size, permissions)
        ├── WifiManagerTool.kt       — connect/disconnect WiFi networks
        └── DateTimeTool.kt          — current date, time, timezone, calendar math
```

---

## 4. Tool Interface & Schema

### 4.1 Base Interface

```kotlin
interface Tool {
    /** Unique tool name (used in LLM tool_call). */
    val name: String

    /** Human-readable description for LLM system prompt. */
    val description: String

    /** JSON Schema for tool parameters. */
    val parameterSchema: JsonObject

    /** Required Android permissions (empty if none). */
    val requiredPermissions: List<String>

    /** Execute the tool with validated parameters. */
    suspend fun execute(params: JsonObject, context: ToolContext): ToolResult

    /** Whether this tool is currently available (permission granted, hardware present). */
    fun isAvailable(context: ToolContext): Boolean
}

data class ToolContext(
    val appContext: Context,
    val sessionId: String,
    val userId: String?,
    val permissionChecker: ToolPermissions,
    val providerRouter: ProviderRouter,  // for AI-powered tools
    val notificationManager: GuappaNotificationManager,
)

sealed class ToolResult {
    data class Success(
        val content: String,
        val data: JsonObject? = null,     // structured data
        val attachments: List<Attachment> = emptyList(),
    ) : ToolResult()

    data class Error(
        val message: String,
        val code: ToolErrorCode,
        val retryable: Boolean = false,
    ) : ToolResult()

    data class NeedsApproval(
        val description: String,           // what the tool wants to do
        val approvalType: ApprovalType,    // SEND_MESSAGE, MAKE_CALL, SPEND_MONEY, etc.
    ) : ToolResult()
}
```

### 4.2 Schema Generation

Every tool defines its parameters as JSON Schema, which is included in the LLM's tool list:

```kotlin
class ToolSchemaGenerator {
    fun generateToolList(tools: List<Tool>, context: ToolContext): List<ToolSchema> {
        return tools
            .filter { it.isAvailable(context) }
            .map { tool ->
                ToolSchema(
                    type = "function",
                    function = FunctionSchema(
                        name = tool.name,
                        description = tool.description,
                        parameters = tool.parameterSchema,
                    ),
                )
            }
    }
}
```

---

## 5. Mandatory Web Tools (Detailed)

### 5.1 web_fetch — HTTP Request with Content Parsing

```kotlin
class WebFetchTool : Tool {
    override val name = "web_fetch"
    override val description = """
        Fetch content from a URL. Returns the page content as clean markdown text.
        Use this to read web pages, articles, documentation, API responses, etc.
        Supports GET and POST requests. HTML is automatically converted to markdown.
        For JSON APIs, returns formatted JSON.
    """.trimIndent()

    override val parameterSchema = jsonSchema {
        property("url", StringSchema("The URL to fetch (must be valid HTTP/HTTPS)"), required = true)
        property("method", EnumSchema(listOf("GET", "POST"), "HTTP method, default GET"))
        property("headers", ObjectSchema("Additional HTTP headers as key-value pairs"))
        property("body", StringSchema("Request body for POST requests"))
        property("format", EnumSchema(listOf("markdown", "text", "json", "html"), "Output format, default markdown"))
        property("max_length", IntSchema("Maximum response length in characters, default 10000"))
        property("timeout_ms", IntSchema("Request timeout in milliseconds, default 30000"))
        property("follow_redirects", BoolSchema("Follow HTTP redirects, default true"))
    }

    override val requiredPermissions = listOf(Manifest.permission.INTERNET)

    override suspend fun execute(params: JsonObject, context: ToolContext): ToolResult {
        val url = params["url"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("URL is required", ToolErrorCode.INVALID_PARAMS)

        // Validate URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult.Error("URL must start with http:// or https://", ToolErrorCode.INVALID_PARAMS)
        }

        val method = params["method"]?.jsonPrimitive?.content ?: "GET"
        val maxLength = params["max_length"]?.jsonPrimitive?.int ?: 10_000
        val timeoutMs = params["timeout_ms"]?.jsonPrimitive?.long ?: 30_000L
        val format = params["format"]?.jsonPrimitive?.content ?: "markdown"

        try {
            val request = Request.Builder().url(url)
            if (method == "POST") {
                val body = params["body"]?.jsonPrimitive?.content ?: ""
                request.post(body.toRequestBody("application/json".toMediaType()))
            }
            // Add custom headers
            params["headers"]?.jsonObject?.forEach { (key, value) ->
                request.addHeader(key, value.jsonPrimitive.content)
            }

            val client = OkHttpClient.Builder()
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .followRedirects(params["follow_redirects"]?.jsonPrimitive?.boolean ?: true)
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request.build()).execute()
            }

            val contentType = response.header("Content-Type") ?: ""
            val rawBody = response.body?.string() ?: ""

            val content = when {
                format == "json" || contentType.contains("json") -> {
                    // Pretty-print JSON
                    try { Json.parseToJsonElement(rawBody).toString() } catch (e: Exception) { rawBody }
                }
                format == "html" -> rawBody.take(maxLength)
                format == "text" -> {
                    // Strip all HTML tags
                    Jsoup.parse(rawBody).text().take(maxLength)
                }
                else -> {
                    // Convert HTML to Markdown
                    val doc = Jsoup.parse(rawBody)
                    htmlToMarkdown(doc).take(maxLength)
                }
            }

            return ToolResult.Success(
                content = "HTTP ${response.code} — ${url}\n\n$content",
                data = buildJsonObject {
                    put("status_code", response.code)
                    put("content_type", contentType)
                    put("content_length", rawBody.length)
                    put("url", response.request.url.toString())  // after redirects
                },
            )
        } catch (e: SocketTimeoutException) {
            return ToolResult.Error("Request timed out after ${timeoutMs}ms", ToolErrorCode.TIMEOUT, retryable = true)
        } catch (e: Exception) {
            return ToolResult.Error("Fetch failed: ${e.message}", ToolErrorCode.NETWORK_ERROR, retryable = true)
        }
    }

    private fun htmlToMarkdown(doc: Document): String {
        // Use a library like flexmark-java or manual conversion
        // Extract main content, convert headings, links, lists, code blocks
        val article = doc.selectFirst("article, main, .content, #content, .post-body") ?: doc.body()
        return convertToMarkdown(article)
    }
}
```

### 5.2 web_search — Search Engine Query

```kotlin
class WebSearchTool : Tool {
    override val name = "web_search"
    override val description = """
        Search the web for information. Returns search results with titles, URLs, and snippets.
        Use this to find current information, news, facts, documentation, etc.
        Supports Brave Search API and Google Custom Search as backends.
    """.trimIndent()

    override val parameterSchema = jsonSchema {
        property("query", StringSchema("Search query"), required = true)
        property("num_results", IntSchema("Number of results to return, default 5, max 20"))
        property("search_type", EnumSchema(listOf("web", "news", "images"), "Type of search, default web"))
        property("time_range", EnumSchema(listOf("day", "week", "month", "year"), "Time range filter"))
        property("region", StringSchema("Country code for localized results, e.g., 'ru', 'us'"))
        property("safe_search", BoolSchema("Enable safe search filter, default true"))
    }

    override val requiredPermissions = listOf(Manifest.permission.INTERNET)

    override suspend fun execute(params: JsonObject, context: ToolContext): ToolResult {
        val query = params["query"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Query is required", ToolErrorCode.INVALID_PARAMS)

        val numResults = params["num_results"]?.jsonPrimitive?.int ?: 5
        val searchType = params["search_type"]?.jsonPrimitive?.content ?: "web"
        val timeRange = params["time_range"]?.jsonPrimitive?.content
        val region = params["region"]?.jsonPrimitive?.content

        // Try Brave Search first, fall back to Google Custom Search
        val results = try {
            braveSearch(query, numResults, searchType, timeRange, region)
        } catch (e: Exception) {
            try {
                googleCustomSearch(query, numResults, searchType)
            } catch (e2: Exception) {
                return ToolResult.Error("Search failed: ${e.message}", ToolErrorCode.NETWORK_ERROR, retryable = true)
            }
        }

        val formatted = results.mapIndexed { i, result ->
            "${i + 1}. **${result.title}**\n   ${result.url}\n   ${result.snippet}\n"
        }.joinToString("\n")

        return ToolResult.Success(
            content = "Search results for \"$query\":\n\n$formatted",
            data = buildJsonObject {
                put("query", query)
                put("result_count", results.size)
            },
        )
    }

    private suspend fun braveSearch(
        query: String, limit: Int, type: String, timeRange: String?, region: String?,
    ): List<SearchResult> {
        val apiKey = config.braveSearchApiKey
        if (apiKey.isBlank()) throw Exception("Brave Search API key not configured")

        val url = "https://api.search.brave.com/res/v1/$type/search"
        val response = httpClient.get(url) {
            header("X-Subscription-Token", apiKey)
            parameter("q", query)
            parameter("count", limit)
            if (timeRange != null) parameter("freshness", timeRange)
            if (region != null) parameter("country", region)
        }
        // Parse response...
    }
}
```

### 5.3 web_scrape — Structured Data Extraction

```kotlin
class WebScrapeTool : Tool {
    override val name = "web_scrape"
    override val description = """
        Extract structured data from a web page using CSS selectors or XPath.
        Use this when you need specific elements from a page (prices, titles, lists, tables).
        Returns extracted data as structured JSON.
    """.trimIndent()

    override val parameterSchema = jsonSchema {
        property("url", StringSchema("URL to scrape"), required = true)
        property("selectors", ObjectSchema("CSS selectors to extract, e.g., {\"title\": \"h1\", \"price\": \".price\"}"), required = true)
        property("extract", EnumSchema(listOf("text", "html", "attr"), "What to extract from selected elements"))
        property("attr_name", StringSchema("Attribute name when extract=attr (e.g., 'href', 'src')"))
        property("list", BoolSchema("If true, return all matching elements as array"))
    }

    override suspend fun execute(params: JsonObject, context: ToolContext): ToolResult {
        val url = params["url"]?.jsonPrimitive?.content ?: return ToolResult.Error("URL required", ToolErrorCode.INVALID_PARAMS)
        val selectors = params["selectors"]?.jsonObject ?: return ToolResult.Error("Selectors required", ToolErrorCode.INVALID_PARAMS)

        val doc = withContext(Dispatchers.IO) {
            Jsoup.connect(url)
                .userAgent("Guappa/1.0")
                .timeout(30_000)
                .get()
        }

        val results = buildJsonObject {
            for ((key, selector) in selectors) {
                val selectorStr = selector.jsonPrimitive.content
                val elements = doc.select(selectorStr)
                // Extract text/html/attr based on config...
                put(key, elements.first()?.text() ?: "")
            }
        }

        return ToolResult.Success(
            content = results.toString(),
            data = results,
        )
    }
}
```

### 5.4 browser_session — Interactive WebView Session

```kotlin
class WebBrowserSessionTool : Tool {
    override val name = "browser_session"
    override val description = """
        Start an interactive browser session using WebView. Navigate pages, fill forms,
        click buttons, execute JavaScript. Use this for complex web interactions that
        require multiple steps (login, form submission, dynamic content).
    """.trimIndent()

    override val parameterSchema = jsonSchema {
        property("action", EnumSchema(listOf("navigate", "click", "type", "scroll", "screenshot", "get_content", "execute_js")), required = true)
        property("url", StringSchema("URL to navigate to (for 'navigate' action)"))
        property("selector", StringSchema("CSS selector for element (for click/type)"))
        property("text", StringSchema("Text to type (for 'type' action)"))
        property("js", StringSchema("JavaScript to execute (for 'execute_js')"))
    }
    // Implementation uses Android WebView with JavaScript bridge
}
```

---

## 6. AI-Powered Tools (Detailed)

### 6.1 image_analyze — Vision Model Analysis

```kotlin
class ImageAnalysisTool : Tool {
    override val name = "image_analyze"
    override val description = """
        Analyze an image using a vision-capable AI model. Can describe content,
        read text, identify objects, answer questions about the image.
    """.trimIndent()

    override val parameterSchema = jsonSchema {
        property("image_source", EnumSchema(listOf("camera", "gallery", "file", "url", "screenshot")), required = true)
        property("question", StringSchema("Question to answer about the image"), required = true)
        property("file_path", StringSchema("File path (for 'file' source)"))
        property("url", StringSchema("Image URL (for 'url' source)"))
    }

    override suspend fun execute(params: JsonObject, context: ToolContext): ToolResult {
        val source = params["image_source"]!!.jsonPrimitive.content
        val question = params["question"]!!.jsonPrimitive.content

        val imageBytes = when (source) {
            "camera" -> capturePhoto(context)
            "gallery" -> pickFromGallery(context)
            "file" -> readFile(params["file_path"]!!.jsonPrimitive.content)
            "url" -> downloadImage(params["url"]!!.jsonPrimitive.content)
            "screenshot" -> takeScreenshot(context)
            else -> return ToolResult.Error("Invalid source", ToolErrorCode.INVALID_PARAMS)
        }

        // Route to vision-capable model
        val response = context.providerRouter.chatWithCapability(
            ModelCapability.VISION,
            messages = listOf(
                ChatMessage.user(
                    content = listOf(
                        ContentPart.text(question),
                        ContentPart.image(imageBytes, "image/jpeg"),
                    )
                )
            ),
        )

        return ToolResult.Success(response.text)
    }
}
```

### 6.2 ocr — Optical Character Recognition

```kotlin
class OCRTool : Tool {
    override val name = "ocr"
    override val description = "Extract text from an image using OCR. Supports photos, screenshots, documents."

    override suspend fun execute(params: JsonObject, context: ToolContext): ToolResult {
        // Uses Google ML Kit Text Recognition
        val imageSource = /* get image from params */
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(inputImage).await()
        return ToolResult.Success(result.text)
    }
}
```

### 6.3 calculator — Safe Math Evaluation

```kotlin
class CalculatorTool : Tool {
    override val name = "calculator"
    override val description = """
        Evaluate a mathematical expression. Supports basic arithmetic, trigonometry,
        logarithms, percentages, unit conversions. No arbitrary code execution.
    """.trimIndent()

    override val parameterSchema = jsonSchema {
        property("expression", StringSchema("Math expression, e.g., '(15.5 * 2) + sqrt(144)'"), required = true)
    }

    override suspend fun execute(params: JsonObject, context: ToolContext): ToolResult {
        val expr = params["expression"]!!.jsonPrimitive.content
        // Use a safe math parser (exp4j or similar) — NO eval()
        val result = MathParser.evaluate(expr)
        return ToolResult.Success("$expr = $result")
    }
}
```

### 6.4 code_interpreter — Sandboxed Code Execution

```kotlin
class CodeInterpreterTool : Tool {
    override val name = "code_interpreter"
    override val description = """
        Execute a code snippet in a sandboxed environment. Supports Python and JavaScript.
        Use for data processing, calculations, text manipulation, format conversion.
        No file system access, no network access, execution timeout 30 seconds.
    """.trimIndent()

    override val parameterSchema = jsonSchema {
        property("language", EnumSchema(listOf("python", "javascript")), required = true)
        property("code", StringSchema("Code to execute"), required = true)
    }

    override suspend fun execute(params: JsonObject, context: ToolContext): ToolResult {
        val language = params["language"]!!.jsonPrimitive.content
        val code = params["code"]!!.jsonPrimitive.content

        // Execute in sandboxed WebView (JavaScript) or Chaquopy (Python)
        // with strict timeout and no I/O access
        val result = SandboxedExecutor.execute(
            language = language,
            code = code,
            timeoutMs = 30_000,
            allowNetwork = false,
            allowFileSystem = false,
        )

        return ToolResult.Success("```\n${result.output}\n```")
    }
}
```

### 6.5 translate — Text Translation

```kotlin
class TranslationTool : Tool {
    override val name = "translate"
    override val description = "Translate text between languages. Uses on-device ML Kit (free) or cloud LLM."

    override val parameterSchema = jsonSchema {
        property("text", StringSchema("Text to translate"), required = true)
        property("target_language", StringSchema("Target language code, e.g., 'en', 'ru', 'ja'"), required = true)
        property("source_language", StringSchema("Source language code (auto-detect if omitted)"))
    }

    override suspend fun execute(params: JsonObject, context: ToolContext): ToolResult {
        // Try ML Kit first (free, on-device), fall back to LLM
        val text = params["text"]!!.jsonPrimitive.content
        val target = params["target_language"]!!.jsonPrimitive.content

        return try {
            val translator = Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(/* auto or specified */)
                    .setTargetLanguage(target)
                    .build()
            )
            translator.downloadModelIfNeeded().await()
            val result = translator.translate(text).await()
            ToolResult.Success(result)
        } catch (e: Exception) {
            // Fallback to LLM
            val result = context.providerRouter.chatSimple(
                userMessage = "Translate to $target: $text",
            )
            ToolResult.Success(result.text)
        }
    }
}
```

### 6.6 image_generate — AI Image Generation

```kotlin
class ImageGenerateTool : Tool {
    override val name = "image_generate"
    override val description = "Generate an image from a text description using DALL-E, Imagen, or Stable Diffusion."

    override val parameterSchema = jsonSchema {
        property("prompt", StringSchema("Image description"), required = true)
        property("size", EnumSchema(listOf("256x256", "512x512", "1024x1024")), "Image size")
        property("style", EnumSchema(listOf("natural", "vivid")), "Image style")
    }

    override suspend fun execute(params: JsonObject, context: ToolContext): ToolResult {
        val prompt = params["prompt"]!!.jsonPrimitive.content
        // Route to IMAGE_GENERATION capable provider
        val response = context.providerRouter.generateImage(
            ImageGenRequest(prompt = prompt, size = /* ... */)
        )
        return ToolResult.Success(
            content = "Image generated: ${response.url}",
            attachments = listOf(Attachment.image(response.url)),
        )
    }
}
```

---

## 7. Complete Tool Catalog (65+ tools)

### 7.1 Device Tools (17)

| # | Tool | Description | Permission | Risk |
|---|------|-------------|-----------|------|
| 1 | `send_sms` | Send SMS message | SEND_SMS | High (charges may apply) |
| 2 | `read_sms` | Read SMS inbox/sent | READ_SMS | Medium |
| 3 | `place_call` | Make phone call | CALL_PHONE | High |
| 4 | `read_call_log` | Read call history | READ_CALL_LOG | Medium |
| 5 | `read_contacts` | Search/read contacts | READ_CONTACTS | Medium |
| 6 | `write_contacts` | Create/update contacts | WRITE_CONTACTS | Medium |
| 7 | `read_calendar` | Read calendar events | READ_CALENDAR | Low |
| 8 | `write_calendar` | Create/update events | WRITE_CALENDAR | Medium |
| 9 | `take_photo` | Capture photo | CAMERA | Low |
| 10 | `get_location` | Current GPS coordinates | ACCESS_FINE_LOCATION | Medium |
| 11 | `record_audio` | Record ambient audio | RECORD_AUDIO | High |
| 12 | `read_sensors` | Accelerometer, gyro, etc. | — | Low |
| 13 | `get_battery` | Battery status | — | Low |
| 14 | `get_network` | Network status | — | Low |
| 15 | `scan_bluetooth` | Scan BLE devices | BLUETOOTH_SCAN | Low |
| 16 | `read_nfc` | Read NFC tag | NFC | Low |
| 17 | `write_nfc` | Write NFC tag | NFC | Medium |

### 7.2 App Control Tools (14)

| # | Tool | Description | Mechanism |
|---|------|-------------|-----------|
| 18 | `launch_app` | Open any installed app | PackageManager + Intent |
| 19 | `list_apps` | List installed apps | PackageManager |
| 20 | `fire_intent` | Fire arbitrary Intent | Custom Intent builder |
| 21 | `set_alarm` | Set alarm at time | AlarmClock Intent (EXTRA_SKIP_UI) |
| 22 | `set_timer` | Set countdown timer | AlarmClock Timer Intent |
| 23 | `create_reminder` | Create reminder | CalendarContract or Keep deep link |
| 24 | `compose_email` | Open email compose | ACTION_SENDTO Intent |
| 25 | `read_email` | Read latest email | ContentProvider / Gmail API |
| 26 | `open_url` | Open URL in browser | ACTION_VIEW Intent |
| 27 | `navigate_to` | Maps navigation | geo: Intent |
| 28 | `control_music` | Play/pause/skip media | MediaSession |
| 29 | `open_settings` | Open settings page | Settings Intent |
| 30 | `share_content` | Share to any app | ACTION_SEND + chooser |
| 31 | `download_file` | Download file from URL | DownloadManager |

### 7.3 Social Media Tools (5)

| # | Tool | Description |
|---|------|-------------|
| 32 | `post_tweet` | Post to Twitter/X |
| 33 | `share_instagram` | Share image to Instagram |
| 34 | `send_telegram` | Send Telegram message |
| 35 | `send_whatsapp` | Send WhatsApp message |
| 36 | `social_share` | Universal social share |

### 7.4 App Automation Tools (7)

| # | Tool | Description | Mechanism |
|---|------|-------------|-----------|
| 37 | `ui_tap` | Tap by text/coordinates | UI Automation Framework |
| 38 | `ui_swipe` | Swipe gesture | UI Automation Framework |
| 39 | `ui_type_text` | Type text | UI Automation Framework |
| 40 | `read_screen` | Read screen content | UI Automation Framework |
| 41 | `navigate_app` | Back/home/recents | AppFunctions / system nav |
| 42 | `fill_form` | Fill form fields | AppFunctions / UI Automation |
| 43 | `take_screenshot` | Capture + analyze screenshot | MediaProjection |

### 7.5 File Tools (6)

| # | Tool | Description |
|---|------|-------------|
| 44 | `read_file` | Read file content |
| 45 | `write_file` | Write/create file |
| 46 | `search_files` | Search files by name/content |
| 47 | `pick_document` | SAF document picker |
| 48 | `browse_media` | Browse photos/videos |
| 49 | `read_pdf` | Extract text from PDF |

### 7.6 Web Tools (6) ★ MANDATORY ★

| # | Tool | Description | Backend |
|---|------|-------------|---------|
| 50 | `web_fetch` | Fetch URL content (HTML→markdown) | OkHttp + Jsoup |
| 51 | `web_search` | Search the web | Brave Search / Google CSE |
| 52 | `web_scrape` | Extract structured data | Jsoup CSS selectors |
| 53 | `browser_session` | Interactive WebView | Android WebView |
| 54 | `read_rss` | Parse RSS/Atom feeds | XML parser |
| 55 | `call_api` | Call REST API | OkHttp |

### 7.7 AI-Powered Tools (9) ★ NEW ★

| # | Tool | Description | Backend |
|---|------|-------------|---------|
| 56 | `image_analyze` | Analyze image with AI | Vision model |
| 57 | `ocr` | Extract text from image | ML Kit |
| 58 | `translate` | Translate text | ML Kit / LLM |
| 59 | `calculator` | Math evaluation | exp4j |
| 60 | `code_interpreter` | Execute code (sandbox) | WebView JS / Chaquopy |
| 61 | `summarize` | Summarize long text | LLM |
| 62 | `qr_code` | Generate/read QR codes | ZXing |
| 63 | `scan_barcode` | Scan barcodes | ML Kit |
| 64 | `generate_image` | Generate image from text | DALL-E / Imagen |

### 7.8 System Tools (5)

| # | Tool | Description |
|---|------|-------------|
| 65 | `run_shell` | Execute shell command (sandboxed) |
| 66 | `system_info` | Device model, OS, storage, RAM |
| 67 | `package_info` | App info (version, size) |
| 68 | `manage_wifi` | Connect/disconnect WiFi |
| 69 | `date_time` | Current date, time, timezone |

**Total: 69 tools**

---

## 8. Tool Permission & Safety System

### 8.1 Risk Levels

```kotlin
enum class ToolRiskLevel {
    LOW,      // read-only, no cost, no privacy (battery, sensors, time)
    MEDIUM,   // reads personal data (contacts, calendar, SMS)
    HIGH,     // sends data, costs money, records audio (send_sms, place_call)
    CRITICAL, // shell execution, package install
}
```

### 8.2 Approval Flow

```kotlin
class ToolPermissions {
    /**
     * Check if tool can execute without user approval.
     *
     * LOW risk tools: auto-approved
     * MEDIUM risk tools: auto-approved after first grant per session
     * HIGH risk tools: require approval every time (unless "always allow" set)
     * CRITICAL risk tools: always require approval
     */
    suspend fun checkPermission(tool: Tool, context: ToolContext): PermissionResult {
        // 1. Check Android permission
        for (permission in tool.requiredPermissions) {
            if (ContextCompat.checkSelfPermission(context.appContext, permission)
                != PackageManager.PERMISSION_GRANTED) {
                return PermissionResult.AndroidPermissionNeeded(permission)
            }
        }

        // 2. Check tool-level approval
        val riskLevel = getToolRiskLevel(tool)
        return when (riskLevel) {
            ToolRiskLevel.LOW -> PermissionResult.Approved
            ToolRiskLevel.MEDIUM -> checkSessionGrant(tool, context)
            ToolRiskLevel.HIGH -> checkExplicitApproval(tool, context)
            ToolRiskLevel.CRITICAL -> PermissionResult.NeedsApproval(
                "Tool '${tool.name}' requires explicit approval for each use"
            )
        }
    }
}
```

### 8.3 Shell Tool Sandboxing

```kotlin
class ShellTool : Tool {
    override val name = "run_shell"

    // Whitelist of allowed commands
    private val allowedCommands = setOf(
        "ls", "cat", "head", "tail", "wc", "grep", "find", "du",
        "date", "uptime", "whoami", "df", "free", "top",
        "ping", "nslookup", "curl",  // network diagnostics
    )

    // Blacklist of dangerous patterns
    private val blockedPatterns = listOf(
        Regex("rm\\s+-rf"), Regex("mkfs"), Regex("dd\\s+if="),
        Regex("chmod\\s+777"), Regex(">(\\s*/dev/)"), Regex("\\|\\s*sh"),
        Regex("su\\s"), Regex("sudo\\s"),
    )

    override suspend fun execute(params: JsonObject, context: ToolContext): ToolResult {
        val command = params["command"]!!.jsonPrimitive.content

        // Validate
        val firstWord = command.trim().split("\\s+".toRegex()).first()
        if (firstWord !in allowedCommands) {
            return ToolResult.Error("Command '$firstWord' not in allowlist", ToolErrorCode.PERMISSION_DENIED)
        }
        for (pattern in blockedPatterns) {
            if (pattern.containsMatchIn(command)) {
                return ToolResult.Error("Blocked dangerous pattern", ToolErrorCode.PERMISSION_DENIED)
            }
        }

        // Execute with timeout
        val process = withContext(Dispatchers.IO) {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        }
        val completed = process.waitFor(30, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            return ToolResult.Error("Command timed out after 30s", ToolErrorCode.TIMEOUT)
        }

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()

        return ToolResult.Success(
            content = if (stderr.isNotBlank()) "STDOUT:\n$stdout\nSTDERR:\n$stderr" else stdout,
        )
    }
}
```

---

## 9. Tool Rate Limiting

```kotlin
class ToolRateLimiter {
    private val callCounts = ConcurrentHashMap<String, MutableList<Long>>()

    data class RateLimit(
        val maxCallsPerMinute: Int = 30,
        val maxCallsPerHour: Int = 200,
        val maxConcurrent: Int = 3,
    )

    // Per-tool rate limits
    private val limits = mapOf(
        "web_search" to RateLimit(10, 50, 2),      // API quota
        "web_fetch" to RateLimit(20, 100, 3),        // be polite
        "send_sms" to RateLimit(5, 20, 1),           // prevent spam
        "place_call" to RateLimit(2, 10, 1),         // prevent spam
        "run_shell" to RateLimit(10, 50, 1),         // safety
        "generate_image" to RateLimit(5, 20, 1),     // cost
    )

    fun checkLimit(toolName: String): Boolean {
        val limit = limits[toolName] ?: RateLimit()
        val calls = callCounts.getOrPut(toolName) { mutableListOf() }
        val now = System.currentTimeMillis()

        // Remove old entries
        calls.removeAll { now - it > 3_600_000 }

        val lastMinute = calls.count { now - it < 60_000 }
        if (lastMinute >= limit.maxCallsPerMinute) return false
        if (calls.size >= limit.maxCallsPerHour) return false

        calls.add(now)
        return true
    }
}
```

---

## 10. Configuration

```kotlin
data class ToolEngineConfig(
    // Web tools
    val braveSearchApiKey: String = "",
    val googleSearchApiKey: String = "",
    val googleSearchEngineId: String = "",
    val webFetchTimeout: Long = 30_000,
    val webFetchMaxLength: Int = 10_000,
    val webFetchUserAgent: String = "Guappa/1.0",

    // Tool enablement (user can toggle each tool)
    val enabledTools: Set<String> = ALL_TOOLS,
    val disabledTools: Set<String> = emptySet(),

    // Safety
    val requireApprovalForHighRisk: Boolean = true,
    val shellCommandWhitelist: Set<String> = DEFAULT_SHELL_WHITELIST,
    val maxToolCallsPerTurn: Int = 10,
    val maxToolCallsPerMinute: Int = 30,

    // AI tools
    val enableImageAnalysis: Boolean = true,
    val enableCodeInterpreter: Boolean = true,
    val enableImageGeneration: Boolean = true,
    val codeInterpreterTimeout: Long = 30_000,
)
```

---

## 11. Test Plan

### 11.1 Unit Tests (per tool)

Each tool needs:
- Valid input → correct result
- Missing required params → error
- Invalid params → error
- Missing permission → permission needed result
- Rate limit exceeded → rate limit error

### 11.2 Key Integration Tests

| Test | Description |
|------|-------------|
| `WebFetch_RealURL` | Fetch a known URL → markdown content |
| `WebSearch_Query` | Search "weather Moscow" → results with URLs |
| `WebScrape_Table` | Scrape a table from Wikipedia → structured data |
| `AlarmTool_SetsAlarm` | Set alarm → verify via AlarmManager query |
| `SmsTool_SendsMessage` | Send SMS → verify in sent folder |
| `ToolEngine_MultiTool` | LLM calls 3 tools → all execute → results fed back |
| `ToolEngine_RateLimit` | 31 calls in 1 minute → 31st blocked |
| `ShellTool_Blocked` | `rm -rf /` → blocked |
| `ShellTool_Allowed` | `ls /sdcard` → file listing |

### 11.3 Maestro E2E

```yaml
# "Set alarm for 7am" → alarm actually set
- inputText: "Поставь будильник на 7 утра"
- tapOn: "Send"
- assertVisible: "будильник"
- assertVisible: "7:00"

# "Search for pizza near me" → web search results shown
- inputText: "Найди пиццерии рядом"
- tapOn: "Send"
- assertVisible: "результат"
```

---

## 12. Acceptance Criteria

- [ ] All 69 tools implemented with correct JSON Schema
- [ ] web_fetch converts HTML to readable markdown
- [ ] web_search returns results from Brave Search or Google CSE
- [ ] web_scrape extracts data by CSS selectors
- [ ] browser_session navigates pages and fills forms
- [ ] image_analyze works via vision-capable model
- [ ] OCR extracts text from photos
- [ ] calculator evaluates math safely (no eval())
- [ ] code_interpreter executes in sandbox with timeout
- [ ] All high-risk tools require user approval
- [ ] Shell tool blocks dangerous commands
- [ ] Rate limiting prevents tool spam
- [ ] All tools return structured ToolResult (never throw)
- [ ] Tool audit log records all executions
