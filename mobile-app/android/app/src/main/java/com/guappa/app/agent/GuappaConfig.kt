package com.guappa.app.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AgentConfig(
    val maxConcurrentSessions: Int = 5,
    val maxToolCallsPerTurn: Int = 10,
    val maxReActIterations: Int = 20,
    val requestTimeoutMs: Long = 120_000,
    val defaultContextTokens: Int = 128_000,
    val compactionThreshold: Float = 0.8f
)

class GuappaConfig {
    private val _config = MutableStateFlow(AgentConfig())
    val config: StateFlow<AgentConfig> = _config.asStateFlow()

    fun update(transform: AgentConfig.() -> AgentConfig) {
        _config.value = _config.value.transform()
    }
}
