package com.guappa.app.proactive

import android.content.Context
import android.util.Log
import com.guappa.app.GuappaAgentService
import com.guappa.app.agent.BusMessage
import com.guappa.app.agent.MessageBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reacts to device events such as SMS, missed calls, and calendar reminders.
 *
 * All actions are routed through [ProactiveEngine] for timing and permission checks
 * before creating agent sessions or firing notifications.
 *
 * Event handlers:
 *   [onSmsReceived]        — suggest reply via agent session
 *   [onMissedCall]         — notify + offer callback after 2-minute delay
 *   [onCalendarReminder]   — remind user of upcoming event
 *
 * Usage:
 *   val reactor = EventReactor(context, messageBus, proactiveEngine)
 *   reactor.onSmsReceived(sender = "+1234567890", body = "Hey, are you free?")
 */
class EventReactor(
    private val context: Context,
    private val messageBus: MessageBus,
    private val proactiveEngine: ProactiveEngine
) {
    companion object {
        private const val TAG = "EventReactor"
        private const val MISSED_CALL_DELAY_MS = 2 * 60 * 1000L  // 2 minutes
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val notificationManager by lazy { GuappaNotificationManager(context) }
    private val deduplicator = NotificationDeduplicator()
    private val smartTiming by lazy { notificationManager.getSmartTiming() }

    /**
     * Called when an SMS is received. Creates an agent session to suggest a reply.
     *
     * @param sender The phone number or contact name of the SMS sender.
     * @param body The SMS message body.
     */
    fun onSmsReceived(sender: String, body: String) {
        val fingerprint = deduplicator.fingerprint("sms_received", "$sender|$body")
        if (deduplicator.checkAndRecord(fingerprint)) {
            Log.d(TAG, "Duplicate SMS event suppressed: sender=$sender")
            return
        }

        if (!shouldReact("sms_received")) return

        Log.d(TAG, "SMS received from $sender — creating agent session")

        scope.launch {
            try {
                messageBus.publish(
                    BusMessage.TriggerEvent(
                        trigger = "sms_received",
                        data = mapOf(
                            "event_type" to "sms_received",
                            "sender" to sender,
                            "body" to body,
                            "message" to "Incoming SMS from $sender: \"$body\". Suggest a reply for the user."
                        )
                    )
                )

                // Show notification if app is in background
                if (smartTiming.shouldDeliver(context, NotificationChannels.CHAT)) {
                    notificationManager.showChatNotification(
                        senderName = "GUAPPA",
                        message = "SMS from $sender. Tap to review and reply."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle SMS event", e)
            }
        }
    }

    /**
     * Called when a missed call is detected.
     * Waits [MISSED_CALL_DELAY_MS] (2 minutes) before notifying, in case the
     * caller rings back or the user returns the call on their own.
     *
     * @param number The phone number of the missed call.
     * @param timestamp When the call was missed (epoch ms).
     */
    fun onMissedCall(number: String, timestamp: Long) {
        val fingerprint = deduplicator.fingerprint("missed_call", "$number|$timestamp")
        if (deduplicator.checkAndRecord(fingerprint)) {
            Log.d(TAG, "Duplicate missed call event suppressed: number=$number")
            return
        }

        if (!shouldReact("missed_call")) return

        Log.d(TAG, "Missed call from $number — scheduling delayed notification")

        scope.launch {
            // Wait before notifying (user may call back naturally)
            delay(MISSED_CALL_DELAY_MS)

            try {
                messageBus.publish(
                    BusMessage.TriggerEvent(
                        trigger = "missed_call",
                        data = mapOf(
                            "event_type" to "missed_call",
                            "number" to number,
                            "timestamp" to timestamp,
                            "message" to "Missed call from $number. Offer to call back or send a message."
                        )
                    )
                )

                // Show alert notification for missed calls
                notificationManager.showAlertNotification(
                    title = "Missed Call",
                    message = "Missed call from $number. GUAPPA can help you respond.",
                    isFullScreen = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle missed call event", e)
            }
        }
    }

    /**
     * Called when a calendar event is approaching.
     *
     * @param eventTitle Title of the calendar event.
     * @param minutesBefore How many minutes until the event starts.
     */
    fun onCalendarReminder(eventTitle: String, minutesBefore: Int) {
        val fingerprint = deduplicator.fingerprint("calendar_reminder", "$eventTitle|$minutesBefore")
        if (deduplicator.checkAndRecord(fingerprint)) {
            Log.d(TAG, "Duplicate calendar reminder suppressed: $eventTitle")
            return
        }

        if (!shouldReact("calendar_reminder")) return

        Log.d(TAG, "Calendar reminder: '$eventTitle' in $minutesBefore minutes")

        scope.launch {
            try {
                val channelId = if (minutesBefore <= 0) {
                    NotificationChannels.ALERTS
                } else {
                    NotificationChannels.PROACTIVE
                }

                messageBus.publish(
                    BusMessage.TriggerEvent(
                        trigger = "calendar_reminder",
                        data = mapOf(
                            "event_type" to "calendar_reminder",
                            "event_title" to eventTitle,
                            "minutes_before" to minutesBefore,
                            "message" to "Calendar event '$eventTitle' starts in $minutesBefore minutes."
                        )
                    )
                )

                if (minutesBefore <= 0) {
                    notificationManager.showAlertNotification(
                        title = "Event Starting Now",
                        message = eventTitle,
                        isFullScreen = false
                    )
                } else {
                    notificationManager.showProactiveNotification(
                        title = "Upcoming: $eventTitle",
                        body = "Starts in $minutesBefore minutes."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle calendar reminder", e)
            }
        }
    }

    /**
     * Checks with ProactiveEngine whether a reaction should proceed.
     */
    private fun shouldReact(eventType: String): Boolean {
        val trigger = proactiveEngine.getEnabledTriggers().find { trigger ->
            trigger.type == TriggerType.EVENT_BASED &&
                trigger.config.optString("event_type", "") == eventType
        }

        if (trigger == null) {
            Log.d(TAG, "No enabled trigger for event: $eventType")
            return false
        }

        return true
    }

    /**
     * Release resources. Call when the reactor is no longer needed.
     */
    fun shutdown() {
        scope.cancel()
        Log.d(TAG, "EventReactor shut down")
    }
}
