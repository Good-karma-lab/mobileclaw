package com.guappa.app.memory

import android.util.Log
import com.guappa.app.agent.GuappaSession
import com.guappa.app.agent.Message
import com.guappa.app.providers.CapabilityType
import com.guappa.app.providers.ChatMessage
import com.guappa.app.providers.ProviderRouter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Monitors the working memory token count and triggers summarization when
 * context usage reaches a configurable threshold (default 80%).
 *
 * Summarization strategy:
 *   1. Keep the last RECENT_MESSAGES_KEEP messages verbatim.
 *   2. Summarize everything before that into a compact narrative.
 *   3. Replace the full conversation with [summary] + [recent messages].
 *
 * Uses a cheap/fast model for the summarization LLM call (falls back to
 * the default provider if no cheap model is available).
 */
class ContextCompactor(
    private val memoryManager: MemoryManager,
    private val providerRouter: ProviderRouter? = null
) {
    private val TAG = "ContextCompactor"
    private val compactionMutex = Mutex()

    companion object {
        /** Trigger summarization when context usage exceeds this ratio. */
        const val COMPACTION_THRESHOLD = 0.8f

        /** Number of recent messages to keep verbatim after compaction. */
        const val RECENT_MESSAGES_KEEP = 10

        /** Rough chars-per-token estimate for quick budget checks. */
        const val CHARS_PER_TOKEN = 4

        /** Max tokens for the summarization LLM response. */
        const val SUMMARY_MAX_TOKENS = 1024
    }

    // =====================================================================
    //  Public API
    // =====================================================================

    /**
     * Check whether the session needs compaction and perform it if so.
     *
     * @return a [CompactionResult] indicating what happened.
     */
    suspend fun checkAndCompact(session: GuappaSession): CompactionResult {
        if (!session.needsCompaction(COMPACTION_THRESHOLD)) {
            return CompactionResult.NotNeeded
        }

        // Avoid concurrent compaction on the same session
        if (!compactionMutex.tryLock()) {
            return CompactionResult.AlreadyRunning
        }

        return try {
            performCompaction(session)
        } catch (e: Exception) {
            Log.e(TAG, "Compaction failed for session ${session.id}: ${e.message}", e)
            CompactionResult.Failed(e.message ?: "Unknown error")
        } finally {
            compactionMutex.unlock()
        }
    }

    /**
     * Force-compact a session regardless of threshold.
     */
    suspend fun forceCompact(session: GuappaSession): CompactionResult {
        return compactionMutex.withLock {
            try {
                performCompaction(session)
            } catch (e: Exception) {
                Log.e(TAG, "Force compaction failed: ${e.message}", e)
                CompactionResult.Failed(e.message ?: "Unknown error")
            }
        }
    }

    // =====================================================================
    //  Core Compaction
    // =====================================================================

    private suspend fun performCompaction(session: GuappaSession): CompactionResult {
        val messages = session.messages
        if (messages.size <= RECENT_MESSAGES_KEEP) {
            return CompactionResult.NotNeeded
        }

        Log.i(TAG, "Starting compaction for session ${session.id} (${messages.size} messages)")

        // Split: older messages to summarize, recent messages to keep
        val splitIndex = messages.size - RECENT_MESSAGES_KEEP
        val toSummarize = messages.subList(0, splitIndex)
        val toKeep = messages.subList(splitIndex, messages.size)

        // Generate summary via LLM
        val summary = generateSummary(toSummarize, session.id)

        // Store the summary as an episodic memory
        memoryManager.addEpisode(
            sessionId = session.id,
            summary = summary,
            emotion = "neutral",
            outcome = "compaction"
        )

        // Build the compacted message list
        val compactedMessages = mutableListOf<Message>()

        // Insert summary as a system-level context message
        compactedMessages.add(Message(
            role = "system",
            content = buildSummaryBlock(summary)
        ))

        // Keep recent messages verbatim
        compactedMessages.addAll(toKeep)

        // Also update working memory
        memoryManager.replaceWorkingMemory(
            session.id,
            compactedMessages.map { msg ->
                MemoryManager.WorkingMemoryItem(
                    role = msg.role,
                    content = msg.content,
                    timestamp = msg.timestamp,
                    tokenCount = msg.tokenCount
                )
            }
        )

        val tokensBefore = estimateTokens(messages)
        val tokensAfter = estimateTokens(compactedMessages)

        Log.i(TAG, "Compaction complete: ${messages.size} -> ${compactedMessages.size} messages, ~$tokensBefore -> ~$tokensAfter tokens")

        return CompactionResult.Success(
            messagesBefore = messages.size,
            messagesAfter = compactedMessages.size,
            estimatedTokensBefore = tokensBefore,
            estimatedTokensAfter = tokensAfter,
            summary = summary,
            compactedMessages = compactedMessages
        )
    }

    // =====================================================================
    //  Summary Generation
    // =====================================================================

    private suspend fun generateSummary(messages: List<Message>, sessionId: String): String {
        val router = providerRouter
        if (router == null) {
            Log.w(TAG, "No provider router available, using extractive summary")
            return generateExtractiveSummary(messages)
        }

        return try {
            generateLlmSummary(router, messages)
        } catch (e: Exception) {
            Log.w(TAG, "LLM summarization failed, falling back to extractive: ${e.message}")
            generateExtractiveSummary(messages)
        }
    }

    private suspend fun generateLlmSummary(router: ProviderRouter, messages: List<Message>): String {
        val conversationText = messages.joinToString("\n") { msg ->
            "[${msg.role}]: ${msg.content.take(500)}"
        }

        val prompt = buildString {
            appendLine("Summarize the following conversation concisely. Preserve:")
            appendLine("1. Key facts, decisions, and outcomes")
            appendLine("2. User preferences or requests that matter for context")
            appendLine("3. Any tool call outcomes (what was done, not the raw call)")
            appendLine("4. Anything the user explicitly asked to remember")
            appendLine()
            appendLine("Do NOT include raw tool call JSON. Do NOT include system messages.")
            appendLine("Keep the summary under 500 words. Format as a brief narrative.")
            appendLine()
            appendLine("--- Conversation ---")
            appendLine(conversationText)
        }

        val response = router.chat(
            messages = listOf(
                ChatMessage(
                    role = "system",
                    content = "You are a precise conversation summarizer. Output only the summary, nothing else."
                ),
                ChatMessage(role = "user", content = prompt)
            ),
            capability = CapabilityType.TEXT_CHAT,
            temperature = 0.3
        )

        return response.content?.trim() ?: generateExtractiveSummary(messages)
    }

    /**
     * Fallback extractive summary when LLM is not available.
     * Takes key messages (user messages and non-tool assistant messages) and
     * produces a condensed list.
     */
    private fun generateExtractiveSummary(messages: List<Message>): String {
        val keyMessages = messages.filter { msg ->
            msg.role == "user" || (msg.role == "assistant" && msg.toolCallId == null)
        }

        if (keyMessages.isEmpty()) {
            return "Previous conversation context (${messages.size} messages exchanged)."
        }

        val builder = StringBuilder("Summary of previous conversation:\n")
        for (msg in keyMessages.takeLast(15)) {
            val prefix = if (msg.role == "user") "User" else "Assistant"
            val truncated = msg.content.take(200).replace("\n", " ")
            builder.appendLine("- $prefix: $truncated")
        }
        return builder.toString()
    }

    // =====================================================================
    //  Fact Extraction (from compacted messages)
    // =====================================================================

    /**
     * Extract memorable facts from messages being compacted.
     * This is called during compaction to capture any persistent facts
     * before the messages are summarized away.
     */
    suspend fun extractFactsFromMessages(messages: List<Message>) {
        val router = providerRouter ?: return

        // Only bother if there are enough messages to extract from
        if (messages.size < 3) return

        try {
            val conversationText = messages
                .filter { it.role == "user" || it.role == "assistant" }
                .takeLast(20)
                .joinToString("\n") { "[${it.role}]: ${it.content.take(300)}" }

            val prompt = buildString {
                appendLine("Analyze these conversation messages and extract persistent facts about the user.")
                appendLine("Only extract facts that should be remembered for future conversations.")
                appendLine()
                appendLine("Categories: preference, fact, relationship, routine, date")
                appendLine()
                appendLine("Output ONLY a simple list, one fact per line, in this format:")
                appendLine("category|key|value")
                appendLine()
                appendLine("Example:")
                appendLine("preference|language|Prefers Russian")
                appendLine("fact|pet_name|Dog named Rex")
                appendLine("relationship|mother|Contact: Mom")
                appendLine()
                appendLine("If no extractable facts, output: NONE")
                appendLine()
                appendLine("--- Messages ---")
                appendLine(conversationText)
            }

            val response = router.chat(
                messages = listOf(
                    ChatMessage(role = "system", content = "Extract user facts. Output only the fact list."),
                    ChatMessage(role = "user", content = prompt)
                ),
                capability = CapabilityType.TEXT_CHAT,
                temperature = 0.2
            )

            val resultText = response.content?.trim() ?: return
            if (resultText.equals("NONE", ignoreCase = true)) return

            for (line in resultText.lines()) {
                val parts = line.trim().split("|", limit = 3)
                if (parts.size == 3) {
                    val (category, key, value) = parts
                    val cleanCategory = category.trim().lowercase()
                    val cleanKey = key.trim()
                    val cleanValue = value.trim()

                    if (cleanKey.isNotBlank() && cleanValue.isNotBlank()) {
                        // Check if we already have this fact
                        val existing = memoryManager.getFactByKey(cleanKey)
                        if (existing == null) {
                            memoryManager.addFact(
                                key = cleanKey,
                                value = cleanValue,
                                category = cleanCategory,
                                tier = "short_term",
                                importance = 0.6f
                            )
                            Log.d(TAG, "Extracted fact: $cleanKey = $cleanValue")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fact extraction failed: ${e.message}")
        }
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private fun buildSummaryBlock(summary: String): String {
        return buildString {
            appendLine("[Conversation Summary — earlier messages have been summarized to save context]")
            appendLine()
            appendLine(summary)
        }
    }

    private fun estimateTokens(messages: List<Message>): Int {
        return messages.sumOf { it.content.length / CHARS_PER_TOKEN }
    }

    sealed class CompactionResult {
        object NotNeeded : CompactionResult()
        object AlreadyRunning : CompactionResult()
        data class Failed(val reason: String) : CompactionResult()
        data class Success(
            val messagesBefore: Int,
            val messagesAfter: Int,
            val estimatedTokensBefore: Int,
            val estimatedTokensAfter: Int,
            val summary: String,
            val compactedMessages: List<Message>
        ) : CompactionResult()
    }
}
