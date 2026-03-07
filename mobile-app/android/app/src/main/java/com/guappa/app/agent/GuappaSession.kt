package com.guappa.app.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class SessionType { CHAT, BACKGROUND_TASK, TRIGGER, SYSTEM }
enum class SessionState { ACTIVE, IDLE, COMPACTING, CLOSED }

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCallId: String? = null,
    val tokenCount: Int = 0
)

class GuappaSession(
    val id: String = UUID.randomUUID().toString(),
    val type: SessionType = SessionType.CHAT,
    private val maxTokens: Int = 128_000
) {
    private val _messages = mutableListOf<Message>()
    val messages: List<Message> get() = _messages.toList()

    private val _state = MutableStateFlow(SessionState.IDLE)
    val state = _state.asStateFlow()

    private var totalTokens: Int = 0

    val contextUsageRatio: Float get() = if (maxTokens > 0) totalTokens.toFloat() / maxTokens else 0f

    fun addMessage(message: Message) {
        _messages.add(message)
        totalTokens += message.tokenCount
        _state.value = SessionState.ACTIVE
    }

    fun needsCompaction(threshold: Float): Boolean = contextUsageRatio > threshold

    fun getContextMessages(systemPrompt: String): List<Message> {
        val result = mutableListOf(Message(role = "system", content = systemPrompt))
        result.addAll(_messages.takeLast(40))
        return result
    }

    fun close() {
        _state.value = SessionState.CLOSED
    }
}
