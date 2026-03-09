package com.guappa.app.providers

/**
 * Infers model capabilities from model ID when the provider API
 * doesn't return capability metadata. Uses naming conventions
 * common across providers (as of March 2026).
 */
object CapabilityInferrer {

    fun infer(modelId: String): Set<CapabilityType> {
        val id = modelId.lowercase()
        val caps = mutableSetOf<CapabilityType>()

        // All models support text chat at minimum
        caps.add(CapabilityType.TEXT_CHAT)

        // Vision models
        if (id.contains("vision") ||
            id.contains("4o") ||       // GPT-4o has vision
            id.contains("gemini") ||    // All Gemini models have vision
            id.contains("claude-3") ||  // Claude 3+ has vision
            id.contains("claude-4") ||
            id.contains("grok-2") ||
            id.contains("pixtral") ||
            id.contains("llava") ||
            id.contains("moondream")
        ) {
            caps.add(CapabilityType.VISION)
        }

        // Image generation
        if (id.contains("dall-e") ||
            id.contains("imagen") ||
            id.contains("stable-diffusion") ||
            id.contains("sdxl") ||
            id.contains("flux") ||
            id.contains("midjourney")
        ) {
            caps.add(CapabilityType.IMAGE_GENERATION)
        }

        // Video generation
        if (id.contains("sora") ||
            id.contains("veo") ||
            id.contains("kling") ||
            id.contains("runway")
        ) {
            caps.add(CapabilityType.VIDEO_GENERATION)
        }

        // Audio / STT
        if (id.contains("whisper") ||
            id.contains("stt") ||
            id.contains("speech-to-text")
        ) {
            caps.add(CapabilityType.AUDIO_STT)
        }

        // Audio / TTS
        if (id.contains("tts") ||
            id.contains("text-to-speech") ||
            id.contains("orca") ||
            id.contains("kokoro") ||
            id.contains("piper")
        ) {
            caps.add(CapabilityType.AUDIO_TTS)
        }

        // Embeddings
        if (id.contains("embedding") ||
            id.contains("embed") ||
            id.contains("text-embedding") ||
            id.contains("voyage")
        ) {
            caps.add(CapabilityType.EMBEDDING)
            caps.remove(CapabilityType.TEXT_CHAT) // embedding-only models
        }

        // Code-specialized
        if (id.contains("codestral") ||
            id.contains("deepseek-coder") ||
            id.contains("codegemma") ||
            id.contains("starcoder") ||
            id.contains("codellama")
        ) {
            caps.add(CapabilityType.CODE)
        }

        // Tool use (most modern chat models support it)
        if (id.contains("gpt-4") ||
            id.contains("gpt-3.5") ||
            id.contains("claude-3") ||
            id.contains("claude-4") ||
            id.contains("gemini") ||
            id.contains("mistral-large") ||
            id.contains("mistral-small") ||
            id.contains("grok") ||
            id.contains("command-r") ||
            id.contains("deepseek-chat")
        ) {
            caps.add(CapabilityType.TOOL_USE)
        }

        // Streaming (most chat models)
        if (!id.contains("embedding") && !id.contains("dall-e")) {
            caps.add(CapabilityType.STREAMING)
        }

        // Search-augmented
        if (id.contains("perplexity") ||
            id.contains("sonar") ||
            id.contains("search")
        ) {
            caps.add(CapabilityType.SEARCH)
        }

        // Reasoning models
        if (id.contains("o1") ||
            id.contains("o3") ||
            id.contains("reasoner") ||
            id.contains("thinking") ||
            id.contains("r1")
        ) {
            caps.add(CapabilityType.REASONING)
        }

        return caps
    }

    /**
     * Estimate context window length from model ID.
     * Returns conservative estimates.
     */
    fun inferContextLength(modelId: String): Int {
        val id = modelId.lowercase()
        return when {
            id.contains("200k") -> 200_000
            id.contains("128k") || id.contains("gpt-4o") -> 128_000
            id.contains("100k") -> 100_000
            id.contains("gemini-2") -> 1_000_000
            id.contains("gemini-1.5") -> 1_000_000
            id.contains("claude-3") || id.contains("claude-4") -> 200_000
            id.contains("gpt-4-turbo") -> 128_000
            id.contains("gpt-3.5") -> 16_385
            id.contains("mistral-large") -> 128_000
            id.contains("deepseek") -> 64_000
            id.contains("llama-3") -> 128_000
            id.contains("grok") -> 131_072
            id.contains("command-r") -> 128_000
            else -> 8_192
        }
    }
}
