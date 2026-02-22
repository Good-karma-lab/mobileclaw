package com.mobileclaw.app

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
import kotlinx.coroutines.*

/**
 * Foreground service that runs the ZeroClaw agent runtime using JNI.
 *
 * This service:
 * - Loads the Rust backend as a native library (in-process, not subprocess)
 * - Starts the agent runtime with full feature support
 * - Runs in foreground with persistent notification
 * - Provides agent conversation, scheduling, hooks, and tool execution
 *
 * **Architecture Change**: Uses JNI instead of subprocess to bypass Android SELinux restrictions.
 */
class ZeroClawDaemonService : Service() {
    private val TAG = "ZeroClawDaemon"
    private val CHANNEL_ID = "zeroclaw_daemon"
    private val NOTIFICATION_ID = 1001

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var agentHandle: Long = 0

    companion object {
        const val ACTION_START = "com.mobileclaw.START_DAEMON"
        const val ACTION_STOP = "com.mobileclaw.STOP_DAEMON"
        const val ACTION_RESTART = "com.mobileclaw.RESTART_DAEMON"

        const val EXTRA_API_KEY = "api_key"
        const val EXTRA_MODEL = "model"
        const val EXTRA_TELEGRAM_TOKEN = "telegram_token"
        const val EXTRA_TELEGRAM_CHAT_ID = "telegram_chat_id"
        const val EXTRA_DISCORD_BOT_TOKEN = "discord_bot_token"
        const val EXTRA_SLACK_BOT_TOKEN = "slack_bot_token"
        const val EXTRA_COMPOSIO_API_KEY = "composio_api_key"

        @Volatile
        private var isRunning = false

        @Volatile
        private var agentHandleId: Long = 0

        fun isRunning(): Boolean = isRunning

        fun getAgentHandle(): Long = agentHandleId
    }

    private var apiKey: String = ""
    private var model: String = ""
    private var telegramToken: String = ""
    private var telegramChatId: String = ""
    private var discordBotToken: String = ""
    private var slackBotToken: String = ""
    private var composioApiKey: String = ""

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Extract config from intent extras when starting
        if (intent?.action != ACTION_STOP) {
            apiKey = intent?.getStringExtra(EXTRA_API_KEY) ?: ""
            model = intent?.getStringExtra(EXTRA_MODEL) ?: ""
            telegramToken = intent?.getStringExtra(EXTRA_TELEGRAM_TOKEN) ?: ""
            telegramChatId = intent?.getStringExtra(EXTRA_TELEGRAM_CHAT_ID) ?: ""
            discordBotToken = intent?.getStringExtra(EXTRA_DISCORD_BOT_TOKEN) ?: ""
            slackBotToken = intent?.getStringExtra(EXTRA_SLACK_BOT_TOKEN) ?: ""
            composioApiKey = intent?.getStringExtra(EXTRA_COMPOSIO_API_KEY) ?: ""
        }

        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "Received START action")
                startForeground(NOTIFICATION_ID, createNotification("Starting ZeroClaw...", "Initializing agent runtime"))
                startAgent()
            }
            ACTION_STOP -> {
                Log.d(TAG, "Received STOP action")
                stopAgent()
                stopSelf()
            }
            ACTION_RESTART -> {
                Log.d(TAG, "Received RESTART action")
                stopAgent()
                Thread.sleep(500)
                startAgent()
            }
            else -> {
                // Default action is to start
                Log.d(TAG, "Default START action")
                startForeground(NOTIFICATION_ID, createNotification("Starting ZeroClaw...", "Initializing agent runtime"))
                startAgent()
            }
        }

        // Restart service if killed by system
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        stopAgent()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startAgent() {
        if (isRunning) {
            Log.w(TAG, "Agent already running")
            updateNotification("ZeroClaw Running", "Agent runtime active")
            return
        }

        serviceScope.launch {
            try {
                Log.d(TAG, "Starting ZeroClaw agent via JNI")

                // Get config path (app files directory)
                val configPath = (applicationContext.filesDir.parent ?: applicationContext.filesDir.absolutePath) + "/.zeroclaw"

                // Start Android action bridge server so Rust can call native Android APIs
                RuntimeBridge.ensureAndroidActionBridge(applicationContext)

                // Start agent runtime via JNI with config params
                agentHandle = ZeroClawBackend.startAgent(
                    configPath, apiKey, model, telegramToken,
                    telegramChatId, discordBotToken, slackBotToken, composioApiKey
                )

                if (agentHandle == 0L) {
                    Log.e(TAG, "Failed to start agent - handle is 0")
                    updateNotification("ZeroClaw Error", "Failed to start agent")
                    return@launch
                }

                agentHandleId = agentHandle
                isRunning = true

                Log.i(TAG, "✅ Agent started successfully with handle: $agentHandle")

                // Get gateway URL
                val gatewayUrl = try {
                    ZeroClawBackend.getGatewayUrl(agentHandle)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not get gateway URL: ${e.message}")
                    "http://127.0.0.1:8000"
                }

                Log.i(TAG, "Gateway URL: $gatewayUrl")

                // Update notification to show running state
                updateNotification("ZeroClaw Running", "Agent runtime active on $gatewayUrl")

                // Monitor health periodically
                monitorAgentHealth()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start agent", e)
                updateNotification("ZeroClaw Error", "Failed to start: ${e.message}")
                isRunning = false
                agentHandle = 0
                agentHandleId = 0

                // Try restart after delay
                launch {
                    delay(5000)
                    if (!isRunning) {
                        Log.d(TAG, "Attempting automatic restart after failure...")
                        startAgent()
                    }
                }
            }
        }
    }

    private fun stopAgent() {
        serviceScope.launch {
            try {
                if (agentHandle != 0L) {
                    Log.d(TAG, "Stopping agent with handle: $agentHandle")
                    ZeroClawBackend.stopAgent(agentHandle)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping agent", e)
            } finally {
                isRunning = false
                agentHandle = 0
                agentHandleId = 0
                Log.d(TAG, "Agent stopped")
            }
        }
    }

    private suspend fun monitorAgentHealth() {
        while (isRunning && agentHandle != 0L) {
            delay(30000) // Check every 30 seconds

            try {
                val healthy = ZeroClawBackend.isHealthy(agentHandle)
                if (!healthy) {
                    Log.w(TAG, "Agent health check failed")
                    updateNotification("ZeroClaw Warning", "Agent health check failed")
                } else {
                    Log.d(TAG, "Agent health check: OK")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Health check error", e)
                // Don't restart on health check errors - agent might still be working
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "ZeroClaw Agent"
            val descriptionText = "ZeroClaw autonomous agent runtime"
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

    private fun updateNotification(title: String, text: String) {
        val notification = createNotification(title, text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
