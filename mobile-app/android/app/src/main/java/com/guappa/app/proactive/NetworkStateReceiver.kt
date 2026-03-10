package com.guappa.app.proactive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.guappa.app.agent.MessageBus
import com.guappa.app.agent.BusMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives network connectivity changes and publishes to MessageBus.
 * Register in manifest with action: android.net.conn.CONNECTIVITY_CHANGE
 */
class NetworkStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NetworkStateReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        val network = cm.activeNetwork
        val caps = if (network != null) cm.getNetworkCapabilities(network) else null

        val isConnected = caps != null
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isMetered = cm.isActiveNetworkMetered

        val networkType = when {
            isWifi -> "wifi"
            isCellular -> "cellular"
            isConnected -> "other"
            else -> "disconnected"
        }

        Log.d(TAG, "Network state: $networkType, metered=$isMetered")

        val bus = com.guappa.app.GuappaAgentService.messageBus ?: return
        CoroutineScope(Dispatchers.Default).launch {
            bus.publish(BusMessage.SystemEvent(
                type = "network_state_changed",
                data = mapOf(
                    "connected" to isConnected,
                    "type" to networkType,
                    "metered" to isMetered
                )
            ))
        }
    }
}
