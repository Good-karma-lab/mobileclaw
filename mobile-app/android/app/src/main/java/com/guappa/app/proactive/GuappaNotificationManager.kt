package com.guappa.app.proactive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class GuappaNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_AGENT = "agent_messages"
        const val CHANNEL_TOOLS = "tool_results"
        const val CHANNEL_TRIGGERS = "triggers"
        private var notificationId = 1000
    }

    init {
        createNotificationChannels()
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_AGENT, "Agent Messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Messages from GUAPPA agent"
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_TOOLS, "Tool Results", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Results from tool executions"
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_TRIGGERS, "Triggers", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Proactive trigger notifications"
                }
            )
        }
    }

    fun showAgentNotification(title: String, body: String, actionPrompt: String? = null) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_AGENT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId++, notification)
    }

    fun showToolResultNotification(toolName: String, result: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_TOOLS)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Tool: $toolName")
            .setContentText(result)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId++, notification)
    }

    fun showTriggerNotification(triggerName: String, message: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_TRIGGERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Trigger: $triggerName")
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId++, notification)
    }
}
