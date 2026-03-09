package com.guappa.app.config

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Listens to GuappaConfigStore changes and dispatches to subsystem
 * hot-swap handlers. Changes are debounced (300ms) to avoid rapid-fire
 * updates when the user adjusts multiple settings quickly.
 */
class ConfigChangeDispatcher(
    private val configStore: GuappaConfigStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val TAG = "ConfigChangeDispatcher"
    private val handlers = mutableListOf<ConfigChangeHandler>()
    private var dispatchJob: Job? = null

    companion object {
        private const val DEBOUNCE_MS = 300L
    }

    fun registerHandler(handler: ConfigChangeHandler) {
        handlers.add(handler)
        Log.d(TAG, "Registered handler: ${handler.name}")
    }

    fun unregisterHandler(handler: ConfigChangeHandler) {
        handlers.remove(handler)
        Log.d(TAG, "Unregistered handler: ${handler.name}")
    }

    /**
     * Start observing all config sections and dispatching changes.
     * Each section is independently debounced.
     */
    fun start() {
        dispatchJob?.cancel()
        dispatchJob = scope.launch {
            // Provider changes
            launch {
                configStore.providerConfig
                    .debounce(DEBOUNCE_MS)
                    .distinctUntilChanged()
                    .drop(1) // Skip initial value
                    .collect { config ->
                        Log.d(TAG, "Provider config changed: ${config.providerId}")
                        dispatch(ConfigSection.PROVIDER, config)
                    }
            }

            // Voice changes
            launch {
                configStore.voiceConfig
                    .debounce(DEBOUNCE_MS)
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { config ->
                        Log.d(TAG, "Voice config changed")
                        dispatch(ConfigSection.VOICE, config)
                    }
            }

            // Memory changes
            launch {
                configStore.memoryConfig
                    .debounce(DEBOUNCE_MS)
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { config ->
                        Log.d(TAG, "Memory config changed")
                        dispatch(ConfigSection.MEMORY, config)
                    }
            }

            // Proactive changes
            launch {
                configStore.proactiveConfig
                    .debounce(DEBOUNCE_MS)
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { config ->
                        Log.d(TAG, "Proactive config changed")
                        dispatch(ConfigSection.PROACTIVE, config)
                    }
            }

            // Channel changes
            launch {
                configStore.channelsConfig
                    .debounce(DEBOUNCE_MS)
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { config ->
                        Log.d(TAG, "Channels config changed")
                        dispatch(ConfigSection.CHANNELS, config)
                    }
            }

            // Persona changes
            launch {
                configStore.personaConfig
                    .debounce(DEBOUNCE_MS)
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { config ->
                        Log.d(TAG, "Persona config changed")
                        dispatch(ConfigSection.PERSONA, config)
                    }
            }

            // Tools changes
            launch {
                configStore.toolsConfig
                    .debounce(DEBOUNCE_MS)
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { config ->
                        Log.d(TAG, "Tools config changed")
                        dispatch(ConfigSection.TOOLS, config)
                    }
            }
        }

        Log.d(TAG, "Config change dispatcher started with ${handlers.size} handlers")
    }

    fun stop() {
        dispatchJob?.cancel()
        dispatchJob = null
        Log.d(TAG, "Config change dispatcher stopped")
    }

    private suspend fun dispatch(section: ConfigSection, config: Any) {
        for (handler in handlers) {
            try {
                if (handler.handles(section)) {
                    handler.onConfigChanged(section, config)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Handler '${handler.name}' failed for section $section: ${e.message}")
            }
        }
    }
}

enum class ConfigSection {
    PROVIDER,
    VOICE,
    MEMORY,
    PROACTIVE,
    CHANNELS,
    PERSONA,
    TOOLS,
}

/**
 * Interface for subsystems that need to react to config changes.
 * Each handler declares which sections it handles, and receives
 * the typed config object when that section changes.
 */
interface ConfigChangeHandler {
    val name: String

    /** Which config sections this handler cares about. */
    fun handles(section: ConfigSection): Boolean

    /**
     * Called when a config section changes. The [config] parameter
     * is the typed config object for the [section].
     */
    suspend fun onConfigChanged(section: ConfigSection, config: Any)
}
