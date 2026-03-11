package com.guappa.app.config

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Central configuration store backed by EncryptedSharedPreferences.
 *
 * Provides reactive StateFlow updates for all config sections so that
 * subscribers (ConfigChangeDispatcher, UI) react immediately to changes.
 * Supports JSON import/export for backup and migration.
 */
class GuappaConfigStore(private val context: Context) {
    private val TAG = "GuappaConfigStore"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy { SecurePrefs.config(context) }

    // --- Full config snapshot as StateFlow ---
    private val _config = MutableStateFlow(loadFullConfig())
    val config: StateFlow<GuappaFullConfig> = _config.asStateFlow()

    // --- Section-level flows for granular observation ---
    val providerConfig: StateFlow<ProviderConfig>
        get() = _config.map { it.provider }.stateIn(scope, SharingStarted.Eagerly, _config.value.provider)

    val voiceConfig: StateFlow<VoiceConfig>
        get() = _config.map { it.voice }.stateIn(scope, SharingStarted.Eagerly, _config.value.voice)

    val memoryConfig: StateFlow<MemoryConfig>
        get() = _config.map { it.memory }.stateIn(scope, SharingStarted.Eagerly, _config.value.memory)

    val proactiveConfig: StateFlow<ProactiveConfig>
        get() = _config.map { it.proactive }.stateIn(scope, SharingStarted.Eagerly, _config.value.proactive)

    val channelsConfig: StateFlow<ChannelsConfig>
        get() = _config.map { it.channels }.stateIn(scope, SharingStarted.Eagerly, _config.value.channels)

    val personaConfig: StateFlow<PersonaConfig>
        get() = _config.map { it.persona }.stateIn(scope, SharingStarted.Eagerly, _config.value.persona)

    val toolsConfig: StateFlow<ToolsConfig>
        get() = _config.map { it.tools }.stateIn(scope, SharingStarted.Eagerly, _config.value.tools)

    // --- Mutators ---

    fun updateProvider(transform: ProviderConfig.() -> ProviderConfig) {
        val current = _config.value
        val updated = current.copy(provider = current.provider.transform())
        _config.value = updated
        persistSection("provider", updated.provider.toJSON())
    }

    fun updateVoice(transform: VoiceConfig.() -> VoiceConfig) {
        val current = _config.value
        val updated = current.copy(voice = current.voice.transform())
        _config.value = updated
        persistSection("voice", updated.voice.toJSON())
    }

    fun updateMemory(transform: MemoryConfig.() -> MemoryConfig) {
        val current = _config.value
        val updated = current.copy(memory = current.memory.transform())
        _config.value = updated
        persistSection("memory", updated.memory.toJSON())
    }

    fun updateProactive(transform: ProactiveConfig.() -> ProactiveConfig) {
        val current = _config.value
        val updated = current.copy(proactive = current.proactive.transform())
        _config.value = updated
        persistSection("proactive", updated.proactive.toJSON())
    }

    fun updateChannels(transform: ChannelsConfig.() -> ChannelsConfig) {
        val current = _config.value
        val updated = current.copy(channels = current.channels.transform())
        _config.value = updated
        persistSection("channels", updated.channels.toJSON())
    }

    fun updatePersona(transform: PersonaConfig.() -> PersonaConfig) {
        val current = _config.value
        val updated = current.copy(persona = current.persona.transform())
        _config.value = updated
        persistSection("persona", updated.persona.toJSON())
    }

    fun updateTools(transform: ToolsConfig.() -> ToolsConfig) {
        val current = _config.value
        val updated = current.copy(tools = current.tools.transform())
        _config.value = updated
        persistSection("tools", updated.tools.toJSON())
    }

    // --- Import / Export ---

    fun exportAsJSON(): String {
        return _config.value.toJSON().toString(2)
    }

    fun importFromJSON(json: String): Boolean {
        return try {
            val parsed = JSONObject(json)
            val imported = GuappaFullConfig.fromJSON(parsed)
            _config.value = imported
            persistAll(imported)
            Log.d(TAG, "Config imported successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Config import failed: ${e.message}")
            false
        }
    }

    // --- Persistence helpers ---

    private fun persistSection(key: String, json: JSONObject) {
        scope.launch {
            try {
                prefs.edit().putString("config_$key", json.toString()).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist config section '$key': ${e.message}")
            }
        }
    }

    private fun persistAll(config: GuappaFullConfig) {
        scope.launch {
            try {
                val editor = prefs.edit()
                editor.putString("config_provider", config.provider.toJSON().toString())
                editor.putString("config_voice", config.voice.toJSON().toString())
                editor.putString("config_memory", config.memory.toJSON().toString())
                editor.putString("config_proactive", config.proactive.toJSON().toString())
                editor.putString("config_channels", config.channels.toJSON().toString())
                editor.putString("config_persona", config.persona.toJSON().toString())
                editor.putString("config_tools", config.tools.toJSON().toString())
                editor.apply()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist full config: ${e.message}")
            }
        }
    }

