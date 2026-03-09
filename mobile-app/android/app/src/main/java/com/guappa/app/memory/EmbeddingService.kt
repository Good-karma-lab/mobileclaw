package com.guappa.app.memory

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Embedding service for Tier 5 semantic memory.
 *
 * Uses a lightweight keyword-based TF-IDF approach to generate vector embeddings
 * on-device without requiring ONNX Runtime or any ML framework. This provides
 * a functional semantic search capability that can be upgraded to a neural
 * embedding model (e.g. all-MiniLM-L6-v2 via ONNX) in a future milestone.
 *
 * Architecture:
 *   - Builds a vocabulary from all indexed content (capped at MAX_VOCAB_SIZE)
 *   - Computes TF-IDF vectors for each indexed document
 *   - Stores serialized vectors in Room via [EmbeddingDao]
 *   - Performs cosine similarity search across stored embeddings
 */
class EmbeddingService(private val db: GuappaDatabase) {

    private val TAG = "EmbeddingService"
    private val vocabMutex = Mutex()

    /** Global vocabulary: term -> index in the vector. */
    private val vocabulary = LinkedHashMap<String, Int>()

    /** Document frequency: term -> number of documents containing it. */
    private val documentFrequency = mutableMapOf<String, Int>()

    /** Total number of indexed documents (for IDF calculation). */
    private var totalDocuments = 0

    private val embeddingDao get() = db.embeddingDao()

    companion object {
        /** Maximum vocabulary size to keep vectors manageable on-device. */
        const val MAX_VOCAB_SIZE = 2048

        /** Minimum token length to include in vocabulary. */
        const val MIN_TOKEN_LENGTH = 2

        /** Stop words excluded from vocabulary to reduce noise. */
        private val STOP_WORDS = setOf(
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "shall", "can", "to", "of", "in", "for",
            "on", "with", "at", "by", "from", "as", "into", "through", "during",
            "before", "after", "above", "below", "between", "out", "off", "over",
            "under", "again", "further", "then", "once", "here", "there", "when",
            "where", "why", "how", "all", "each", "every", "both", "few", "more",
            "most", "other", "some", "such", "no", "nor", "not", "only", "own",
            "same", "so", "than", "too", "very", "just", "because", "but", "and",
            "or", "if", "while", "about", "up", "it", "its", "i", "me", "my",
            "we", "our", "you", "your", "he", "him", "his", "she", "her", "they",
            "them", "their", "this", "that", "these", "those", "what", "which"
        )
    }

    // =====================================================================
    //  Public Embedding API
    // =====================================================================

    /**
     * Generate a TF-IDF embedding vector for the given text.
     * The vector dimensions correspond to the current vocabulary.
     */
    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return@withContext FloatArray(0)

        vocabMutex.withLock {
            ensureVocabularyContains(tokens)
        }

