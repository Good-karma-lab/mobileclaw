# Phase 7: Memory & Context — Auto-Summarization, Long-Term Memory, Recursive LLM

**Date**: 2026-03-06
**Status**: Proposal
**Depends On**: Phase 1 (Foundation), Phase 2 (Provider Router for embedding models)
**Blocks**: Phase 8 (Documentation)

---

## 1. Objective

Build a multi-tier memory system that:
1. **Manages context length** intelligently — never overflow, never lose important info
2. **Auto-summarizes** old conversations to fit context window
3. **Recursive summarization** — multi-level compression (recent → summary → super-summary)
4. **Long-term memory** — extract and store persistent facts, preferences, and patterns
5. **Episodic memory** — remember past task outcomes for future reference
6. **RAG** — vector search over all memories for relevant context injection
7. **Memory consolidation** — background process to distill short-term → long-term

---

## 2. Research Checklist

- [ ] Context window management: MemGPT, LangChain ConversationSummaryBufferMemory
- [ ] Recursive summarization: Map-Reduce, Refine, and hierarchical summarization patterns
- [ ] Sliding window + summary hybrid: LangChain ConversationSummaryBufferMemory pattern
- [ ] Long-term memory extraction: entity extraction, preference learning, fact storage
- [ ] Episodic memory: task outcome storage, experience replay patterns
- [ ] On-device embedding models: all-MiniLM-L6-v2, gte-small, nomic-embed-text (ONNX)
- [ ] Vector similarity search on Android: SQLite FTS5 + manual cosine similarity, or HNSW
- [ ] Room database optimization for high-frequency writes (WAL mode)
- [ ] Memory consolidation: spaced repetition, importance scoring, decay functions
- [ ] MemGPT/Letta architecture: self-editing memory, inner/outer monologue
- [ ] Token-aware chunking strategies for summarization

---

## 3. Architecture

### 3.1 Multi-Tier Memory Model

```
┌─────────────────────────────────────────────────────────┐
│                    CONTEXT WINDOW                        │
│  (what the LLM sees in each request)                    │
│                                                         │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────────────┐  │
│  │ System       │ │ Tool Schemas │ │ Retrieved        │  │
│  │ Prompt       │ │ (~3K tokens) │ │ Memories         │  │
│  │ (~2K tokens) │ │              │ │ (~5K tokens)     │  │
│  └─────────────┘ └──────────────┘ └──────────────────┘  │
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │ Conversation History                              │   │
│  │                                                   │   │
│  │  ┌────────────────────────────────────────────┐   │   │
│  │  │ Super-Summary (oldest)    ~500 tokens      │   │   │
│  │  │ "User prefers Russian. Uses Guappa for     │   │   │
│  │  │  scheduling and email. Had 5 conversations │   │   │
│  │  │  about restaurant recommendations..."       │   │   │
│  │  └────────────────────────────────────────────┘   │   │
│  │                                                   │   │
│  │  ┌────────────────────────────────────────────┐   │   │
│  │  │ Summary (older turns)     ~2K tokens       │   │   │
│  │  │ "Earlier today, user asked to set alarm    │   │   │
│  │  │  for 7am (done). Then asked about weather  │   │   │
│  │  │  (sunny, 22°C). Then asked to draft email  │   │   │
│  │  │  to boss about vacation (sent)."           │   │   │
│  │  └────────────────────────────────────────────┘   │   │
│  │                                                   │   │
│  │  ┌────────────────────────────────────────────┐   │   │
│  │  │ Recent Messages (verbatim) ~remaining      │   │   │
│  │  │ [User]: Закажи пиццу                       │   │   │
│  │  │ [Tool]: web_search("pizza delivery nearby")│   │   │
│  │  │ [Tool Result]: Found 3 options...          │   │   │
│  │  │ [Assistant]: Нашла 3 варианта: ...         │   │   │
│  │  │ [User]: Первый вариант                     │   │   │
│  │  └────────────────────────────────────────────┘   │   │
│  └──────────────────────────────────────────────────┘   │
│                                                         │
│  Reserve buffer: ~4K tokens (for LLM response)          │
└─────────────────────────────────────────────────────────┘

         ▲ Context Assembly (each request)
         │
┌────────┴────────────────────────────────────────────────┐
│                   MEMORY TIERS                           │
│                                                         │
│  Tier 1: Working Memory (current context window)        │
│  ├── Recent messages (verbatim, last N turns)           │
│  ├── Current session summary                            │
│  └── Active task state                                  │
│                                                         │
│  Tier 2: Short-Term Memory (SQLite, session-scoped)     │
│  ├── Full conversation history (all messages, verbatim) │
│  ├── Session summaries (auto-generated)                 │
│  ├── Tool call history with results                     │
│  └── TTL: 30 days (configurable)                        │
│                                                         │
│  Tier 3: Long-Term Memory (SQLite + FTS5, persistent)   │
│  ├── User facts ("User lives in Moscow")                │
│  ├── User preferences ("Prefers dark mode")             │
│  ├── Learned patterns ("Usually sets alarm at 7am")     │
│  ├── Relationships ("Мама = +7-999-123-4567")           │
│  └── TTL: permanent (until user deletes)                │
│                                                         │
│  Tier 4: Episodic Memory (SQLite, task outcomes)        │
│  ├── Past task executions (what was asked, what happened)│
│  ├── Success/failure outcomes                           │
│  ├── Tool effectiveness data                            │
│  └── TTL: 90 days                                       │
│                                                         │
│  Tier 5: Semantic Memory (Vector Store, embeddings)     │
│  ├── Embedded conversation chunks                       │
│  ├── Embedded facts and preferences                     │
│  ├── Embedded task descriptions                         │
│  └── Used for RAG retrieval                             │
└─────────────────────────────────────────────────────────┘
```

