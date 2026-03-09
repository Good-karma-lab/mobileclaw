package com.guappa.app.memory

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Background worker that performs periodic memory consolidation.
 *
 * Runs every 6 hours via WorkManager periodic scheduling and performs:
 *   1. Tier promotion:  short-term -> long-term based on access/importance criteria
 *   2. Expiration:      removes stale short-term facts older than 24h with low access
 *   3. Deduplication:   merges facts with the same key, keeping the most recent value
 *   4. Episodic summary generation: creates summaries for ended sessions lacking them
 *   5. Importance decay: reduces importance of facts not accessed in 7+ days
 *
 * Promotion criteria (expanded beyond the basic MemoryManager.runPromotion):
 *   - accessCount >= 3
 *   - importance >= 0.7
 *   - age > 12 hours AND accessCount >= 2
 */
class MemoryConsolidationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "MemoryConsolidation"

    companion object {
        private const val WORK_NAME = "guappa_memory_consolidation"
        private const val REPEAT_INTERVAL_HOURS = 6L

        /** Promotion thresholds. */
        private const val PROMOTE_ACCESS_COUNT = 3
        private const val PROMOTE_IMPORTANCE = 0.7f
        private const val PROMOTE_AGE_MS = 12 * 60 * 60 * 1000L       // 12 hours
        private const val PROMOTE_AGE_ACCESS_COUNT = 2

        /** Expiration thresholds. */
        private const val EXPIRE_TTL_MS = 24 * 60 * 60 * 1000L        // 24 hours
        private const val EXPIRE_MAX_ACCESS = 1

        /** Importance decay. */
        private const val DECAY_INACTIVITY_MS = 7 * 24 * 60 * 60 * 1000L  // 7 days
        private const val DECAY_FACTOR = 0.9f

        /**
         * Schedule the periodic consolidation worker.
         * Safe to call multiple times — WorkManager keeps only one instance.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<MemoryConsolidationWorker>(
                REPEAT_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )

            Log.i("MemoryConsolidation", "Periodic consolidation scheduled (every ${REPEAT_INTERVAL_HOURS}h)")
        }

        /**
         * Cancel the periodic consolidation worker.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Memory consolidation started")
        val startTime = System.currentTimeMillis()

        return try {
            val db = GuappaDatabase.getInstance(applicationContext)
            val factDao = db.memoryFactDao()
            val sessionDao = db.sessionDao()
            val episodeDao = db.episodeDao()
            val now = System.currentTimeMillis()

            val promoted = runPromotion(factDao, now)
            val expired = runExpiration(factDao, now)
            val merged = runDeduplication(factDao)
            val summaries = runEpisodicSummaries(sessionDao, episodeDao)
            val decayed = runImportanceDecay(factDao, now)

            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "Consolidation complete in ${elapsed}ms: " +
                "promoted=$promoted, expired=$expired, merged=$merged, " +
                "summaries=$summaries, decayed=$decayed")

            Result.success(workDataOf(
                "promoted" to promoted,
                "expired" to expired,
                "merged" to merged,
                "summaries" to summaries,
                "decayed" to decayed,
                "elapsed_ms" to elapsed
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Consolidation failed: ${e.message}", e)
            Result.retry()
        }
    }

    // =====================================================================
    //  1. Tier Promotion (short-term -> long-term)
    // =====================================================================

    private suspend fun runPromotion(factDao: MemoryFactDao, now: Long): Int {
        val shortTermFacts = factDao.getByTier("short_term")
        var promoted = 0

        for (fact in shortTermFacts) {
            val age = now - fact.createdAt
            val shouldPromote =
                fact.accessCount >= PROMOTE_ACCESS_COUNT ||
                fact.importance >= PROMOTE_IMPORTANCE ||
                (age > PROMOTE_AGE_MS && fact.accessCount >= PROMOTE_AGE_ACCESS_COUNT)

            if (shouldPromote) {
                val boostedImportance = (fact.importance + 0.1f).coerceAtMost(1.0f)
                factDao.updateTierAndImportance(fact.id, "long_term", boostedImportance)
                promoted++
                Log.d(TAG, "Promoted: ${fact.key} (access=${fact.accessCount}, imp=${fact.importance}, age=${age / 3600000}h)")
            }
        }

        return promoted
    }

    // =====================================================================
    //  2. Expiration (stale short-term facts)
    // =====================================================================

    private suspend fun runExpiration(factDao: MemoryFactDao, now: Long): Int {
        val cutoff = now - EXPIRE_TTL_MS
        val expired = factDao.getExpiredShortTerm(cutoff)
        var deleted = 0

        for (fact in expired) {
            if (fact.accessCount <= EXPIRE_MAX_ACCESS && fact.importance < PROMOTE_IMPORTANCE) {
                factDao.delete(fact)
                deleted++
                Log.d(TAG, "Expired: ${fact.key} (access=${fact.accessCount}, imp=${fact.importance})")
            }
        }

        return deleted
    }

    // =====================================================================
    //  3. Deduplication (same key, different values)
    // =====================================================================

    private suspend fun runDeduplication(factDao: MemoryFactDao): Int {
        val allFacts = factDao.getTopFacts(Int.MAX_VALUE)
        val byKey = allFacts.groupBy { it.key }
        var merged = 0

        for ((key, facts) in byKey) {
            if (facts.size <= 1) continue

            // Keep the most recently accessed version
            val sorted = facts.sortedByDescending { it.accessedAt }
            val keeper = sorted.first()

            // Merge: bump importance based on duplicate count
            val mergedImportance = (keeper.importance + 0.05f * (facts.size - 1))
                .coerceAtMost(1.0f)
            val mergedAccessCount = facts.sumOf { it.accessCount }

            factDao.updateTierAndImportance(keeper.id, keeper.tier, mergedImportance)
            factDao.recordAccess(keeper.id, keeper.accessedAt) // keep the access count synced

            // Delete the duplicates
            for (duplicate in sorted.drop(1)) {
                factDao.delete(duplicate)
                merged++
                Log.d(TAG, "Merged duplicate: $key (kept id=${keeper.id})")
            }
        }

        return merged
    }

    // =====================================================================
    //  4. Episodic Summaries for Completed Sessions
    // =====================================================================

    private suspend fun runEpisodicSummaries(
        sessionDao: SessionDao,
        episodeDao: EpisodeDao
    ): Int {
        val recentSessions = sessionDao.getRecent(50)
        var generated = 0

        for (session in recentSessions) {
            // Only process ended sessions
            if (session.endedAt == null) continue

            // Check if this session already has an episode
            val existing = episodeDao.getBySession(session.id)
            if (existing.isNotEmpty()) continue

            // Generate a simple extractive summary from the session data
            val summary = buildSessionSummary(session)
            if (summary.isNotBlank()) {
                episodeDao.insert(EpisodeEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    sessionId = session.id,
                    summary = summary,
                    emotion = "neutral",
                    outcome = "consolidation"
                ))
                generated++
                Log.d(TAG, "Generated episode for session ${session.id}")
            }
        }

        return generated
    }

    private fun buildSessionSummary(session: SessionEntity): String {
        // Use session-level summary if available
        if (!session.summary.isNullOrBlank()) {
            return session.summary
        }

        // Fallback: basic metadata summary
        val duration = if (session.endedAt != null) {
            val durationMs = session.endedAt - session.startedAt
            val minutes = durationMs / 60_000
            if (minutes > 0) "${minutes}min" else "<1min"
        } else {
            "unknown duration"
        }

        val title = session.title.ifBlank { "Untitled session" }
        return "$title ($duration, ${session.tokenCount} tokens)"
    }

    // =====================================================================
    //  5. Importance Decay
    // =====================================================================

    private suspend fun runImportanceDecay(factDao: MemoryFactDao, now: Long): Int {
        val inactivityCutoff = now - DECAY_INACTIVITY_MS
        val allFacts = factDao.getTopFacts(Int.MAX_VALUE)
        var decayed = 0

        for (fact in allFacts) {
            if (fact.accessedAt < inactivityCutoff && fact.importance > 0.1f) {
                val newImportance = (fact.importance * DECAY_FACTOR).coerceAtLeast(0.1f)
                if (newImportance < fact.importance) {
                    factDao.updateTierAndImportance(fact.id, fact.tier, newImportance)
                    decayed++
                }
            }
        }

        return decayed
    }
}
