package com.guappa.app.proactive

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Defines and creates all GUAPPA notification channels.
 *
 * Channels (Android O+):
 *   guappa_chat       — Chat messages from the agent (HIGH)
 *   guappa_tasks      — Task status updates (DEFAULT)
 *   guappa_questions   — Agent questions requiring user input (HIGH)
 *   guappa_alerts      — Urgent alerts (MAX)
 *   guappa_proactive   — Proactive insights and briefings (LOW)
 *   guappa_service     — Background service persistent notification (MIN)
 *
 * Call [createAll] once during Application.onCreate or service init.
 */
object NotificationChannels {

    private const val TAG = "NotificationChannels"

    const val GROUP_ID = "guappa_group"

    const val CHAT = "guappa_chat"
    const val TASKS = "guappa_tasks"
    const val QUESTIONS = "guappa_questions"
    const val ALERTS = "guappa_alerts"
    const val PROACTIVE = "guappa_proactive"
    const val SERVICE = "guappa_service"

    data class ChannelDef(
        val id: String,
        val name: String,
        val description: String,
        val importance: Int
    )

    private val channels = listOf(
        ChannelDef(
            id = CHAT,
            name = "Chat Messages",
            description = "Messages from your GUAPPA assistant",
            importance = NotificationManager.IMPORTANCE_HIGH
        ),
        ChannelDef(
            id = TASKS,
            name = "Task Updates",
            description = "Status updates for running and completed tasks",
            importance = NotificationManager.IMPORTANCE_DEFAULT
        ),
        ChannelDef(
            id = QUESTIONS,
            name = "Questions",
            description = "Questions from GUAPPA that need your input",
            importance = NotificationManager.IMPORTANCE_HIGH
        ),
        ChannelDef(
            id = ALERTS,
            name = "Alerts",
            description = "Urgent alerts requiring immediate attention",
            importance = NotificationManager.IMPORTANCE_MAX
        ),
        ChannelDef(
            id = PROACTIVE,
            name = "Proactive Insights",
            description = "Morning briefings, suggestions, and contextual tips",
            importance = NotificationManager.IMPORTANCE_LOW
        ),
        ChannelDef(
            id = SERVICE,
            name = "Background Service",
            description = "Persistent notification for the GUAPPA background service",
            importance = NotificationManager.IMPORTANCE_MIN
        )
    )

    /**
     * Creates all notification channels. Safe to call multiple times;
     * existing channels are updated, not duplicated.
     */
    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create a channel group so users see "GUAPPA" in system settings
        nm.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_ID, "GUAPPA")
        )

        for (def in channels) {
            val channel = NotificationChannel(def.id, def.name, def.importance).apply {
                description = def.description
                group = GROUP_ID

                when (def.id) {
                    ALERTS -> {
                        enableVibration(true)
                        enableLights(true)
                        setBypassDnd(true)
                    }
                    QUESTIONS -> {
                        enableVibration(true)
                        enableLights(true)
                    }
                    CHAT -> {
                        enableVibration(true)
                    }
                    SERVICE -> {
                        setShowBadge(false)
                    }
                }
            }
            nm.createNotificationChannel(channel)
        }

        Log.d(TAG, "Created ${channels.size} notification channels")
    }

    /** Returns all channel IDs for iteration/testing. */
    fun allChannelIds(): List<String> = channels.map { it.id }
}
