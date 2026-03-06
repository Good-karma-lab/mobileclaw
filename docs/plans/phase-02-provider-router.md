# Phase 2: Provider Router — Dynamic Model Discovery & Capability-Based Selection

**Date**: 2026-03-06
**Status**: Proposal
**Depends On**: Phase 1 (Foundation)
**Blocks**: Phase 6 (Voice), Phase 10 (Live Config)

---

## 1. Objective

Build a universal provider router that:
1. **Dynamically fetches** available models from each provider's API (no hardcoded model lists)
2. **Classifies models by capability type** (text, vision, image gen, video gen, audio, embedding, code)
3. **Separates settings UI by capability type** — user picks a provider+model for each task type independently
4. **Auto-selects** the best available model when user doesn't specify
5. Supports **fallback chains**, **cost tracking**, **streaming**, and **hot-swap** (Phase 10)

---

## 2. Core Design Principle: NO HARDCODED MODELS

Every provider implementation MUST:
- Fetch available models via the provider's list-models API
- Cache the result locally (TTL: 1 hour, refresh on demand)
- Parse model capabilities from API response (or infer from model ID naming conventions)
- Present only models the user has access to

**Why:** Model lists change weekly. Hardcoding creates staleness and maintenance burden.

---

## 3. Research Checklist

- [ ] OpenAI `GET /v1/models` — response format, capability detection
- [ ] Anthropic `GET /v1/models` — response format
- [ ] Google Gemini `models.list()` — capabilities, supported generation methods
- [ ] DeepSeek `GET /models` — OpenAI-compatible
- [ ] Mistral `GET /v1/models` — capabilities field
- [ ] xAI `GET /v1/models` — Grok API format
- [ ] Cohere `GET /v2/models` — model capabilities
- [ ] Groq `GET /openai/v1/models` — OpenAI-compatible
- [ ] Together `GET /v1/models` — model type field
- [ ] Fireworks `GET /v1/models` — model info
- [ ] OpenRouter `GET /v1/models` — pricing, context length, modality
- [ ] Perplexity `GET /models` — search-augmented models
- [ ] Ollama `GET /api/tags` — local model info
- [ ] LM Studio `GET /v1/models` — OpenAI-compatible local
- [ ] Qwen API model listing
- [ ] How to infer model capabilities when API doesn't return them

---

## 4. Architecture

### 4.1 Module Structure

```
android/app/src/main/java/com/guappa/app/
└── providers/
    ├── ProviderRouter.kt             — routes requests to correct provider by capability type
    ├── ProviderInterface.kt          — unified provider interface (chat, stream, embed, generate)
    ├── ProviderConfig.kt             — per-provider settings (key, url, custom headers)
    ├── ProviderRegistry.kt           — registry of all available providers
    ├── StreamingResponse.kt          — Flow<ChatChunk> abstraction
    ├── TokenCounter.kt               — tiktoken-compatible counter per model family
    ├── CostTracker.kt                — per-request cost estimation and budget
    │
    ├── models/
    │   ├── ModelCapability.kt        — capability enum (TEXT, VISION, IMAGE_GEN, etc.)
    │   ├── ModelInfo.kt              — model metadata (id, capabilities, context, pricing)
    │   ├── ModelRegistry.kt          — local cache of fetched models per provider
    │   ├── ModelSelector.kt          — auto-select best model for capability type
    │   └── CapabilityRouter.kt       — route request to correct provider+model by task type
    │
    ├── discovery/
    │   ├── ModelDiscoveryService.kt  — background service to refresh model lists
    │   ├── ModelFetcher.kt           — interface for fetching models from provider API
    │   ├── CapabilityInferrer.kt     — infer capabilities from model ID when API lacks info
    │   └── ModelCache.kt             — local SQLite cache with TTL
    │
    ├── cloud/
    │   ├── CloudProvider.kt          — base HTTP provider (OkHttp + kotlinx.serialization)
    │   ├── OpenAICompatProvider.kt   — base for all OpenAI-compatible APIs
    │   ├── OpenAIProvider.kt         — OpenAI (GPT-5.2, o3, o4-mini, DALL-E 3, Whisper)
    │   ├── AnthropicProvider.kt      — Anthropic (Claude Opus 4, Sonnet 4.5, Haiku 4.5)
    │   ├── GoogleProvider.kt         — Google (Gemini 2.5 Pro/Flash, Gemini 3, Gemma, Imagen)
    │   ├── DeepSeekProvider.kt       — DeepSeek (V3.1, R1) — OpenAI-compatible
    │   ├── MistralProvider.kt        — Mistral (Magistral 2, Codestral, Pixtral)
    │   ├── XAIProvider.kt            — xAI (Grok 3, Grok 3 Mini) — OpenAI-compatible
    │   ├── CohereProvider.kt         — Cohere (Command A, Embed v4)
    │   ├── GroqProvider.kt           — Groq — OpenAI-compatible
    │   ├── TogetherProvider.kt       — Together AI — OpenAI-compatible
    │   ├── FireworksProvider.kt      — Fireworks AI — OpenAI-compatible
    │   ├── PerplexityProvider.kt     — Perplexity — OpenAI-compatible
    │   ├── OpenRouterProvider.kt     — OpenRouter — OpenAI-compatible (richest model metadata)
    │   ├── QwenProvider.kt           — Alibaba Qwen — OpenAI-compatible
    │   ├── GLMProvider.kt            — Zhipu GLM-4
    │   ├── MoonshotProvider.kt       — Moonshot (Kimi) — OpenAI-compatible
    │   ├── MinimaxProvider.kt        — Minimax
    │   ├── VeniceProvider.kt         — Venice AI — OpenAI-compatible
    │   ├── CopilotProvider.kt        — GitHub Copilot (OAuth, special auth flow)
    │   ├── LMStudioProvider.kt       — LM Studio local — OpenAI-compatible
    │   ├── OllamaProvider.kt         — Ollama local — OpenAI-compatible
    │   └── CustomOpenAIProvider.kt   — any custom OpenAI-compatible endpoint
    │
    ├── local/
    │   ├── LocalInferenceEngine.kt   — unified local inference interface
    │   ├── LlamaCppEngine.kt         — llama.cpp via JNI (GGUF)
    │   ├── LiteRTLMEngine.kt         — Google LiteRT-LM (Gemini Nano runtime)
    │   ├── QualcommGenieEngine.kt    — Qualcomm GENIE (Snapdragon NPU)
    │   ├── CactusEngine.kt           — Cactus SDK (sub-50ms TTFT)
    │   ├── ONNXEngine.kt             — ONNX Runtime Mobile
    │   └── ModelDownloadManager.kt   — download, verify, cache local models
    │
    └── acceleration/
        ├── HardwareProbe.kt          — detect SoC, NPU, GPU, available RAM
        ├── AcceleratorSelector.kt    — pick best backend for device hardware
        ├── LiteRTDelegate.kt         — Google LiteRT (replaces NNAPI)
        ├── QualcommQNNDelegate.kt    — Hexagon NPU
        ├── SamsungONEDelegate.kt     — Exynos NPU
        ├── GoogleEdgeTPUDelegate.kt  — Tensor G5 TPU
        ├── MediaTekNeuroPilot.kt     — Dimensity APU
        ├── VulkanComputeDelegate.kt  — Vulkan GPU compute
        └── OpenCLDelegate.kt         — OpenCL fallback
```