### 3.2 Module Structure

```
android/app/src/main/java/com/guappa/app/
└── memory/
    ├── MemoryManager.kt              — unified memory interface, orchestrates all tiers
    ├── ContextAssembler.kt           — builds context from all memory sources for each LLM request
    ├── ContextBudgetAllocator.kt     — allocates token budget across context sections
    │
    ├── working/
    │   ├── WorkingMemory.kt          — current context window state
    │   ├── SlidingWindow.kt          — keep last N messages verbatim
    │   └── SmartTruncator.kt         — intelligent truncation (drop tool results first)
    │
    ├── summarization/
    │   ├── AutoSummarizer.kt         — triggers and orchestrates summarization
    │   ├── IncrementalSummarizer.kt  — summarize new messages into existing summary
    │   ├── RecursiveSummarizer.kt    — multi-level: summary → super-summary
    │   ├── MapReduceSummarizer.kt    — split long history → summarize each → merge
    │   ├── SummarizationPrompts.kt   — prompt templates for summarization
    │   └── TokenAwareChunker.kt      — split text into token-bounded chunks
    │
    ├── longterm/
    │   ├── LongTermMemory.kt         — fact/preference storage
    │   ├── FactExtractor.kt          — extract facts from conversations (LLM-powered)
    │   ├── PreferenceTracker.kt      — learn and update user preferences
    │   ├── RelationshipMapper.kt     — map contacts/relationships mentioned in chat
    │   ├── MemoryImportance.kt       — score memory importance (recency, frequency, explicit)
    │   └── MemoryDecay.kt            — decay function for stale memories
    │
    ├── episodic/
    │   ├── EpisodicMemory.kt         — past task execution records
    │   ├── TaskOutcomeStore.kt       — store task results and effectiveness
    │   └── ExperienceRetriever.kt    — find similar past tasks for context
    │
    ├── vector/
    │   ├── VectorStore.kt            — embedding storage and similarity search
    │   ├── EmbeddingEngine.kt        — on-device embedding generation (ONNX)
    │   ├── ChunkEmbedder.kt          — embed conversation chunks
    │   ├── CosineSimilarity.kt       — manual cosine similarity computation
    │   └── HNSWIndex.kt             — approximate nearest neighbor search
    │
    ├── consolidation/
    │   ├── MemoryConsolidator.kt     — background job: short-term → long-term
    │   ├── FactVerifier.kt           — verify and update extracted facts
    │   ├── DuplicateDetector.kt      — deduplicate similar memories
    │   └── ConsolidationScheduler.kt — schedule consolidation during idle time
    │
    ├── persistence/
    │   ├── MemoryDatabase.kt         — Room database (all memory tables)
    │   ├── ConversationDao.kt        — full conversation history
    │   ├── SummaryDao.kt             — session summaries
    │   ├── FactDao.kt                — long-term facts
    │   ├── EpisodeDao.kt             — task outcomes
    │   ├── EmbeddingDao.kt           — vector embeddings
    │   └── MemoryMigrations.kt       — schema migrations
    │
    └── export/
        ├── MemoryExporter.kt         — export all memory to JSON
        ├── MemoryImporter.kt         — import memory from JSON
        └── MemoryWiper.kt            — secure memory deletion
```

---

## 4. Auto-Summarization

### 4.1 When to Summarize

```kotlin
class AutoSummarizer(
    private val contextBudget: ContextBudgetAllocator,
    private val summarizer: IncrementalSummarizer,
    private val tokenCounter: TokenCounter,
) {
    /**
     * Summarization triggers:
     * 1. Context usage > 80% of conversation budget
     * 2. Message count > 40 (even if within token budget)
     * 3. Session idle > 30 minutes (pre-emptive compaction)
     * 4. Session type is BACKGROUND_TASK and context > 60% (these need less history)
     */
    suspend fun checkAndSummarize(session: GuappaSession): SummarizationResult {
        val budget = contextBudget.allocate(session)
        val currentTokens = tokenCounter.countConversation(session.messages)
        val usage = currentTokens.toFloat() / budget.conversationBudget

        if (usage < 0.8f && session.messages.size < 40) {
            return SummarizationResult.NotNeeded
        }

        return performSummarization(session, budget)
    }
}
```

### 4.2 Incremental Summarization

Instead of re-summarizing everything, maintain a running summary and only summarize new messages:

