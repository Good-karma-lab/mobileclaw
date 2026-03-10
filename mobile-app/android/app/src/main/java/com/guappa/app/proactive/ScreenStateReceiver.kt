package com.guappa.app.proactive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.guappa.app.agent.BusMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives screen on/off/user-present events and publishes to MessageBus.
 * Must be registered dynamically (not in manifest) for ACTION_SCREEN_ON/OFF.
 */
class ScreenStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenStateReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        val action = intent?.action ?: return

        val screenState = when (action) {
            Intent.ACTION_SCREEN_ON -> "screen_on"
            Intent.ACTION_SCREEN_OFF -> "screen_off"
            Intent.ACTION_USER_PRESENT -> "user_unlocked"
            else -> return
        }

        Log.d(TAG, "Screen state: $screenState")

        val bus = com.guappa.app.GuappaAgentService.messageBus ?: return
        CoroutineScope(Dispatchers.Default).launch {
            bus.publish(BusMessage.SystemEvent(
                type = "screen_state_changed",
                data = mapOf("state" to screenState)
            ))
        }
    }
}
