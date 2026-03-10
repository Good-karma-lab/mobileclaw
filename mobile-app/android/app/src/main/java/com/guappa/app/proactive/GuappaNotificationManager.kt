package com.guappa.app.proactive

import com.guappa.app.R

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput

/**
 * Enhanced notification manager for GUAPPA.
 *
 * Notification styles:
 *   - MessagingStyle for chat messages (conversation-like)
 *   - BigTextStyle for task updates (expandable details)
 *   - RemoteInput for inline reply on chat/question notifications
 *   - Full-screen intent for urgent alerts
 *
 * Works with [NotificationChannels] for channel routing and
 * [SmartTiming] for delivery control.
 */
class GuappaNotificationManager(private val context: Context) {

    companion object {
        private const val TAG = "GuappaNotifManager"
        const val REPLY_ACTION_KEY = "guappa_inline_reply"
        const val EXTRA_SESSION_ID = "guappa_session_id"
        const val EXTRA_NOTIFICATION_ID = "guappa_notification_id"

        // Notification ID ranges per channel to avoid collisions
        private var chatNotifId = 2000
        private var taskNotifId = 3000
        private var questionNotifId = 4000
        private var alertNotifId = 5000
        private var proactiveNotifId = 6000
        private var generalNotifId = 7000
    }

    private val smartTiming = SmartTiming()
    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // -----------------------------------------------------------------------
    // Chat Messages — MessagingStyle with inline reply
    // -----------------------------------------------------------------------