### 4.2 Key Interfaces

```kotlin
/**
 * Model capability types.
 * Each model can have multiple capabilities.
 * Settings UI shows provider+model selector for each type.
 */
enum class ModelCapability {
    TEXT_CHAT,        // General text conversation, reasoning
    TEXT_COMPLETION,  // Raw text completion (legacy)
    VISION,           // Image understanding (multimodal input)
    IMAGE_GENERATION, // Generate images from text (DALL-E, Imagen, SD)
    VIDEO_GENERATION, // Generate video from text (Sora, Kling)
    AUDIO_STT,        // Speech-to-text (Whisper)
    AUDIO_TTS,        // Text-to-speech (OpenAI TTS)
    EMBEDDING,        // Text embeddings for RAG/semantic search
    CODE,             // Specialized code generation
    TOOL_USE,         // Function/tool calling support
    STREAMING,        // Streaming response support
    SEARCH,           // Built-in web search (Perplexity)
    REASONING,        // Extended reasoning (o3, R1, QwQ)
}

/**
 * Metadata for a single model, fetched from provider API.
 */
data class ModelInfo(
    val id: String,                           // e.g., "gpt-5.2", "claude-opus-4"
    val providerId: String,                   // e.g., "openai", "anthropic"
    val displayName: String,                  // e.g., "GPT-5.2", "Claude Opus 4"
    val capabilities: Set<ModelCapability>,    // what this model can do
    val contextWindow: Int?,                  // max tokens (input + output)
    val maxOutputTokens: Int?,                // max output tokens
    val inputPricePer1MTokens: Double?,       // USD per 1M input tokens
    val outputPricePer1MTokens: Double?,      // USD per 1M output tokens
    val supportsToolUse: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsStreaming: Boolean = true,
    val isDeprecated: Boolean = false,
    val createdAt: Long? = null,              // model creation timestamp
    val metadata: Map<String, String> = emptyMap(), // provider-specific metadata
)

/**
 * Interface every provider must implement.
 */
interface ProviderInterface {
    val providerId: String
    val displayName: String
    val apiBaseUrl: String

    /** Fetch available models from provider API. */
    suspend fun fetchModels(apiKey: String): Result<List<ModelInfo>>

    /** Send a chat completion request. */
    suspend fun chat(request: ChatRequest): Result<ChatResponse>

    /** Send a streaming chat completion request. */
    fun chatStream(request: ChatRequest): Flow<ChatChunk>

    /** Generate embeddings. */
    suspend fun embed(request: EmbedRequest): Result<EmbedResponse>

    /** Generate an image (if supported). */
    suspend fun generateImage(request: ImageGenRequest): Result<ImageGenResponse>

    /** Test connection / validate API key. */
    suspend fun healthCheck(apiKey: String): Result<HealthStatus>
}
```

---

## 5. Dynamic Model Discovery

### 5.1 Model Fetching per Provider

Each provider implements `fetchModels()` which calls the provider's model listing API:

```kotlin
// Example: OpenAI
class OpenAIProvider : OpenAICompatProvider() {
    override val providerId = "openai"
    override val apiBaseUrl = "https://api.openai.com/v1"

    override suspend fun fetchModels(apiKey: String): Result<List<ModelInfo>> {
        // GET https://api.openai.com/v1/models
        // Authorization: Bearer $apiKey
        val response = httpClient.get("$apiBaseUrl/models") {
            header("Authorization", "Bearer $apiKey")
        }
        val rawModels = response.body<OpenAIModelsResponse>()
        return Result.success(rawModels.data.map { it.toModelInfo() })
    }
}
```

### 5.2 Provider API Endpoints for Model Discovery (Verified March 2026)

