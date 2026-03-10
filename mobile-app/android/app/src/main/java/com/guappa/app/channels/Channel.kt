package com.guappa.app.channels

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.json.JSONObject

/**
 * Incoming message from a channel.
 */
data class IncomingMessage(
    val channelId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
)

interface Channel {
    val id: String
    val name: String
    val isConfigured: Boolean

    suspend fun send(message: String, metadata: Map<String, String>? = null): Boolean
    suspend fun healthCheck(): Boolean

    /**
     * Flow of incoming messages from this channel.
     * Default returns empty flow for send-only channels.
     */
    fun incoming(): Flow<IncomingMessage> = emptyFlow()

    /**
     * Start receiving messages (e.g., start long-polling or WebSocket).
     * Default no-op for send-only channels.
     */
    suspend fun startReceiving() {}

    /**
     * Stop receiving messages.
     */
    suspend fun stopReceiving() {}

    /**
     * Apply runtime configuration from JSON. Default no-op for channels
     * that are configured at construction time via ChannelFactory.
     */
    fun configure(config: JSONObject) {}

    /**
     * Reset channel to unconfigured state. Default no-op.
     */
    fun reset() {}
}
