package com.guappa.app.swarm

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for PeerInfo serialization.
 */
class PeerInfoTest {

    @Test
    fun `toJSON includes all fields`() {
        val peer = PeerInfo(
            peerId = "peer-1",
            displayName = "Test Agent",
            capabilities = listOf("text_generation", "vision"),
            address = "192.168.1.100:9370",
            lastSeen = 1000L,
            latencyMs = 42,
            isOnline = true,
        )

        val json = peer.toJSON()
        assertEquals("peer-1", json.getString("peer_id"))
        assertEquals("Test Agent", json.getString("display_name"))
        assertEquals(2, json.getJSONArray("capabilities").length())
        assertEquals("192.168.1.100:9370", json.getString("address"))
        assertEquals(1000L, json.getLong("last_seen"))
        assertEquals(42L, json.getLong("latency_ms"))
        assertTrue(json.getBoolean("is_online"))
    }

    @Test
    fun `fromJSON roundtrips correctly`() {
        val original = PeerInfo(
            peerId = "peer-2",
            displayName = "Agent B",
            capabilities = listOf("web_search"),
            address = "10.0.0.5:9370",
            isOnline = false,
        )

        val restored = PeerInfo.fromJSON(original.toJSON())
        assertEquals(original.peerId, restored.peerId)
        assertEquals(original.displayName, restored.displayName)
        assertEquals(original.capabilities, restored.capabilities)
        assertEquals(original.address, restored.address)
        assertEquals(original.isOnline, restored.isOnline)
    }

    @Test
    fun `fromJSON handles missing optional fields`() {
        val json = JSONObject().apply {
            put("peer_id", "peer-3")
        }

        val peer = PeerInfo.fromJSON(json)
        assertEquals("peer-3", peer.peerId)
        assertEquals("Unknown", peer.displayName)
        assertTrue(peer.capabilities.isEmpty())
        assertEquals("", peer.address)
        assertTrue(peer.isOnline) // default
    }
}