| Provider | Endpoint | Auth | Format | Capabilities? | Context? | Pricing? |
|----------|----------|------|--------|:---:|:---:|:---:|
| **OpenAI** | `GET https://api.openai.com/v1/models` | `Bearer $key` | OpenAI ref | No | No | No |
| **Anthropic** | `GET https://api.anthropic.com/v1/models` | `x-api-key: $key` + `anthropic-version: 2023-06-01` | Custom | No | No | No |
| **Google** | `GET https://generativelanguage.googleapis.com/v1beta/models?key=$key` | API key in URL | Custom | **Yes** (supportedGenerationMethods, supportThinking) | **Yes** (inputTokenLimit, outputTokenLimit) | No |
| **DeepSeek** | `GET https://api.deepseek.com/models` | `Bearer $key` | OpenAI-compat | No | No | No |
| **Mistral** | `GET https://api.mistral.ai/v1/models` | `Bearer $key` | OpenAI-compat | No | No | No |
| **xAI** | `GET https://api.x.ai/v1/models` | `Bearer $key` | OpenAI-compat | No | No | No |
| **Cohere** | `GET https://api.cohere.com/v1/models` | `Bearer $key` | Custom | **Yes** (endpoints, features) | **Yes** | No |
| **Groq** | `GET https://api.groq.com/openai/v1/models` | `Bearer $key` | OpenAI-compat | No | No | No |
| **Together** | `GET https://api.together.xyz/v1/models` | `Bearer $key` | Partial OAI* | Partial (type field) | **Yes** | **Yes** |
| **Fireworks** | `GET https://api.fireworks.ai/inference/v1/models` | `Bearer $key` | OpenAI-compat | No | No | No |
| **OpenRouter** | `GET https://openrouter.ai/api/v1/models` | `Bearer $key` (optional) | OpenAI-compat+ | **Yes** (tools, reasoning, json_mode, web_search) | **Yes** | **Yes** |
| **Perplexity** | `GET /models` | `Bearer $key` | OpenAI-compat | No | No | No |
| **Ollama** | `GET http://localhost:11434/api/tags` (native) / `/v1/models` (OAI) | None | Custom/OAI | No (param_size, quant in native) | No | N/A |
| **LM Studio** | `GET http://localhost:1234/api/v1/models` (native) / `/v1/models` (OAI) | Optional | Custom/OAI | Partial (type: vlm) | **Yes** (native) | N/A |
| **Qwen** | `GET /v1/models` | `Bearer $key` | OpenAI-compat | No | No | No |

*Together AI response is a bare JSON array `[{...}]` not wrapped in `{"object":"list","data":[...]}`

**OpenAI-compatible providers** (use same base class):
OpenAI, DeepSeek, Mistral, xAI, Groq, Fireworks, OpenRouter, Together (partial), Perplexity, Ollama, LM Studio, Qwen

**Custom API providers** (need dedicated implementation):
Anthropic (x-api-key + anthropic-version headers), Google Gemini (completely different), Cohere (own format)

> **Key insight:** Most providers return minimal data (just `id`). Use **OpenRouter** as a
> supplementary metadata source — it aggregates context_length, pricing, and capability flags
> for 200+ models across all major providers. Strategy:
> 1. Fetch model **IDs** from each provider's own API (validates access + shows what user has)
> 2. Fetch **metadata** from OpenRouter for context window, pricing, capabilities
> 3. Use **CapabilityInferrer** as fallback when neither source has capability data

### 5.3 Capability Inference

When the API doesn't return capabilities, infer from model ID:

```kotlin
class CapabilityInferrer {
    fun infer(modelId: String, providerId: String): Set<ModelCapability> {
        val caps = mutableSetOf(ModelCapability.TEXT_CHAT, ModelCapability.STREAMING)

        // Vision models
        if (modelId.containsAny("vision", "vl", "-v", "4o", "gpt-5", "pixtral", "gemini")) {
            caps += ModelCapability.VISION
        }

        // Image generation
        if (modelId.containsAny("dall-e", "imagen", "stable-diffusion", "flux", "ideogram")) {
            caps += ModelCapability.IMAGE_GENERATION
            caps -= ModelCapability.TEXT_CHAT  // these don't do chat
        }

        // Video generation
        if (modelId.containsAny("sora", "kling", "runway", "gen-3")) {
            caps += ModelCapability.VIDEO_GENERATION
            caps -= ModelCapability.TEXT_CHAT
        }

        // Audio
        if (modelId.containsAny("whisper", "stt")) {
            caps += ModelCapability.AUDIO_STT
            caps -= ModelCapability.TEXT_CHAT
        }
        if (modelId.containsAny("tts")) {
            caps += ModelCapability.AUDIO_TTS
            caps -= ModelCapability.TEXT_CHAT
        }

        // Embedding
        if (modelId.containsAny("embed", "embedding", "text-embedding")) {
            caps += ModelCapability.EMBEDDING
            caps -= ModelCapability.TEXT_CHAT
        }

        // Code
        if (modelId.containsAny("codestral", "code", "starcoder", "deepseek-coder")) {
            caps += ModelCapability.CODE
        }

        // Reasoning
        if (modelId.containsAny("o3", "o4", "r1", "qwq", "reasoning")) {
            caps += ModelCapability.REASONING
        }

        // Tool use (most modern chat models support it)
        if (caps.contains(ModelCapability.TEXT_CHAT) && !modelId.containsAny("embed", "tts", "stt")) {
            caps += ModelCapability.TOOL_USE
        }

        // Search (Perplexity-specific)
        if (modelId.containsAny("sonar")) {
            caps += ModelCapability.SEARCH
        }

        return caps
    }
}
```

