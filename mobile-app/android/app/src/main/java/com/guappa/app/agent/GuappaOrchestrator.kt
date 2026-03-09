package com.guappa.app.agent

import android.content.Context
import com.guappa.app.config.GuappaConfigStore
import com.guappa.app.providers.ChatMessage
import com.guappa.app.providers.ChatResponse
import com.guappa.app.providers.ProviderRouter
import com.guappa.app.providers.CapabilityType
import com.guappa.app.tools.ToolEngine
import com.guappa.app.tools.ToolRegistry
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

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

    val toolRegistry = ToolRegistry()
    val toolEngine = ToolEngine(toolRegistry)

    // Phase 1 subsystems
    private var persona: GuappaPersona = GuappaPersona()
    private var planner: GuappaPlanner = GuappaPlanner(providerRouter, messageBus)
    val taskManager: TaskManager = TaskManager(messageBus)

    companion object {
        private const val MAX_REACT_ITERATIONS = 5
        private const val STREAM_CHUNK_SIZE = 36
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
        toolRegistry.registerCoreTools()
    }

    fun start() {
        toolRegistry.registerCoreTools()
        _isRunning.value = true
        scope.launch {
            messageBus.messages.collect { message -> handleMessage(message) }
        }
        scope.launch {
            messageBus.urgentMessages.collect { message -> handleMessage(message) }
        }
    }

    fun stop() {
        _isRunning.value = false
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

    private suspend fun handleMessage(message: BusMessage) {
        when (message) {
            is BusMessage.UserMessage -> {
                val session = if (message.sessionId.isNotEmpty()) {
                    sessions[resolveSessionId(message.sessionId)] ?: getOrCreateDefaultSession()
                } else {
                    getOrCreateDefaultSession()
                }
                session.addMessage(Message(role = "user", content = message.text))

                if (planner.isComplexRequest(message.text)) {
                    handleComplexRequest(message.text, session)
                } else {
                    runReActLoop(session)
                }
            }
            is BusMessage.ToolResult -> {
                val session = sessions[message.sessionId] ?: return
                session.addMessage(Message(
                    role = "tool",
                    content = message.result,
                    toolCallId = message.toolName
                ))
            }
            is BusMessage.TriggerEvent -> {
                val session = GuappaSession(type = SessionType.TRIGGER)
                sessions[session.id] = session
            }
            else -> { }
        }
    }

    private suspend fun handleComplexRequest(userRequest: String, session: GuappaSession) {
        val steps = planner.decompose(userRequest)
        if (steps.size <= 1) {
            // Planner decided it's not actually multi-step
            runReActLoop(session)
            return
        }

        // Execute multi-step plan via TaskManager for long-running awareness
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

    private suspend fun runReActLoop(session: GuappaSession) {
        val router = providerRouter ?: return
        val ctx = context ?: return

        val systemPrompt = persona.getSystemPrompt()
        val toolSchemas = toolRegistry.getToolSchemas(ctx)

        for (iteration in 0 until MAX_REACT_ITERATIONS) {
            val chatMessages = session.getContextMessages(systemPrompt).map { msg ->
                ChatMessage(
                    role = msg.role,
                    content = msg.content,
                    toolCallId = msg.toolCallId
                )
            }

            val response: ChatResponse = try {
                router.chat(
                    messages = chatMessages,
                    capability = CapabilityType.TOOL_USE,
                    tools = if (toolSchemas.isNotEmpty()) toolSchemas else null,
                    model = selectedModel,
                    temperature = selectedTemperature,
                )
            } catch (e: Exception) {
                messageBus.publish(BusMessage.AgentMessage(
                    text = "I encountered an error processing your request: ${e.message}",
                    sessionId = session.id,
                    isComplete = true
                ))
                return
            }

            // If there are tool calls, execute them and continue the loop
            val toolCalls = response.toolCalls
            if (!toolCalls.isNullOrEmpty()) {
                // Add assistant message with tool calls to session
                session.addMessage(Message(
                    role = "assistant",
                    content = response.content ?: ""
                ))

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
                    session.addMessage(Message(
                        role = "tool",
                        content = resultJson.toString(),
                        toolCallId = callId
                    ))

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

                // Continue the loop for next LLM call with tool results
                continue
            }

            // No tool calls: we have a final text response
            val rawText = response.content ?: ""
            if (rawText.isNotEmpty()) {
                val responseText = persona.adaptResponse(rawText)
                session.addMessage(Message(role = "assistant", content = responseText))
                publishStreamedResponse(session.id, responseText)
            }
            return
        }

        // Exhausted iterations
        messageBus.publish(BusMessage.AgentMessage(
            text = "I reached the maximum number of tool-use iterations. Here is what I have so far.",
            sessionId = session.id,
            isComplete = true
        ))
    }

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
}
