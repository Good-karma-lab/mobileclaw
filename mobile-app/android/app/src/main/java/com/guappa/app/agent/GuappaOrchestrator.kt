package com.guappa.app.agent

import android.content.Context
import com.guappa.app.config.GuappaConfigStore
import com.guappa.app.memory.GuappaDatabase
import com.guappa.app.memory.SessionEntity
import com.guappa.app.memory.MessageEntity
import com.guappa.app.memory.MemoryManager
import com.guappa.app.providers.ChatMessage
import com.guappa.app.providers.ChatResponse
import com.guappa.app.providers.ContentPart
import com.guappa.app.providers.ProviderRouter
import com.guappa.app.providers.CapabilityType
import com.guappa.app.tools.ToolEngine
import com.guappa.app.tools.ToolRegistry
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.File
import java.util.UUID
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

class GuappaOrchestrator(
    private val messageBus: MessageBus,
    private val config: GuappaConfig,
    private var providerRouter: ProviderRouter? = null,
    private var context: Context? = null,
    private var selectedModel: String? = null,
    private var selectedTemperature: Double = 0.7
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessions = mutableMapOf<String, GuappaSession>()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var defaultSessionId: String? = null
    private var database: GuappaDatabase? = null
    private var memoryManager: MemoryManager? = null

    val toolRegistry = ToolRegistry()
    val toolEngine = ToolEngine(toolRegistry)

    // Phase 1 subsystems
    private var persona: GuappaPersona = GuappaPersona()
    private var planner: GuappaPlanner = GuappaPlanner(providerRouter, messageBus)
    val taskManager: TaskManager = TaskManager(messageBus)

    companion object {
        private const val MAX_REACT_ITERATIONS = 50
        private const val STREAM_CHUNK_SIZE = 36
        private const val CONTEXT_COMPACTION_THRESHOLD = 60 // compact when > 60 messages
        private const val CONTEXT_KEEP_RECENT = 30 // keep last 30 messages verbatim
    }

    fun configure(
        router: ProviderRouter,
        ctx: Context,
        model: String? = null,
        temperature: Double = 0.7,
        configStore: GuappaConfigStore? = null
    ) {
        this.providerRouter = router
        this.context = ctx
        this.selectedModel = model?.takeIf { it.isNotBlank() }
        this.selectedTemperature = temperature
        this.persona = GuappaPersona(configStore)
        this.planner = GuappaPlanner(router, messageBus)
        this.database = GuappaDatabase.getInstance(ctx)
        this.memoryManager = MemoryManager(ctx)
        toolRegistry.registerCoreTools()
    }

    /** Expose router for direct fast calls (SwarmDirector). */
    fun getRouter(): ProviderRouter? = providerRouter

    fun start() {
        toolRegistry.registerCoreTools()
        _isRunning.value = true

        // Restore sessions from database
        scope.launch {
            restoreSessions()
        }

        scope.launch {
            messageBus.messages.collect { message -> handleMessage(message) }
        }
        scope.launch {
            messageBus.urgentMessages.collect { message -> handleMessage(message) }
        }
    }

    fun stop() {
        _isRunning.value = false
        // Persist all active sessions before stopping
        scope.launch {
            persistAllSessions()
        }
        taskManager.shutdown()
        scope.cancel()
    }

    fun getOrCreateDefaultSession(): GuappaSession {
        val id = defaultSessionId
        if (id != null && sessions[id]?.state?.value != SessionState.CLOSED) {
            return sessions[id]!!
        }
        val session = GuappaSession(type = SessionType.CHAT)
        sessions[session.id] = session
        defaultSessionId = session.id

        // Persist new session to DB
        scope.launch { persistSession(session) }

        return session
    }

    fun resolveSessionId(requestedSessionId: String?): String {
        val normalizedSessionId = requestedSessionId?.takeIf { it.isNotBlank() } ?: return getOrCreateDefaultSession().id
        val existing = sessions[normalizedSessionId]
        return if (existing != null && existing.state.value != SessionState.CLOSED) {
            normalizedSessionId
        } else {
            getOrCreateDefaultSession().id
        }
    }

    // ---- Session Persistence ----

    private suspend fun restoreSessions() {
        val db = database ?: return
        try {
            val activeSessions = db.sessionDao().getActiveSessions()
            for (entity in activeSessions) {
                val session = GuappaSession(id = entity.id, type = SessionType.CHAT)
                val messages = db.messageDao().getBySession(entity.id)
                for (msg in messages) {
                    session.addMessage(Message(
                        id = msg.id,
                        role = msg.role,
                        content = msg.content,
                        timestamp = msg.timestamp,
                        toolCallId = null,
                        tokenCount = msg.tokenCount
                    ))
                }
                sessions[entity.id] = session
                // Use the most recently created session as default
                if (defaultSessionId == null) {
                    defaultSessionId = entity.id
                }
            }
        } catch (_: Exception) {
            // Database errors shouldn't prevent agent from starting
        }
    }

    private suspend fun persistSession(session: GuappaSession) {
        val db = database ?: return
        try {
            db.sessionDao().insert(SessionEntity(
                id = session.id,
                title = session.messages.firstOrNull { it.role == "user" }?.content?.take(80) ?: "New chat",
                startedAt = session.messages.firstOrNull()?.timestamp ?: System.currentTimeMillis(),
                tokenCount = session.messages.sumOf { it.tokenCount }
            ))
        } catch (_: Exception) {
            // Non-fatal
        }
    }

    private suspend fun persistMessage(sessionId: String, message: Message) {
        val db = database ?: return
        try {
            db.messageDao().insert(MessageEntity(
                id = message.id,
                sessionId = sessionId,
                role = message.role,
                content = message.content,
                timestamp = message.timestamp,
                tokenCount = message.tokenCount
            ))
        } catch (_: Exception) {
            // Non-fatal
        }
    }

    private suspend fun persistAllSessions() {
        val db = database ?: return
        for ((_, session) in sessions) {
            try {
                persistSession(session)
                for (msg in session.messages) {
                    persistMessage(session.id, msg)
                }
            } catch (_: Exception) {
                // Non-fatal
            }
        }
    }

    // ---- Message Handling ----

    private suspend fun handleMessage(message: BusMessage) {
        when (message) {
            is BusMessage.UserMessage -> {
                val session = if (message.sessionId.isNotEmpty()) {
                    sessions[resolveSessionId(message.sessionId)] ?: getOrCreateDefaultSession()
                } else {
                    getOrCreateDefaultSession()
                }
                val msg = Message(
                    role = "user",
                    content = message.text,
                    imageAttachments = message.imageAttachments
                )
                session.addMessage(msg)
                persistMessage(session.id, msg)

                // Check if context needs compaction before LLM call
                if (session.messages.size > CONTEXT_COMPACTION_THRESHOLD) {
                    compactContext(session)
                }

                if (planner.isComplexRequest(message.text)) {
                    handleComplexRequest(message.text, session)
                } else {
                    runReActLoop(session)
                }
            }
            is BusMessage.ToolResult -> {
                val session = sessions[message.sessionId] ?: return
                val msg = Message(
                    role = "tool",
                    content = message.result,
                    toolCallId = message.toolName
                )
                session.addMessage(msg)
                persistMessage(session.id, msg)
            }
            is BusMessage.TriggerEvent -> {
                // Use the default chat session so trigger responses appear in the chat UI
                val session = getOrCreateDefaultSession()

                // Create a prompt from the trigger event and process it
                val triggerPrompt = buildTriggerPrompt(message)
                if (triggerPrompt.isNotBlank()) {
                    val msg = Message(role = "user", content = triggerPrompt)
                    session.addMessage(msg)
                    persistMessage(session.id, msg)
                    runReActLoop(session)
                }
            }
            else -> { }
        }
    }

    private fun buildTriggerPrompt(event: BusMessage.TriggerEvent): String {
        val data = event.data.entries.joinToString(", ") { "${it.key}=${it.value}" }
        return when (event.trigger) {
            "morning_briefing" -> "Give me a morning briefing: summarize my tasks, calendar, and any important notifications."
            "daily_summary" -> "Give me an end-of-day summary: what was accomplished today, pending tasks, and any follow-ups."
            "incoming_sms" -> "I received an SMS. ${data}. Summarize it and suggest a response if appropriate."
            "incoming_call" -> "I just received an incoming phone call from ${event.data["phone_number"] ?: "unknown number"}. $data. Please note this call and tell me the caller's number."
            "battery_low" -> "My battery is low. Suggest power-saving actions."
            "calendar_reminder" -> "I have a calendar event coming up. ${data}. Remind me about it."
            else -> "A device event occurred: ${event.trigger}. Data: $data. Take appropriate action if needed."
        }
    }

    // ---- Context Compaction ----

    private suspend fun compactContext(session: GuappaSession) {
        val router = providerRouter ?: return
        val messages = session.messages
        if (messages.size <= CONTEXT_KEEP_RECENT) return

        val oldMessages = messages.dropLast(CONTEXT_KEEP_RECENT)
        if (oldMessages.isEmpty()) return

        // Build a summary of old messages using the LLM
        val summaryContent = oldMessages.joinToString("\n") { "${it.role}: ${it.content.take(200)}" }
        val summaryPrompt = "Summarize this conversation history concisely, preserving key facts, decisions, and context:\n\n$summaryContent"

        try {
            val response = router.chat(
                messages = listOf(
                    ChatMessage(role = "system", content = "You are a conversation summarizer. Be concise but preserve all important facts."),
                    ChatMessage(role = "user", content = summaryPrompt)
                ),
                capability = CapabilityType.TEXT_CHAT,
                model = selectedModel,
                temperature = 0.3
            )
            val summaryText = response.content ?: return

            // Replace old messages with summary
            session.compactWith(summaryText, CONTEXT_KEEP_RECENT)

            // Store summary as episode in memory
            memoryManager?.let { mm ->
                scope.launch {
                    try {
                        mm.storeEpisode(session.id, summaryText)
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) {
            // If summarization fails, just keep going with full context
        }
    }

    private suspend fun handleComplexRequest(userRequest: String, session: GuappaSession) {
        val steps = planner.decompose(userRequest)
        if (steps.size <= 1) {
            runReActLoop(session)
            return
        }

        taskManager.submitTask(
            title = "Plan: ${userRequest.take(60)}",
            action = {
                planner.executePlan(steps, session) { planSession, _ ->
                    runReActLoop(planSession)
                }
                "Plan completed: ${steps.count { it.status == PlanStepStatus.DONE }}/${steps.size} steps done"
            }
        )
    }

    // ---- ReAct Loop ----

    private suspend fun runReActLoop(session: GuappaSession) {
        val router = providerRouter ?: return
        val ctx = context ?: return

        val systemPrompt = persona.getSystemPrompt()
        val toolSchemas = toolRegistry.getToolSchemas(ctx)

        // Inject relevant memories into system prompt
        val memoriesBlock = buildMemoriesBlock(session)
        val fullSystemPrompt = if (memoriesBlock.isNotEmpty()) {
            "$systemPrompt\n\nRelevant memories about this user:\n$memoriesBlock"
        } else {
            systemPrompt
        }

        for (iteration in 0 until MAX_REACT_ITERATIONS) {
            val chatMessages = session.getContextMessages(fullSystemPrompt).map { msg ->
                if (msg.hasImages) {
                    val imageParts = msg.imageAttachments.mapNotNull { path ->
                        encodeImageToBase64(path)
                    }
                    ChatMessage.withImages(
                        role = msg.role,
                        text = msg.content,
                        images = imageParts
                    ).copy(toolCallId = msg.toolCallId)
                } else {
                    ChatMessage(
                        role = msg.role,
                        content = msg.content,
                        toolCallId = msg.toolCallId
                    )
                }
            }

            // Determine capability: prefer TOOL_USE if available, fall back to TEXT_CHAT
            val capability = if (router.getProviderForCapability(CapabilityType.TOOL_USE) != null) {
                CapabilityType.TOOL_USE
            } else {
                CapabilityType.TEXT_CHAT
            }
            val useTools = capability == CapabilityType.TOOL_USE && toolSchemas.isNotEmpty()

            val response: ChatResponse = try {
                router.chat(
                    messages = chatMessages,
                    capability = capability,
                    tools = if (useTools) toolSchemas else null,
                    model = selectedModel,
                    temperature = selectedTemperature,
                )
            } catch (e: Exception) {
                val errorText = if (e.message?.contains("No provider registered") == true) {
                    "⚙\uFE0F No AI provider configured yet.\n\nGo to Config → set a Provider and API Key, then tap Apply.\n\nOr download a local model in Config → How GUAPPA Thinks."
                } else {
                    "I encountered an error: ${e.message}"
                }
                messageBus.publish(BusMessage.AgentMessage(
                    text = errorText,
                    sessionId = session.id,
                    isComplete = true
                ))
                return
            }

            // If there are tool calls, execute them and continue the loop
            val toolCalls = response.toolCalls
            if (!toolCalls.isNullOrEmpty()) {
                val assistantMsg = Message(role = "assistant", content = response.content ?: "")
                session.addMessage(assistantMsg)
                persistMessage(session.id, assistantMsg)

                for (toolCall in toolCalls) {
                    messageBus.publish(BusMessage.SystemEvent(
                        type = "tool_call_started",
                        data = mapOf(
                            "tool" to toolCall.function.name,
                            "call_id" to toolCall.id,
                            "session_id" to session.id,
                            "iteration" to iteration
                        )
                    ))
                }

                val results = toolEngine.executeToolCalls(toolCalls, ctx)

                for ((callId, result) in results) {
                    val toolCall = toolCalls.find { it.id == callId }
                    val toolName = toolCall?.function?.name ?: callId

                    val resultJson = result.toJSON()

                    // Collect image attachments from tool results (camera, screenshot, etc.)
                    val toolImageAttachments = if (result is ToolResult.Success) {
                        result.attachments?.filter { path ->
                            val lower = path.lowercase()
                            lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                            lower.endsWith(".png") || lower.endsWith(".webp") ||
                            lower.endsWith(".gif")
                        } ?: emptyList()
                    } else emptyList()

                    val toolMsg = Message(
                        role = "tool",
                        content = resultJson.toString(),
                        toolCallId = callId,
                        imageAttachments = toolImageAttachments
                    )
                    session.addMessage(toolMsg)
                    persistMessage(session.id, toolMsg)

                    // Emit tool images to the chat UI so the user sees them
                    if (toolImageAttachments.isNotEmpty()) {
                        messageBus.publish(BusMessage.AgentMessage(
                            text = "",
                            sessionId = session.id,
                            isComplete = false,
                            imageAttachments = toolImageAttachments
                        ))
                    }

                    val isSuccess = result is ToolResult.Success
                    messageBus.publish(BusMessage.SystemEvent(
                        type = "tool_executed",
                        data = mapOf(
                            "tool" to toolName,
                            "call_id" to callId,
                            "success" to isSuccess,
                            "session_id" to session.id,
                            "iteration" to iteration
                        )
                    ))
                }

                continue
            }

            // No tool calls: we have a final text response
            val rawText = response.content ?: ""
            if (rawText.isNotEmpty()) {
                val responseText = persona.adaptResponse(rawText)
                val assistantMsg = Message(role = "assistant", content = responseText)
                session.addMessage(assistantMsg)
                persistMessage(session.id, assistantMsg)

                // Extract facts from the conversation for long-term memory
                scope.launch { extractMemoryFacts(session, responseText) }

                publishStreamedResponse(session.id, responseText)
            }
            return
        }

        // Exhausted iterations
        messageBus.publish(BusMessage.AgentMessage(
            text = "I've been working on this for a while ($MAX_REACT_ITERATIONS tool calls). Let me share what I have so far — ask me to continue if you need more.",
            sessionId = session.id,
            isComplete = true
        ))
    }

    // ---- Memory Integration ----

    private suspend fun buildMemoriesBlock(session: GuappaSession): String {
        val mm = memoryManager ?: return ""
        return try {
            val lastUserMsg = session.messages.lastOrNull { it.role == "user" }?.content ?: return ""
            val facts = mm.getRelevantFacts(lastUserMsg, limit = 10)
            if (facts.isEmpty()) return ""
            facts.joinToString("\n") { "- ${it.key}: ${it.value}" }
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun extractMemoryFacts(session: GuappaSession, responseText: String) {
        val mm = memoryManager ?: return
        try {
            // Extract facts from the last user message + assistant response pair
            val lastUserMsg = session.messages.lastOrNull { it.role == "user" }?.content ?: return
            mm.extractAndStoreFacts(lastUserMsg, responseText)
        } catch (_: Exception) {
            // Non-fatal
        }
    }

    // ---- Streaming ----

    private suspend fun publishStreamedResponse(sessionId: String, responseText: String) {
        for (chunk in chunkResponse(responseText)) {
            messageBus.publish(BusMessage.AgentMessage(
                text = chunk,
                sessionId = sessionId,
                isStreaming = true,
                isComplete = false
            ))
            delay(24)
        }

        messageBus.publish(BusMessage.AgentMessage(
            text = responseText,
            sessionId = sessionId,
            isComplete = true
        ))
    }

    private fun chunkResponse(text: String): List<String> {
        if (text.length <= STREAM_CHUNK_SIZE) {
            return listOf(text)
        }

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + STREAM_CHUNK_SIZE, text.length)
            if (end < text.length) {
                val boundary = text.lastIndexOf(' ', end)
                if (boundary > start) {
                    end = boundary + 1
                }
            }
            chunks.add(text.substring(start, end))
            start = end
        }
        return chunks.filter { it.isNotEmpty() }
    }

    // ---- Image Encoding ----

    /**
     * Encode an image file to base64 ContentPart for vision models.
     * Resizes large images to max 1024px on longest side to stay within API limits.
     */
    private fun encodeImageToBase64(filePath: String): ContentPart.ImagePart? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null

            val mimeType = when {
                filePath.endsWith(".png", ignoreCase = true) -> "image/png"
                filePath.endsWith(".webp", ignoreCase = true) -> "image/webp"
                filePath.endsWith(".gif", ignoreCase = true) -> "image/gif"
                else -> "image/jpeg"
            }

            // Decode with inJustDecodeBounds first to check size
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, options)
            val origWidth = options.outWidth
            val origHeight = options.outHeight

            // Calculate sample size for images > 1024px
            val maxDim = 1024
            var sampleSize = 1
            if (origWidth > maxDim || origHeight > maxDim) {
                val longestSide = maxOf(origWidth, origHeight)
                sampleSize = (longestSide.toFloat() / maxDim).toInt().coerceAtLeast(1)
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(filePath, decodeOptions) ?: return null

            val outputStream = ByteArrayOutputStream()
            val compressFormat = when (mimeType) {
                "image/png" -> android.graphics.Bitmap.CompressFormat.PNG
                "image/webp" -> android.graphics.Bitmap.CompressFormat.WEBP
                else -> android.graphics.Bitmap.CompressFormat.JPEG
            }
            bitmap.compress(compressFormat, 85, outputStream)
            bitmap.recycle()

            val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            ContentPart.ImagePart(base64 = base64, mimeType = mimeType)
        } catch (e: Exception) {
            android.util.Log.w("GuappaOrchestrator", "Failed to encode image: $filePath", e)
            null
        }
    }
}