### 5.4 Model Cache

```kotlin
@Entity(tableName = "model_cache")
data class CachedModel(
    @PrimaryKey val cacheKey: String,  // "$providerId:$modelId"
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val capabilitiesJson: String,  // JSON array of ModelCapability
    val contextWindow: Int?,
    val maxOutputTokens: Int?,
    val inputPrice: Double?,
    val outputPrice: Double?,
    val fetchedAt: Long,           // timestamp
    val ttlMs: Long = 3_600_000,   // 1 hour default TTL
) {
    fun isExpired(): Boolean = System.currentTimeMillis() > fetchedAt + ttlMs
}
```

### 5.5 OpenRouter Metadata Enrichment

Most providers return only model IDs. Use OpenRouter as a supplementary metadata source:

```kotlin
class OpenRouterMetadataEnricher(private val httpClient: HttpClient) {
    /**
     * Fetch rich metadata from OpenRouter for all known models.
     * OpenRouter returns: context_length, pricing, supported_parameters (tools, reasoning, etc.)
     *
     * Strategy:
     * 1. Fetch full model list from OpenRouter (no auth needed for public list)
     * 2. Cache as a lookup map: modelId → metadata
     * 3. When enriching a provider's models, match by model ID patterns
     *    (e.g., "gpt-5.2" matches "openai/gpt-5.2" in OpenRouter)
     */
    suspend fun fetchMetadata(): Map<String, OpenRouterModelInfo> {
        val response = httpClient.get("https://openrouter.ai/api/v1/models")
        val body = response.body<OpenRouterModelsResponse>()
        return body.data.associateBy { it.id }
    }

    fun enrich(model: ModelInfo, orMetadata: Map<String, OpenRouterModelInfo>): ModelInfo {
        // Try exact match first: "openai/gpt-5.2"
        val orKey = "${model.providerId}/${model.id}"
        val metadata = orMetadata[orKey]
            ?: orMetadata.values.find { it.id.endsWith("/${model.id}") }
            ?: return model

        return model.copy(
            contextWindow = model.contextWindow ?: metadata.context_length,
            maxOutputTokens = model.maxOutputTokens ?: metadata.max_completion_tokens,
            inputPricePer1MTokens = model.inputPricePer1MTokens
                ?: metadata.pricing?.prompt?.toDoubleOrNull()?.times(1_000_000),
            outputPricePer1MTokens = model.outputPricePer1MTokens
                ?: metadata.pricing?.completion?.toDoubleOrNull()?.times(1_000_000),
            // Enrich capabilities from OpenRouter's supported_parameters
            capabilities = model.capabilities + inferFromOpenRouter(metadata),
        )
    }

    private fun inferFromOpenRouter(metadata: OpenRouterModelInfo): Set<ModelCapability> {
        val caps = mutableSetOf<ModelCapability>()
        metadata.supported_parameters?.let { params ->
            if ("tools" in params) caps += ModelCapability.TOOL_USE
            if ("reasoning" in params) caps += ModelCapability.REASONING
            if ("web_search" in params) caps += ModelCapability.SEARCH
        }
        return caps
    }
}
```

### 5.6 Refresh Strategy

```kotlin
class ModelDiscoveryService(
    private val providers: List<ProviderInterface>,
    private val cache: ModelCache,
) {
    /** Refresh models for all configured providers. */
    suspend fun refreshAll(configs: Map<String, ProviderConfig>) {
        configs.entries
            .filter { it.value.enabled && it.value.apiKey.isNotBlank() }
            .map { (providerId, config) ->
                coroutineScope {
                    async {
                        refreshProvider(providerId, config)
                    }
                }
            }
            .awaitAll()
    }

    /** Refresh models for a single provider. */
    suspend fun refreshProvider(providerId: String, config: ProviderConfig) {
        val provider = providers.find { it.providerId == providerId } ?: return
        val result = provider.fetchModels(config.apiKey)
        result.onSuccess { models ->
            cache.upsert(providerId, models)
        }
        result.onFailure { error ->
            // Keep stale cache, log error
            Log.w("ModelDiscovery", "Failed to fetch models for $providerId: $error")
        }
    }

    /**
     * Triggers:
     * 1. App launch (if cache expired)
     * 2. User opens Settings → Provider section
     * 3. User changes API key for a provider
     * 4. Manual pull-to-refresh in model list
     * 5. Periodic background refresh (every 6 hours via WorkManager)
     */
}
```

---

## 6. Capability-Based Settings UI

### 6.1 Settings Screen Structure

The Settings screen shows **separate provider+model selectors for each capability type**:

