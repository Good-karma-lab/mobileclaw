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

open class OpenAICompatibleProvider(
    override val id: String,
    override val name: String,
    private val apiKey: String,
    private val baseUrl: String
) : Provider {

    override val capabilities: Set<CapabilityType> = setOf(
        CapabilityType.TEXT_CHAT,
        CapabilityType.STREAMING,
        CapabilityType.TOOL_USE
    )

    protected val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val normalizedBaseUrl: String
        get() = baseUrl.trimEnd('/')

    protected open fun buildHeaders(): Map<String, String> = mapOf(
        "Authorization" to "Bearer $apiKey",
        "Content-Type" to "application/json"
    )

    override suspend fun fetchModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url("$normalizedBaseUrl/v1/models")
                .get()
            buildHeaders().forEach { (key, value) -> requestBuilder.addHeader(key, value) }

            val response = client.newCall(requestBuilder.build()).await()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val dataArray = json.optJSONArray("data") ?: return@withContext emptyList()

            val models = mutableListOf<ModelInfo>()
            for (i in 0 until dataArray.length()) {
                val modelObj = dataArray.getJSONObject(i)
                val modelId = modelObj.optString("id", "")
                if (modelId.isNotEmpty()) {
                    models.add(
                        ModelInfo(
                            id = modelId,
                            name = modelId,
                            provider = id,
                            capabilities = capabilities,
                            contextLength = modelObj.optInt("context_length", 4096),
                            maxOutputTokens = modelObj.optInt("max_output_tokens", 0)
                                .takeIf { it > 0 }
                        )
                    )
                }
            }
            models
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<JSONObject>?,
        model: String?,
        temperature: Double
    ): ChatResponse = withContext(Dispatchers.IO) {
        val requestBody = buildChatRequestBody(messages, tools, model, temperature, stream = false)

        val requestBuilder = Request.Builder()
            .url("$normalizedBaseUrl/v1/chat/completions")
            .post(requestBody.toString().toRequestBody(jsonMediaType))
        buildHeaders().forEach { (key, value) -> requestBuilder.addHeader(key, value) }

        val response = client.newCall(requestBuilder.build()).await()
        val body = response.body?.string()
            ?: throw IOException("Empty response body")
        parseChatResponse(JSONObject(body))
    }

    override fun streamChat(
        messages: List<ChatMessage>,
        tools: List<JSONObject>?,
        model: String?,
        temperature: Double
    ): Flow<String> = callbackFlow {
        val requestBody = buildChatRequestBody(messages, tools, model, temperature, stream = true)

        val requestBuilder = Request.Builder()
            .url("$normalizedBaseUrl/v1/chat/completions")
            .post(requestBody.toString().toRequestBody(jsonMediaType))
        buildHeaders().forEach { (key, value) -> requestBuilder.addHeader(key, value) }

        val call = client.newCall(requestBuilder.build())

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val source = response.body?.byteStream()
                        ?: run { close(IOException("Empty response body")); return }
                    val reader = BufferedReader(InputStreamReader(source))

                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: continue
                        if (!currentLine.startsWith("data: ")) continue

                        val data = currentLine.removePrefix("data: ").trim()
                        if (data == "[DONE]") break

                        try {
                            val json = JSONObject(data)
                            val choices = json.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val delta = choices.getJSONObject(0).optJSONObject("delta")
                                val content = delta?.optString("content", "") ?: ""
                                if (content.isNotEmpty()) {
                                    trySend(content)
                                }
                            }
                        } catch (_: Exception) {
                            // Skip malformed JSON chunks
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
            fetchModels().isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    protected open fun buildChatRequestBody(
        messages: List<ChatMessage>,
        tools: List<JSONObject>?,
        model: String?,
        temperature: Double,
        stream: Boolean
    ): JSONObject {
        val body = JSONObject()
        body.put("model", model ?: "gpt-4o")
        body.put("temperature", temperature)
        body.put("stream", stream)

        val messagesArray = JSONArray()
        for (msg in messages) {
            val msgObj = JSONObject()
            msgObj.put("role", msg.role)
            msgObj.put("content", msg.content)
            if (msg.toolCallId != null) {
                msgObj.put("tool_call_id", msg.toolCallId)
            }
            if (msg.name != null) {
                msgObj.put("name", msg.name)
            }
            if (msg.toolCalls != null) {
                val toolCallsArray = JSONArray()
                for (tc in msg.toolCalls) {
                    val tcObj = JSONObject()
                    tcObj.put("id", tc.id)
                    tcObj.put("type", tc.type)
                    val fnObj = JSONObject()
                    fnObj.put("name", tc.function.name)
                    fnObj.put("arguments", tc.function.arguments)
                    tcObj.put("function", fnObj)
                    toolCallsArray.put(tcObj)
                }
                msgObj.put("tool_calls", toolCallsArray)
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

    protected open fun parseChatResponse(json: JSONObject): ChatResponse {
        val choices = json.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            return ChatResponse(content = null, toolCalls = null, finishReason = null, usage = null)
        }

        val choice = choices.getJSONObject(0)
        val message = choice.optJSONObject("message")
        val content = if (message != null && message.has("content") && !message.isNull("content")) message.getString("content") else null
        val finishReason = if (choice.has("finish_reason") && !choice.isNull("finish_reason")) choice.getString("finish_reason") else null

        val toolCalls = parseToolCalls(message)

        val usageObj = json.optJSONObject("usage")
        val usage = if (usageObj != null) {
            TokenUsage(
                promptTokens = usageObj.optInt("prompt_tokens", 0),
                completionTokens = usageObj.optInt("completion_tokens", 0),
                totalTokens = usageObj.optInt("total_tokens", 0)
            )
        } else {
            null
        }

        return ChatResponse(
            content = content,
            toolCalls = toolCalls,
            finishReason = finishReason,
            usage = usage
        )
    }

    private fun parseToolCalls(message: JSONObject?): List<ToolCall>? {
        val toolCallsArray = message?.optJSONArray("tool_calls") ?: return null
        if (toolCallsArray.length() == 0) return null

        val toolCalls = mutableListOf<ToolCall>()
        for (i in 0 until toolCallsArray.length()) {
            val tcObj = toolCallsArray.getJSONObject(i)
            val fnObj = tcObj.optJSONObject("function") ?: continue
            toolCalls.add(
                ToolCall(
                    id = tcObj.optString("id", ""),
                    type = tcObj.optString("type", "function"),
                    function = ToolCallFunction(
                        name = fnObj.optString("name", ""),
                        arguments = fnObj.optString("arguments", "{}")
                    )
                )
            )
        }
        return toolCalls.ifEmpty { null }
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
