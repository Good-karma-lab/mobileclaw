package com.guappa.app.tools

import android.content.Context
import com.guappa.app.tools.impl.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class ToolRegistry {
    private val tools = ConcurrentHashMap<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): Tool? = tools[name]

    fun getAvailableTools(context: Context): List<Tool> {
        return tools.values.filter { it.isAvailable(context) }
    }

    fun getToolSchemas(context: Context): List<JSONObject> {
        return getAvailableTools(context).map { tool ->
            val schema = JSONObject()
            schema.put("type", "function")
            val function = JSONObject()
            function.put("name", tool.name)
            function.put("description", tool.description)
            function.put("parameters", tool.parametersSchema)
            schema.put("function", function)
            schema
        }
    }

    fun registerCoreTools() {
        register(SetAlarmTool())
        register(SendSmsTool())
        register(ReadSmsTool())
        register(PlaceCallTool())
        register(GetBatteryTool())
        register(GetContactsTool())
        register(LaunchAppTool())
        register(OpenBrowserTool())
        register(SetTimerTool())
        register(ShareTextTool())
        register(WebFetchTool())
        register(WebSearchTool())
        register(CalculatorTool())
        register(TranslationTool())
        register(ImageAnalyzeTool())
    }
}
