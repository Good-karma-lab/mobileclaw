package com.guappa.app.swarm

import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a task received from the World Wide Swarm.
 */
data class SwarmTask(
    val id: String,
    val description: String,
    val complexity: Double = 0.5,
    val status: TaskStatus = TaskStatus.PENDING,
    val requiredCapabilities: List<String> = emptyList(),
    val deadline: Long? = null,
    val assignedAt: Long = System.currentTimeMillis(),
    val parentTaskId: String? = null,
    val holonId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("task_id", id)
        put("description", description)
        put("complexity", complexity)
        put("status", status.name.lowercase())
        put("required_capabilities", JSONArray(requiredCapabilities))
        deadline?.let { put("deadline", it) }
        put("assigned_at", assignedAt)
        parentTaskId?.let { put("parent_task_id", it) }
        holonId?.let { put("holon_id", it) }
        if (metadata.isNotEmpty()) {
            put("metadata", JSONObject(metadata))
        }
    }

    companion object {
        fun fromJSON(json: JSONObject): SwarmTask {
            val caps = mutableListOf<String>()
            json.optJSONArray("required_capabilities")?.let { arr ->
                for (i in 0 until arr.length()) caps.add(arr.getString(i))
            }

            val meta = mutableMapOf<String, String>()
            json.optJSONObject("metadata")?.let { obj ->
                for (key in obj.keys()) {
                    meta[key] = obj.optString(key, "")
                }
            }

            return SwarmTask(
                id = json.optString("task_id", json.optString("id", "")),
                description = json.optString("description", ""),
                complexity = json.optDouble("complexity", 0.5),
                status = try {
                    TaskStatus.valueOf(json.optString("status", "pending").uppercase())
                } catch (_: Exception) {
                    TaskStatus.PENDING
                },
                requiredCapabilities = caps,
                deadline = if (json.has("deadline")) json.getLong("deadline") else null,
                assignedAt = json.optLong("assigned_at", System.currentTimeMillis()),
                parentTaskId = json.optString("parent_task_id", null),
                holonId = json.optString("holon_id", null),
                metadata = meta,
            )
        }
    }
}

enum class TaskStatus {
    PENDING,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    TIMEOUT,
    CANCELLED,
}

/**
 * Result of executing a swarm task.
 */
data class SwarmTaskResult(
    val taskId: String,
    val status: TaskStatus,
    val content: String,
    val durationMs: Long = 0,
    val error: String? = null,
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("task_id", taskId)
        put("status", status.name.lowercase())
        put("result", content)
        put("duration_ms", durationMs)
        error?.let { put("error", it) }
    }
}
