# Memory System Guide

GUAPPA uses a multi-tier memory system to remember your conversations, preferences, and past interactions. This guide explains how memory works and how to configure it.

## 5-Tier Memory Architecture

GUAPPA organizes memory into five tiers, from fast and ephemeral to persistent and searchable.

### Tier 1: Working Memory

What the AI model sees in each request. This includes:
- System prompt and persona
- Tool schemas
- Retrieved memories from other tiers
- Recent conversation messages (verbatim)
- Summaries of older messages

Working memory is constrained by the model's context window (default: 128K tokens). GUAPPA manages this automatically.

### Tier 2: Short-Term Memory

Full conversation history stored in SQLite, scoped to each session.

- All messages are stored verbatim
- Session summaries are auto-generated
- Tool call history with results is retained
- Default retention: 30 days (configurable)

### Tier 3: Long-Term Memory

Persistent facts and preferences extracted from conversations.

- User facts: "User lives in Berlin"
- Preferences: "Prefers responses in Russian"
- Learned patterns: "Usually sets alarm at 7 AM"
- Relationships: "Mom's number is +1-555-0123"
- Retained permanently until you delete them

GUAPPA extracts these automatically from conversations using the AI model. For example, if you mention "I just moved to Berlin," GUAPPA stores "User lives in Berlin" as a long-term fact.

### Tier 4: Episodic Memory

Records of past task executions and their outcomes.

- What was asked, what tools were used, what happened
- Success or failure outcomes
- Helps GUAPPA learn from past experience (e.g., "last time I searched with Brave, it worked better than Google for this type of query")
- Default retention: 90 days

### Tier 5: Semantic Memory (Vector Store)

Embedded representations of conversations, facts, and task descriptions used for similarity search (RAG).

- Conversation chunks are embedded using an embedding model
- Facts and preferences are embedded for retrieval
- When you ask a question, GUAPPA searches semantic memory for relevant context
- Uses on-device embedding models (all-MiniLM-L6-v2 or similar) for offline operation

## How Context Assembly Works

Before each AI request, GUAPPA assembles the context window from all memory tiers:

1. **System prompt** -- GUAPPA's persona and instructions (~2K tokens)
2. **Tool schemas** -- Descriptions of available tools (~3K tokens)
3. **Retrieved memories** -- Relevant long-term facts and episodic memories found via vector search (~5K tokens)
4. **Conversation history** -- Structured as three layers:
   - **Super-summary** -- Compressed summary of oldest interactions (~500 tokens)
   - **Summary** -- Summary of older turns in current session (~2K tokens)
   - **Recent messages** -- Verbatim recent exchanges (remaining budget)
5. **Response buffer** -- Reserved space for the model's reply (~4K tokens)

The Context Budget Allocator distributes tokens across these sections to maximize relevant context within the model's limit.

## Auto-Summarization

When the conversation grows long, GUAPPA automatically summarizes older messages to free up context space.

### Incremental Summarization
New messages are folded into the existing summary as the conversation progresses. This keeps the summary up to date without re-processing the entire history.

### Recursive Summarization
When summaries themselves grow too long, GUAPPA compresses them further into super-summaries. This creates a multi-level compression hierarchy:

```
Recent messages (verbatim)
    --> Summary (summarized older turns)
        --> Super-summary (summarized summaries)
```

### Map-Reduce Summarization
For very long sessions or when processing large documents, GUAPPA splits the content into chunks, summarizes each chunk independently, then merges the summaries.

## Memory Settings

Configure memory behavior in **Settings** > **Memory**.

### Context Budget

- **Max context tokens** -- Maximum tokens for the context window (default: model's limit, e.g., 128K)
- **Memory retrieval budget** -- How many tokens to allocate for retrieved memories (default: 5K)
- **Summary budget** -- Token budget for conversation summaries (default: 2.5K)

### Retention

- **Short-term retention** -- How long to keep full conversation history (default: 30 days)
- **Episodic retention** -- How long to keep task execution records (default: 90 days)
- **Long-term memory** -- Permanent by default. You can clear individual facts or all long-term memory.

### Memory Extraction

- **Auto-extract facts** -- Whether GUAPPA automatically extracts facts from conversations (default: on)
- **Auto-extract preferences** -- Whether to learn user preferences automatically (default: on)
- **Extraction frequency** -- How often to run extraction (default: every 10 messages)

## Viewing and Managing Memory

### View Stored Memories

Go to **Settings** > **Memory** > **View Memories** to see what GUAPPA remembers:

- **Facts** -- Extracted user facts
- **Preferences** -- Learned preferences
- **Episodes** -- Past task records

### Edit or Delete Memories

You can edit or delete any stored memory:
- Tap a memory entry to view details.
- Tap **Edit** to correct a fact.
- Tap **Delete** to remove it.

### Clear All Memory

To erase everything GUAPPA has learned:
1. Go to **Settings** > **Memory** > **Clear All Memory**.
2. Confirm the action.

This deletes all tiers of memory. Conversation history, facts, preferences, episodes, and vector embeddings are all removed. This cannot be undone.

### Export Memory

Export your memory data as JSON for backup or analysis:
1. Go to **Settings** > **Memory** > **Export**.
2. Choose a location to save the file.

The export includes all facts, preferences, conversation summaries, and episodic records.

## How GUAPPA Uses Memory in Practice

**Remembering preferences:**
If you say "I prefer dark roast coffee," GUAPPA stores this. Next time you ask "order me coffee," she already knows your preference.

**Learning from context:**
If you frequently set alarms at 7 AM, GUAPPA notices the pattern and can proactively suggest it.

**Task experience:**
If a web search using one query style failed before but another worked, GUAPPA remembers and adapts.

**Cross-session continuity:**
Start a conversation today about planning a trip. Come back tomorrow and say "what were we discussing about the trip?" GUAPPA retrieves the relevant context from memory.
