package com.guappa.app.config

import android.util.Log
import com.guappa.app.tools.ToolRegistry

/**
 * Enables/disables tools in real-time without restarting the agent.
 *
 * On tools config change:
 * 1. Reads enabled/disabled tool sets from config
 * 2. Updates the ToolRegistry's active set
 * 3. Next tool schema generation reflects the change immediately
 *
 * Tools are identified by their registered name (e.g. "web_search",
 * "set_alarm", "calculator"). The registry filters on
 * isAvailable() + isEnabled() for schema generation.
 */
class ToolHotSwap(
    private val getToolRegistry: () -> ToolRegistry?
) : ConfigChangeHandler {
    private val TAG = "ToolHotSwap"

    override val name: String = "ToolHotSwap"

    override fun handles(section: ConfigSection): Boolean =
        section == ConfigSection.TOOLS

    override suspend fun onConfigChanged(section: ConfigSection, config: Any) {
        val toolsConfig = config as? ToolsConfig ?: return
        applyToolConfig(toolsConfig)
    }

    /**
     * Apply the tools configuration to the registry.
     * Returns the list of currently enabled tool names.
     */
    fun applyToolConfig(config: ToolsConfig): List<String> {
        val registry = getToolRegistry() ?: run {
            Log.w(TAG, "No tool registry available")
            return emptyList()
        }

        val disabledSet = config.disabledTools
        val enabledSet = config.enabledTools

        // Apply enable/disable state to registry
        var enabledCount = 0
        var disabledCount = 0

        for (toolName in registry.getAllToolNames()) {
            when {
                toolName in disabledSet -> {
                    registry.setToolEnabled(toolName, false)
                    disabledCount++
                }
                enabledSet.isNotEmpty() && toolName !in enabledSet -> {
                    // If an explicit enabled set is provided, disable anything not in it
                    registry.setToolEnabled(toolName, false)
                    disabledCount++
                }
                else -> {
                    registry.setToolEnabled(toolName, true)
                    enabledCount++
                }
            }
        }

        Log.d(TAG, "Tool config applied: $enabledCount enabled, $disabledCount disabled")
        return registry.getEnabledToolNames()
    }
}
