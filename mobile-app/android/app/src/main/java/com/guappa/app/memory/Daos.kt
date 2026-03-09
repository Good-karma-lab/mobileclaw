package com.guappa.app.memory

import androidx.room.*

// ---------- Session DAO ----------

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC")
    suspend fun getActiveSessions(): List<SessionEntity>

    @Query("UPDATE sessions SET endedAt = :endedAt, summary = :summary WHERE id = :id")
    suspend fun endSession(id: String, endedAt: Long, summary: String?)

    @Query("UPDATE sessions SET tokenCount = :tokenCount WHERE id = :id")
    suspend fun updateTokenCount(id: String, tokenCount: Int)

    @Delete
    suspend fun delete(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE endedAt IS NOT NULL AND endedAt < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}

// ---------- Message DAO ----------

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySession(sessionId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentBySession(sessionId: String, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: String): Int

    @Query("SELECT COALESCE(SUM(tokenCount), 0) FROM messages WHERE sessionId = :sessionId")
    suspend fun totalTokensBySession(sessionId: String): Int

    @Query("DELETE FROM messages WHERE sessionId = :sessionId AND timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(sessionId: String, beforeTimestamp: Long)

    @Delete
    suspend fun delete(message: MessageEntity)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}

// ---------- Task DAO ----------

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY priority DESC, createdAt DESC")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY priority DESC, createdAt DESC")
    suspend fun getByStatus(status: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status IN ('pending', 'in_progress') ORDER BY priority DESC, createdAt DESC")
    suspend fun getActive(): List<TaskEntity>

    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}

// ---------- Memory Fact DAO ----------

@Dao
interface MemoryFactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fact: MemoryFactEntity)

    @Update
    suspend fun update(fact: MemoryFactEntity)

    @Query("SELECT * FROM memory_facts WHERE id = :id")
    suspend fun getById(id: String): MemoryFactEntity?

    @Query("SELECT * FROM memory_facts WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): MemoryFactEntity?

    @Query("SELECT * FROM memory_facts WHERE category = :category ORDER BY importance DESC, accessedAt DESC")
    suspend fun getByCategory(category: String): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts WHERE tier = :tier ORDER BY importance DESC, accessedAt DESC")
    suspend fun getByTier(tier: String): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts WHERE category = :category AND tier = :tier ORDER BY importance DESC")
    suspend fun getByCategoryAndTier(category: String, tier: String): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts ORDER BY importance DESC, accessedAt DESC LIMIT :limit")
    suspend fun getTopFacts(limit: Int = 50): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts WHERE value LIKE '%' || :query || '%' OR `key` LIKE '%' || :query || '%' ORDER BY importance DESC")
    suspend fun search(query: String): List<MemoryFactEntity>

    @Query("UPDATE memory_facts SET accessedAt = :now, accessCount = accessCount + 1 WHERE id = :id")
    suspend fun recordAccess(id: String, now: Long)

    @Query("UPDATE memory_facts SET tier = :tier, importance = :importance WHERE id = :id")
    suspend fun updateTierAndImportance(id: String, tier: String, importance: Float)

    @Query("SELECT * FROM memory_facts WHERE tier = 'short_term' AND createdAt < :cutoff")
    suspend fun getExpiredShortTerm(cutoff: Long): List<MemoryFactEntity>

    @Query("DELETE FROM memory_facts WHERE tier = 'short_term' AND createdAt < :cutoff AND accessCount < :minAccess")
    suspend fun deleteExpiredShortTerm(cutoff: Long, minAccess: Int)

    @Delete
    suspend fun delete(fact: MemoryFactEntity)

    @Query("DELETE FROM memory_facts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM memory_facts")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM memory_facts WHERE tier = :tier")
    suspend fun countByTier(tier: String): Int
}

// ---------- Episode DAO ----------

@Dao
interface EpisodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(episode: EpisodeEntity)

    @Update
    suspend fun update(episode: EpisodeEntity)

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getById(id: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun getBySession(sessionId: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE summary LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    suspend fun search(query: String): List<EpisodeEntity>

    @Delete
    suspend fun delete(episode: EpisodeEntity)

    @Query("DELETE FROM episodes WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}

// ---------- Embedding DAO ----------

@Dao
interface EmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: EmbeddingEntity)

    /**
     * Insert or replace embedding for a given source.
     * Uses REPLACE strategy so re-indexing the same source overwrites the old vector.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBySource(embedding: EmbeddingEntity)

    @Query("SELECT * FROM embeddings WHERE id = :id")
    suspend fun getById(id: String): EmbeddingEntity?

    @Query("SELECT * FROM embeddings WHERE sourceId = :sourceId LIMIT 1")
    suspend fun getBySourceId(sourceId: String): EmbeddingEntity?

    @Query("SELECT * FROM embeddings WHERE sourceType = :sourceType ORDER BY createdAt DESC")
    suspend fun getBySourceType(sourceType: String): List<EmbeddingEntity>

    @Query("SELECT * FROM embeddings ORDER BY createdAt DESC")
    suspend fun getAll(): List<EmbeddingEntity>

    @Query("UPDATE embeddings SET vector = :vector WHERE id = :id")
    suspend fun updateVector(id: String, vector: ByteArray)

    @Query("DELETE FROM embeddings WHERE sourceId = :sourceId")
    suspend fun deleteBySourceId(sourceId: String)

    @Delete
    suspend fun delete(embedding: EmbeddingEntity)

    @Query("DELETE FROM embeddings")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM embeddings")
    suspend fun count(): Int
}
