package com.guappa.app.providers

object ProviderFactory {
    fun createRouter(providerName: String, apiKey: String, apiUrl: String): ProviderRouter {
        val router = ProviderRouter()
        if (apiKey.isNotBlank() || normalizeProviderId(providerName) in setOf("ollama", "lm-studio")) {
            router.registerProvider(createProvider(providerName, apiKey, apiUrl))
        }
        return router
    }

    fun createProvider(providerName: String, apiKey: String, apiUrl: String): Provider {
        val normalizedProvider = normalizeProviderId(providerName)
        val normalizedBaseUrl = normalizeBaseUrl(normalizedProvider, apiUrl)

        return when (normalizedProvider) {
            "anthropic" -> AnthropicProvider(apiKey, normalizedBaseUrl)
            "gemini" -> GoogleGeminiProvider(apiKey, normalizedBaseUrl)
            else -> OpenAICompatibleProvider(
                id = normalizedProvider,
                name = normalizedProvider,
                apiKey = apiKey,
                baseUrl = normalizedBaseUrl
            )
        }
    }

    fun normalizeProviderId(providerName: String): String {
        return when (providerName.trim().lowercase()) {
            "google-gemini" -> "gemini"
            else -> providerName.trim().lowercase()
        }
    }

    private fun normalizeBaseUrl(providerName: String, apiUrl: String): String {
        val raw = apiUrl.trim().ifEmpty { defaultBaseUrl(providerName) }
        return when (providerName) {
            "anthropic" -> raw.removeSuffix("/v1")
            "gemini" -> raw.removeSuffix("/v1beta")
            else -> raw.removeSuffix("/v1")
        }.trimEnd('/')
    }

    private fun defaultBaseUrl(providerName: String): String {
        return when (providerName) {
            "openrouter" -> "https://openrouter.ai/api"
            "openai" -> "https://api.openai.com"
            "anthropic" -> "https://api.anthropic.com"
            "gemini" -> "https://generativelanguage.googleapis.com"
            "mistral" -> "https://api.mistral.ai"
            "deepseek" -> "https://api.deepseek.com"
            "xai" -> "https://api.x.ai"
            "groq" -> "https://api.groq.com/openai"
            "together" -> "https://api.together.xyz"
            "fireworks" -> "https://api.fireworks.ai/inference"
            "perplexity" -> "https://api.perplexity.ai"
            "cohere" -> "https://api.cohere.com/compatibility"
            "minimax" -> "https://api.minimaxi.com"
            "venice" -> "https://api.venice.ai"
            "moonshot" -> "https://api.moonshot.cn"
            "glm" -> "https://open.bigmodel.cn/api/paas/v4"
            "qwen" -> "https://dashscope.aliyuncs.com/compatible-mode"
            "ollama" -> "http://10.0.2.2:11434"
            "lm-studio" -> "http://10.0.2.2:1234"
            "copilot" -> "https://api.githubcopilot.com"
            else -> "https://openrouter.ai/api"
        }
    }
}
