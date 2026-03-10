package com.guappa.app.swarm

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SwarmConfig serialization and deserialization.
 */
class SwarmConfigTest {

    @Test
    fun `default config has expected values`() {
        val config = SwarmConfig()
        assertFalse(config.enabled)
        assertEquals(SwarmConnectionMode.REMOTE, config.connectionMode)
        assertEquals("http://10.0.2.2:9371", config.connectorUrl)
        assertEquals(9370, config.connectorPort)
        assertTrue(config.autoAcceptTasks)
        assertEquals(3, config.maxConcurrentTasks)
        assertFalse(config.autoConnect)
        assertTrue(config.capabilities.isNotEmpty())
    }

    @Test
    fun `toJSON roundtrips correctly`() {
        val original = SwarmConfig(
            enabled = true,
            connectionMode = SwarmConnectionMode.REMOTE,
            connectorUrl = "http://test:9371",
            connectorPort = 9370,
            activeTaskPollMs = 10000,
            autoAcceptTasks = false,
            maxConcurrentTasks = 5,
            agentName = "Test Agent",
            autoConnect = true,
            capabilities = setOf("text_generation", "vision"),
        )

        val json = original.toJSON()
        val restored = SwarmConfig.fromJSON(json)

        assertEquals(original.enabled, restored.enabled)
        assertEquals(original.connectionMode, restored.connectionMode)
        assertEquals(original.connectorUrl, restored.connectorUrl)
        assertEquals(original.connectorPort, restored.connectorPort)
        assertEquals(original.activeTaskPollMs, restored.activeTaskPollMs)
        assertEquals(original.autoAcceptTasks, restored.autoAcceptTasks)
        assertEquals(original.maxConcurrentTasks, restored.maxConcurrentTasks)
        assertEquals(original.agentName, restored.agentName)
        assertEquals(original.autoConnect, restored.autoConnect)
        assertEquals(original.capabilities, restored.capabilities)
    }

    @Test
    fun `fromJSON handles missing fields with defaults`() {
        val json = JSONObject().apply {
            put("enabled", true)
        }
        val config = SwarmConfig.fromJSON(json)
        assertTrue(config.enabled)
        assertEquals("http://10.0.2.2:9371", config.connectorUrl)
        assertEquals(SwarmConnectionMode.REMOTE, config.connectionMode)
    }

    @Test
    fun `fromJSON handles invalid connection mode gracefully`() {
        val json = JSONObject().apply {
            put("connection_mode", "invalid_mode")
        }
        val config = SwarmConfig.fromJSON(json)
        assertEquals(SwarmConnectionMode.REMOTE, config.connectionMode)
    }

    @Test
    fun `default capabilities include expected values`() {
        val caps = SwarmConfig.DEFAULT_CAPABILITIES
        assertTrue(caps.contains("text_generation"))
        assertTrue(caps.contains("tool_use"))
        assertTrue(caps.contains("vision"))
        assertTrue(caps.contains("web_search"))
    }
}
