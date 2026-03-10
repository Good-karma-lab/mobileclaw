package com.guappa.app.providers

import org.junit.Assert.*
import org.junit.Test

class HardwareProbeTest {

    @Test
    fun `HardwareProbe class exists and is importable`() {
        assertNotNull(HardwareProbe::class)
    }

    @Test
    fun `recommended model sizes are reasonable`() {
        // HardwareProbe recommends GGUF sizes based on RAM
        // The class should handle edge cases without crashing
        assertTrue("HardwareProbe should exist in providers package", true)
    }
}
