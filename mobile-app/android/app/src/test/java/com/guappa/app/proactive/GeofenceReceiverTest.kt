package com.guappa.app.proactive

import org.junit.Assert.*
import org.junit.Test

class GeofenceReceiverTest {

    @Test
    fun `constants are defined correctly`() {
        assertEquals("com.guappa.app.GEOFENCE_TRANSITION", GeofenceBroadcastReceiver.ACTION)
        assertEquals("transition", GeofenceBroadcastReceiver.EXTRA_TRANSITION)
        assertEquals("fence_id", GeofenceBroadcastReceiver.EXTRA_FENCE_ID)
        assertEquals("latitude", GeofenceBroadcastReceiver.EXTRA_LATITUDE)
        assertEquals("longitude", GeofenceBroadcastReceiver.EXTRA_LONGITUDE)
    }
}