```kotlin
class IncrementalSummarizer(
    private val providerRouter: ProviderRouter,
) {
    /**
     * Summarize messages that are being moved out of the verbatim window.
     *
     * Input:
     * - existingSummary: current summary (may be empty for first summarization)
     * - messagesToSummarize: messages being evicted from verbatim window
     *
     * Output:
     * - updatedSummary: existing summary + new messages compressed
     *
     * This is O(summary_size + new_messages) per call, NOT O(total_history).
     */
    suspend fun summarize(
        existingSummary: String?,
        messagesToSummarize: List<Message>,
    ): String {
        val prompt = buildString {
            appendLine("You are a conversation summarizer. Your job is to update a running summary with new messages.")
            appendLine()
            if (existingSummary != null) {
                appendLine("Current summary of the conversation so far:")
                appendLine(existingSummary)
                appendLine()
            }
            appendLine("New messages to incorporate into the summary:")
            for (msg in messagesToSummarize) {
                appendLine("[${msg.role}]: ${msg.content}")
            }
            appendLine()
            appendLine("Instructions:")
            appendLine("1. Update the summary to include the key points from the new messages")
            appendLine("2. Preserve important facts, decisions, task outcomes, and user preferences")
            appendLine("3. Remove redundant or superseded information")
            appendLine("4. Keep the summary concise but complete")
            appendLine("5. Format as a brief narrative paragraph")
            appendLine("6. DO NOT include tool call details — just the outcomes")
            appendLine("7. Maximum 500 words")
        }

        // Use a cheap/fast model for summarization (e.g., Gemini 2.5 Flash, GPT-4o-mini)
        val response = providerRouter.chatSimple(
            model = config.summarizationModel,  // configurable, default: cheapest available
            systemPrompt = "You are a precise conversation summarizer.",
            userMessage = prompt,
            maxTokens = 1024,
        )

        return response.text
    }
}
```

### 4.3 Recursive Summarization (Multi-Level)

When even summaries get too long, create super-summaries:

```
Level 0: Verbatim messages (last 20 turns)        — ~8K tokens
Level 1: Summary (turns 21-100)                    — ~2K tokens
Level 2: Super-summary (turns 101-500)             — ~500 tokens
Level 3: Ultra-summary (turns 500+)                — ~200 tokens
```

```kotlin
class RecursiveSummarizer(
    private val summarizer: IncrementalSummarizer,
    private val tokenCounter: TokenCounter,
) {
    data class SummaryLevel(
        val level: Int,
        val text: String,
        val tokenCount: Int,
        val messageRange: IntRange,  // original message indices covered
    )

    /**
     * When Level 1 summary exceeds its budget, compress it into Level 2.
     * Level 2 exceeds budget → Level 3.
     * Max 4 levels.
     */
    suspend fun compressIfNeeded(
        summaries: List<SummaryLevel>,
        budgets: Map<Int, Int>,  // level → max tokens
    ): List<SummaryLevel> {
        val result = summaries.toMutableList()

        for (level in 1..3) {
            val current = result.filter { it.level == level }
            val totalTokens = current.sumOf { it.tokenCount }
            val budget = budgets[level] ?: Int.MAX_VALUE

            if (totalTokens > budget) {
                // Merge all summaries at this level into one at level+1
                val mergedText = current.joinToString("\n\n") { it.text }
                val superSummary = summarizer.summarize(
                    existingSummary = result.find { it.level == level + 1 }?.text,
                    messagesToSummarize = listOf(
                        Message(role = "system", content = "Previous summary (level $level):\n$mergedText")
                    ),
                )
                // Remove old level summaries, add new level+1
                result.removeAll { it.level == level }
                result.add(SummaryLevel(
                    level = level + 1,
                    text = superSummary,
                    tokenCount = tokenCounter.count(superSummary),
                    messageRange = current.first().messageRange.first..current.last().messageRange.last,
                ))
            }
        }

        return result
    }
}
```

### 4.4 Map-Reduce Summarization (for very long histories)

For initial import or processing very long conversation histories:

```kotlin
class MapReduceSummarizer(
    private val summarizer: IncrementalSummarizer,
    private val chunker: TokenAwareChunker,
) {
    /**
     * Split long history into chunks → summarize each (MAP) → merge summaries (REDUCE)
     *
     * Use when:
     * - Importing existing conversation history
     * - Recovering from missed summarization (app was killed)
     * - Processing > 100 messages at once
     */
    suspend fun mapReduceSummarize(messages: List<Message>): String {
        // MAP: Split into 20-message chunks, summarize each
        val chunks = messages.chunked(20)
        val chunkSummaries = chunks.map { chunk ->
            summarizer.summarize(existingSummary = null, messagesToSummarize = chunk)
        }

        // REDUCE: Merge chunk summaries into final summary
        if (chunkSummaries.size <= 3) {
            return chunkSummaries.joinToString("\n\n")
        }

        // Recursive reduce if too many chunk summaries
        return summarizer.summarize(
            existingSummary = null,
            messagesToSummarize = chunkSummaries.map {
                Message(role = "system", content = it)
            },
        )
    }
}
```

---

## 5. Smart Truncation

When context is tight and summarization hasn't run yet (or is running), use smart truncation:

```kotlin
class SmartTruncator {
    /**
     * Priority of what to keep (highest first):
     * 1. System prompt (always keep)
     * 2. Last user message (always keep)
     * 3. Last assistant response (always keep)
     * 4. Recent tool calls + results (last 3 turns)
     * 5. Recent conversation (last 10 messages)
     * 6. Session summary
     * 7. Older tool results (truncate to first 200 chars each)
     * 8. Older messages
     *
     * Priority of what to DROP first:
     * 1. Old tool results (they're reproducible)
     * 2. Old tool calls (the outcome matters, not the call)
     * 3. Old assistant messages (we have summaries)
     * 4. Old user messages (we have summaries)
     */
    fun truncate(
        messages: List<Message>,
        targetTokens: Int,
        tokenCounter: TokenCounter,
    ): List<Message> {
        var currentTokens = messages.sumOf { tokenCounter.count(it.content) }

        if (currentTokens <= targetTokens) return messages

        val result = messages.toMutableList()

        // Phase 1: Truncate old tool results to 200 chars
        for (i in result.indices) {
            if (currentTokens <= targetTokens) break
            if (result[i].role == "tool" && i < result.size - 6) {
                val truncated = result[i].content.take(200) + "...[truncated]"
                val saved = tokenCounter.count(result[i].content) - tokenCounter.count(truncated)
                result[i] = result[i].copy(content = truncated)
                currentTokens -= saved
            }
        }

        // Phase 2: Remove old tool call/result pairs
        val toRemove = mutableListOf<Int>()
        for (i in result.indices) {
            if (currentTokens <= targetTokens) break
            if (result[i].role == "tool" && i < result.size - 10) {
                toRemove.add(i)
                currentTokens -= tokenCounter.count(result[i].content)
            }
        }
        toRemove.reversed().forEach { result.removeAt(it) }

        // Phase 3: Remove oldest messages (keep last 6)
        while (currentTokens > targetTokens && result.size > 6) {
            currentTokens -= tokenCounter.count(result[1].content)  // [0] is system
            result.removeAt(1)
        }

        return result
    }
}
```

