package com.guappa.app.swarm

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for reputation tier computation and event serialization.
 */
class SwarmReputationTrackerTest {

    @Test
    fun `tier thresholds are correctly ordered`() {
        assertTrue(SwarmReputationTracker.TRUSTED_THRESHOLD < SwarmReputationTracker.VETERAN_THRESHOLD)
        assertTrue(SwarmReputationTracker.VETERAN_THRESHOLD < SwarmReputationTracker.ELITE_THRESHOLD)
    }

    @Test
    fun `reputation event serializes correctly`() {
        val event = ReputationEvent(
            type = ReputationEventType.TASK_COMPLETED,
            points = 5,
            description = "Completed task t1",
            taskId = "t1",
            timestamp = 12345L,
        )

        val json = event.toJSON()
        assertEquals("TASK_COMPLETED", json.getString("type"))
        assertEquals(5, json.getInt("points"))
        assertEquals("Completed task t1", json.getString("description"))
        assertEquals("t1", json.getString("task_id"))
        assertEquals(12345L, json.getLong("timestamp"))
    }

    @Test
    fun `reputation event roundtrips correctly`() {
        val original = ReputationEvent(
            type = ReputationEventType.HOLON_PARTICIPATION,
            points = 3,
            description = "Participated in holon h1",
        )

        val restored = ReputationEvent.fromJSON(original.toJSON())
        assertEquals(original.type, restored.type)
        assertEquals(original.points, restored.points)
        assertEquals(original.description, restored.description)
    }

    @Test
    fun `reputation event handles invalid type gracefully`() {
        val json = JSONObject().apply {
            put("type", "NONEXISTENT_TYPE")
            put("points", 0)
            put("description", "test")
        }
        val event = ReputationEvent.fromJSON(json)
        assertEquals(ReputationEventType.TASK_COMPLETED, event.type) // fallback
    }

    @Test
    fun `reputation tier display names are correct`() {
        assertEquals("New", ReputationTier.NEW.displayName)
        assertEquals("Trusted", ReputationTier.TRUSTED.displayName)
        assertEquals("Veteran", ReputationTier.VETERAN.displayName)
        assertEquals("Elite", ReputationTier.ELITE.displayName)
    }

    @Test
    fun `completion points favor faster tasks`() {
        assertTrue(
            SwarmReputationTracker.TASK_COMPLETION_FAST_POINTS >
                SwarmReputationTracker.TASK_COMPLETION_NORMAL_POINTS
        )
        assertTrue(
            SwarmReputationTracker.TASK_COMPLETION_NORMAL_POINTS >
                SwarmReputationTracker.TASK_COMPLETION_SLOW_POINTS
        )
    }
}