```
┌─────────────────────────────────────┐
│         ⚙️ AI Settings              │
├─────────────────────────────────────┤
│                                     │
│  📝 Text / Chat                     │
│  ┌───────────────────────────────┐  │
│  │ Provider: [OpenAI ▼]          │  │
│  │ Model:    [GPT-5.2 ▼]        │  │
│  │ Fallback: [Anthropic/Sonnet ▼]│  │
│  └───────────────────────────────┘  │
│                                     │
│  👁️ Vision (Image Understanding)    │
│  ┌───────────────────────────────┐  │
│  │ Provider: [Google ▼]          │  │
│  │ Model:    [Gemini 2.5 Pro ▼]  │  │
│  └───────────────────────────────┘  │
│                                     │
│  🎨 Image Generation                │
│  ┌───────────────────────────────┐  │
│  │ Provider: [OpenAI ▼]          │  │
│  │ Model:    [DALL-E 3 ▼]        │  │
│  └───────────────────────────────┘  │
│                                     │
│  🎬 Video Generation                │
│  ┌───────────────────────────────┐  │
│  │ Provider: [OpenAI ▼]          │  │
│  │ Model:    [Sora ▼]            │  │
│  └───────────────────────────────┘  │
│                                     │
│  🎤 Voice Mode (STT)               │
│  ┌───────────────────────────────┐  │
│  │ Engine:   [WhisperKit ▼]      │  │
│  │ Model:    [Whisper Small ▼]   │  │
│  │ Fallback: [Google ML Kit ▼]   │  │
│  └───────────────────────────────┘  │
│                                     │
│  🔊 Voice Mode (TTS)               │
│  ┌───────────────────────────────┐  │
│  │ Engine:   [Picovoice Orca ▼]  │  │
│  │ Voice:    [Female Ru ▼]       │  │
│  │ Fallback: [Android TTS ▼]    │  │
│  └───────────────────────────────┘  │
│                                     │
│  🔍 Embeddings (Memory/RAG)        │
│  ┌───────────────────────────────┐  │
│  │ Provider: [On-Device ▼]       │  │
│  │ Model:    [all-MiniLM-L6 ▼]  │  │
│  └───────────────────────────────┘  │
│                                     │
│  💻 Code (Specialized)             │
│  ┌───────────────────────────────┐  │
│  │ Provider: [Mistral ▼]         │  │
│  │ Model:    [Codestral ▼]       │  │
│  └───────────────────────────────┘  │
│                                     │
│  🧠 Reasoning (Extended Thinking)  │
│  ┌───────────────────────────────┐  │
│  │ Provider: [DeepSeek ▼]        │  │
│  │ Model:    [DeepSeek-R1 ▼]     │  │
│  └───────────────────────────────┘  │
│                                     │
│  🔎 Web Search                      │
│  ┌───────────────────────────────┐  │
│  │ Provider: [Perplexity ▼]      │  │
│  │ Model:    [Sonar Pro ▼]       │  │
│  └───────────────────────────────┘  │
│                                     │
│  📱 On-Device (Offline)            │
│  ┌───────────────────────────────┐  │
│  │ Engine:   [llama.cpp ▼]       │  │
│  │ Model:    [Qwen 2.5 7B ▼]    │  │
│  │ [Download Model] [Manage...]  │  │
│  └───────────────────────────────┘  │
│                                     │
│  ─── Provider API Keys ───         │
│  OpenAI:     [sk-...] ✅           │
│  Anthropic:  [sk-ant-...] ✅       │
│  Google:     [AIza...] ✅          │
│  DeepSeek:   [Not configured]      │
│  ...                                │
│                                     │
│  ─── Advanced ───                  │
│  Temperature: [0.7]                │
│  Max tokens:  [4096]               │
│  Auto-fallback: [✅ On]            │
│  Cost budget:   [$5.00/day]        │
│                                     │
└─────────────────────────────────────┘
```

### 6.2 How Model Dropdowns Are Populated

When user selects a provider in a capability section:
1. Check ModelCache for models of that provider with matching capability
2. If cache expired → trigger background refresh → show stale data with spinner
3. Filter models: only show models with the required capability
4. Sort: newest first, then by context window (descending)
5. Show model ID + context window + price hint

```kotlin
class ModelSelector(private val cache: ModelCache) {
    fun getModelsForCapability(
        providerId: String,
        capability: ModelCapability,
    ): List<ModelInfo> {
        return cache.getModels(providerId)
            .filter { capability in it.capabilities }
            .sortedWith(
                compareByDescending<ModelInfo> { it.createdAt ?: 0 }
                    .thenByDescending { it.contextWindow ?: 0 }
            )
    }

    fun getProvidersForCapability(
        capability: ModelCapability,
        configs: Map<String, ProviderConfig>,
    ): List<String> {
        return configs.entries
            .filter { it.value.enabled && it.value.apiKey.isNotBlank() }
            .filter { (providerId, _) ->
                cache.getModels(providerId).any { capability in it.capabilities }
            }
            .map { it.key }
    }
}
```

### 6.3 Configuration Schema

```kotlin
data class ProviderRouterConfig(
    // Per-capability provider+model selection
    val textChat: CapabilityConfig = CapabilityConfig(),
    val vision: CapabilityConfig = CapabilityConfig(),
    val imageGeneration: CapabilityConfig = CapabilityConfig(),
    val videoGeneration: CapabilityConfig = CapabilityConfig(),
    val audioSTT: CapabilityConfig = CapabilityConfig(),
    val audioTTS: CapabilityConfig = CapabilityConfig(),
    val embedding: CapabilityConfig = CapabilityConfig(),
    val code: CapabilityConfig = CapabilityConfig(),
    val reasoning: CapabilityConfig = CapabilityConfig(),
    val webSearch: CapabilityConfig = CapabilityConfig(),
    val onDevice: CapabilityConfig = CapabilityConfig(),

    // Per-provider API keys
    val providerKeys: Map<String, ProviderConfig> = emptyMap(),

    // Global settings
    val temperature: Float = 0.7f,
    val maxOutputTokens: Int = 4096,
    val autoFallback: Boolean = true,
    val dailyCostBudget: Double = 5.0,  // USD

    // Cache
    val modelCacheTtlMs: Long = 3_600_000,  // 1 hour
)

data class CapabilityConfig(
    val providerId: String = "",
    val modelId: String = "",
    val fallbackProviderId: String = "",
    val fallbackModelId: String = "",
    val enabled: Boolean = true,
)

data class ProviderConfig(
    val apiKey: String = "",
    val baseUrl: String = "",  // custom endpoint override
    val enabled: Boolean = true,
    val customHeaders: Map<String, String> = emptyMap(),
)
```