    /**
     * Show a chat message notification using MessagingStyle.
     * Supports inline reply via RemoteInput.
     */
    fun showChatNotification(
        senderName: String = "GUAPPA",
        message: String,
        sessionId: String = "",
        conversationHistory: List<Pair<String, String>>? = null
    ) {
        if (!smartTiming.shouldDeliver(context, NotificationChannels.CHAT)) return

        val notifId = chatNotifId++
        val guappaPerson = Person.Builder()
            .setName(senderName)
            .setKey("guappa_agent")
            .build()

        val userPerson = Person.Builder()
            .setName("You")
            .setKey("user")
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(userPerson)
            .setConversationTitle("GUAPPA Chat")

        // Add conversation history if available
        conversationHistory?.forEach { (role, text) ->
            val person = if (role == "user") userPerson else guappaPerson
            messagingStyle.addMessage(text, System.currentTimeMillis(), person)
        }

        // Add the current message
        messagingStyle.addMessage(message, System.currentTimeMillis(), guappaPerson)

        val replyPendingIntent = createReplyPendingIntent(notifId, sessionId)
        val remoteInput = RemoteInput.Builder(REPLY_ACTION_KEY)
            .setLabel("Reply to GUAPPA")
            .build()

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notif_message,
            "Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        val contentIntent = createContentPendingIntent(notifId)

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHAT)
            .setSmallIcon(R.drawable.ic_notif_info)
            .setStyle(messagingStyle)
            .setContentIntent(contentIntent)
            .addAction(replyAction)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        nm.notify(notifId, notification)
        smartTiming.recordDelivery(NotificationChannels.CHAT)
        Log.d(TAG, "Chat notification shown: id=$notifId")
    }

    // -----------------------------------------------------------------------
    // Task Updates — BigTextStyle
    // -----------------------------------------------------------------------

    /**
     * Show a task update notification with expandable BigTextStyle.
     */
    fun showTaskNotification(
        taskName: String,
        status: String,
        details: String = ""
    ) {
        if (!smartTiming.shouldDeliver(context, NotificationChannels.TASKS)) return

        val notifId = taskNotifId++
        val contentIntent = createContentPendingIntent(notifId)

        val bigText = if (details.isNotEmpty()) {
            "$status\n\n$details"
        } else {
            status
        }

        val notification = NotificationCompat.Builder(context, NotificationChannels.TASKS)
            .setSmallIcon(R.drawable.ic_notif_task)
            .setContentTitle("Task: $taskName")
            .setContentText(status)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        nm.notify(notifId, notification)
        smartTiming.recordDelivery(NotificationChannels.TASKS)
        Log.d(TAG, "Task notification shown: id=$notifId, task=$taskName")
    }

    // -----------------------------------------------------------------------
    // Questions — MessagingStyle + inline reply
    // -----------------------------------------------------------------------

    /**
     * Show a question notification with inline reply capability.
     */
    fun showQuestionNotification(
        question: String,
        sessionId: String = ""
    ) {
        if (!smartTiming.shouldDeliver(context, NotificationChannels.QUESTIONS)) return

        val notifId = questionNotifId++
        val guappaPerson = Person.Builder()
            .setName("GUAPPA")
            .setKey("guappa_agent")
            .build()

        val userPerson = Person.Builder()
            .setName("You")
            .setKey("user")
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(userPerson)
            .setConversationTitle("GUAPPA needs your input")
            .addMessage(question, System.currentTimeMillis(), guappaPerson)

        val replyPendingIntent = createReplyPendingIntent(notifId, sessionId)
        val remoteInput = RemoteInput.Builder(REPLY_ACTION_KEY)
            .setLabel("Your answer")
            .build()

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notif_message,
            "Answer",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        val contentIntent = createContentPendingIntent(notifId)

        val notification = NotificationCompat.Builder(context, NotificationChannels.QUESTIONS)
            .setSmallIcon(R.drawable.ic_notif_action)
            .setStyle(messagingStyle)
            .setContentIntent(contentIntent)
            .addAction(replyAction)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        nm.notify(notifId, notification)
        smartTiming.recordDelivery(NotificationChannels.QUESTIONS)
        Log.d(TAG, "Question notification shown: id=$notifId")
    }

    // -----------------------------------------------------------------------
    // Alerts — Full-screen intent for urgent alerts
    // -----------------------------------------------------------------------

    /**
     * Show an urgent alert notification.
     * Uses full-screen intent on supported devices/permissions.
     */
    fun showAlertNotification(
        title: String,
        message: String,
        isFullScreen: Boolean = true
    ) {
        // Alerts bypass SmartTiming (always deliver)
        val notifId = alertNotifId++
        val contentIntent = createContentPendingIntent(notifId)

        val builder = NotificationCompat.Builder(context, NotificationChannels.ALERTS)
            .setSmallIcon(R.drawable.ic_notif_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 200, 500))

        if (isFullScreen) {
            val fullScreenIntent = createFullScreenPendingIntent(notifId)
            builder.setFullScreenIntent(fullScreenIntent, true)
        }

        nm.notify(notifId, builder.build())
        smartTiming.recordDelivery(NotificationChannels.ALERTS)
        Log.d(TAG, "Alert notification shown: id=$notifId, fullScreen=$isFullScreen")
    }

    // -----------------------------------------------------------------------
    // Proactive Insights
    // -----------------------------------------------------------------------

    /**
     * Show a proactive insight notification (briefings, suggestions).
     */
    fun showProactiveNotification(
        title: String,
        body: String
    ) {
        if (!smartTiming.shouldDeliver(context, NotificationChannels.PROACTIVE)) return

        val notifId = proactiveNotifId++
        val contentIntent = createContentPendingIntent(notifId)

        val notification = NotificationCompat.Builder(context, NotificationChannels.PROACTIVE)
            .setSmallIcon(R.drawable.ic_notif_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()

        nm.notify(notifId, notification)
        smartTiming.recordDelivery(NotificationChannels.PROACTIVE)
        Log.d(TAG, "Proactive notification shown: id=$notifId")
    }

    // -----------------------------------------------------------------------
    // Legacy API — kept for backward compatibility
    // -----------------------------------------------------------------------

    fun showAgentNotification(title: String, body: String, actionPrompt: String? = null) {
        showChatNotification(message = body)
    }

    fun showToolResultNotification(toolName: String, result: String) {
        showTaskNotification(taskName = toolName, status = result)
    }

    fun showTriggerNotification(triggerName: String, message: String) {
        showProactiveNotification(title = "Trigger: $triggerName", body = message)
    }

    // -----------------------------------------------------------------------
    // Service notification (for foreground service)
    // -----------------------------------------------------------------------

    /**
     * Build the persistent foreground service notification.
     * Uses the SERVICE channel (MIN importance, no badge).
     */
    fun buildServiceNotification(title: String, text: String): android.app.Notification {
        val contentIntent = createContentPendingIntent(0)

        return NotificationCompat.Builder(context, NotificationChannels.SERVICE)
            .setSmallIcon(R.drawable.ic_notif_info)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    fun getSmartTiming(): SmartTiming = smartTiming

    fun cancelNotification(notificationId: Int) {
        nm.cancel(notificationId)
    }

    fun cancelAll() {
        nm.cancelAll()
    }

    private fun createContentPendingIntent(notifId: Int): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createReplyPendingIntent(notifId: Int, sessionId: String): PendingIntent {
        // Intent targets the main activity which will extract RemoteInput
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        return PendingIntent.getActivity(
            context, notifId + 10000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private fun createFullScreenPendingIntent(notifId: Int): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("guappa_alert", true)
        }
        return PendingIntent.getActivity(
            context, notifId + 20000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
