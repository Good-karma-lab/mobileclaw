package com.guappa.app.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class ProviderRouter {
    private val providers = ConcurrentHashMap<String, Provider>()
    private val capabilityMap = ConcurrentHashMap<CapabilityType, String>()
    private val modelCache = ConcurrentHashMap<String, Pair<List<ModelInfo>, Long>>()

    companion object {
        private const val CACHE_TTL = 3600_000L // 1 hour
    }

    fun registerProvider(provider: Provider) {
        providers[provider.id] = provider
        // Auto-map capabilities if not already mapped
        for (capability in provider.capabilities) {
            capabilityMap.putIfAbsent(capability, provider.id)
        }
    }

    fun setCapabilityProvider(capability: CapabilityType, providerId: String) {
        if (providers.containsKey(providerId)) {
            capabilityMap[capability] = providerId
        }
    }

    fun getProvider(id: String): Provider? = providers[id]

    fun getProviderForCapability(capability: CapabilityType): Provider? {
        val providerId = capabilityMap[capability] ?: return null
        return providers[providerId]
    }

    suspend fun fetchModels(providerId: String): List<ModelInfo> {
        val cached = modelCache[providerId]
        if (cached != null && (System.currentTimeMillis() - cached.second) < CACHE_TTL) {
            return cached.first
        }

        val provider = providers[providerId] ?: return emptyList()
        return try {
            val models = provider.fetchModels()
            modelCache[providerId] = Pair(models, System.currentTimeMillis())
            models
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun healthCheck(providerId: String): Boolean {
        val provider = providers[providerId] ?: return false
        return try {
            provider.healthCheck()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun chat(
        messages: List<ChatMessage>,
        capability: CapabilityType = CapabilityType.TEXT_CHAT,
        tools: List<JSONObject>? = null,
        model: String? = null,
        temperature: Double = 0.7
    ): ChatResponse {
        val provider = getProviderForCapability(capability)
            ?: throw IllegalStateException("No provider registered for capability: $capability")
        return provider.chat(messages, tools, model, temperature)
    }

    fun streamChat(
        messages: List<ChatMessage>,
        capability: CapabilityType = CapabilityType.TEXT_CHAT,
        tools: List<JSONObject>? = null,
        model: String? = null,
        temperature: Double = 0.7
    ): Flow<String> {
        val provider = getProviderForCapability(capability) ?: return emptyFlow()
        return provider.streamChat(messages, tools, model, temperature)
    }

    fun streamChatStructured(
        messages: List<ChatMessage>,
        capability: CapabilityType = CapabilityType.TEXT_CHAT,
        tools: List<JSONObject>? = null,
        model: String? = null,
        temperature: Double = 0.7
    ): Flow<StreamDelta> {
        val provider = getProviderForCapability(capability)
            ?: throw IllegalStateException("No provider registered for capability: $capability")
        return provider.streamChatStructured(messages, tools, model, temperature)
    }

    /**
     * Quick, lightweight LLM call for the Swarm Director.
     * Uses the text chat provider with low max_tokens for fast response.
     * Separate from orchestrator context — stateless single-turn.
     */
    suspend fun quickCall(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int = 100
    ): String {
        val provider = getProviderForCapability(CapabilityType.TEXT_CHAT)
            ?: throw IllegalStateException("No text chat provider configured")

        val messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = userPrompt)
        )

        val response = provider.chat(
            messages = messages,
            tools = null,
            model = null, // use default model
            temperature = 0.3 // low temperature for classification
        )
        return response.content ?: ""
    }

    fun listProviders(): List<String> = providers.keys().toList()
}