---

## 7. Capability Router

The CapabilityRouter decides which provider+model to use based on the task:

```kotlin
class CapabilityRouter(
    private val config: StateFlow<ProviderRouterConfig>,
    private val registry: ProviderRegistry,
    private val costTracker: CostTracker,
) {
    /**
     * Route a request to the correct provider+model.
     *
     * Priority:
     * 1. User-configured provider+model for this capability
     * 2. Fallback provider+model
     * 3. Auto-select best available (if auto-fallback enabled)
     * 4. Error: no provider available for this capability
     */
    suspend fun route(capability: ModelCapability): RoutingResult {
        val cfg = config.value
        val capConfig = cfg.getCapabilityConfig(capability)

        // Check daily budget
        if (costTracker.todaySpent() >= cfg.dailyCostBudget) {
            // Try on-device model
            val onDevice = cfg.onDevice
            if (onDevice.enabled && onDevice.modelId.isNotBlank()) {
                return RoutingResult.OnDevice(onDevice.modelId)
            }
            return RoutingResult.Error("Daily cost budget exceeded ($${cfg.dailyCostBudget})")
        }

        // Try primary
        if (capConfig.enabled && capConfig.providerId.isNotBlank()) {
            val provider = registry.get(capConfig.providerId)
            if (provider != null) {
                return RoutingResult.Cloud(provider, capConfig.modelId)
            }
        }

        // Try fallback
        if (capConfig.fallbackProviderId.isNotBlank()) {
            val provider = registry.get(capConfig.fallbackProviderId)
            if (provider != null) {
                return RoutingResult.Cloud(provider, capConfig.fallbackModelId)
            }
        }

        // Auto-select
        if (cfg.autoFallback) {
            return autoSelect(capability)
        }

        return RoutingResult.Error("No provider configured for $capability")
    }
}
```

---

## 8. OpenAI-Compatible Provider Base

Most providers (13 out of 21) use OpenAI-compatible API format. Share implementation:

```kotlin
/**
 * Base class for all OpenAI-compatible API providers.
 * Covers: OpenAI, DeepSeek, xAI, Groq, Together, Fireworks, Perplexity,
 *         OpenRouter, Qwen, Moonshot, Venice, LM Studio, Ollama.
 */
abstract class OpenAICompatProvider : ProviderInterface {
    protected abstract val authHeaderName: String  // "Authorization" or custom
    protected abstract fun authHeaderValue(apiKey: String): String  // "Bearer $key"

    override suspend fun fetchModels(apiKey: String): Result<List<ModelInfo>> {
        val response = httpClient.get("$apiBaseUrl/models") {
            header(authHeaderName, authHeaderValue(apiKey))
        }
        val body = response.body<OpenAIModelsListResponse>()
        return Result.success(body.data.map { raw ->
            ModelInfo(
                id = raw.id,
                providerId = providerId,
                displayName = raw.id.formatAsDisplayName(),
                capabilities = capabilityInferrer.infer(raw.id, providerId),
                contextWindow = raw.contextWindow,  // if provided
                // ... fill from response or infer
            )
        })
    }

    override suspend fun chat(request: ChatRequest): Result<ChatResponse> {
        val response = httpClient.post("$apiBaseUrl/chat/completions") {
            header(authHeaderName, authHeaderValue(request.apiKey))
            contentType(ContentType.Application.Json)
            setBody(request.toOpenAIFormat())
        }
        return Result.success(response.body<OpenAIChatResponse>().toChatResponse())
    }

    override fun chatStream(request: ChatRequest): Flow<ChatChunk> = flow {
        httpClient.preparePost("$apiBaseUrl/chat/completions") {
            header(authHeaderName, authHeaderValue(request.apiKey))
            contentType(ContentType.Application.Json)
            setBody(request.toOpenAIFormat().copy(stream = true))
        }.execute { response ->
            response.bodyAsChannel().toSSEFlow().collect { sse ->
                if (sse.data != "[DONE]") {
                    emit(sse.data.parseAsChatChunk())
                }
            }
        }
    }
}
```

---

## 9. Provider-Specific Implementations

### 9.1 Non-OpenAI-Compatible Providers

| Provider | API Format | Key Differences |
|----------|-----------|-----------------|
| **Anthropic** | Custom (Messages API) | `x-api-key` header, `messages` format, `content` is array of blocks |
| **Google** | Custom (Gemini API) | API key in URL param, `contents` / `parts` format |
| **Cohere** | Custom (Chat API) | `tools` format differs, `stream` is separate endpoint |
| **GLM** | Custom | `zhipuai` SDK-style auth |
| **Minimax** | Custom | Group ID + API key auth |

### 9.2 Anthropic Special Handling

