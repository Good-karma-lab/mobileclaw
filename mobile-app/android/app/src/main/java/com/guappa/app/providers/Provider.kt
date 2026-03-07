package com.guappa.app.providers

import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

data class ChatMessage(
    val role: String,
    val content: String,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val name: String? = null
)

data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunction
)

data class ToolCallFunction(
    val name: String,
    val arguments: String
)

data class ChatResponse(
    val content: String?,
    val toolCalls: List<ToolCall>?,
    val finishReason: String?,
    val usage: TokenUsage?
)

data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

data class ModelInfo(
    val id: String,
    val name: String,
    val provider: String,
    val capabilities: Set<CapabilityType>,
    val contextLength: Int = 4096,
    val maxOutputTokens: Int? = null
)

interface Provider {
    val id: String
    val name: String
    val capabilities: Set<CapabilityType>

    suspend fun fetchModels(): List<ModelInfo>

    suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<JSONObject>? = null,
        model: String? = null,
        temperature: Double = 0.7
    ): ChatResponse

    fun streamChat(
        messages: List<ChatMessage>,
        tools: List<JSONObject>? = null,
        model: String? = null,
        temperature: Double = 0.7
    ): Flow<String>

    suspend fun healthCheck(): Boolean
}
