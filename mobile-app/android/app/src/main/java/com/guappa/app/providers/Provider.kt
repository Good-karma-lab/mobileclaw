package com.guappa.app.providers

import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

/**
 * A single content part in a multimodal message.
 * Text-only messages use a single TextPart.
 * Vision messages include ImagePart(s) alongside text.
 */
sealed class ContentPart {
    data class TextPart(val text: String) : ContentPart()
    data class ImagePart(
        val base64: String,
        val mimeType: String = "image/jpeg"
    ) : ContentPart()
    data class ImageUrlPart(val url: String) : ContentPart()
}

data class ChatMessage(
    val role: String,
    val content: String,
    val contentParts: List<ContentPart>? = null,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val name: String? = null
) {
    /** True if this message contains image content. */
    val hasImages: Boolean
        get() = contentParts?.any { it is ContentPart.ImagePart || it is ContentPart.ImageUrlPart } == true

    companion object {
        /** Create a text-only message. */
        fun text(role: String, content: String) = ChatMessage(role = role, content = content)

        /** Create a multimodal message with text + images. */
        fun withImages(
            role: String,
            text: String,
            images: List<ContentPart>
        ): ChatMessage {
            val parts = mutableListOf<ContentPart>()
            parts.addAll(images)
            if (text.isNotBlank()) parts.add(ContentPart.TextPart(text))
            return ChatMessage(
                role = role,
                content = text,
                contentParts = parts
            )
        }
    }
}

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

    /**
     * Structured streaming: returns [StreamDelta] items including text, thinking,
     * tool call fragments, and done signals. Callers can accumulate tool calls
     * from the stream and execute them without needing a separate non-streaming call.
     */
    fun streamChatStructured(
        messages: List<ChatMessage>,
        tools: List<JSONObject>? = null,
        model: String? = null,
        temperature: Double = 0.7
    ): Flow<StreamDelta> {
        // Default: wrap legacy streamChat() as Text deltas
        val self = this
        return kotlinx.coroutines.flow.flow {
            self.streamChat(messages, tools, model, temperature).collect { text ->
                emit(StreamDelta.Text(text))
            }
            emit(StreamDelta.Done("stop"))
        }
    }

    suspend fun healthCheck(): Boolean
}