```kotlin
class AnthropicProvider : ProviderInterface {
    override val providerId = "anthropic"
    override val apiBaseUrl = "https://api.anthropic.com/v1"

    override suspend fun fetchModels(apiKey: String): Result<List<ModelInfo>> {
        // GET https://api.anthropic.com/v1/models
        // Paginated: cursor-based via before_id/after_id, page size 1-1000 (default 20)
        val allModels = mutableListOf<ModelInfo>()
        var afterId: String? = null
        do {
            val response = httpClient.get("$apiBaseUrl/models") {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                parameter("limit", 100)
                if (afterId != null) parameter("after_id", afterId)
            }
            val body = response.body<AnthropicModelsResponse>()
            // Response: { data: [{ id, display_name, created_at, type }], has_more, last_id }
            allModels += body.data.map { raw ->
                ModelInfo(
                    id = raw.id,
                    providerId = "anthropic",
                    displayName = raw.display_name,
                    capabilities = capabilityInferrer.infer(raw.id, "anthropic"),
                    // Anthropic API doesn't return context_length or pricing —
                    // supplement from OpenRouter metadata cache
                )
            }
            afterId = body.last_id
        } while (body.has_more)
        return Result.success(allModels)
    }

    override suspend fun chat(request: ChatRequest): Result<ChatResponse> {
        // Convert from universal format to Anthropic Messages format
        val body = AnthropicMessagesRequest(
            model = request.model,
            messages = request.messages.toAnthropicFormat(),
            system = request.systemPrompt,
            tools = request.tools?.toAnthropicToolFormat(),
            max_tokens = request.maxOutputTokens,
            temperature = request.temperature,
            stream = false,
        )
        // POST /v1/messages
    }
}
```

### 9.3 Google Gemini Special Handling

```kotlin
class GoogleProvider : ProviderInterface {
    override val providerId = "google"
    override val apiBaseUrl = "https://generativelanguage.googleapis.com/v1beta"

    override suspend fun fetchModels(apiKey: String): Result<List<ModelInfo>> {
        // GET https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey
        // Paginated via pageSize + pageToken
        // Response: { models: [{ name, displayName, description, inputTokenLimit,
        //   outputTokenLimit, supportedGenerationMethods, supportThinking, temperature }] }
        val allModels = mutableListOf<ModelInfo>()
        var pageToken: String? = null
        do {
            val response = httpClient.get("$apiBaseUrl/models") {
                parameter("key", apiKey)
                parameter("pageSize", 100)
                if (pageToken != null) parameter("pageToken", pageToken)
            }
            val body = response.body<GeminiModelsResponse>()
            allModels += body.models.map { raw ->
                ModelInfo(
                    id = raw.name.removePrefix("models/"),
                    providerId = "google",
                    displayName = raw.displayName,
                    capabilities = inferFromGenerationMethods(raw.supportedGenerationMethods, raw.supportThinking),
                    contextWindow = raw.inputTokenLimit,
                    maxOutputTokens = raw.outputTokenLimit,
                    // Google is the richest API — returns token limits directly
                )
            }
            pageToken = body.nextPageToken
        } while (pageToken != null)
        return Result.success(allModels)
    }

    private fun inferFromGenerationMethods(
        methods: List<String>,
        supportThinking: Boolean?,
    ): Set<ModelCapability> {
        val caps = mutableSetOf<ModelCapability>()
        if ("generateContent" in methods) {
            caps += ModelCapability.TEXT_CHAT
            caps += ModelCapability.STREAMING
            caps += ModelCapability.TOOL_USE
        }
        if ("embedContent" in methods) caps += ModelCapability.EMBEDDING
        if ("generateImage" in methods) caps += ModelCapability.IMAGE_GENERATION
        if (supportThinking == true) caps += ModelCapability.REASONING
        return caps
    }
}
```

---

## 10. Cost Tracking

```kotlin
class CostTracker(private val db: CostDatabase) {
    suspend fun trackRequest(
        providerId: String,
        modelId: String,
        inputTokens: Int,
        outputTokens: Int,
        modelInfo: ModelInfo?,
    ) {
        val inputCost = (modelInfo?.inputPricePer1MTokens ?: 0.0) * inputTokens / 1_000_000
        val outputCost = (modelInfo?.outputPricePer1MTokens ?: 0.0) * outputTokens / 1_000_000
        val totalCost = inputCost + outputCost

        db.insert(CostEntry(
            providerId = providerId,
            modelId = modelId,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            costUsd = totalCost,
            timestamp = System.currentTimeMillis(),
        ))
    }

    suspend fun todaySpent(): Double {
        val startOfDay = /* midnight today */
        return db.sumCostSince(startOfDay)
    }

    suspend fun monthlyReport(): CostReport {
        // Breakdown by provider, model, day
    }
}
```

---

## 11. Hardware Acceleration & Local Inference

### 11.1 Hardware Probe

```kotlin
class HardwareProbe(private val context: Context) {
    data class DeviceCapabilities(
        val socVendor: SocVendor,      // QUALCOMM, MEDIATEK, SAMSUNG, GOOGLE, UNKNOWN
        val socModel: String,           // e.g., "SM8650" (SD 8 Elite)
        val hasNPU: Boolean,
        val npuTops: Int?,              // estimated TOPS
        val gpuVendor: GpuVendor,       // ADRENO, MALI, POWERVR, UNKNOWN
        val vulkanVersion: String?,
        val openClSupported: Boolean,
        val totalRamMb: Int,
        val availableRamMb: Int,
        val recommendedModelSize: ModelSize, // TINY, SMALL, MEDIUM, LARGE
        val recommendedBackend: InferenceBackend, // CPU, GPU, NPU
    )

    fun probe(): DeviceCapabilities {
        val soc = detectSoC()
        val ram = getAvailableRam()
        val modelSize = when {
            ram >= 12_000 -> ModelSize.LARGE    // 7B+ models
            ram >= 8_000  -> ModelSize.MEDIUM   // 4B models
            ram >= 4_000  -> ModelSize.SMALL    // 1.5B models
            else          -> ModelSize.TINY     // 0.5B models
        }
        // ...
    }
}
```

### 11.2 Local Model Download Manager

