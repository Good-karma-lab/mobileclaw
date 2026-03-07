package com.guappa.app.agent

import android.content.Context
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
    private var context: Context? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessions = mutableMapOf<String, GuappaSession>()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var defaultSessionId: String? = null

    val toolRegistry = ToolRegistry()
    val toolEngine = ToolEngine(toolRegistry)

    companion object {
        private const val MAX_REACT_ITERATIONS = 5
        private const val SYSTEM_PROMPT = "You are Guappa, a helpful AI assistant running on an Android device. You have access to device tools to help the user. Use tools when appropriate to fulfill user requests."
    }

    fun configure(router: ProviderRouter, ctx: Context) {
        this.providerRouter = router
        this.context = ctx
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

    private suspend fun handleMessage(message: BusMessage) {
        when (message) {
            is BusMessage.UserMessage -> {
                val session = if (message.sessionId.isNotEmpty()) {
                    sessions[message.sessionId] ?: getOrCreateDefaultSession()
                } else {
                    getOrCreateDefaultSession()
                }
                session.addMessage(Message(role = "user", content = message.text))
                runReActLoop(session)
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

    private suspend fun runReActLoop(session: GuappaSession) {
        val router = providerRouter ?: return
        val ctx = context ?: return

        val toolSchemas = toolRegistry.getToolSchemas(ctx)

        for (iteration in 0 until MAX_REACT_ITERATIONS) {
            val chatMessages = session.getContextMessages(SYSTEM_PROMPT).map { msg ->
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
                    tools = if (toolSchemas.isNotEmpty()) toolSchemas else null
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
            val responseText = response.content ?: ""
            if (responseText.isNotEmpty()) {
                session.addMessage(Message(role = "assistant", content = responseText))
                messageBus.publish(BusMessage.AgentMessage(
                    text = responseText,
                    sessionId = session.id,
                    isComplete = true
                ))
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
}
