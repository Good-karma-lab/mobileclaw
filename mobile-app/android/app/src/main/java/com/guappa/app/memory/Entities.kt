package com.guappa.app.memory

import androidx.room.*

// ---------- Session ----------

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val title: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val summary: String? = null,
    val tokenCount: Int = 0
)

// ---------- Message ----------

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId"), Index("timestamp")]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tokenCount: Int = 0
)

// ---------- Task ----------

@Entity(tableName = "tasks", indices = [Index("status"), Index("priority")])
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val status: String = "pending",   // pending, in_progress, completed, cancelled
    val priority: Int = 0,            // 0=low, 1=normal, 2=high, 3=urgent
    val dueDate: Long? = null,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// ---------- Memory Fact ----------

@Entity(
    tableName = "memory_facts",
    indices = [Index("category"), Index("tier"), Index("key")]
)
data class MemoryFactEntity(
    @PrimaryKey
    val id: String,
    val key: String,
    val value: String,
    val category: String,             // preference, fact, relationship, routine, date
    val tier: String = "short_term",  // short_term, long_term
    val importance: Float = 0.5f,     // 0.0-1.0
    val createdAt: Long = System.currentTimeMillis(),
    val accessedAt: Long = System.currentTimeMillis(),
    val accessCount: Int = 0
)

// ---------- Episode ----------

@Entity(
    tableName = "episodes",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId"), Index("timestamp")]
)
data class EpisodeEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val summary: String,
    val emotion: String = "neutral",  // neutral, positive, negative, mixed
    val outcome: String = "",         // success, failure, partial, unknown
    val timestamp: Long = System.currentTimeMillis()
)

// ---------- Embedding (Tier 5: Semantic Memory) ----------

@Entity(
    tableName = "embeddings",
    indices = [Index("sourceId"), Index("sourceType")]
)
data class EmbeddingEntity(
    @PrimaryKey
    val id: String,
    val sourceId: String,         // ID of the source entity (fact, episode, etc.)
    val sourceType: String,       // "fact", "episode", "message"
    val vector: ByteArray,        // serialized FloatArray (little-endian)
    val content: String = "",     // truncated source content for display in search results
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
