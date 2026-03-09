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

class GoogleGeminiProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com"
) : Provider {

    override val id: String = "gemini"
    override val name: String = "Google Gemini"

    override val capabilities: Set<CapabilityType> = setOf(
        CapabilityType.TEXT_CHAT,
        CapabilityType.STREAMING,
        CapabilityType.VISION,
        CapabilityType.CODE,
        CapabilityType.TOOL_USE
    )

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val normalizedBaseUrl: String get() = baseUrl.trimEnd('/')

    override suspend fun fetchModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$normalizedBaseUrl/v1beta/models?key=$apiKey")
                .get()
                .build()

            val response = client.newCall(request).await()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val modelsArray = json.optJSONArray("models") ?: return@withContext emptyList()

            val models = mutableListOf<ModelInfo>()
            for (i in 0 until modelsArray.length()) {
                val modelObj = modelsArray.getJSONObject(i)

                // Filter for models that support generateContent
                val supportedMethods = modelObj.optJSONArray("supportedGenerationMethods")
                if (supportedMethods == null) continue
                var supportsGenerate = false
                for (j in 0 until supportedMethods.length()) {
                    if (supportedMethods.getString(j) == "generateContent") {
                        supportsGenerate = true
                        break
                    }
                }
                if (!supportsGenerate) continue

                val fullName = modelObj.optString("name", "")
                val modelId = fullName.removePrefix("models/")
                val displayName = modelObj.optString("displayName", modelId)

                models.add(
                    ModelInfo(
                        id = modelId,
                        name = displayName,
                        provider = id,
                        capabilities = capabilities,
                        contextLength = modelObj.optInt("inputTokenLimit", 4096),
                        maxOutputTokens = modelObj.optInt("outputTokenLimit", 0)
                            .takeIf { it > 0 }
                    )
                )
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
        val modelName = model ?: "gemini-2.0-flash"
        val requestBody = buildRequestBody(messages, tools, temperature)

        val request = Request.Builder()
            .url("$normalizedBaseUrl/v1beta/models/$modelName:generateContent?key=$apiKey")
            .post(requestBody.toString().toRequestBody(jsonMediaType))
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
        val modelName = model ?: "gemini-2.0-flash"
        val requestBody = buildRequestBody(messages, tools, temperature)

        val request = Request.Builder()
            .url("$normalizedBaseUrl/v1beta/models/$modelName:streamGenerateContent?alt=sse&key=$apiKey")
            .post(requestBody.toString().toRequestBody(jsonMediaType))
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

                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: continue
                        if (!currentLine.startsWith("data: ")) continue

                        val data = currentLine.removePrefix("data: ").trim()
                        if (data.isEmpty()) continue

                        try {
                            val json = JSONObject(data)
                            val candidates = json.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val content = candidates.getJSONObject(0)
                                    .optJSONObject("content")
                                val parts = content?.optJSONArray("parts")
                                if (parts != null) {
                                    for (i in 0 until parts.length()) {
                                        val text = parts.getJSONObject(i).optString("text", "")
                                        if (text.isNotEmpty()) {
                                            trySend(text)
                                        }
                                    }
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

    private fun buildRequestBody(
        messages: List<ChatMessage>,
        tools: List<JSONObject>?,
        temperature: Double
    ): JSONObject {
        val body = JSONObject()

        // Generation config
        val generationConfig = JSONObject()
        generationConfig.put("temperature", temperature)
        body.put("generationConfig", generationConfig)

        // Extract system instruction
        val systemMessage = messages.firstOrNull { it.role == "system" }
        if (systemMessage != null) {
            val systemInstruction = JSONObject()
            val parts = JSONArray()
            val part = JSONObject()
            part.put("text", systemMessage.content)
            parts.put(part)
            systemInstruction.put("parts", parts)
            body.put("systemInstruction", systemInstruction)
        }

        // Build contents array
        val contentsArray = JSONArray()
        for (msg in messages) {
            if (msg.role == "system") continue

            val contentObj = JSONObject()
            val geminiRole = when (msg.role) {
                "assistant" -> "model"
                "tool" -> "user"
                else -> "user"
            }
            contentObj.put("role", geminiRole)

            val partsArray = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", msg.content)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)

            contentsArray.put(contentObj)
        }
        body.put("contents", contentsArray)

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
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            return ChatResponse(content = null, toolCalls = null, finishReason = null, usage = null)
        }

        val candidate = candidates.getJSONObject(0)
        val content = candidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")

        var textContent: String? = null
        val toolCalls = mutableListOf<ToolCall>()

        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val text = part.optString("text", "")
                if (text.isNotEmpty()) {
                    textContent = (textContent ?: "") + text
                }

                val functionCall = part.optJSONObject("functionCall")
                if (functionCall != null) {
                    toolCalls.add(
                        ToolCall(
                            id = "gemini_tc_$i",
                            type = "function",
                            function = ToolCallFunction(
                                name = functionCall.optString("name", ""),
                                arguments = functionCall.optJSONObject("args")?.toString() ?: "{}"
                            )
                        )
                    )
                }
            }
        }

        val finishReason = if (candidate.has("finishReason")) candidate.getString("finishReason") else null

        val usageObj = json.optJSONObject("usageMetadata")
        val usage = if (usageObj != null) {
            TokenUsage(
                promptTokens = usageObj.optInt("promptTokenCount", 0),
                completionTokens = usageObj.optInt("candidatesTokenCount", 0),
                totalTokens = usageObj.optInt("totalTokenCount", 0)
            )
        } else {
            null
        }

        return ChatResponse(
            content = textContent,
            toolCalls = toolCalls.ifEmpty { null },
            finishReason = finishReason,
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
