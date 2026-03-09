package com.guappa.app.proactive

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.*
import com.guappa.app.GuappaAgentService
import com.guappa.app.agent.BusMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * WorkManager periodic worker that generates a morning briefing.
 *
 * Scheduled daily at a configurable time (default 07:30).
 * Gathers contextual data (calendar, pending tasks, etc.) and publishes
 * a briefing trigger to the MessageBus for the agent to process via LLM.
 *
 * Scheduling:
 *   MorningBriefingWorker.schedule(context)          // default 07:30
 *   MorningBriefingWorker.schedule(context, 8, 0)    // 08:00
 *   MorningBriefingWorker.cancel(context)
 */
class MorningBriefingWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "MorningBriefingWorker"
        const val WORK_NAME = "guappa_morning_briefing"
        private const val KEY_HOUR = "briefing_hour"
        private const val KEY_MINUTE = "briefing_minute"

        /**
         * Schedule the morning briefing worker.
         * Computes initial delay to the next occurrence of [hour]:[minute].
         */
        fun schedule(
            context: Context,
            hour: Int = 7,
            minute: Int = 30
        ) {
            val delay = computeDelayMs(hour, minute)

            val inputData = Data.Builder()
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .build()

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<MorningBriefingWorker>(
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
            Log.d(TAG, "Scheduled morning briefing at $hour:${minute.toString().padStart(2, '0')} " +
                    "(initial delay: ${delayHrs}h ${delayMin}m)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled morning briefing")
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
        Log.d(TAG, "Morning briefing worker started")

        try {
            val briefingData = gatherBriefingData()

            val bus = GuappaAgentService.messageBus
            if (bus == null) {
                Log.w(TAG, "MessageBus not available; skipping briefing")
                return@withContext Result.retry()
            }

            // Publish trigger so the orchestrator can generate the briefing via LLM
            bus.publish(
                BusMessage.TriggerEvent(
                    trigger = "morning_briefing",
                    data = mapOf(
                        "event_type" to "morning_briefing",
                        "briefing_data" to briefingData.toString(),
                        "message" to "Generate a concise morning briefing for the user based on the provided data."
                    )
                )
            )

            // Also show a proactive notification
            val smartTiming = SmartTiming()
            if (smartTiming.shouldDeliver(appContext, NotificationChannels.PROACTIVE)) {
                val notifManager = GuappaNotificationManager(appContext)
                notifManager.showAgentNotification(
                    title = "Good Morning",
                    body = "Your daily briefing is ready. Tap to review."
                )
                smartTiming.recordDelivery(NotificationChannels.PROACTIVE)
            }

            // Re-schedule for the same time tomorrow
            val hour = inputData.getInt(KEY_HOUR, 7)
            val minute = inputData.getInt(KEY_MINUTE, 30)
            Log.d(TAG, "Morning briefing completed — next at $hour:${minute.toString().padStart(2, '0')}")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Morning briefing failed", e)
            Result.retry()
        }
    }

    /**
     * Gathers contextual data for the briefing.
     * Each section is best-effort: missing permissions or data are skipped.
     */
    private fun gatherBriefingData(): JSONObject {
        val data = JSONObject()
        data.put("timestamp", System.currentTimeMillis())

        // 1. Calendar events for today
        data.put("calendar_events", gatherCalendarEvents())

        // 2. Current date/time info
        val now = Calendar.getInstance()
        data.put("date", "${now.get(Calendar.YEAR)}-${(now.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}-${now.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')}")
        data.put("day_of_week", now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, java.util.Locale.getDefault()))

        // 3. Pending triggers count
        val orchestrator = GuappaAgentService.orchestrator
        data.put("agent_running", orchestrator?.isRunning?.value == true)

        return data
    }

    private fun gatherCalendarEvents(): JSONArray {
        val events = JSONArray()

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Calendar permission not granted; skipping events")
            return events
        }

        try {
            val now = Calendar.getInstance()
            val startOfDay = now.clone() as Calendar
            startOfDay.set(Calendar.HOUR_OF_DAY, 0)
            startOfDay.set(Calendar.MINUTE, 0)
            startOfDay.set(Calendar.SECOND, 0)

            val endOfDay = now.clone() as Calendar
            endOfDay.set(Calendar.HOUR_OF_DAY, 23)
            endOfDay.set(Calendar.MINUTE, 59)
            endOfDay.set(Calendar.SECOND, 59)

            val projection = arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.ALL_DAY
            )

            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
            val selectionArgs = arrayOf(
                startOfDay.timeInMillis.toString(),
                endOfDay.timeInMillis.toString()
            )

            appContext.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val event = JSONObject()
                    event.put("title", cursor.getString(0) ?: "Untitled")
                    event.put("start", cursor.getLong(1))
                    event.put("end", cursor.getLong(2))
                    event.put("location", cursor.getString(3) ?: "")
                    event.put("all_day", cursor.getInt(4) == 1)
                    events.put(event)
                }
            }

            Log.d(TAG, "Gathered ${events.length()} calendar events")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query calendar: ${e.message}")
        }

        return events
    }
}
