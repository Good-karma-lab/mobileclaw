package com.guappa.app.swarm

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SwarmTask and SwarmTaskResult serialization.
 */
class SwarmTaskTest {

    @Test
    fun `task toJSON includes all fields`() {
        val task = SwarmTask(
            id = "task-1",
            description = "Summarize document",
            complexity = 0.7,
            status = TaskStatus.IN_PROGRESS,
            requiredCapabilities = listOf("text_generation"),
            deadline = 2000L,
            holonId = "holon-1",
        )

        val json = task.toJSON()
        assertEquals("task-1", json.getString("task_id"))
        assertEquals("Summarize document", json.getString("description"))
        assertEquals(0.7, json.getDouble("complexity"), 0.001)
        assertEquals("in_progress", json.getString("status"))
        assertEquals(1, json.getJSONArray("required_capabilities").length())
        assertEquals(2000L, json.getLong("deadline"))
        assertEquals("holon-1", json.getString("holon_id"))
    }

    @Test
    fun `task fromJSON roundtrips correctly`() {
        val original = SwarmTask(
            id = "task-2",
            description = "Search the web",
            complexity = 0.3,
            requiredCapabilities = listOf("web_search", "text_generation"),
        )

        val restored = SwarmTask.fromJSON(original.toJSON())
        assertEquals(original.id, restored.id)
        assertEquals(original.description, restored.description)
        assertEquals(original.complexity, restored.complexity, 0.001)
        assertEquals(original.requiredCapabilities, restored.requiredCapabilities)
        assertEquals(TaskStatus.PENDING, restored.status) // default
    }

    @Test
    fun `task fromJSON handles both id formats`() {
        val json1 = JSONObject().apply {
            put("task_id", "t1")
            put("description", "test")
        }
        assertEquals("t1", SwarmTask.fromJSON(json1).id)

        val json2 = JSONObject().apply {
            put("id", "t2")
            put("description", "test")
        }
        assertEquals("t2", SwarmTask.fromJSON(json2).id)
    }

    @Test
    fun `task fromJSON handles invalid status gracefully`() {
        val json = JSONObject().apply {
            put("task_id", "t3")
            put("description", "test")
            put("status", "nonexistent_status")
        }
        val task = SwarmTask.fromJSON(json)
        assertEquals(TaskStatus.PENDING, task.status)
    }

    @Test
    fun `task result serializes correctly`() {
        val result = SwarmTaskResult(
            taskId = "task-1",
            status = TaskStatus.COMPLETED,
            content = "Result text",
            durationMs = 5000,
        )

        val json = result.toJSON()
        assertEquals("task-1", json.getString("task_id"))
        assertEquals("completed", json.getString("status"))
        assertEquals("Result text", json.getString("result"))
        assertEquals(5000L, json.getLong("duration_ms"))
        assertFalse(json.has("error"))
    }

    @Test
    fun `task result with error includes error field`() {
        val result = SwarmTaskResult(
            taskId = "task-2",
            status = TaskStatus.FAILED,
            content = "",
            error = "Timeout exceeded",
        )

        val json = result.toJSON()
        assertEquals("failed", json.getString("status"))
        assertEquals("Timeout exceeded", json.getString("error"))
    }
}
