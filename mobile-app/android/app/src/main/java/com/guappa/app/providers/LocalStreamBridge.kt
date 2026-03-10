package com.guappa.app.providers

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Direct token streaming bridge for local LLM inference.
 * Bypasses the NanoHTTPD → OkHttp HTTP path which buffers tokens.
 *
 * JS llama.rn callback → RN bridge → LocalStreamBridge.pushToken() → Flow → Orchestrator
 */
object LocalStreamBridge {
    private val _tokens = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val tokens: SharedFlow<String> = _tokens.asSharedFlow()

    @Volatile
    var isStreaming = false
        private set

    fun startStream() {
        isStreaming = true
    }

    fun pushToken(token: String) {
        _tokens.tryEmit(token)
    }

    fun endStream() {
        isStreaming = false
        _tokens.tryEmit("\u0000") // Sentinel: end of stream
    }
}