        computeTfIdfVector(tokens)
    }

    /**
     * Compute cosine similarity between two embedding vectors.
     * Returns a value in [-1, 1] where 1 means identical direction.
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f

        val minLen = minOf(a.size, b.size)
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in 0 until minLen) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        // Account for extra dimensions in the longer vector
        for (i in minLen until a.size) {
            normA += a[i] * a[i]
        }
        for (i in minLen until b.size) {
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0f) dotProduct / denominator else 0f
    }

    // =====================================================================
    //  Index & Search
    // =====================================================================

    /**
     * Index a piece of content: compute its embedding and store in the database.
     *
     * @param sourceId  The ID of the source entity (fact ID, episode ID, etc.)
     * @param sourceType  The type of source ("fact", "episode", "message")
     * @param content  The text content to embed
     */
    suspend fun index(sourceId: String, sourceType: String, content: String) {
        try {
            val vector = embed(content)
            if (vector.isEmpty()) return

            val entity = EmbeddingEntity(
                id = UUID.randomUUID().toString(),
                sourceId = sourceId,
                sourceType = sourceType,
                vector = serializeVector(vector),
                content = content.take(500) // store truncated content for display
            )

            embeddingDao.upsertBySource(entity)
            Log.d(TAG, "Indexed embedding for $sourceType:$sourceId (${vector.size} dims)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to index embedding for $sourceType:$sourceId: ${e.message}")
        }
    }

    /**
     * Remove the embedding for a given source entity.
     */
    suspend fun removeIndex(sourceId: String) {
        embeddingDao.deleteBySourceId(sourceId)
    }

    /**
     * Perform semantic search across all stored embeddings.
     *
     * @param query  The search query text
     * @param limit  Maximum number of results to return
     * @return  List of [MemoryManager.MemorySearchResult] sorted by similarity score
     */
    suspend fun searchSimilar(
        query: String,
        limit: Int = 10
    ): List<MemoryManager.MemorySearchResult> = withContext(Dispatchers.Default) {
        val queryVector = embed(query)
        if (queryVector.isEmpty()) return@withContext emptyList()

        val allEmbeddings = embeddingDao.getAll()
        if (allEmbeddings.isEmpty()) return@withContext emptyList()

        val scored = allEmbeddings.mapNotNull { entity ->
            val storedVector = deserializeVector(entity.vector)
            if (storedVector.isEmpty()) return@mapNotNull null

            val similarity = cosineSimilarity(queryVector, storedVector)
            if (similarity <= 0f) return@mapNotNull null

            MemoryManager.MemorySearchResult(
                id = entity.sourceId,
                content = entity.content,
                source = entity.sourceType,
                category = entity.sourceType,
                score = similarity.toDouble()
            )
        }

        scored
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * Rebuild the vocabulary and re-index all stored embeddings.
     * Call this periodically or when the vocabulary drifts significantly.
     */
    suspend fun rebuildIndex() = withContext(Dispatchers.Default) {
        vocabMutex.withLock {
            vocabulary.clear()
            documentFrequency.clear()
            totalDocuments = 0
        }

        val allEmbeddings = embeddingDao.getAll()
        Log.i(TAG, "Rebuilding index for ${allEmbeddings.size} embeddings")

        // First pass: rebuild vocabulary from all stored content
        val allTokenSets = allEmbeddings.map { tokenize(it.content) }
        vocabMutex.withLock {
            for (tokens in allTokenSets) {
                totalDocuments++
                val uniqueTerms = tokens.toSet()
                for (term in uniqueTerms) {
                    documentFrequency[term] = (documentFrequency[term] ?: 0) + 1
                }
            }
            rebuildVocabularyFromFrequencies()
        }

        // Second pass: recompute vectors
        for ((i, entity) in allEmbeddings.withIndex()) {
            val vector = computeTfIdfVector(allTokenSets[i])
            if (vector.isNotEmpty()) {
                embeddingDao.updateVector(entity.id, serializeVector(vector))
            }
        }

        Log.i(TAG, "Index rebuilt: ${vocabulary.size} terms, $totalDocuments documents")
    }

    // =====================================================================
    //  Tokenization
    // =====================================================================

    /**
     * Tokenize text into lowercase terms, filtering stop words and short tokens.
     */
    internal fun tokenize(text: String): List<String> {
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= MIN_TOKEN_LENGTH && it !in STOP_WORDS }
    }

    // =====================================================================
    //  TF-IDF Computation
    // =====================================================================

    /**
     * Expand the vocabulary to include new terms from the given tokens.
     * Must be called under [vocabMutex].
     */
    private fun ensureVocabularyContains(tokens: List<String>) {
        totalDocuments++
        val uniqueTerms = tokens.toSet()

        for (term in uniqueTerms) {
            documentFrequency[term] = (documentFrequency[term] ?: 0) + 1
        }

        for (term in uniqueTerms) {
            if (term !in vocabulary && vocabulary.size < MAX_VOCAB_SIZE) {
                vocabulary[term] = vocabulary.size
            }
        }
    }

    /**
     * Rebuild vocabulary from document frequencies, keeping the top terms.
     * Must be called under [vocabMutex].
     */
    private fun rebuildVocabularyFromFrequencies() {
        vocabulary.clear()
        // Keep terms with highest document frequency (most common across docs)
        // but not too common (IDF would be low) — balance with a simple sort
        val sortedTerms = documentFrequency.entries
            .sortedByDescending { it.value }
            .take(MAX_VOCAB_SIZE)

        for ((i, entry) in sortedTerms.withIndex()) {
            vocabulary[entry.key] = i
        }
    }

    /**
     * Compute a TF-IDF vector for the given tokens against the current vocabulary.
     */
    private fun computeTfIdfVector(tokens: List<String>): FloatArray {
        if (vocabulary.isEmpty() || tokens.isEmpty()) return FloatArray(0)

        val vector = FloatArray(vocabulary.size)
        val termCounts = mutableMapOf<String, Int>()

        for (token in tokens) {
            termCounts[token] = (termCounts[token] ?: 0) + 1
        }

        val maxTf = termCounts.values.maxOrNull()?.toFloat() ?: 1f

        for ((term, count) in termCounts) {
            val index = vocabulary[term] ?: continue
            // Augmented TF to prevent bias towards longer documents
            val tf = 0.5f + 0.5f * (count.toFloat() / maxTf)
            // IDF with smoothing
            val df = documentFrequency[term] ?: 1
            val idf = ln((totalDocuments.toFloat() + 1f) / (df.toFloat() + 1f)) + 1f
            vector[index] = tf * idf
        }

        // L2 normalize
        val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }

        return vector
    }

    // =====================================================================
    //  Vector Serialization
    // =====================================================================

    /**
     * Serialize a FloatArray to ByteArray for Room storage.
     */
    internal fun serializeVector(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        for (v in vector) {
            buffer.putFloat(v)
        }
        return buffer.array()
    }

    /**
     * Deserialize a ByteArray back to FloatArray.
     */
    internal fun deserializeVector(bytes: ByteArray): FloatArray {
        if (bytes.isEmpty()) return FloatArray(0)
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val vector = FloatArray(bytes.size / 4)
        for (i in vector.indices) {
            vector[i] = buffer.getFloat()
        }
        return vector
    }
}
