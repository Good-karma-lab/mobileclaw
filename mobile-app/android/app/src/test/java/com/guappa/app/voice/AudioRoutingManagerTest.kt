package com.guappa.app.voice

import org.junit.Assert.*
import org.junit.Test

class AudioRoutingManagerTest {

    @Test
    fun `audio route enum has all expected values`() {
        val routes = AudioRoutingManager.AudioRoute.values()
        assertEquals(5, routes.size)
        assertTrue(routes.any { it.name == "SPEAKER" })
        assertTrue(routes.any { it.name == "EARPIECE" })
        assertTrue(routes.any { it.name == "BLUETOOTH_SCO" })
        assertTrue(routes.any { it.name == "WIRED_HEADSET" })
        assertTrue(routes.any { it.name == "AUTO" })
    }
}
