package com.guappa.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.guappa.app.agent.BusMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IncomingCallReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE).orEmpty()
        if (state != TelephonyManager.EXTRA_STATE_RINGING) return

        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "unknown"

        // Emit to legacy bridge (for RN event)
        AndroidAgentToolsModule.emitIncomingCall(context, "ringing", number)

        // Publish to orchestrator message bus so the agent can act on it
        val bus = GuappaAgentService.messageBus
        if (bus != null) {
            scope.launch {
                bus.publish(BusMessage.TriggerEvent(
                    trigger = "incoming_call",
                    data = mapOf(
                        "phone_number" to number,
                        "state" to "ringing"
                    )
                ))
            }
        }
    }
}
