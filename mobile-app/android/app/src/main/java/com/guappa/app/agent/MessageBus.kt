package com.guappa.app.agent

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.BufferOverflow

enum class MessagePriority { NORMAL, URGENT }

sealed class BusMessage {
    abstract val priority: MessagePriority
    abstract val timestamp: Long

    data class UserMessage(
        val text: String,
        val sessionId: String = "",
        /** File paths of images attached by the user (from gallery, camera, or file picker). */
        val imageAttachments: List<String> = emptyList(),
        override val priority: MessagePriority = MessagePriority.NORMAL,
        override val timestamp: Long = System.currentTimeMillis()
    ) : BusMessage()

    data class AgentMessage(
        val text: String,
        val sessionId: String = "",
        val isStreaming: Boolean = false,
        val isComplete: Boolean = false,
        /**
         * Content type for differentiated chat rendering:
         * - "text"     : regular assistant response (default)
         * - "thinking" : reasoning / chain-of-thought (shown collapsed, secondary color)
         * - "tool_call": tool invocation announcement (shown with ⚡ icon)
         * - "tool_result": tool execution result
         */
        val contentType: String = "text",
        /** File paths of images the agent wants to show in chat (tool outputs, generated images). */
        val imageAttachments: List<String> = emptyList(),
        override val priority: MessagePriority = MessagePriority.NORMAL,
        override val timestamp: Long = System.currentTimeMillis()
    ) : BusMessage()

    data class SystemEvent(
        val type: String,
        val data: Map<String, Any?> = emptyMap(),
        override val priority: MessagePriority = MessagePriority.NORMAL,
        override val timestamp: Long = System.currentTimeMillis()
    ) : BusMessage()

    data class ToolResult(
        val toolName: String,
        val result: String,
        val success: Boolean,
        val sessionId: String = "",
        override val priority: MessagePriority = MessagePriority.NORMAL,
        override val timestamp: Long = System.currentTimeMillis()
    ) : BusMessage()

    data class TriggerEvent(
        val trigger: String,
        val data: Map<String, Any?> = emptyMap(),
        override val priority: MessagePriority = MessagePriority.URGENT,
        override val timestamp: Long = System.currentTimeMillis()
    ) : BusMessage()
}

class MessageBus {
    private val _messages = MutableSharedFlow<BusMessage>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _urgentMessages = MutableSharedFlow<BusMessage>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.SUSPEND
    )

    val messages: SharedFlow<BusMessage> = _messages.asSharedFlow()
    val urgentMessages: SharedFlow<BusMessage> = _urgentMessages.asSharedFlow()

    suspend fun publish(message: BusMessage) {
        when (message.priority) {
            MessagePriority.URGENT -> _urgentMessages.emit(message)
            MessagePriority.NORMAL -> _messages.emit(message)
        }
    }
}
