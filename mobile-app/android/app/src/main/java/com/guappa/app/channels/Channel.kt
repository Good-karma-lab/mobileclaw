package com.guappa.app.channels

interface Channel {
    val id: String
    val name: String
    val isConfigured: Boolean

    suspend fun send(message: String, metadata: Map<String, String>? = null): Boolean
    suspend fun healthCheck(): Boolean
}