```kotlin
class ModelDownloadManager(
    private val context: Context,
    private val notificationManager: GuappaNotificationManager,
) {
    data class DownloadTask(
        val modelId: String,
        val url: String,
        val expectedSizeBytes: Long,
        val sha256: String,
        val progress: StateFlow<Float>,
    )

    suspend fun download(model: LocalModelInfo): Result<File> {
        // 1. Check available storage
        // 2. Start download with progress notification
        // 3. Verify SHA256 hash
        // 4. Move to models directory
        // 5. Update model cache
    }

    fun getDownloadedModels(): List<LocalModelInfo> {
        val modelsDir = File(context.filesDir, "models")
        return modelsDir.listFiles()
            ?.filter { it.extension == "gguf" }
            ?.map { parseModelInfo(it) }
            ?: emptyList()
    }
}
```

---

## 12. Token Counting

```kotlin
class TokenCounter {
    // Per-model-family tokenizer
    private val tokenizers = mutableMapOf<String, Tokenizer>()

    fun count(text: String, modelId: String): Int {
        val family = getModelFamily(modelId)
        val tokenizer = tokenizers.getOrPut(family) { loadTokenizer(family) }
        return tokenizer.encode(text).size
    }

    private fun getModelFamily(modelId: String): String = when {
        modelId.startsWith("gpt-") || modelId.startsWith("o") -> "cl100k"  // OpenAI
        modelId.startsWith("claude") -> "claude"  // Anthropic
        modelId.startsWith("gemini") -> "gemini"  // Google
        else -> "cl100k"  // default fallback
    }

    // Use jtokkit library for tiktoken-compatible counting on JVM
}
```

---

## 13. Test Plan

### 13.1 Unit Tests

| Test | Description |
|------|-------------|
| `ModelDiscovery_OpenAI_ParsesModels` | Mock OpenAI /models response → correct ModelInfo list |
| `ModelDiscovery_Anthropic_ParsesModels` | Mock Anthropic /models → correct parsing |
| `ModelDiscovery_Google_ParsesCapabilities` | Gemini supportedGenerationMethods → correct capabilities |
| `CapabilityInferrer_VisionModel` | "gpt-5.2" → includes VISION |
| `CapabilityInferrer_EmbeddingModel` | "text-embedding-3-large" → EMBEDDING only |
| `CapabilityInferrer_ImageGenModel` | "dall-e-3" → IMAGE_GENERATION |
| `CapabilityRouter_PrimaryAvailable` | Primary configured → routes to primary |
| `CapabilityRouter_PrimaryDown_Fallback` | Primary fails → routes to fallback |
| `CapabilityRouter_BudgetExceeded` | Over budget → routes to on-device |
| `ModelCache_TTL_Expires` | After 1 hour → cache marked expired |
| `ModelSelector_FiltersCapability` | Request TEXT_CHAT → only chat models returned |
| `CostTracker_DailySum` | 3 requests → correct daily total |
| `TokenCounter_OpenAI` | Known text → correct token count |
| `OpenAICompatProvider_Chat` | Mock response → correct ChatResponse |
| `OpenAICompatProvider_Stream` | Mock SSE → correct Flow<ChatChunk> |
| `AnthropicProvider_MessageFormat` | Universal request → correct Anthropic format |
| `GoogleProvider_ContentFormat` | Universal request → correct Gemini format |

### 13.2 Integration Tests

| Test | Scenario |
|------|----------|
| `ProviderRouter_E2E_Chat` | Configure provider → send chat → get response |
| `ProviderRouter_E2E_Stream` | Stream response → all chunks received → complete |
| `ProviderRouter_Fallback` | Primary returns 500 → fallback succeeds |
| `ModelDiscovery_RefreshAll` | Refresh 3 providers → all models cached |
| `HardwareProbe_RealDevice` | Probe on real device → reasonable capabilities |

### 13.3 Maestro E2E

```yaml
# Settings: configure provider and model
- launchApp: com.guappa.app
- tapOn: "Settings"
- tapOn: "AI Settings"
- tapOn: "Text / Chat"
- tapOn: "Provider"
- assertVisible: "OpenAI"  # dynamically fetched
- tapOn: "OpenAI"
- tapOn: "Model"
- assertVisible: "gpt-"  # dynamically fetched model list
- tapOn:
    text: "gpt-5.2"
- pressBack
- pressBack
# Send message with new provider
- tapOn: "Chat"
- inputText: "Hello"
- tapOn: "Send"
- assertVisible:
    timeout: 30000
    text: ".*"  # response appears
```

---

## 14. Acceptance Criteria

- [ ] All configured providers fetch models dynamically via API
- [ ] Model lists update automatically (1h TTL, pull-to-refresh)
- [ ] Settings UI shows separate provider+model selector per capability type
- [ ] Only models with matching capability appear in each selector
- [ ] Fallback chain works (primary → fallback → auto-select → error)
- [ ] Cost tracking per request, daily budget enforcement
- [ ] OpenAI-compatible base class covers 13+ providers with minimal per-provider code
- [ ] Non-OpenAI providers (Anthropic, Google, Cohere) format conversion works
- [ ] Streaming works for all providers that support it
- [ ] Local inference works via llama.cpp / LiteRT-LM
- [ ] Hardware probe correctly identifies device capabilities
- [ ] Model download with progress, resume, hash verification
- [ ] Token counting accurate within 5% for all major model families

---

## 15. Rollback Strategy

- Feature flag: `useNewProviderRouter` — can revert to old hardcoded provider
- Model cache is SQLite — simply delete table to reset
- Provider configs stored in DataStore — backwards compatible with old format
- No breaking changes to agent orchestrator interface