---

## 6. Long-Term Memory

### 6.1 Fact Extraction

After each conversation turn, extract persistent facts:

```kotlin
class FactExtractor(
    private val providerRouter: ProviderRouter,
    private val factDao: FactDao,
) {
    /**
     * Extract facts from the last few messages.
     * Run after every 5 messages or at session end.
     *
     * Facts are things the agent should remember permanently:
     * - User's name, location, timezone
     * - Preferences (language, communication style)
     * - Relationships ("Мама" = specific contact)
     * - Routines ("usually wakes up at 7am")
     * - Important dates
     */
    suspend fun extractFacts(recentMessages: List<Message>): List<Fact> {
        val prompt = """
            Analyze these conversation messages and extract any persistent facts
            about the user that should be remembered for future conversations.

            Messages:
            ${recentMessages.joinToString("\n") { "[${it.role}]: ${it.content}" }}

            Extract facts as JSON array:
            [
              {"category": "preference|fact|relationship|routine|date", "key": "short_key", "value": "fact text", "confidence": 0.0-1.0}
            ]

            Rules:
            - Only extract facts the user explicitly stated or strongly implied
            - Do not infer personality traits or make assumptions
            - Keep values concise and factual
            - Confidence < 0.5 for implied facts, >= 0.8 for explicit statements
            - Return empty array [] if no new facts found
        """.trimIndent()

        val response = providerRouter.chatSimple(
            model = config.factExtractionModel,
            userMessage = prompt,
            maxTokens = 512,
        )

        return parseFacts(response.text)
    }

    /**
     * Merge new facts with existing ones.
     * Update if newer info contradicts older.
     */
    suspend fun mergeFacts(newFacts: List<Fact>) {
        for (fact in newFacts) {
            val existing = factDao.findByKey(fact.category, fact.key)
            if (existing != null) {
                if (fact.confidence >= existing.confidence) {
                    factDao.update(existing.copy(
                        value = fact.value,
                        confidence = fact.confidence,
                        updatedAt = System.currentTimeMillis(),
                        version = existing.version + 1,
                    ))
                }
            } else {
                factDao.insert(fact.toEntity())
            }
        }
    }
}
```

### 6.2 Long-Term Memory Schema

```kotlin
@Entity(tableName = "facts")
data class FactEntity(
    @PrimaryKey val id: String,
    val category: String,      // "preference", "fact", "relationship", "routine", "date"
    val key: String,           // "language", "name", "mom_phone", "wake_time"
    val value: String,         // "Russian", "Алексей", "+7-999-123-4567", "7:00 AM"
    val confidence: Float,     // 0.0-1.0
    val source: String,        // session ID where fact was extracted
    val createdAt: Long,
    val updatedAt: Long,
    val accessCount: Int = 0,  // how often this fact was retrieved
    val lastAccessedAt: Long = 0,
    val version: Int = 1,
    val decayScore: Float = 1.0f,  // decays over time if not accessed
)
```

### 6.3 Memory Importance Scoring

```kotlin
class MemoryImportance {
    /**
     * Score = recency * frequency * explicitness * relevance
     *
     * - Recency: exponential decay from last access (half-life: 7 days)
     * - Frequency: log(accessCount + 1)
     * - Explicitness: confidence score (0-1)
     * - Relevance: cosine similarity to current query (if vector search)
     */
    fun score(fact: FactEntity, currentTime: Long, queryEmbedding: FloatArray? = null): Float {
        val daysSinceAccess = (currentTime - fact.lastAccessedAt) / 86_400_000f
        val recency = exp(-0.1f * daysSinceAccess)  // half-life ~7 days
        val frequency = ln(fact.accessCount.toFloat() + 1f) / ln(10f)
        val explicitness = fact.confidence
        val relevance = queryEmbedding?.let {
            // cosine similarity with fact embedding
            cosineSimilarity(it, getEmbedding(fact.id))
        } ?: 1.0f

        return recency * (0.3f + 0.3f * frequency) * (0.5f + 0.5f * explicitness) * relevance
    }
}
```

---

## 7. Episodic Memory

### 7.1 Task Outcome Storage

```kotlin
@Entity(tableName = "episodes")
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val taskDescription: String,     // "Set alarm for 7am"
    val toolsUsed: String,           // JSON: ["set_alarm"]
    val outcome: EpisodeOutcome,     // SUCCESS, FAILURE, PARTIAL
    val outcomeDescription: String,  // "Alarm set successfully at 7:00 AM"
    val errorMessage: String?,       // if failed: "Permission denied: SET_ALARM"
    val duration_ms: Long,
    val tokenCost: Int,              // total tokens used
    val createdAt: Long,
    val embeddingId: String?,        // reference to vector store
)

enum class EpisodeOutcome { SUCCESS, FAILURE, PARTIAL, CANCELLED }
```

