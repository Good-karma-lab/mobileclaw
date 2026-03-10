package com.guappa.app.providers

/**
 * Structured chunk from an LLM streaming response.
 * Can carry text content, reasoning/thinking tokens, or tool call fragments.
 */
sealed class StreamDelta {
    /** Regular assistant text content. */
    data class Text(val content: String) : StreamDelta()

    /** Reasoning / thinking tokens (chain-of-thought). */
    data class Thinking(val content: String) : StreamDelta()

    /** Incremental tool call fragment. */
    data class ToolCallDelta(
        val index: Int,
        val id: String?,          // present on first chunk only
        val functionName: String?, // present on first chunk only
        val argumentsDelta: String // incremental JSON fragment
    ) : StreamDelta()

    /** Stream finished. Contains the finish_reason. */
    data class Done(val finishReason: String?) : StreamDelta()
}
