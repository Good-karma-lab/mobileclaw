package com.guappa.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.guappa.app.agent.GuappaConfig
import com.guappa.app.agent.GuappaOrchestrator
import com.guappa.app.agent.MessageBus

/**
 * Foreground service that hosts the Guappa agent core (MessageBus, Config, Orchestrator).
 *
 * Lifecycle:
 * - onCreate  -> instantiate agent core, create notification channel, start orchestrator
 * - onDestroy -> stop orchestrator, release resources
 *
 * The companion object exposes the singleton instances so other components
 * (activities, receivers, React Native bridge) can publish messages and read state.
 */
class GuappaAgentService : Service() {
    private val TAG = "GuappaAgentService"
    private val NOTIFICATION_ID = 1002

    companion object {
        const val CHANNEL_ID = "guappa_agent"

        @Volatile
        private var _messageBus: MessageBus? = null

        @Volatile
        private var _config: GuappaConfig? = null

        @Volatile
        private var _orchestrator: GuappaOrchestrator? = null

        val messageBus: MessageBus? get() = _messageBus
        val config: GuappaConfig? get() = _config
        val orchestrator: GuappaOrchestrator? get() = _orchestrator

        fun isRunning(): Boolean = _orchestrator?.isRunning?.value == true
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        createNotificationChannel()

        // Initialize agent core singletons
        val bus = MessageBus()
        val cfg = GuappaConfig()
        val orch = GuappaOrchestrator(bus, cfg)

        _messageBus = bus
        _config = cfg
        _orchestrator = orch

        orch.start()
        Log.i(TAG, "Guappa orchestrator started")

        try {
            startForeground(NOTIFICATION_ID, createNotification(
                "Guappa agent active",
                "Assistant is running"
            ))
        } catch (e: Exception) {
            Log.w(TAG, "Could not start foreground service: ${e.message}. Running as background service.")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        _orchestrator?.stop()
        _orchestrator = null
        _config = null
        _messageBus = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Guappa Agent"
            val descriptionText = "Guappa on-device assistant runtime"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
