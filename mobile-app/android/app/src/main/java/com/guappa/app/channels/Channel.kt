package com.guappa.app.channels

import org.json.JSONObject

interface Channel {
    val id: String
    val name: String
    val isConfigured: Boolean

    suspend fun send(message: String, metadata: Map<String, String>? = null): Boolean
    suspend fun healthCheck(): Boolean

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
