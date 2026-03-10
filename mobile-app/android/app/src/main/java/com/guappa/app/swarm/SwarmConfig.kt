package com.guappa.app.swarm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Swarm configuration with persistence via SharedPreferences.
 *
 * Stores all settings for WWSP connectivity:
 * - Connector URL and connection mode
 * - Polling intervals (foreground, background)
 * - Task policy (auto-accept, max concurrent, allowed capabilities)
 * - Agent display info (name, avatar)
 * - Auto-connect behavior
 */
data class SwarmConfig(
    val enabled: Boolean = false,
    val connectionMode: SwarmConnectionMode = SwarmConnectionMode.REMOTE,
    val connectorUrl: String = "http://10.0.2.2:9371",
    val connectorPort: Int = 9370,

    // Polling intervals (milliseconds)
    val activeTaskPollMs: Long = 5_000,
    val activeStatusPollMs: Long = 10_000,
    val activeNetworkStatsPollMs: Long = 30_000,
    val backgroundPollMs: Long = 60_000,
    val idlePollMs: Long = 30_000,

    // Task policy
    val autoAcceptTasks: Boolean = true,
    val autoAcceptHolonInvites: Boolean = true,
    val maxConcurrentTasks: Int = 3,
    val capabilities: Set<String> = DEFAULT_CAPABILITIES,

    // Agent identity display
    val agentName: String = "Guappa Mobile Agent",
    val agentAvatar: String = "",

    // Auto-connect on app start
    val autoConnect: Boolean = false,
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("connection_mode", connectionMode.name.lowercase())
        put("connector_url", connectorUrl)
        put("connector_port", connectorPort)
        put("active_task_poll_ms", activeTaskPollMs)
        put("active_status_poll_ms", activeStatusPollMs)
        put("active_network_stats_poll_ms", activeNetworkStatsPollMs)
        put("background_poll_ms", backgroundPollMs)
        put("idle_poll_ms", idlePollMs)
        put("auto_accept_tasks", autoAcceptTasks)
        put("auto_accept_holon_invites", autoAcceptHolonInvites)
        put("max_concurrent_tasks", maxConcurrentTasks)
        put("capabilities", JSONArray(capabilities.toList()))
        put("agent_name", agentName)
        put("agent_avatar", agentAvatar)
        put("auto_connect", autoConnect)
    }

    companion object {
        val DEFAULT_CAPABILITIES = setOf(
            "text_generation",
            "tool_use",
            "vision",
            "app_control",
            "web_search",
            "code_execution",
        )

        fun fromJSON(json: JSONObject): SwarmConfig {
            val caps = mutableSetOf<String>()
            json.optJSONArray("capabilities")?.let { arr ->
                for (i in 0 until arr.length()) caps.add(arr.getString(i))
            }

            return SwarmConfig(
                enabled = json.optBoolean("enabled", false),
                connectionMode = try {
                    SwarmConnectionMode.valueOf(
                        json.optString("connection_mode", "remote").uppercase()
                    )
                } catch (_: Exception) {
                    SwarmConnectionMode.REMOTE
                },
                connectorUrl = json.optString("connector_url", "http://10.0.2.2:9371"),
                connectorPort = json.optInt("connector_port", 9370),
                activeTaskPollMs = json.optLong("active_task_poll_ms", 5_000),
                activeStatusPollMs = json.optLong("active_status_poll_ms", 10_000),
                activeNetworkStatsPollMs = json.optLong("active_network_stats_poll_ms", 30_000),
                backgroundPollMs = json.optLong("background_poll_ms", 60_000),
                idlePollMs = json.optLong("idle_poll_ms", 30_000),
                autoAcceptTasks = json.optBoolean("auto_accept_tasks", true),
                autoAcceptHolonInvites = json.optBoolean("auto_accept_holon_invites", true),
                maxConcurrentTasks = json.optInt("max_concurrent_tasks", 3),
                capabilities = if (caps.isNotEmpty()) caps else DEFAULT_CAPABILITIES,
                agentName = json.optString("agent_name", "Guappa Mobile Agent"),
                agentAvatar = json.optString("agent_avatar", ""),
                autoConnect = json.optBoolean("auto_connect", false),
            )
        }
    }
}

enum class SwarmConnectionMode {
    REMOTE,    // HTTP REST + WebSocket to external connector
    EMBEDDED,  // Local wws-connector binary (future)
}

/**
 * Persistent wrapper around SwarmConfig using SharedPreferences.
 * Provides a reactive StateFlow for observing config changes.
 */
class SwarmConfigStore(private val context: Context) {
    private val TAG = "SwarmConfigStore"
    private val PREFS_NAME = "guappa_swarm_config"

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _config = MutableStateFlow(load())
    val config: StateFlow<SwarmConfig> = _config.asStateFlow()

    val current: SwarmConfig
        get() = _config.value

    fun update(transform: SwarmConfig.() -> SwarmConfig) {
        val updated = _config.value.transform()
        _config.value = updated
        persist(updated)
    }

    fun reset() {
        val defaults = SwarmConfig()
        _config.value = defaults
        persist(defaults)
    }

    fun importFromJSON(json: String): Boolean {
        return try {
            val parsed = SwarmConfig.fromJSON(JSONObject(json))
            _config.value = parsed
            persist(parsed)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import swarm config: ${e.message}")
            false
        }
    }

    fun exportAsJSON(): String {
        return _config.value.toJSON().toString(2)
    }

    private fun load(): SwarmConfig {
        val raw = prefs.getString("swarm_config", null)
        return if (raw != null) {
            try {
                SwarmConfig.fromJSON(JSONObject(raw))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load swarm config, using defaults: ${e.message}")
                SwarmConfig()
            }
        } else {
            SwarmConfig()
        }
    }

    private fun persist(config: SwarmConfig) {
        try {
            prefs.edit()
                .putString("swarm_config", config.toJSON().toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist swarm config: ${e.message}")
        }
    }
}
