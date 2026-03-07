package com.guappa.app.tools

import android.content.Context
import com.guappa.app.providers.ToolCall
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class ToolEngine(
    private val registry: ToolRegistry,
    private val defaultTimeoutMs: Long = 30_000L
) {
    suspend fun executeTool(name: String, paramsJson: String, context: Context): ToolResult {
        val tool = registry.getTool(name)
            ?: return ToolResult.Error(
                message = "Unknown tool: $name",
                code = "TOOL_NOT_FOUND"
            )

        if (!tool.isAvailable(context)) {
            return ToolResult.Error(
                message = "Tool '$name' is not available. Missing required permissions: ${tool.requiredPermissions}",
                code = "PERMISSION_DENIED"
            )
        }

        val params = try {
            JSONObject(paramsJson)
        } catch (e: Exception) {
            return ToolResult.Error(
                message = "Invalid JSON parameters for tool '$name': ${e.message}",
                code = "INVALID_PARAMS"
            )
        }

        return try {
            withTimeout(defaultTimeoutMs) {
                tool.execute(params, context)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            ToolResult.Error(
                message = "Tool '$name' timed out after ${defaultTimeoutMs}ms",
                code = "TIMEOUT",
                retryable = true
            )
        } catch (e: Exception) {
            ToolResult.Error(
                message = "Tool '$name' execution failed: ${e.message}",
                code = "EXECUTION_ERROR"
            )
        }
    }

    suspend fun executeToolCalls(
        toolCalls: List<ToolCall>,
        context: Context
    ): List<Pair<String, ToolResult>> {
        return toolCalls.map { call ->
            val result = executeTool(call.function.name, call.function.arguments, context)
            Pair(call.id, result)
        }
    }
}
