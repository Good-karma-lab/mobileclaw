package com.guappa.app.proactive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.guappa.app.agent.MessageBus
import com.guappa.app.agent.BusMessage
import kotlinx.coroutines.launch

/**
 * GeofenceBroadcastReceiver — handles geofence transition events.
 *
 * Receives intents from the GeofenceTool's PendingIntents and
 * publishes enter/exit/dwell events to the MessageBus so the
 * ProactiveEngine can trigger location-based automations.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val transition = intent.getStringExtra(EXTRA_TRANSITION) ?: return
        val fenceId = intent.getStringExtra(EXTRA_FENCE_ID) ?: return
        val latitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
        val longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)

        Log.d(TAG, "Geofence $transition: fence=$fenceId lat=$latitude lon=$longitude")

        val bus = com.guappa.app.GuappaAgentService.messageBus ?: return
        kotlinx.coroutines.GlobalScope.launch {
            bus.publish(
                BusMessage.SystemEvent(
                    type = "geofence_transition",
                    data = mapOf(
                        "transition" to transition,
                        "fence_id" to fenceId,
                        "latitude" to latitude.toString(),
                        "longitude" to longitude.toString(),
                        "timestamp" to System.currentTimeMillis().toString()
                    )
                )
            )
        }
    }

    companion object {
        private const val TAG = "GeofenceReceiver"
        const val ACTION = "com.guappa.app.GEOFENCE_TRANSITION"
        const val EXTRA_TRANSITION = "transition"
        const val EXTRA_FENCE_ID = "fence_id"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
    }
}
