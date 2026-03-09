package com.guappa.app.tools

import org.json.JSONArray
import org.json.JSONObject

data class ToolExecution(
    val toolName: String,
    val params: String,
    val result: String,
    val timestamp: Long,
    val durationMs: Long,
    val sessionId: String
)

class ToolAuditLog(
    private val maxBufferSize: Int = 500
) {
    private val buffer = ArrayDeque<ToolExecution>(maxBufferSize)
    private val lock = Any()

    fun log(execution: ToolExecution) {
        synchronized(lock) {
            if (buffer.size >= maxBufferSize) {
                buffer.removeFirst()
            }
            buffer.addLast(execution)
        }
    }

    fun getRecent(limit: Int = 50): List<ToolExecution> {
        synchronized(lock) {
            if (buffer.isEmpty()) return emptyList()
            val count = limit.coerceIn(1, buffer.size)
            return buffer.toList().takeLast(count)
        }
    }

    fun getByTool(toolName: String): List<ToolExecution> {
        synchronized(lock) {
            return buffer.filter { it.toolName == toolName }
        }
    }

    fun getBySession(sessionId: String): List<ToolExecution> {
        synchronized(lock) {
            return buffer.filter { it.sessionId == sessionId }
        }
    }

    fun size(): Int {
        synchronized(lock) {
            return buffer.size
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
        }
    }

    fun toJson(): JSONObject {
        synchronized(lock) {
            val json = JSONObject()
            val entries = JSONArray()

            for (execution in buffer) {
                val entry = JSONObject()
                entry.put("tool_name", execution.toolName)
                entry.put("params", execution.params)
                entry.put("result", execution.result)
                entry.put("timestamp", execution.timestamp)
                entry.put("duration_ms", execution.durationMs)
                entry.put("session_id", execution.sessionId)
                entries.put(entry)
            }

            json.put("executions", entries)
            json.put("total_count", buffer.size)
            json.put("max_buffer_size", maxBufferSize)
            json.put("exported_at", System.currentTimeMillis())
            return json
        }
    }
}