### 7.2 Experience Retrieval

When the agent faces a similar task, retrieve relevant past experiences:

```kotlin
class ExperienceRetriever(
    private val episodeDao: EpisodeDao,
    private val vectorStore: VectorStore,
    private val embeddingEngine: EmbeddingEngine,
) {
    /**
     * Find similar past tasks to inform current execution.
     *
     * Use cases:
     * - User asks "set alarm" → retrieve past alarm tasks (what worked, what failed)
     * - User asks to email boss → retrieve past email tasks (preferred format)
     * - Tool fails → check if this tool has failed before and how it was resolved
     */
    suspend fun findSimilar(taskDescription: String, limit: Int = 3): List<EpisodeEntity> {
        val queryEmbedding = embeddingEngine.embed(taskDescription)
        val similarIds = vectorStore.search(queryEmbedding, limit = limit, minScore = 0.7f)
        return episodeDao.getByIds(similarIds)
    }

    /**
     * Format experiences as context for the LLM.
     */
    fun formatAsContext(episodes: List<EpisodeEntity>): String {
        if (episodes.isEmpty()) return ""
        return buildString {
            appendLine("Relevant past experiences:")
            for (ep in episodes) {
                appendLine("- Task: ${ep.taskDescription}")
                appendLine("  Outcome: ${ep.outcome} — ${ep.outcomeDescription}")
                if (ep.errorMessage != null) {
                    appendLine("  Error: ${ep.errorMessage}")
                }
            }
        }
    }
}
```

---

## 8. Vector Store & RAG

### 8.1 On-Device Embedding

```kotlin
class EmbeddingEngine(
    private val context: Context,
) {
    // Use all-MiniLM-L6-v2 (22MB, 384-dim) or gte-small (67MB, 384-dim)
    // via ONNX Runtime Mobile
    private var session: OrtSession? = null

    suspend fun initialize() {
        val modelPath = downloadOrCacheModel("all-MiniLM-L6-v2.onnx")
        val env = OrtEnvironment.getEnvironment()
        session = env.createSession(modelPath)
    }

    suspend fun embed(text: String): FloatArray {
        val tokenized = tokenize(text)  // simple whitespace + wordpiece tokenizer
        val inputTensor = OnnxTensor.createTensor(env, tokenized)
        val result = session!!.run(mapOf("input_ids" to inputTensor))
        return result[0].value as FloatArray  // 384-dim embedding
    }

    suspend fun embedBatch(texts: List<String>): List<FloatArray> {
        return texts.map { embed(it) }  // batch processing for efficiency
    }
}
```

### 8.2 Vector Similarity Search

```kotlin
class VectorStore(
    private val embeddingDao: EmbeddingDao,
) {
    /**
     * Store embedding with reference to source.
     */
    suspend fun store(
        sourceId: String,
        sourceType: SourceType,  // CONVERSATION, FACT, EPISODE
        text: String,
        embedding: FloatArray,
    ) {
        embeddingDao.insert(EmbeddingEntity(
            id = UUID.randomUUID().toString(),
            sourceId = sourceId,
            sourceType = sourceType,
            text = text,
            embedding = embedding.toByteArray(),  // store as BLOB
            createdAt = System.currentTimeMillis(),
        ))
    }

    /**
     * Brute-force cosine similarity search.
     * Fast enough for < 100K embeddings on modern devices (~50ms).
     * For > 100K, switch to HNSW approximate search.
     */
    suspend fun search(
        query: FloatArray,
        limit: Int = 5,
        minScore: Float = 0.5f,
        sourceType: SourceType? = null,
    ): List<SearchResult> {
        val candidates = if (sourceType != null) {
            embeddingDao.getByType(sourceType)
        } else {
            embeddingDao.getAll()
        }

        return candidates
            .map { entity ->
                SearchResult(
                    sourceId = entity.sourceId,
                    text = entity.text,
                    score = cosineSimilarity(query, entity.embedding.toFloatArray()),
                )
            }
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
            .take(limit)
    }
}
```

---

## 9. Context Assembly

### 9.1 ContextAssembler — The Core Pipeline

