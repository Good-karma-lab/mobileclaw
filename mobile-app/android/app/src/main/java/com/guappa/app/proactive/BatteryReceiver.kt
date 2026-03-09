package com.guappa.app.proactive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.guappa.app.GuappaAgentService
import com.guappa.app.agent.BusMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Receives ACTION_BATTERY_LOW and ACTION_BATTERY_OKAY broadcasts.
 *
 * When battery is low, fires a proactive trigger through the MessageBus
 * so the agent can alert the user or take energy-saving actions.
 *
 * Register dynamically via [register] or statically in AndroidManifest.
 */
class BatteryReceiver : BroadcastReceiver() {

    private val TAG = "BatteryReceiver"
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BATTERY_LOW -> {
                val level = getBatteryLevel(context)
                Log.d(TAG, "Battery low received — level: $level%")

                val bus = GuappaAgentService.messageBus ?: return

                scope.launch {
                    bus.publish(
                        BusMessage.TriggerEvent(
                            trigger = "battery_low",
                            data = mapOf(
                                "battery_level" to level,
                                "event_type" to "battery_low",
                                "message" to "Battery is low ($level%). Consider charging or reducing background activity."
                            )
                        )
                    )
                }

                // Show proactive notification
                val notifManager = GuappaNotificationManager(context)
                notifManager.showTriggerNotification(
                    triggerName = "Battery Low",
                    message = "Battery at $level%. GUAPPA can help conserve power."
                )
            }
            Intent.ACTION_BATTERY_OKAY -> {
                val level = getBatteryLevel(context)
                Log.d(TAG, "Battery okay received — level: $level%")

                val bus = GuappaAgentService.messageBus ?: return
                scope.launch {
                    bus.publish(
                        BusMessage.SystemEvent(
                            type = "battery_okay",
                            data = mapOf(
                                "battery_level" to level,
                                "event_type" to "battery_okay"
                            )
                        )
                    )
                }
            }
        }
    }

    private fun getBatteryLevel(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }

    companion object {
        /**
         * Dynamically register this receiver for battery events.
         * Returns the receiver instance so it can be unregistered later.
         */
        fun register(context: Context): BatteryReceiver {
            val receiver = BatteryReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
            }
            context.registerReceiver(receiver, filter)
            Log.d("BatteryReceiver", "Registered for battery events")
            return receiver
        }
    }
}
