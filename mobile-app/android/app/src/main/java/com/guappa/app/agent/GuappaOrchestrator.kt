package com.guappa.app.agent

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class GuappaOrchestrator(
    private val messageBus: MessageBus,
    private val config: GuappaConfig
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessions = mutableMapOf<String, GuappaSession>()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var defaultSessionId: String? = null

    fun start() {
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
                // Provider call will be wired in M2
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
}