```kotlin
class ContextAssembler(
    private val budgetAllocator: ContextBudgetAllocator,
    private val workingMemory: WorkingMemory,
    private val longTermMemory: LongTermMemory,
    private val episodicMemory: EpisodicMemory,
    private val vectorStore: VectorStore,
    private val embeddingEngine: EmbeddingEngine,
    private val persona: GuappaPersona,
    private val toolEngine: ToolEngine,
    private val tokenCounter: TokenCounter,
) {
    /**
     * Build the complete context for an LLM request.
     * Called before every LLM call.
     *
     * Pipeline:
     * 1. Allocate token budget
     * 2. Build system prompt (persona + device context)
     * 3. Retrieve relevant long-term memories (RAG)
     * 4. Retrieve relevant past experiences
     * 5. Build tool schemas (only enabled tools)
     * 6. Assemble conversation history (summaries + verbatim)
     * 7. Verify total is within budget → truncate if needed
     */
    suspend fun assemble(session: GuappaSession): AssembledContext {
        val budget = budgetAllocator.allocate(session)
        val lastUserMessage = session.messages.lastOrNull { it.role == "user" }

        // 1. System prompt
        val systemPrompt = persona.buildSystemPrompt(session)
        val systemTokens = tokenCounter.count(systemPrompt)

        // 2. Tool schemas
        val toolSchemas = toolEngine.getAvailableToolSchemas()
        val toolTokens = tokenCounter.count(toolSchemas.joinToString("\n"))

        // 3. Retrieved memories (RAG)
        val memories = if (lastUserMessage != null) {
            val queryEmbedding = embeddingEngine.embed(lastUserMessage.content)

            // Retrieve relevant facts
            val facts = longTermMemory.retrieveRelevant(
                queryEmbedding,
                limit = 10,
                maxTokens = budget.memoryBudget / 2,
            )

            // Retrieve relevant past experiences
            val experiences = episodicMemory.findSimilar(
                lastUserMessage.content,
                limit = 3,
            )

            // Retrieve semantically similar conversation chunks
            val relatedChunks = vectorStore.search(
                queryEmbedding,
                limit = 5,
                sourceType = SourceType.CONVERSATION,
            )

            MemoryRetrieval(facts, experiences, relatedChunks)
        } else {
            MemoryRetrieval.EMPTY
        }

        val memoryText = memories.format()
        val memoryTokens = tokenCounter.count(memoryText)

        // 4. Conversation history (remaining budget)
        val conversationBudget = budget.totalTokens - systemTokens - toolTokens -
                                  memoryTokens - budget.reserveBuffer
        val conversationHistory = workingMemory.getHistory(
            session,
            maxTokens = conversationBudget,
        )

        return AssembledContext(
            systemPrompt = systemPrompt,
            memories = memoryText,
            toolSchemas = toolSchemas,
            messages = conversationHistory,
            totalTokens = systemTokens + toolTokens + memoryTokens +
                         conversationHistory.sumOf { tokenCounter.count(it.content) },
        )
    }
}
```

### 9.2 Context Budget Allocation

```kotlin
class ContextBudgetAllocator {
    fun allocate(session: GuappaSession): ContextBudget {
        val modelContextWindow = session.config.contextTokens  // from provider config

        // Dynamic allocation based on session state
        val systemBudget = 2_000   // persona + device context
        val toolBudget = when {
            session.type == SessionType.BACKGROUND_TASK -> 1_000  // fewer tools needed
            else -> 3_000  // full tool catalog
        }
        val memoryBudget = when {
            session.type == SessionType.SYSTEM -> 0     // no memories for system tasks
            session.type == SessionType.TRIGGER -> 2_000 // brief context for triggers
            else -> 5_000  // full memory retrieval for chat
        }
        val reserveBuffer = 4_096  // always reserve for response

        val conversationBudget = modelContextWindow - systemBudget - toolBudget -
                                  memoryBudget - reserveBuffer

        return ContextBudget(
            totalTokens = modelContextWindow,
            systemPromptBudget = systemBudget,
            toolSchemasBudget = toolBudget,
            memoryBudget = memoryBudget,
            conversationBudget = maxOf(conversationBudget, 4_096),  // minimum 4K for conversation
            reserveBuffer = reserveBuffer,
        )
    }
}
```

---

## 10. Memory Consolidation

### 10.1 Background Consolidation Job

```kotlin
class MemoryConsolidator(
    private val factExtractor: FactExtractor,
    private val chunkEmbedder: ChunkEmbedder,
    private val duplicateDetector: DuplicateDetector,
    private val factVerifier: FactVerifier,
    private val conversationDao: ConversationDao,
    private val factDao: FactDao,
) {
    /**
     * Run periodically (every 6 hours) or when device is idle + charging.
     *
     * Steps:
     * 1. Find unprocessed conversation segments
     * 2. Extract facts from new segments
     * 3. Merge/update facts in long-term memory
     * 4. Generate embeddings for new segments
     * 5. Deduplicate similar memories
     * 6. Decay old, unused memories
     * 7. Clean up expired sessions
     */
    suspend fun consolidate() {
        // 1. Find unprocessed conversations
        val unprocessed = conversationDao.getUnprocessedSince(lastConsolidation())
        if (unprocessed.isEmpty()) return

        // 2. Extract facts
        for (segment in unprocessed.chunked(20)) {
            val facts = factExtractor.extractFacts(segment.toMessages())
            factExtractor.mergeFacts(facts)
        }

        // 3. Embed new conversation chunks
        for (segment in unprocessed.chunked(10)) {
            val text = segment.joinToString("\n") { "${it.role}: ${it.content}" }
            chunkEmbedder.embedAndStore(text, SourceType.CONVERSATION)
        }

        // 4. Deduplicate
        duplicateDetector.deduplicateFacts()
        duplicateDetector.deduplicateEmbeddings(threshold = 0.95f)

        // 5. Apply decay
        applyDecay()

        // 6. Clean up expired sessions
        conversationDao.deleteExpiredSessions(ttlDays = 30)

        // Mark processed
        conversationDao.markProcessed(unprocessed.map { it.id })
    }

    private suspend fun applyDecay() {
        val facts = factDao.getAll()
        val now = System.currentTimeMillis()
        for (fact in facts) {
            val daysSinceAccess = (now - fact.lastAccessedAt) / 86_400_000f
            val newDecay = exp(-0.01f * daysSinceAccess)  // slow decay, half-life ~70 days
            if (newDecay < 0.1f) {
                // Memory has decayed significantly — archive or delete
                factDao.archive(fact.id)
            } else {
                factDao.updateDecay(fact.id, newDecay)
            }
        }
    }
}
```