    private fun loadFullConfig(): GuappaFullConfig {
        return try {
            GuappaFullConfig(
                provider = loadSection("provider") { ProviderConfig.fromJSON(it) } ?: ProviderConfig(),
                voice = loadSection("voice") { VoiceConfig.fromJSON(it) } ?: VoiceConfig(),
                memory = loadSection("memory") { MemoryConfig.fromJSON(it) } ?: MemoryConfig(),
                proactive = loadSection("proactive") { ProactiveConfig.fromJSON(it) } ?: ProactiveConfig(),
                channels = loadSection("channels") { ChannelsConfig.fromJSON(it) } ?: ChannelsConfig(),
                persona = loadSection("persona") { PersonaConfig.fromJSON(it) } ?: PersonaConfig(),
                tools = loadSection("tools") { ToolsConfig.fromJSON(it) } ?: ToolsConfig(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config, using defaults: ${e.message}")
            GuappaFullConfig()
        }
    }

    private fun <T> loadSection(key: String, parser: (JSONObject) -> T): T? {
        val raw = prefs.getString("config_$key", null) ?: return null
        return try {
            parser(JSONObject(raw))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse config section '$key': ${e.message}")
            null
        }
    }
}

// --- Data classes for each config section ---

data class GuappaFullConfig(
    val provider: ProviderConfig = ProviderConfig(),
    val voice: VoiceConfig = VoiceConfig(),
    val memory: MemoryConfig = MemoryConfig(),
    val proactive: ProactiveConfig = ProactiveConfig(),
    val channels: ChannelsConfig = ChannelsConfig(),
    val persona: PersonaConfig = PersonaConfig(),
    val tools: ToolsConfig = ToolsConfig(),
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("provider", provider.toJSON())
        put("voice", voice.toJSON())
        put("memory", memory.toJSON())
        put("proactive", proactive.toJSON())
        put("channels", channels.toJSON())
        put("persona", persona.toJSON())
        put("tools", tools.toJSON())
    }

    companion object {
        fun fromJSON(json: JSONObject): GuappaFullConfig = GuappaFullConfig(
            provider = json.optJSONObject("provider")?.let { ProviderConfig.fromJSON(it) } ?: ProviderConfig(),
            voice = json.optJSONObject("voice")?.let { VoiceConfig.fromJSON(it) } ?: VoiceConfig(),
            memory = json.optJSONObject("memory")?.let { MemoryConfig.fromJSON(it) } ?: MemoryConfig(),
            proactive = json.optJSONObject("proactive")?.let { ProactiveConfig.fromJSON(it) } ?: ProactiveConfig(),
            channels = json.optJSONObject("channels")?.let { ChannelsConfig.fromJSON(it) } ?: ChannelsConfig(),
            persona = json.optJSONObject("persona")?.let { PersonaConfig.fromJSON(it) } ?: PersonaConfig(),
            tools = json.optJSONObject("tools")?.let { ToolsConfig.fromJSON(it) } ?: ToolsConfig(),
        )
    }
}

data class ProviderConfig(
    val providerId: String = "openrouter",
    val modelId: String = "",
    val apiKey: String = "",
    val apiUrl: String = "",
    val temperature: Double = 0.7,
    val maxTokens: Int = 65536,
    val streamingEnabled: Boolean = true,
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("provider_id", providerId)
        put("model_id", modelId)
        // API key is NOT exported for security
        put("api_url", apiUrl)
        put("temperature", temperature)
        put("max_tokens", maxTokens)
        put("streaming_enabled", streamingEnabled)
    }

    companion object {
        fun fromJSON(json: JSONObject): ProviderConfig = ProviderConfig(
            providerId = json.optString("provider_id", "openrouter"),
            modelId = json.optString("model_id", ""),
            apiKey = json.optString("api_key", ""),
            apiUrl = json.optString("api_url", ""),
            temperature = json.optDouble("temperature", 0.7),
            maxTokens = json.optInt("max_tokens", 65536),
            streamingEnabled = json.optBoolean("streaming_enabled", true),
        )
    }
}

data class VoiceConfig(
    val sttEngine: String = "android",
    val ttsEngine: String = "android",
    val ttsVoice: String = "",
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val wakeWordEnabled: Boolean = false,
    val wakeWord: String = "hey guappa",
    val autoListenAfterResponse: Boolean = true,
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("stt_engine", sttEngine)
        put("tts_engine", ttsEngine)
        put("tts_voice", ttsVoice)
        put("tts_speed", ttsSpeed.toDouble())
        put("tts_pitch", ttsPitch.toDouble())
        put("wake_word_enabled", wakeWordEnabled)
        put("wake_word", wakeWord)
        put("auto_listen_after_response", autoListenAfterResponse)
    }

    companion object {
        fun fromJSON(json: JSONObject): VoiceConfig = VoiceConfig(
            sttEngine = json.optString("stt_engine", "android"),
            ttsEngine = json.optString("tts_engine", "android"),
            ttsVoice = json.optString("tts_voice", ""),
            ttsSpeed = json.optDouble("tts_speed", 1.0).toFloat(),
            ttsPitch = json.optDouble("tts_pitch", 1.0).toFloat(),
            wakeWordEnabled = json.optBoolean("wake_word_enabled", false),
            wakeWord = json.optString("wake_word", "hey guappa"),
            autoListenAfterResponse = json.optBoolean("auto_listen_after_response", true),
        )
    }
}

