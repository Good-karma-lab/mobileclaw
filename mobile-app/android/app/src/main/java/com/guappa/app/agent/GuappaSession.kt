package com.guappa.app.agent

import com.guappa.app.providers.ToolCall
import com.guappa.app.providers.ToolCallFunction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class SessionType { CHAT, BACKGROUND_TASK, TRIGGER, SYSTEM }
enum class SessionState { ACTIVE, IDLE, COMPACTING, CLOSED }

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCallId: String? = null,
    val tokenCount: Int = 0,
    /** File paths of images attached to this message (user photos, tool outputs, agent-generated). */
    val imageAttachments: List<String> = emptyList(),
    /** Tool calls made by the assistant in this message (required for tool-use loop). */
    val toolCalls: List<ToolCall>? = null
) {
    val hasImages: Boolean get() = imageAttachments.isNotEmpty()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("role", role)
        put("content", content)
        put("timestamp", timestamp)
        toolCallId?.let { put("toolCallId", it) }
        put("tokenCount", tokenCount)
        if (imageAttachments.isNotEmpty()) {
            put("imageAttachments", JSONArray(imageAttachments))
        }
        if (!toolCalls.isNullOrEmpty()) {
            put("toolCalls", JSONArray().apply {
                for (tc in toolCalls) {
                    put(JSONObject().apply {
                        put("id", tc.id)
                        put("type", tc.type)
                        put("function", JSONObject().apply {
                            put("name", tc.function.name)
                            put("arguments", tc.function.arguments)
                        })
                    })
                }
            })
        }
    }

    companion object {
        fun fromJson(json: JSONObject): Message = Message(
            id = json.optString("id", UUID.randomUUID().toString()),
            role = json.getString("role"),
            content = json.getString("content"),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            toolCallId = if (json.has("toolCallId") && !json.isNull("toolCallId")) json.getString("toolCallId") else null,
            tokenCount = json.optInt("tokenCount", 0),
            imageAttachments = json.optJSONArray("imageAttachments")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            toolCalls = json.optJSONArray("toolCalls")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val tc = arr.getJSONObject(i)
                    val fn = tc.getJSONObject("function")
                    ToolCall(
                        id = tc.getString("id"),
                        type = tc.optString("type", "function"),
                        function = ToolCallFunction(
                            name = fn.getString("name"),
                            arguments = fn.optString("arguments", "{}")
                        )
                    )
                }
            }
        )
    }
}

class GuappaSession(
    val id: String = UUID.randomUUID().toString(),
    val type: SessionType = SessionType.CHAT,
    private val maxTokens: Int = 128_000,
    /** Session time-to-live in milliseconds. 0 = no TTL. */
    val ttlMs: Long = 0L
) {
    private val _messages = mutableListOf<Message>()
    val messages: List<Message> get() = _messages.toList()

    private val _state = MutableStateFlow(SessionState.IDLE)
    val state = _state.asStateFlow()

    private var totalTokens: Int = 0
    val createdAt: Long = System.currentTimeMillis()
    var lastActiveAt: Long = System.currentTimeMillis()
        private set

    val contextUsageRatio: Float get() = if (maxTokens > 0) totalTokens.toFloat() / maxTokens else 0f

    val isExpired: Boolean get() = ttlMs > 0 && (System.currentTimeMillis() - lastActiveAt) > ttlMs

    fun addMessage(message: Message) {
        _messages.add(message)
        totalTokens += message.tokenCount
        lastActiveAt = System.currentTimeMillis()
        _state.value = SessionState.ACTIVE
    }

    fun needsCompaction(threshold: Float): Boolean = contextUsageRatio > threshold

    /**
     * Replace old messages with a summary, keeping the last N messages verbatim.
     */
    fun compactWith(summaryText: String, keepRecent: Int) {
        if (_messages.size <= keepRecent) return
        _state.value = SessionState.COMPACTING
        val recent = _messages.takeLast(keepRecent).toMutableList()
        _messages.clear()
        _messages.add(Message(
            role = "system",
            content = "[Conversation summary]\n$summaryText"
        ))
        _messages.addAll(recent)
        totalTokens = _messages.sumOf { it.tokenCount }
        _state.value = SessionState.ACTIVE
    }

    fun getContextMessages(systemPrompt: String): List<Message> {
        val result = mutableListOf(Message(role = "system", content = systemPrompt))
        result.addAll(_messages.takeLast(40))
        return result
    }

    fun close() {
        _state.value = SessionState.CLOSED
    }

    // ---- Checkpoint / Recovery ----

    /**
     * Serialize session to JSON for checkpoint persistence.
     */
    fun checkpoint(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("maxTokens", maxTokens)
        put("ttlMs", ttlMs)
        put("createdAt", createdAt)
        put("lastActiveAt", lastActiveAt)
        put("state", _state.value.name)
        put("messages", JSONArray().apply {
            _messages.forEach { put(it.toJson()) }
        })
    }

    companion object {
        /** Restore session from a checkpoint JSON. */
        fun fromCheckpoint(json: JSONObject): GuappaSession {
            val session = GuappaSession(
                id = json.getString("id"),
                type = SessionType.valueOf(json.optString("type", "CHAT")),
                maxTokens = json.optInt("maxTokens", 128_000),
                ttlMs = json.optLong("ttlMs", 0L)
            )
            val msgs = json.optJSONArray("messages")
            if (msgs != null) {
                for (i in 0 until msgs.length()) {
                    session.addMessage(Message.fromJson(msgs.getJSONObject(i)))
                }
            }
            return session
        }
    }
}