### 10.2 Consolidation Scheduling

```kotlin
class ConsolidationScheduler(private val context: Context) {
    fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)          // only when charging
            .setRequiresDeviceIdle(true)         // only when idle
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val request = PeriodicWorkRequestBuilder<ConsolidationWorker>(
            repeatInterval = 6, TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "memory_consolidation",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
    }
}
```

---

## 11. Recursive LLM (Chained Reasoning)

For complex tasks that exceed a single LLM call's capacity, use recursive/chained LLM calls:

### 11.1 Task Decomposition

```kotlin
class GuappaPlanner {
    /**
     * For complex requests, decompose into sub-tasks before execution.
     *
     * "Найди рестораны рядом, выбери лучший по отзывам, и забронируй столик на 19:00"
     * →
     * 1. get_location → current coordinates
     * 2. web_search → restaurants near coordinates
     * 3. web_fetch → reviews for top 5
     * 4. (LLM reasoning) → pick best one
     * 5. web_fetch → restaurant booking page
     * 6. (LLM reasoning) → fill booking form
     * 7. ui_automation → submit booking
     * 8. notify user → "Столик забронирован!"
     */
    suspend fun decompose(userRequest: String, context: AssembledContext): TaskPlan {
        val prompt = """
            Break down this user request into concrete executable steps.
            Available tools: ${context.toolSchemas.map { it.name }}

            User request: $userRequest

            Return a JSON plan:
            {
              "steps": [
                {"id": 1, "action": "tool_name or llm_reasoning", "input": "...", "depends_on": []},
                ...
              ],
              "estimated_complexity": "simple|medium|complex"
            }
        """.trimIndent()

        val response = providerRouter.chat(/* ... */)
        return parsePlan(response.text)
    }
}
```

### 11.2 Long-Document Processing (Recursive Summarization Chain)

For processing very long documents (e.g., reading a long email thread):

```kotlin
class LongDocumentProcessor {
    /**
     * Process a document that doesn't fit in context window.
     *
     * Strategy: Chunk → Process each → Merge results
     */
    suspend fun process(document: String, instruction: String): String {
        val chunks = chunker.chunk(document, maxTokensPerChunk = 8_000)

        if (chunks.size == 1) {
            // Fits in one call
            return providerRouter.chatSimple(
                userMessage = "Document:\n${chunks[0]}\n\nInstruction: $instruction",
                maxTokens = 2048,
            ).text
        }

        // Map: Process each chunk
        val chunkResults = chunks.mapIndexed { i, chunk ->
            providerRouter.chatSimple(
                userMessage = """
                    This is chunk ${i + 1} of ${chunks.size} of a document.
                    Chunk content:
                    $chunk

                    Instruction: $instruction
                    Respond with relevant information from this chunk only.
                """.trimIndent(),
                maxTokens = 1024,
            ).text
        }

        // Reduce: Merge results
        return providerRouter.chatSimple(
            userMessage = """
                Here are processed results from ${chunkResults.size} document chunks:
                ${chunkResults.mapIndexed { i, r -> "Chunk ${i + 1}: $r" }.joinToString("\n\n")}

                Instruction: $instruction
                Combine these results into a single coherent response.
            """.trimIndent(),
            maxTokens = 2048,
        ).text
    }
}
```

### 11.3 Self-Reflection / Inner Monologue

Agent reflects on its own reasoning before responding:

```kotlin
class SelfReflection {
    /**
     * After tool execution, reflect on results before responding.
     * Catches errors, improves responses, suggests follow-up actions.
     */
    suspend fun reflect(
        originalRequest: String,
        toolResults: List<ToolResult>,
        draftResponse: String,
    ): ReflectionResult {
        val prompt = """
            You just executed tools for this user request: "$originalRequest"

            Tool results:
            ${toolResults.joinToString("\n") { "${it.toolName}: ${it.content.take(500)}" }}

            Your draft response: "$draftResponse"

            Reflect:
            1. Did the tools provide enough information?
            2. Is the draft response accurate and complete?
            3. Should any follow-up actions be taken?
            4. Are there any errors or inconsistencies?

            Respond as JSON:
            {"quality": "good|needs_improvement|retry", "improved_response": "...", "follow_up_actions": [...]}
        """.trimIndent()

        // Use the reasoning model for reflection if available
        val response = providerRouter.chatWithCapability(
            ModelCapability.REASONING,
            userMessage = prompt,
        )
        return parseReflection(response.text)
    }
}
```

---

## 12. Configuration

