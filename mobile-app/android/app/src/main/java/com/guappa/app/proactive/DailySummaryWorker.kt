package com.guappa.app.proactive

import android.content.Context
import android.util.Log
import androidx.work.*
import com.guappa.app.GuappaAgentService
import com.guappa.app.agent.BusMessage
import com.guappa.app.agent.TaskStatus
import com.guappa.app.providers.CapabilityType
import com.guappa.app.providers.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * WorkManager periodic worker that generates an evening daily summary.
 *
 * Gathers today's completed tasks, messages sent, and memory facts learned,
 * generates a summary via ProviderRouter LLM call, and posts a notification
 * on the [NotificationChannels.PROACTIVE] channel.
 *
 * Scheduling:
 *   DailySummaryWorker.schedule(context)          // default 21:00
 *   DailySummaryWorker.schedule(context, 20, 0)   // 20:00
 *   DailySummaryWorker.cancel(context)
 */
class DailySummaryWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "DailySummaryWorker"
        const val WORK_NAME = "guappa_daily_summary"
        private const val KEY_HOUR = "summary_hour"
        private const val KEY_MINUTE = "summary_minute"

        /**
         * Schedule the daily summary worker.
         * Computes initial delay to the next occurrence of [hour]:[minute].
         */
        fun schedule(
            context: Context,
            hour: Int = 21,
            minute: Int = 0
        ) {
            val delay = computeDelayMs(hour, minute)

            val inputData = Data.Builder()
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .build()

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(
                24, TimeUnit.HOURS
            )
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

            val delayHrs = delay / 3_600_000
            val delayMin = (delay % 3_600_000) / 60_000
            Log.d(TAG, "Scheduled daily summary at $hour:${minute.toString().padStart(2, '0')} " +
                    "(initial delay: ${delayHrs}h ${delayMin}m)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled daily summary")
        }

        private fun computeDelayMs(hour: Int, minute: Int): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If target time has already passed today, schedule for tomorrow
            if (target.before(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            return target.timeInMillis - now.timeInMillis
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Daily summary worker started")

        try {
            val summaryData = gatherSummaryData()
            val summaryText = generateSummary(summaryData)

            // Publish trigger for the agent
            val bus = GuappaAgentService.messageBus
            if (bus != null) {
                bus.publish(
                    BusMessage.TriggerEvent(
                        trigger = "daily_summary",
                        data = mapOf(
                            "event_type" to "daily_summary",
                            "summary_data" to summaryData,
                            "summary_text" to summaryText,
                            "message" to "Daily summary generated. Present it to the user."
                        )
                    )
                )
            } else {
                Log.w(TAG, "MessageBus not available; posting notification only")
            }

            // Show proactive notification with the summary
            val smartTiming = SmartTiming()
            if (smartTiming.shouldDeliver(appContext, NotificationChannels.PROACTIVE)) {
                val notifManager = GuappaNotificationManager(appContext)
                notifManager.showProactiveNotification(
                    title = "Daily Summary",
                    body = summaryText
                )
                smartTiming.recordDelivery(NotificationChannels.PROACTIVE)
            }

            val hour = inputData.getInt(KEY_HOUR, 21)
            val minute = inputData.getInt(KEY_MINUTE, 0)
            Log.d(TAG, "Daily summary completed — next at $hour:${minute.toString().padStart(2, '0')}")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Daily summary failed", e)
            Result.retry()
        }
    }

    /**
     * Gathers contextual data for the daily summary.
     * Returns a human-readable summary of the day's activity.
     */
    private fun gatherSummaryData(): String {
        val sb = StringBuilder()
        val now = Calendar.getInstance()

        sb.appendLine("Date: ${now.get(Calendar.YEAR)}-${(now.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}-${now.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')}")
        sb.appendLine("Day: ${now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, java.util.Locale.getDefault())}")

        // Gather task information from orchestrator
        val orchestrator = GuappaAgentService.orchestrator
        if (orchestrator != null) {
            val taskManager = orchestrator.taskManager
            val tasks = taskManager.getActiveTasks()
            val completed = tasks.filter { it.status == TaskStatus.COMPLETED }
            val failed = tasks.filter { it.status == TaskStatus.FAILED }
            val pending = tasks.filter { it.status == TaskStatus.PENDING || it.status == TaskStatus.RUNNING }

            sb.appendLine("\nTasks:")
            sb.appendLine("  Completed: ${completed.size}")
            sb.appendLine("  Failed: ${failed.size}")
            sb.appendLine("  Pending: ${pending.size}")

            if (completed.isNotEmpty()) {
                sb.appendLine("\nCompleted tasks:")
                completed.take(10).forEach { task ->
                    sb.appendLine("  - ${task.title}")
                }
            }

            if (failed.isNotEmpty()) {
                sb.appendLine("\nFailed tasks:")
                failed.take(5).forEach { task ->
                    sb.appendLine("  - ${task.title}")
                }
            }
        } else {
            sb.appendLine("\nAgent: not running")
        }

        // Notification history for today
        val notifHistory = NotificationHistory(appContext)
        val todayChat = notifHistory.todayCountByChannel(NotificationChannels.CHAT)
        val todayTasks = notifHistory.todayCountByChannel(NotificationChannels.TASKS)
        val todayAlerts = notifHistory.todayCountByChannel(NotificationChannels.ALERTS)

        sb.appendLine("\nNotifications sent today:")
        sb.appendLine("  Chat: $todayChat")
        sb.appendLine("  Tasks: $todayTasks")
        sb.appendLine("  Alerts: $todayAlerts")

        return sb.toString()
    }

    /**
     * Generates a natural-language summary using the LLM provider, if available.
     * Falls back to the raw summary data if no provider is configured.
     */
    private suspend fun generateSummary(summaryData: String): String {
        val router = GuappaAgentService.orchestrator?.let { orch ->
            try {
                // Access providerRouter reflectively since it's private
                val field = orch.javaClass.getDeclaredField("providerRouter")
                field.isAccessible = true
                field.get(orch) as? com.guappa.app.providers.ProviderRouter
            } catch (e: Exception) {
                null
            }
        }

        if (router == null) {
            Log.d(TAG, "No provider router available; using raw summary data")
            return formatFallbackSummary(summaryData)
        }

        return try {
            val response = router.chat(
                messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = "You are GUAPPA, a helpful mobile assistant. Generate a brief, friendly daily summary for the user. Keep it concise (3-5 sentences). Do not use markdown."
                    ),
                    ChatMessage(
                        role = "user",
                        content = "Generate a daily summary based on today's activity:\n$summaryData"
                    )
                ),
                capability = CapabilityType.TEXT_CHAT,
                temperature = 0.7
            )
            response.content ?: formatFallbackSummary(summaryData)
        } catch (e: Exception) {
            Log.w(TAG, "LLM summary generation failed: ${e.message}")
            formatFallbackSummary(summaryData)
        }
    }

    private fun formatFallbackSummary(data: String): String {
        // Extract key metrics from the raw data for a simpler summary
        val lines = data.lines()
        val completed = lines.find { it.contains("Completed:") }?.trim() ?: "Completed: 0"
        val failed = lines.find { it.contains("Failed:") }?.trim() ?: "Failed: 0"
        return "Today's summary: $completed, $failed. Tap for details."
    }
}