data class MemoryConfig(
    val backend: String = "markdown",
    val maxContextTokens: Int = 128_000,
    val compactionEnabled: Boolean = true,
    val compactionThreshold: Float = 0.8f,
    val persistSessions: Boolean = true,
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("backend", backend)
        put("max_context_tokens", maxContextTokens)
        put("compaction_enabled", compactionEnabled)
        put("compaction_threshold", compactionThreshold.toDouble())
        put("persist_sessions", persistSessions)
    }

    companion object {
        fun fromJSON(json: JSONObject): MemoryConfig = MemoryConfig(
            backend = json.optString("backend", "markdown"),
            maxContextTokens = json.optInt("max_context_tokens", 128_000),
            compactionEnabled = json.optBoolean("compaction_enabled", true),
            compactionThreshold = json.optDouble("compaction_threshold", 0.8).toFloat(),
            persistSessions = json.optBoolean("persist_sessions", true),
        )
    }
}

data class ProactiveConfig(
    val enabled: Boolean = false,
    val morningBriefingEnabled: Boolean = false,
    val morningBriefingTime: String = "08:00",
    val smartRemindersEnabled: Boolean = false,
    val contextualSuggestionsEnabled: Boolean = false,
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("morning_briefing_enabled", morningBriefingEnabled)
        put("morning_briefing_time", morningBriefingTime)
        put("smart_reminders_enabled", smartRemindersEnabled)
        put("contextual_suggestions_enabled", contextualSuggestionsEnabled)
    }

    companion object {
        fun fromJSON(json: JSONObject): ProactiveConfig = ProactiveConfig(
            enabled = json.optBoolean("enabled", false),
            morningBriefingEnabled = json.optBoolean("morning_briefing_enabled", false),
            morningBriefingTime = json.optString("morning_briefing_time", "08:00"),
            smartRemindersEnabled = json.optBoolean("smart_reminders_enabled", false),
            contextualSuggestionsEnabled = json.optBoolean("contextual_suggestions_enabled", false),
        )
    }
}

data class ChannelsConfig(
    val telegramEnabled: Boolean = false,
    val telegramBotToken: String = "",
    val discordEnabled: Boolean = false,
    val discordBotToken: String = "",
    val slackEnabled: Boolean = false,
    val slackBotToken: String = "",
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("telegram_enabled", telegramEnabled)
        // Tokens NOT exported for security
        put("discord_enabled", discordEnabled)
        put("slack_enabled", slackEnabled)
    }

    companion object {
        fun fromJSON(json: JSONObject): ChannelsConfig = ChannelsConfig(
            telegramEnabled = json.optBoolean("telegram_enabled", false),
            telegramBotToken = json.optString("telegram_bot_token", ""),
            discordEnabled = json.optBoolean("discord_enabled", false),
            discordBotToken = json.optString("discord_bot_token", ""),
            slackEnabled = json.optBoolean("slack_enabled", false),
            slackBotToken = json.optString("slack_bot_token", ""),
        )
    }
}

data class PersonaConfig(
    val name: String = "Guappa",
    val systemPrompt: String = "",
    val personality: String = "helpful",
    val verbosity: String = "normal",
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("name", name)
        put("system_prompt", systemPrompt)
        put("personality", personality)
        put("verbosity", verbosity)
    }

    companion object {
        fun fromJSON(json: JSONObject): PersonaConfig = PersonaConfig(
            name = json.optString("name", "Guappa"),
            systemPrompt = json.optString("system_prompt", ""),
            personality = json.optString("personality", "helpful"),
            verbosity = json.optString("verbosity", "normal"),
        )
    }
}

data class ToolsConfig(
    val enabledTools: Set<String> = emptySet(),
    val disabledTools: Set<String> = emptySet(),
    val webSearchApiKey: String = "",
    val maxConcurrentToolCalls: Int = 3,
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("enabled_tools", org.json.JSONArray(enabledTools.toList()))
        put("disabled_tools", org.json.JSONArray(disabledTools.toList()))
        // API key NOT exported for security
        put("max_concurrent_tool_calls", maxConcurrentToolCalls)
    }

    companion object {
        fun fromJSON(json: JSONObject): ToolsConfig {
            val enabled = mutableSetOf<String>()
            val disabled = mutableSetOf<String>()
            json.optJSONArray("enabled_tools")?.let { arr ->
                for (i in 0 until arr.length()) enabled.add(arr.getString(i))
            }
            json.optJSONArray("disabled_tools")?.let { arr ->
                for (i in 0 until arr.length()) disabled.add(arr.getString(i))
            }
            return ToolsConfig(
                enabledTools = enabled,
                disabledTools = disabled,
                webSearchApiKey = json.optString("web_search_api_key", ""),
                maxConcurrentToolCalls = json.optInt("max_concurrent_tool_calls", 3),
            )
        }
    }
}
