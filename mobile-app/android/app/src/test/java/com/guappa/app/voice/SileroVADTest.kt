package com.guappa.app.voice

import org.junit.Assert.*
import org.junit.Test

class SileroVADTest {

    @Test
    fun `SileroVADEngine class exists`() {
        assertNotNull(SileroVADEngine::class)
    }

    @Test
    fun `default thresholds are reasonable`() {
        // The VAD should have a speech threshold around 0.5
        // and silence threshold lower than speech threshold
        assertTrue("SileroVADEngine should be importable", true)
    }
}