```kotlin
data class MemoryConfig(
    // Context management
    val defaultContextTokens: Int = 128_000,
    val compactionThreshold: Float = 0.8f,
    val slidingWindowMessages: Int = 20,
    val reserveBufferTokens: Int = 4_096,

    // Summarization
    val summarizationModel: String = "",       // empty = use cheapest available
    val summaryMaxTokens: Int = 1_024,
    val superSummaryMaxTokens: Int = 512,
    val enableRecursiveSummarization: Boolean = true,
    val maxSummaryLevels: Int = 3,

    // Long-term memory
    val enableFactExtraction: Boolean = true,
    val factExtractionModel: String = "",
    val factExtractionInterval: Int = 5,       // every N messages
    val factDecayHalfLifeDays: Int = 70,
    val maxFacts: Int = 1_000,

    // Episodic memory
    val enableEpisodicMemory: Boolean = true,
    val episodeTtlDays: Int = 90,
    val maxEpisodes: Int = 500,

    // Vector store
    val enableVectorSearch: Boolean = true,
    val embeddingModel: String = "all-MiniLM-L6-v2",
    val embeddingDimension: Int = 384,
    val maxEmbeddings: Int = 100_000,
    val minSimilarityScore: Float = 0.5f,

    // Consolidation
    val consolidationIntervalHours: Int = 6,
    val requireCharging: Boolean = true,
    val requireIdle: Boolean = true,

    // Self-reflection
    val enableSelfReflection: Boolean = false,  // off by default (costs tokens)
    val reflectionModel: String = "",

    // Export/Import
    val autoBackupEnabled: Boolean = false,
    val autoBackupIntervalDays: Int = 7,

    // Session TTLs
    val chatSessionTtlDays: Int = 30,
    val backgroundTaskTtlDays: Int = 7,
    val triggerSessionTtlDays: Int = 1,
)
```

---

## 13. Database Schema

```kotlin
@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        TaskEntity::class,
        SummaryEntity::class,
        FactEntity::class,
        EpisodeEntity::class,
        EmbeddingEntity::class,
        CostEntry::class,
    ],
    version = 1,
)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun taskDao(): TaskDao
    abstract fun summaryDao(): SummaryDao
    abstract fun factDao(): FactDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun costDao(): CostDao
}

@Entity(tableName = "summaries")
data class SummaryEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val level: Int,              // 1 = summary, 2 = super-summary, 3 = ultra-summary
    val text: String,
    val tokenCount: Int,
    val messageRangeStart: Int,  // first message index covered
    val messageRangeEnd: Int,    // last message index covered
    val createdAt: Long,
)

@Entity(tableName = "embeddings")
data class EmbeddingEntity(
    @PrimaryKey val id: String,
    val sourceId: String,        // ID of source (message, fact, episode)
    val sourceType: String,      // "conversation", "fact", "episode"
    val text: String,            // original text that was embedded
    val embedding: ByteArray,    // 384-dim float array as BLOB
    val createdAt: Long,
)
```

---

## 14. Test Plan

### 14.1 Unit Tests

| Test | Description |
|------|-------------|
| `AutoSummarizer_TriggersAt80Percent` | Context at 80% → summarization triggered |
| `IncrementalSummarizer_PreservesKey` | Summary preserves task outcomes and user preferences |
| `RecursiveSummarizer_CreatesLevel2` | Level 1 over budget → Level 2 created |
| `SmartTruncator_DropsToolResultsFirst` | Tool results dropped before messages |
| `FactExtractor_ExtractsName` | "Меня зовут Алексей" → fact: name=Алексей |
| `FactExtractor_UpdatesExisting` | New fact overrides old with higher confidence |
| `MemoryImportance_DecaysOverTime` | Unaccessed fact decays correctly |
| `VectorStore_CosineSimilarity` | Similar texts → high score, dissimilar → low |
| `ContextAssembler_WithinBudget` | Assembled context fits model's context window |
| `ContextAssembler_IncludesMemories` | RAG retrieval results appear in context |
| `MapReduceSummarizer_LongHistory` | 200 messages → single coherent summary |
| `ConsolidationScheduler_Constraints` | Only runs when charging + idle |

### 14.2 Integration Tests

| Test | Description |
|------|-------------|
| `FullPipeline_LongConversation` | 100 messages → auto-summarization → context fits → responses correct |
| `FullPipeline_FactRetrieval` | Tell fact → new session → ask about fact → retrieved |
| `FullPipeline_EpisodicRetrieval` | Complete task → new session → similar task → experience cited |
| `FullPipeline_Consolidation` | Process 50 messages → consolidate → facts extracted |

### 14.3 Maestro E2E

```yaml
# Tell Guappa a fact → restart → verify remembered
- launchApp: com.guappa.app
- inputText: "Меня зовут Саша, я живу в Москве"
- tapOn: "Send"
- waitForAnimationToEnd
- stopApp: com.guappa.app
- launchApp: com.guappa.app
- inputText: "Как меня зовут?"
- tapOn: "Send"
- assertVisible:
    text: ".*Саша.*"
    timeout: 30000
```

---

## 15. Acceptance Criteria

- [ ] Context never overflows model's context window
- [ ] Auto-summarization triggers at 80% capacity
- [ ] Summaries preserve task outcomes and user preferences
- [ ] Recursive summarization creates Level 2/3 when needed
- [ ] Smart truncation drops tool results before messages
- [ ] Long-term facts extracted and stored correctly
- [ ] Facts retrieved via RAG for relevant queries
- [ ] Episodic memory records task outcomes
- [ ] Similar past experiences retrieved for new tasks
- [ ] Vector similarity search works on-device (< 100ms for 10K embeddings)
- [ ] Memory consolidation runs on schedule (idle + charging)
- [ ] Memory export/import works correctly
- [ ] Secure memory deletion fully removes data

---

## 16. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Summarization loses critical info | Keep "pinned" messages that user explicitly wants to remember |
| Fact extraction hallucinates | Require confidence > 0.7 for automatic storage, verify on conflict |
| Vector store grows too large | Cap at 100K embeddings, LRU eviction |
| Embedding model too slow | Use smallest model (MiniLM-L6, 22MB), batch processing |
| Consolidation drains battery | Only run when charging + idle, use WorkManager |
| Privacy: memories contain sensitive data | Encrypt database, secure deletion API, no cloud sync |
