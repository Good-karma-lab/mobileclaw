package com.guappa.app.proactive

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.guappa.app.GuappaAgentService
import com.guappa.app.agent.BusMessage
import com.guappa.app.agent.MessagePriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that handles notification button clicks and inline replies.
 *
 * Actions:
 *   ACTION_REPLY   — extract RemoteInput text, forward to MessageBus as a UserMessage
 *   ACTION_APPROVE — approve a tool-use request from the agent
 *   ACTION_DENY    — deny a tool-use request from the agent
 *   ACTION_SNOOZE  — snooze the notification for N minutes (default 15)
 *
 * Register in AndroidManifest as:
 *   <receiver android:name=".proactive.NotificationActionReceiver"
 *       android:exported="false" />
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotifActionReceiver"

        const val ACTION_REPLY = "com.guappa.app.ACTION_REPLY"
        const val ACTION_APPROVE = "com.guappa.app.ACTION_APPROVE"
        const val ACTION_DENY = "com.guappa.app.ACTION_DENY"
        const val ACTION_SNOOZE = "com.guappa.app.ACTION_SNOOZE"

        const val EXTRA_SESSION_ID = "guappa_session_id"
        const val EXTRA_NOTIFICATION_ID = "guappa_notification_id"
        const val EXTRA_TOOL_CALL_ID = "guappa_tool_call_id"
        const val EXTRA_SNOOZE_MINUTES = "guappa_snooze_minutes"

        private const val DEFAULT_SNOOZE_MINUTES = 15
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""

        when (intent.action) {
            ACTION_REPLY -> handleReply(context, intent, notificationId, sessionId)
            ACTION_APPROVE -> handleApproval(context, intent, notificationId, sessionId, approved = true)
            ACTION_DENY -> handleApproval(context, intent, notificationId, sessionId, approved = false)
            ACTION_SNOOZE -> handleSnooze(context, intent, notificationId)
            else -> Log.w(TAG, "Unknown action: ${intent.action}")
        }
    }

    private fun handleReply(
        context: Context,
        intent: Intent,
        notificationId: Int,
        sessionId: String
    ) {
        val remoteInputBundle = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInputBundle
            ?.getCharSequence(GuappaNotificationManager.REPLY_ACTION_KEY)
            ?.toString()

        if (replyText.isNullOrBlank()) {
            Log.w(TAG, "Reply action received but no text extracted")
            return
        }

        Log.d(TAG, "Inline reply received: notifId=$notificationId, session=$sessionId")

        val bus = GuappaAgentService.messageBus
        if (bus == null) {
            Log.w(TAG, "MessageBus not available; cannot forward reply")
            return
        }

        scope.launch {
            bus.publish(
                BusMessage.UserMessage(
                    text = replyText,
                    sessionId = sessionId
                )
            )
        }

        // Update the notification to show confirmation
        updateNotificationStatus(context, notificationId, "Sent")
    }

    private fun handleApproval(
        context: Context,
        intent: Intent,
        notificationId: Int,
        sessionId: String,
        approved: Boolean
    ) {
        val toolCallId = intent.getStringExtra(EXTRA_TOOL_CALL_ID) ?: ""
        val action = if (approved) "approved" else "denied"
        Log.d(TAG, "Tool $action: toolCallId=$toolCallId, session=$sessionId")

        val bus = GuappaAgentService.messageBus
        if (bus == null) {
            Log.w(TAG, "MessageBus not available; cannot forward $action")
            return
        }

        scope.launch {
            bus.publish(
                BusMessage.SystemEvent(
                    type = "tool_approval",
                    data = mapOf(
                        "tool_call_id" to toolCallId,
                        "session_id" to sessionId,
                        "approved" to approved
                    ),
                    priority = MessagePriority.URGENT
                )
            )
        }

        val statusText = if (approved) "Approved" else "Denied"
        updateNotificationStatus(context, notificationId, statusText)
    }

    private fun handleSnooze(
        context: Context,
        intent: Intent,
        notificationId: Int
    ) {
        val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES)
        Log.d(TAG, "Snooze: notifId=$notificationId, minutes=$snoozeMinutes")

        // Dismiss the current notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationId)

        // Schedule a re-notification after the snooze period
        val bus = GuappaAgentService.messageBus ?: return

        scope.launch {
            bus.publish(
                BusMessage.SystemEvent(
                    type = "notification_snoozed",
                    data = mapOf(
                        "notification_id" to notificationId,
                        "snooze_minutes" to snoozeMinutes
                    )
                )
            )
        }
    }

    /**
     * Updates the notification to show a status message (e.g., "Sent", "Approved").
     * Replaces the notification content with a simple confirmation.
     */
    private fun updateNotificationStatus(
        context: Context,
        notificationId: Int,
        status: String
    ) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, NotificationChannels.CHAT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("GUAPPA")
                .setContentText(status)
                .setAutoCancel(true)
                .build()
            nm.notify(notificationId, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notification status: ${e.message}")
        }
    }
}
