package com.guappa.app.swarm

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * Polls for swarm tasks at configurable intervals.
 *
 * Two modes of operation:
 * 1. Foreground coroutine-based polling (app active, fine-grained intervals)
 * 2. WorkManager-based periodic polling (Doze-safe, background)
 *
 * Battery-aware: reduces frequency or pauses when battery is low.
 * Doze-compatible: uses WorkManager for background polling.
 */
class SwarmTaskPoller(
    private val context: Context,
    private val connector: SwarmConnectorClient,
    private val onTaskReceived: suspend (SwarmTask) -> Unit,
    private val config: SwarmConfig = SwarmConfig(),
) {
    private val TAG = "SwarmTaskPoller"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    companion object {
        const val WORK_NAME = "guappa_swarm_poll"
        private const val LOW_BATTERY_THRESHOLD = 15 // percent
        private const val LOW_BATTERY_MULTIPLIER = 3 // 3x slower polling on low battery
    }

    /**
     * Start foreground polling with adaptive intervals.
     * Use this when the app is in the foreground.
     */
    fun startForegroundPolling() {
        if (pollingJob?.isActive == true) return

        pollingJob = scope.launch {
            Log.d(TAG, "Foreground polling started")
            while (isActive) {
                try {
                    pollOnce()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Poll cycle error: ${e.message}")
                }

                val interval = computeInterval()
                delay(interval)
            }
        }
    }

    /**
     * Stop foreground polling.
     */
    fun stopForegroundPolling() {
        pollingJob?.cancel()
        pollingJob = null
        Log.d(TAG, "Foreground polling stopped")
    }

    /**
     * Schedule WorkManager-based background polling.
     * This survives Doze mode and app process death.
     */
    fun scheduleBackgroundPolling() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SwarmPollWorker>(
            repeatInterval = 15, // Minimum allowed by WorkManager
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag("swarm_poll")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        Log.d(TAG, "Background polling scheduled via WorkManager")
    }

    /**
     * Cancel background WorkManager polling.
     */
    fun cancelBackgroundPolling() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Log.d(TAG, "Background polling cancelled")
    }

    /**
     * Execute a single poll cycle.
     * Fetches status from the connector and checks for pending tasks.
     */
    suspend fun pollOnce() {
        val status = connector.getStatus()
        if (status == null) {
            Log.d(TAG, "Connector not available, skip poll")
            return
        }

        try {
            // Check if there are pending tasks in the status response
            val pendingTasks = status.optJSONArray("pending_tasks")
            if (pendingTasks != null && pendingTasks.length() > 0) {
                for (i in 0 until pendingTasks.length()) {
                    val taskJson = pendingTasks.getJSONObject(i)
                    val task = SwarmTask.fromJSON(taskJson)
                    Log.d(TAG, "Task received: ${task.id}")
                    onTaskReceived(task)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "No tasks available: ${e.message}")
        }
    }

    /**
     * Compute the polling interval based on app state and battery level.
     */
    private fun computeInterval(): Long {
        val baseInterval = if (isAppForeground()) {
            config.activeTaskPollMs
        } else {
            config.backgroundPollMs
        }

        // Increase interval on low battery
        return if (isBatteryLow()) {
            val adjusted = baseInterval * LOW_BATTERY_MULTIPLIER
            Log.d(TAG, "Low battery: polling interval increased to ${adjusted}ms")
            adjusted
        } else {
            baseInterval
        }
    }

    private fun isBatteryLow(): Boolean {
        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) return false
        val percent = (level * 100) / scale
        return percent <= LOW_BATTERY_THRESHOLD
    }

    private fun isAppForeground(): Boolean {
        // Simple heuristic: if foreground polling is active, we are in foreground
        return pollingJob?.isActive == true
    }

    private fun getConnectorUrl(): String = config.connectorUrl

    fun shutdown() {
        stopForegroundPolling()
        cancelBackgroundPolling()
        scope.cancel()
    }
}

/**
 * WorkManager worker for Doze-safe swarm task polling.
 */
class SwarmPollWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val connector = SwarmConnectorClient()
            val status = connector.getStatus()
            if (status != null) {
                val pendingTasks = status.optJSONArray("pending_tasks")
                if (pendingTasks != null && pendingTasks.length() > 0) {
                    // Signal that tasks are available — the SwarmManager
                    // will handle execution when the app wakes up
                    Log.d("SwarmPollWorker", "Found ${pendingTasks.length()} pending tasks")
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.w("SwarmPollWorker", "Poll failed: ${e.message}")
            Result.retry()
        }
    }
}
