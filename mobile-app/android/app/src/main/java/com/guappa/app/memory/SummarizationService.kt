package com.guappa.app.memory

import android.util.Log
import com.guappa.app.agent.GuappaSession
import com.guappa.app.agent.Message
import com.guappa.app.memory.ContextCompactor.CompactionResult
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Async background summarization service that wraps [ContextCompactor].
 *
 * Design:
 *   - Uses a dedicated CoroutineScope so summarization never blocks the agent thread.
 *   - Queue-based: accepts [SummarizationRequest] objects, processes them sequentially.
 *   - Debounced: if multiple requests arrive within [DEBOUNCE_MS], they are batched
 *     into a single compaction pass (the latest session state is used).
 *   - Callback-based: callers register a [SummarizationListener] to be notified
 *     when compaction completes, so MemoryManager can update context.
 *   - Falls back to extractive summarization (first/last key sentences) when
 *     no LLM provider is available (handled internally by ContextCompactor).
 */
class SummarizationService(
    private val contextCompactor: ContextCompactor,
    private val memoryManager: MemoryManager
) {
    private val TAG = "SummarizationService"

    /** Dedicated scope — not tied to any UI or agent lifecycle. */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("SummarizationService")
    )

    /** Unbuffered channel for incoming requests. */
    private val requestChannel = Channel<SummarizationRequest>(Channel.UNLIMITED)

    /** Listener for compaction results. */
    private var listener: SummarizationListener? = null
    private val listenerMutex = Mutex()

    /** Debounce window in milliseconds. */
    private val debounceMs: Long = DEBOUNCE_MS

    /** Track whether the processing loop has been started. */
    @Volatile
    private var started = false

    companion object {
        /** Debounce window: batch requests arriving within this window. */
        const val DEBOUNCE_MS = 5_000L
    }

    // =====================================================================
    //  Lifecycle
    // =====================================================================

    /**
     * Start the background processing loop. Idempotent — safe to call multiple times.
     */
    fun start() {
        if (started) return
        started = true
        scope.launch { processLoop() }
        Log.d(TAG, "Summarization service started")
    }

    /**
     * Shutdown the service and cancel pending work.
     */
    fun shutdown() {
        started = false
        requestChannel.close()
        scope.cancel()
        Log.d(TAG, "Summarization service shut down")
    }

    // =====================================================================
    //  Public API
    // =====================================================================

    /**
     * Submit a summarization request. Non-blocking — the request is queued
     * and will be processed on the background scope.
     *
     * @param session  The session to compact.
     * @param force    If true, bypass the threshold check and force compaction.
     */
    fun requestSummarization(session: GuappaSession, force: Boolean = false) {
        if (!started) {
            Log.w(TAG, "Service not started, starting now")
            start()
        }

        val request = SummarizationRequest(
            session = session,
            force = force,
            requestedAt = System.currentTimeMillis()
        )

        val sent = requestChannel.trySend(request)
        if (sent.isSuccess) {
            Log.d(TAG, "Queued summarization for session ${session.id} (force=$force)")
        } else {
            Log.w(TAG, "Failed to queue summarization for session ${session.id}: channel closed")
        }
    }

    /**
     * Request extractive summarization for a list of messages without needing
     * a full session. Useful for ad-hoc summarization of message batches.
     *
     * @return The extractive summary text.
     */
    fun extractiveSummary(messages: List<Message>): String {
        if (messages.isEmpty()) return ""

        val keyMessages = messages.filter { msg ->
            msg.role == "user" || (msg.role == "assistant" && msg.toolCallId == null)
        }

        if (keyMessages.isEmpty()) {
            return "Conversation context (${messages.size} messages exchanged)."
        }

        val builder = StringBuilder()

        // Take first few messages for context opening
        val opening = keyMessages.take(3)
        for (msg in opening) {
            val prefix = if (msg.role == "user") "User" else "Assistant"
            val truncated = msg.content.take(200).replace("\n", " ").trim()
            if (truncated.isNotBlank()) {
                builder.appendLine("- $prefix: $truncated")
            }
        }

        // Take last few messages for the most recent context
        if (keyMessages.size > 3) {
            val closing = keyMessages.takeLast(3)
            if (builder.isNotEmpty()) {
                builder.appendLine("...")
            }
            for (msg in closing) {
                val prefix = if (msg.role == "user") "User" else "Assistant"
                val truncated = msg.content.take(200).replace("\n", " ").trim()
                if (truncated.isNotBlank()) {
                    builder.appendLine("- $prefix: $truncated")
                }
            }
        }

        return builder.toString().trim()
    }

    /**
     * Register a listener to be notified when summarization completes.
     */
    suspend fun setListener(callback: SummarizationListener?) {
        listenerMutex.withLock {
            listener = callback
        }
    }

    // =====================================================================
    //  Processing Loop
    // =====================================================================

    /**
     * Main processing loop with debounce logic.
     *
     * Collects requests from the channel. When a request arrives, waits
     * [debounceMs] for additional requests on the same session. If more
     * arrive, only the latest is processed (using the most recent session
     * state).
     */
    private suspend fun processLoop() {
        for (request in requestChannel) {
            // Debounce: collect all requests that arrive within the window
            val batch = mutableMapOf<String, SummarizationRequest>()
            batch[request.session.id] = request

            // Wait for the debounce window, collecting any new requests
            val debounceEnd = System.currentTimeMillis() + debounceMs
            while (System.currentTimeMillis() < debounceEnd) {
                val next = withTimeoutOrNull(debounceEnd - System.currentTimeMillis()) {
                    requestChannel.receive()
                } ?: break

                // Keep only the latest request per session
                batch[next.session.id] = next
            }

            // Process all batched requests sequentially
            for ((sessionId, batchedRequest) in batch) {
                processSingleRequest(batchedRequest)
            }
        }
    }

    private suspend fun processSingleRequest(request: SummarizationRequest) {
        val session = request.session
        Log.d(TAG, "Processing summarization for session ${session.id}")

        try {
            val result = if (request.force) {
                contextCompactor.forceCompact(session)
            } else {
                contextCompactor.checkAndCompact(session)
            }

            // Also try to extract facts from compacted messages
            if (result is CompactionResult.Success) {
                try {
                    contextCompactor.extractFactsFromMessages(
                        session.messages.take(result.messagesBefore - result.messagesAfter)
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Fact extraction failed during summarization: ${e.message}")
                }
            }

            // Notify listener
            val currentListener = listenerMutex.withLock { listener }
            currentListener?.onSummarizationComplete(session.id, result)

            when (result) {
                is CompactionResult.Success -> {
                    Log.i(TAG, "Summarization complete for ${session.id}: " +
                        "${result.messagesBefore}->${result.messagesAfter} messages, " +
                        "~${result.estimatedTokensBefore}->~${result.estimatedTokensAfter} tokens")
                }
                is CompactionResult.NotNeeded -> {
                    Log.d(TAG, "Summarization not needed for ${session.id}")
                }
                is CompactionResult.AlreadyRunning -> {
                    Log.d(TAG, "Summarization already running for ${session.id}")
                }
                is CompactionResult.Failed -> {
                    Log.w(TAG, "Summarization failed for ${session.id}: ${result.reason}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Summarization error for ${session.id}: ${e.message}", e)
            val currentListener = listenerMutex.withLock { listener }
            currentListener?.onSummarizationComplete(
                session.id,
                CompactionResult.Failed(e.message ?: "Unknown error")
            )
        }
    }

    // =====================================================================
    //  Types
    // =====================================================================

    private data class SummarizationRequest(
        val session: GuappaSession,
        val force: Boolean,
        val requestedAt: Long
    )

    /**
     * Callback interface for summarization completion.
     */
    interface SummarizationListener {
        /**
         * Called on the background scope when summarization finishes.
         * Implementations should not perform heavy blocking work here.
         */
        fun onSummarizationComplete(sessionId: String, result: CompactionResult)
    }
}
