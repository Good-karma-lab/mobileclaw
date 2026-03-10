package com.guappa.app.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

class AnthropicProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.anthropic.com"
) : Provider {

    override val id: String = "anthropic"
    override val name: String = "Anthropic"

    override val capabilities: Set<CapabilityType> = setOf(
        CapabilityType.TEXT_CHAT,
        CapabilityType.STREAMING,
        CapabilityType.TOOL_USE,
        CapabilityType.VISION,
        CapabilityType.CODE,
        CapabilityType.REASONING
    )

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val normalizedBaseUrl: String get() = baseUrl.trimEnd('/')

    private val hardcodedModels = listOf(
        ModelInfo(
            id = "claude-sonnet-4-20250514",
            name = "Claude Sonnet 4",
            provider = id,
            capabilities = capabilities,
            contextLength = 200_000,
            maxOutputTokens = 16_384
        ),
        ModelInfo(
            id = "claude-haiku-4-5-20251001",
            name = "Claude Haiku 4.5",
            provider = id,
            capabilities = capabilities,
            contextLength = 200_000,
            maxOutputTokens = 16_384
        ),
        ModelInfo(
            id = "claude-opus-4-20250514",
            name = "Claude Opus 4",
            provider = id,
            capabilities = capabilities,
            contextLength = 200_000,
            maxOutputTokens = 32_000
        )
    )

    override suspend fun fetchModels(): List<ModelInfo> = hardcodedModels

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<JSONObject>?,
        model: String?,
        temperature: Double
    ): ChatResponse = withContext(Dispatchers.IO) {
        val requestBody = buildRequestBody(messages, tools, model, temperature, stream = false)

        val request = Request.Builder()
            .url("$normalizedBaseUrl/v1/messages")
            .post(requestBody.toString().toRequestBody(jsonMediaType))
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).await()
        val body = response.body?.string()
            ?: throw IOException("Empty response body")
        parseResponse(JSONObject(body))
    }

    override fun streamChat(
        messages: List<ChatMessage>,
        tools: List<JSONObject>?,
        model: String?,
        temperature: Double
    ): Flow<String> = callbackFlow {
        val requestBody = buildRequestBody(messages, tools, model, temperature, stream = true)

        val request = Request.Builder()
            .url("$normalizedBaseUrl/v1/messages")
            .post(requestBody.toString().toRequestBody(jsonMediaType))
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .build()

        val call = client.newCall(request)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val source = response.body?.byteStream()
                        ?: run { close(IOException("Empty response body")); return }
                    val reader = BufferedReader(InputStreamReader(source))

                    var eventType = ""
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: continue

                        if (currentLine.startsWith("event: ")) {
                            eventType = currentLine.removePrefix("event: ").trim()
                            continue
                        }

                        if (!currentLine.startsWith("data: ")) continue

                        val data = currentLine.removePrefix("data: ").trim()

                        if (eventType == "message_stop") break

                        if (eventType == "content_block_delta") {
                            try {
                                val json = JSONObject(data)
                                val delta = json.optJSONObject("delta")
                                val text = delta?.optString("text", "") ?: ""
                                if (text.isNotEmpty()) {
                                    trySend(text)
                                }
                            } catch (_: Exception) {
                                // Skip malformed JSON chunks
                            }
                        }
                    }

                    reader.close()
                    close()
                } catch (e: Exception) {
                    close(e)
                }
            }
        })

        awaitClose { call.cancel() }
    }

    override suspend fun healthCheck(): Boolean {
        return try {
            // Anthropic doesn't have a models endpoint; do a minimal chat request
            val testMessages = listOf(ChatMessage(role = "user", content = "ping"))
            val response = chat(testMessages, model = "claude-haiku-4-5-20251001", temperature = 0.0)
            response.content != null
        } catch (e: Exception) {
            false
        }
    }

    private fun buildRequestBody(
        messages: List<ChatMessage>,
        tools: List<JSONObject>?,
        model: String?,
        temperature: Double,
        stream: Boolean
    ): JSONObject {
        val body = JSONObject()
        body.put("model", model ?: "claude-sonnet-4-20250514")
        body.put("max_tokens", 4096)
        body.put("temperature", temperature)
        body.put("stream", stream)

        // Extract system message (Anthropic uses a separate system field)
        val systemMessage = messages.firstOrNull { it.role == "system" }
        if (systemMessage != null) {
            body.put("system", systemMessage.content)
        }

        // Build messages array excluding system messages
        val messagesArray = JSONArray()
        for (msg in messages) {
            if (msg.role == "system") continue

            val msgObj = JSONObject()
            when (msg.role) {
                "tool" -> {
                    msgObj.put("role", "user")
                    val contentArray = JSONArray()
                    val toolResultObj = JSONObject()
                    toolResultObj.put("type", "tool_result")
                    toolResultObj.put("tool_use_id", msg.toolCallId ?: "")
                    toolResultObj.put("content", msg.content)
                    contentArray.put(toolResultObj)
                    msgObj.put("content", contentArray)
                }
                else -> {
                    msgObj.put("role", msg.role)
                    // Build content: multipart array if images present, plain string otherwise
                    if (msg.hasImages && msg.contentParts != null) {
                        val contentArray = JSONArray()
                        for (part in msg.contentParts) {
                            when (part) {
                                is ContentPart.TextPart -> {
                                    contentArray.put(JSONObject().apply {
                                        put("type", "text")
                                        put("text", part.text)
                                    })
                                }
                                is ContentPart.ImagePart -> {
                                    contentArray.put(JSONObject().apply {
                                        put("type", "image")
                                        put("source", JSONObject().apply {
                                            put("type", "base64")
                                            put("media_type", part.mimeType)
                                            put("data", part.base64)
                                        })
                                    })
                                }
                                is ContentPart.ImageUrlPart -> {
                                    contentArray.put(JSONObject().apply {
                                        put("type", "image")
                                        put("source", JSONObject().apply {
                                            put("type", "url")
                                            put("url", part.url)
                                        })
                                    })
                                }
                            }
                        }
                        msgObj.put("content", contentArray)
                    } else {
                        msgObj.put("content", msg.content)
                    }
                }
            }
            messagesArray.put(msgObj)
        }
        body.put("messages", messagesArray)

        if (!tools.isNullOrEmpty()) {
            val toolsArray = JSONArray()
            for (tool in tools) {
                toolsArray.put(tool)
            }
            body.put("tools", toolsArray)
        }

        return body
    }

    private fun parseResponse(json: JSONObject): ChatResponse {
        val contentArray = json.optJSONArray("content")
        var textContent: String? = null
        val toolCalls = mutableListOf<ToolCall>()

        if (contentArray != null) {
            for (i in 0 until contentArray.length()) {
                val block = contentArray.getJSONObject(i)
                when (block.optString("type")) {
                    "text" -> {
                        textContent = block.optString("text", "")
                    }
                    "tool_use" -> {
                        toolCalls.add(
                            ToolCall(
                                id = block.optString("id", ""),
                                type = "function",
                                function = ToolCallFunction(
                                    name = block.optString("name", ""),
                                    arguments = block.optJSONObject("input")?.toString() ?: "{}"
                                )
                            )
                        )
                    }
                }
            }
        }

        val usageObj = json.optJSONObject("usage")
        val usage = if (usageObj != null) {
            TokenUsage(
                promptTokens = usageObj.optInt("input_tokens", 0),
                completionTokens = usageObj.optInt("output_tokens", 0),
                totalTokens = usageObj.optInt("input_tokens", 0) + usageObj.optInt("output_tokens", 0)
            )
        } else {
            null
        }

        return ChatResponse(
            content = textContent,
            toolCalls = toolCalls.ifEmpty { null },
            finishReason = if (json.has("stop_reason")) json.getString("stop_reason") else null,
            usage = usage
        )
    }
}

/**
 * Extension to make OkHttp Call suspendable.
 */
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
        } catch (_: Exception) {
            // Ignore cancellation exceptions
        }
    }
}
