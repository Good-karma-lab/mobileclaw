package com.guappa.app.memory

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 5-Tier Memory Manager for the Guappa agent.
 *
 * Tier 1 — Working memory:  in-memory conversation context for the current turn.
 * Tier 2 — Short-term:      recent facts from current session, persisted in Room, 24h TTL.
 * Tier 3 — Long-term:       important facts persisted across sessions, no TTL.
 * Tier 4 — Episodic:        session summaries and key events.
 * Tier 5 — Semantic:        TF-IDF embeddings + vector cosine similarity search.
 *
 * Promotion logic:
 *   short_term -> long_term  when accessCount >= PROMOTION_ACCESS_THRESHOLD
 *                            or importance >= PROMOTION_IMPORTANCE_THRESHOLD
 *
 * Auto-cleanup runs every CLEANUP_INTERVAL_MS and expires short-term facts older than 24h
 * that have not been accessed enough to be promoted.
 */
class MemoryManager(
    private val context: Context,
    private val db: GuappaDatabase = GuappaDatabase.getInstance(context)
) {
    private val TAG = "MemoryManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Tier 5 embedding service for semantic search. */
    val embeddingService = EmbeddingService(db)

    // ---------- Tier 1: Working Memory (in-memory) ----------

    /** Per-session working memory: maps sessionId -> list of recent context items. */
    private val workingMemory = ConcurrentHashMap<String, MutableList<WorkingMemoryItem>>()

    data class WorkingMemoryItem(
        val role: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        val tokenCount: Int = 0
    )

    // ---------- Constants ----------

    companion object {
        private const val SHORT_TERM_TTL_MS = 24 * 60 * 60 * 1000L  // 24 hours
        private const val CLEANUP_INTERVAL_MS = 30 * 60 * 1000L     // 30 minutes
        private const val PROMOTION_ACCESS_THRESHOLD = 3
        private const val PROMOTION_IMPORTANCE_THRESHOLD = 0.7f

        @Volatile
        private var INSTANCE: MemoryManager? = null

        fun getInstance(context: Context): MemoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MemoryManager(
                    context.applicationContext,
                    GuappaDatabase.getInstance(context.applicationContext)
                ).also {
                    INSTANCE = it
                    it.startCleanupLoop()
                }
            }
        }
    }

    // ---------- DAOs ----------

    private val sessionDao get() = db.sessionDao()
    private val messageDao get() = db.messageDao()
    private val taskDao get() = db.taskDao()
    private val factDao get() = db.memoryFactDao()
    private val episodeDao get() = db.episodeDao()

    // =====================================================================
    //  Tier 1: Working Memory
    // =====================================================================

    fun addToWorkingMemory(sessionId: String, role: String, content: String, tokenCount: Int = 0) {
        val items = workingMemory.getOrPut(sessionId) { mutableListOf() }
        items.add(WorkingMemoryItem(role, content, tokenCount = tokenCount))
    }

    fun getWorkingMemory(sessionId: String): List<WorkingMemoryItem> {
        return workingMemory[sessionId]?.toList() ?: emptyList()
    }

    fun getWorkingMemoryTokenCount(sessionId: String): Int {
        return workingMemory[sessionId]?.sumOf { it.tokenCount } ?: 0
    }

    fun clearWorkingMemory(sessionId: String) {
        workingMemory.remove(sessionId)
    }

    fun replaceWorkingMemory(sessionId: String, items: List<WorkingMemoryItem>) {
        workingMemory[sessionId] = items.toMutableList()
    }

    // =====================================================================
    //  Tier 2: Short-Term Memory (facts with 24h TTL)
    // =====================================================================

    suspend fun addShortTermFact(
        key: String,
        value: String,
        category: String,
        importance: Float = 0.5f
    ): MemoryFactEntity {
        val fact = MemoryFactEntity(
            id = UUID.randomUUID().toString(),
            key = key,
            value = value,
            category = category,
            tier = "short_term",
            importance = importance.coerceIn(0f, 1f)
        )
        factDao.insert(fact)
        Log.d(TAG, "Added short-term fact: $key")
        // Index for semantic search (fire-and-forget)
        scope.launch {
            try {
                embeddingService.index(fact.id, "fact", "$key: $value")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to index fact embedding: ${e.message}")
            }
        }
        return fact
    }

    suspend fun getShortTermFacts(): List<MemoryFactEntity> {
        return factDao.getByTier("short_term")
    }

    // =====================================================================
    //  Tier 3: Long-Term Memory (persistent facts, no TTL)
    // =====================================================================

    suspend fun addLongTermFact(
        key: String,
        value: String,
        category: String,
        importance: Float = 0.7f
    ): MemoryFactEntity {
        val fact = MemoryFactEntity(
            id = UUID.randomUUID().toString(),
            key = key,
            value = value,
            category = category,
            tier = "long_term",
            importance = importance.coerceIn(0f, 1f)
        )
        factDao.insert(fact)
        Log.d(TAG, "Added long-term fact: $key")
        // Index for semantic search (fire-and-forget)
        scope.launch {
            try {
                embeddingService.index(fact.id, "fact", "$key: $value")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to index fact embedding: ${e.message}")
            }
        }
        return fact
    }

    suspend fun getLongTermFacts(): List<MemoryFactEntity> {
        return factDao.getByTier("long_term")
    }

    // =====================================================================
    //  Tier 4: Episodic Memory (session summaries + events)
    // =====================================================================

    suspend fun addEpisode(
        sessionId: String,
        summary: String,
        emotion: String = "neutral",
        outcome: String = ""
    ): EpisodeEntity {
        val episode = EpisodeEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            summary = summary,
            emotion = emotion,
            outcome = outcome
        )
        episodeDao.insert(episode)
        Log.d(TAG, "Added episode for session $sessionId")
        // Index for semantic search (fire-and-forget)
        scope.launch {
            try {
                embeddingService.index(episode.id, "episode", summary)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to index episode embedding: ${e.message}")
            }
        }
        return episode
    }

    suspend fun getRecentEpisodes(limit: Int = 20): List<EpisodeEntity> {
        return episodeDao.getRecent(limit)
    }

    suspend fun getEpisodesBySession(sessionId: String): List<EpisodeEntity> {
        return episodeDao.getBySession(sessionId)
    }

    // =====================================================================
    //  Tier 5: Semantic Memory (TF-IDF vector search via EmbeddingService)
    // =====================================================================

    /**
     * Semantic search using TF-IDF vector embeddings (via [EmbeddingService]).
     *
     * Falls back to keyword search when no embeddings are indexed yet.
     */
    suspend fun semanticSearch(query: String, limit: Int = 10): List<MemorySearchResult> {
        // Try vector-based semantic search first
        val vectorResults = try {
            embeddingService.searchSimilar(query, limit)
        } catch (e: Exception) {
            Log.w(TAG, "Vector search failed, falling back to keyword: ${e.message}")
            emptyList()
        }

        if (vectorResults.isNotEmpty()) {
            return vectorResults
        }

        // Fallback: keyword search across facts and episodes
        val factResults = factDao.search(query).take(limit).map { fact ->
            MemorySearchResult(
                id = fact.id,
                content = "${fact.key}: ${fact.value}",
                source = "fact",
                category = fact.category,
                score = fact.importance.toDouble()
            )
        }
        val episodeResults = episodeDao.search(query).take(limit).map { ep ->
            MemorySearchResult(
                id = ep.id,
                content = ep.summary,
                source = "episode",
                category = ep.emotion,
                score = 0.5
            )
        }
        return (factResults + episodeResults)
            .sortedByDescending { it.score }
            .take(limit)
    }

    data class MemorySearchResult(
        val id: String,
        val content: String,
        val source: String,   // "fact" or "episode"
        val category: String,
        val score: Double
    )

    // =====================================================================
    //  Unified Fact CRUD (used by MemoryBridge)
    // =====================================================================

    suspend fun addFact(
        key: String,
        value: String,
        category: String,
        tier: String = "short_term",
        importance: Float = 0.5f
    ): MemoryFactEntity {
        return if (tier == "long_term") {
            addLongTermFact(key, value, category, importance)
        } else {
            addShortTermFact(key, value, category, importance)
        }
    }

    suspend fun getFact(id: String): MemoryFactEntity? {
        val fact = factDao.getById(id) ?: return null
        factDao.recordAccess(id, System.currentTimeMillis())
        return fact
    }

    suspend fun getFactByKey(key: String): MemoryFactEntity? {
        val fact = factDao.getByKey(key) ?: return null
        factDao.recordAccess(fact.id, System.currentTimeMillis())
        return fact
    }

    suspend fun getMemories(category: String?, tier: String?): List<MemoryFactEntity> {
        return when {
            category != null && tier != null -> factDao.getByCategoryAndTier(category, tier)
            category != null -> factDao.getByCategory(category)
            tier != null -> factDao.getByTier(tier)
            else -> factDao.getTopFacts(100)
        }
    }

    suspend fun searchMemories(query: String): List<MemoryFactEntity> {
        return factDao.search(query)
    }

    suspend fun deleteFact(id: String) {
        factDao.deleteById(id)
        // Remove from semantic index (fire-and-forget)
        scope.launch {
            try {
                embeddingService.removeIndex(id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove fact embedding: ${e.message}")
            }
        }
    }

    // =====================================================================
    //  Session Management
    // =====================================================================

    suspend fun createSession(title: String = ""): SessionEntity {
        val session = SessionEntity(
            id = UUID.randomUUID().toString(),
            title = title
        )
        sessionDao.insert(session)
        Log.d(TAG, "Created session: ${session.id}")
        return session
    }

    suspend fun endSession(sessionId: String, summary: String? = null) {
        sessionDao.endSession(sessionId, System.currentTimeMillis(), summary)
        clearWorkingMemory(sessionId)
        Log.d(TAG, "Ended session: $sessionId")
    }

    suspend fun getSession(id: String): SessionEntity? {
        return sessionDao.getById(id)
    }

    suspend fun getRecentSessions(limit: Int = 20): List<SessionEntity> {
        return sessionDao.getRecent(limit)
    }

    suspend fun getActiveSessions(): List<SessionEntity> {
        return sessionDao.getActiveSessions()
    }

    // =====================================================================
    //  Message Persistence
    // =====================================================================

    suspend fun persistMessage(
        sessionId: String,
        role: String,
        content: String,
        tokenCount: Int = 0
    ): MessageEntity {
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role,
            content = content,
            tokenCount = tokenCount
        )
        messageDao.insert(message)
        return message
    }

    suspend fun getSessionMessages(sessionId: String): List<MessageEntity> {
        return messageDao.getBySession(sessionId)
    }

    suspend fun getRecentSessionMessages(sessionId: String, limit: Int): List<MessageEntity> {
        return messageDao.getRecentBySession(sessionId, limit).reversed()
    }

    // =====================================================================
    //  Task Management
    // =====================================================================

    suspend fun addTask(
        title: String,
        description: String = "",
        priority: Int = 1,
        dueDate: Long? = null
    ): TaskEntity {
        val task = TaskEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            priority = priority,
            dueDate = dueDate
        )
        taskDao.insert(task)
        return task
    }

    suspend fun getActiveTasks(): List<TaskEntity> {
        return taskDao.getActive()
    }

    suspend fun getAllTasks(): List<TaskEntity> {
        return taskDao.getAll()
    }

    suspend fun updateTaskStatus(taskId: String, status: String) {
        taskDao.updateStatus(taskId, status)
    }

    suspend fun deleteTask(taskId: String) {
        taskDao.deleteById(taskId)
    }

    // =====================================================================
    //  Tier Promotion
    // =====================================================================

    /**
     * Evaluate short-term facts for promotion to long-term.
     * Promotion criteria:
     *   - accessCount >= PROMOTION_ACCESS_THRESHOLD
     *   - importance  >= PROMOTION_IMPORTANCE_THRESHOLD
     */
    suspend fun runPromotion(): Int {
        val shortTermFacts = factDao.getByTier("short_term")
        var promoted = 0
        for (fact in shortTermFacts) {
            val shouldPromote =
                fact.accessCount >= PROMOTION_ACCESS_THRESHOLD ||
                fact.importance >= PROMOTION_IMPORTANCE_THRESHOLD
            if (shouldPromote) {
                val newImportance = (fact.importance + 0.1f).coerceAtMost(1.0f)
                factDao.updateTierAndImportance(fact.id, "long_term", newImportance)
                promoted++
                Log.d(TAG, "Promoted fact to long-term: ${fact.key}")
            }
        }
        if (promoted > 0) {
            Log.i(TAG, "Promoted $promoted facts from short-term to long-term")
        }
        return promoted
    }

    // =====================================================================
    //  Auto-Cleanup
    // =====================================================================

    /**
     * Remove expired short-term facts (older than 24h with low access count).
     * Facts that meet promotion criteria are promoted instead of deleted.
     */
    suspend fun runCleanup(): Int {
        // First promote any that qualify
        runPromotion()

        // Then expire the rest
        val cutoff = System.currentTimeMillis() - SHORT_TERM_TTL_MS
        val expired = factDao.getExpiredShortTerm(cutoff)
        var deleted = 0
        for (fact in expired) {
            // Last chance: promote if borderline
            if (fact.accessCount >= PROMOTION_ACCESS_THRESHOLD - 1 && fact.importance >= 0.5f) {
                factDao.updateTierAndImportance(fact.id, "long_term", fact.importance)
                Log.d(TAG, "Last-chance promoted fact: ${fact.key}")
            } else {
                factDao.delete(fact)
                deleted++
            }
        }
        if (deleted > 0) {
            Log.i(TAG, "Cleaned up $deleted expired short-term facts")
        }
        return deleted
    }

    private fun startCleanupLoop() {
        scope.launch {
            while (isActive) {
                delay(CLEANUP_INTERVAL_MS)
                try {
                    runCleanup()
                } catch (e: Exception) {
                    Log.w(TAG, "Cleanup cycle failed: ${e.message}")
                }
            }
        }
    }

    // =====================================================================
    //  Context Assembly (for LLM requests)
    // =====================================================================

    /**
     * Build a memory context block to inject into the LLM system prompt.
     * Pulls relevant long-term facts and recent episodic memories.
     */
    suspend fun assembleMemoryContext(sessionId: String, maxTokens: Int = 2000): String {
        val longTermFacts = factDao.getTopFacts(20)
        val recentEpisodes = episodeDao.getRecent(5)

        val builder = StringBuilder()

        if (longTermFacts.isNotEmpty()) {
            builder.appendLine("## Known Facts About the User")
            for (fact in longTermFacts) {
                builder.appendLine("- ${fact.key}: ${fact.value}")
            }
            builder.appendLine()
        }

        if (recentEpisodes.isNotEmpty()) {
            builder.appendLine("## Recent Session Summaries")
            for (ep in recentEpisodes) {
                builder.appendLine("- ${ep.summary}")
            }
            builder.appendLine()
        }

        // Rough token estimate: ~4 chars per token
        val result = builder.toString()
        val estimatedTokens = result.length / 4
        if (estimatedTokens > maxTokens) {
            // Truncate to fit budget
            val charLimit = maxTokens * 4
            return result.take(charLimit) + "\n...[memory truncated]"
        }

        return result
    }

    // =====================================================================
    //  Stats
    // =====================================================================

    suspend fun getStats(): MemoryStats {
        return MemoryStats(
            totalFacts = factDao.count(),
            shortTermFacts = factDao.countByTier("short_term"),
            longTermFacts = factDao.countByTier("long_term"),
            totalEpisodes = episodeDao.getRecent(Int.MAX_VALUE).size,
            activeSessions = sessionDao.getActiveSessions().size
        )
    }

    data class MemoryStats(
        val totalFacts: Int,
        val shortTermFacts: Int,
        val longTermFacts: Int,
        val totalEpisodes: Int,
        val activeSessions: Int
    )

    // =====================================================================
    //  Orchestrator Integration Methods
    // =====================================================================

    /**
     * Get relevant facts for a given query (used by orchestrator for memory injection).
     */
    suspend fun getRelevantFacts(query: String, limit: Int = 10): List<MemoryFactEntity> {
        // First try semantic search results mapped back to facts
        val searchResults = semanticSearch(query, limit)
        if (searchResults.isNotEmpty()) {
            val ids = searchResults.filter { it.source == "fact" }.map { it.id }
            val facts = ids.mapNotNull { factDao.getById(it) }
            if (facts.isNotEmpty()) return facts
        }
        // Fall back to keyword search
        val keywordResults = factDao.search(query)
        if (keywordResults.isNotEmpty()) return keywordResults.take(limit)
        // Fall back to top facts by importance
        return factDao.getTopFacts(limit)
    }

    /**
     * Store a conversation episode summary (used by orchestrator during context compaction).
     */
    suspend fun storeEpisode(sessionId: String, summaryText: String) {
        addEpisode(sessionId, summaryText, emotion = "neutral", outcome = "compacted")
    }

    /**
     * Extract and store factual information from a conversation turn.
     * Uses simple heuristic extraction (no LLM call to avoid recursion).
     */
    suspend fun extractAndStoreFacts(userMessage: String, assistantResponse: String) {
        // Simple heuristic: look for patterns like "my name is X", "I prefer X",
        // "I live in X", "I work at X", etc.
        val patterns = listOf(
            Regex("(?:my name is|i'm called|call me)\\s+([\\w]+)", RegexOption.IGNORE_CASE) to "name",
            Regex("(?:my (?:wife|husband|partner|spouse)(?:'s)? name is)\\s+([\\w]+)", RegexOption.IGNORE_CASE) to "spouse_name",
            Regex("(?:my (?:son|daughter|child|kid)(?:'s)? name is)\\s+([\\w]+)", RegexOption.IGNORE_CASE) to "child_name",
            Regex("(?:my (?:dog|cat|pet)(?:'s)? name is)\\s+([\\w]+)", RegexOption.IGNORE_CASE) to "pet_name",
            Regex("(?:my (?:brother|sister|mom|dad|mother|father)(?:'s)? name is)\\s+([\\w]+)", RegexOption.IGNORE_CASE) to "family_name",
            Regex("(?:i live in|i'm from|i'm in)\\s+([\\w\\s]+?)(?:\\.|,|$)", RegexOption.IGNORE_CASE) to "location",
            Regex("(?:i work at|i work for|my job is|i'm a)\\s+([\\w\\s]+?)(?:\\.|,|$)", RegexOption.IGNORE_CASE) to "work",
            Regex("(?:i prefer|i like|i love|my favorite)\\s+([\\w\\s]+?)(?:\\.|,|$)", RegexOption.IGNORE_CASE) to "preference",
            Regex("(?:i speak|my language is)\\s+([\\w\\s]+?)(?:\\.|,|$)", RegexOption.IGNORE_CASE) to "language",
            Regex("(?:my birthday is|i was born on)\\s+([\\w\\s]+?)(?:\\.|,|$)", RegexOption.IGNORE_CASE) to "birthday",
            Regex("(?:my email is|my address is|reach me at)\\s+([\\w@.\\-]+)", RegexOption.IGNORE_CASE) to "contact",
        )

        for ((pattern, category) in patterns) {
            val match = pattern.find(userMessage)
            if (match != null) {
                val value = match.groupValues[1].trim()
                if (value.length in 2..100) {
                    val key = "user_$category"
                    // Check if we already have this fact
                    val existing = factDao.getByKey(key)
                    if (existing == null || existing.value != value) {
                        addShortTermFact(key, value, category, importance = 0.6f)
                    }
                }
            }
        }
    }

    fun shutdown() {
        scope.cancel()
    }
}
