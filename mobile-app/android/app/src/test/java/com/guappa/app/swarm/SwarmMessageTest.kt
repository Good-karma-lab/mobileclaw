package com.guappa.app.swarm

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SwarmMessage serialization.
 */
class SwarmMessageTest {

    @Test
    fun `toJSON includes all fields`() {
        val msg = SwarmMessage(
            id = "msg-1",
            type = SwarmMessageType.TASK_REQUEST,
            fromPeerId = "peer-a",
            toPeerId = "peer-b",
            payload = JSONObject().apply { put("task", "test task") },
            timestamp = 1000L,
            signature = "sig123",
        )

        val json = msg.toJSON()
        assertEquals("msg-1", json.getString("id"))
        assertEquals("TASK_REQUEST", json.getString("type"))
        assertEquals("peer-a", json.getString("from_peer_id"))
        assertEquals("peer-b", json.getString("to_peer_id"))
        assertEquals("test task", json.getJSONObject("payload").getString("task"))
        assertEquals(1000L, json.getLong("timestamp"))
        assertEquals("sig123", json.getString("signature"))
    }

    @Test
    fun `toJSON omits optional fields when null`() {
        val msg = SwarmMessage(
            type = SwarmMessageType.BROADCAST,
            fromPeerId = "peer-a",
        )

        val json = msg.toJSON()
        assertFalse(json.has("to_peer_id"))
        assertFalse(json.has("signature"))
    }

    @Test
    fun `fromJSON roundtrips correctly`() {
        val original = SwarmMessage(
            id = "msg-2",
            type = SwarmMessageType.HOLON_INVITE,
            fromPeerId = "peer-x",
            toPeerId = "peer-y",
            payload = JSONObject().apply { put("holon_id", "h1") },
        )

        val restored = SwarmMessage.fromJSON(original.toJSON())
        assertEquals(original.id, restored.id)
        assertEquals(original.type, restored.type)
        assertEquals(original.fromPeerId, restored.fromPeerId)
        assertEquals(original.toPeerId, restored.toPeerId)
        assertEquals("h1", restored.payload.getString("holon_id"))
    }

    @Test
    fun `fromJSON handles missing optional fields`() {
        val json = JSONObject().apply {
            put("id", "msg-3")
            put("type", "HEARTBEAT")
            put("from_peer_id", "peer-z")
        }

        val msg = SwarmMessage.fromJSON(json)
        assertEquals("msg-3", msg.id)
        assertEquals(SwarmMessageType.HEARTBEAT, msg.type)
        assertNull(msg.toPeerId)
        assertNull(msg.signature)
    }
}
