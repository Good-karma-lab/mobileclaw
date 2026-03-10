package com.guappa.app.tools.impl

import com.guappa.app.R

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class CronJobTool : Tool {
    override val name = "cron_job"
    override val description = "Schedule a recurring task that runs at a specified interval. " +
        "Supports actions like showing a notification, launching an app, or sending a message."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "description": "Action to perform: 'create', 'list', or 'cancel'"
                },
                "job_id": {
                    "type": "string",
                    "description": "Unique identifier for the job (required for create/cancel)"
                },
                "interval_minutes": {
                    "type": "integer",
                    "description": "Interval between executions in minutes (min: 15, required for create)"
                },
                "task_type": {
                    "type": "string",
                    "description": "Type of task: 'notification', 'launch_app', 'reminder' (required for create)"
                },
                "task_data": {
                    "type": "object",
                    "description": "Task-specific data (e.g. title/message for notification, package_name for launch_app)"
                }
            },
            "required": ["action"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val action = params.optString("action", "")

        return when (action) {
            "create" -> createJob(params, context)
            "list" -> listJobs(context)
            "cancel" -> cancelJob(params, context)
            else -> ToolResult.Error(
                "Invalid action: '$action'. Use 'create', 'list', or 'cancel'.",
                "INVALID_PARAMS"
            )
        }
    }

    private fun createJob(params: JSONObject, context: Context): ToolResult {
        val jobId = params.optString("job_id", "")
        if (jobId.isEmpty()) {
            return ToolResult.Error("job_id is required for create action.", "INVALID_PARAMS")
        }

        val intervalMinutes = params.optInt("interval_minutes", 0)
        if (intervalMinutes < 15) {
            return ToolResult.Error(
                "interval_minutes must be at least 15 (Android minimum).",
                "INVALID_PARAMS"
            )
        }

        val taskType = params.optString("task_type", "")
        if (taskType.isEmpty()) {
            return ToolResult.Error("task_type is required for create action.", "INVALID_PARAMS")
        }

        val taskData = params.optJSONObject("task_data") ?: JSONObject()

        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val requestCode = jobId.hashCode()

            val intent = Intent(context, CronJobReceiver::class.java).apply {
                putExtra("job_id", jobId)
                putExtra("task_type", taskType)
                putExtra("task_data", taskData.toString())
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val intervalMs = intervalMinutes.toLong() * 60 * 1000
            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + intervalMs,
                intervalMs,
                pendingIntent
            )

            // Store job info in shared prefs for listing
            val prefs = context.getSharedPreferences("guappa_cron_jobs", Context.MODE_PRIVATE)
            val jobInfo = JSONObject().apply {
                put("job_id", jobId)
                put("interval_minutes", intervalMinutes)
                put("task_type", taskType)
                put("task_data", taskData)
                put("created_at", System.currentTimeMillis())
            }
            prefs.edit().putString("job_$jobId", jobInfo.toString()).apply()

            ToolResult.Success(
                content = "Cron job '$jobId' created: $taskType every $intervalMinutes minutes",
                data = jobInfo
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to create cron job: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun listJobs(context: Context): ToolResult {
        return try {
            val prefs = context.getSharedPreferences("guappa_cron_jobs", Context.MODE_PRIVATE)
            val jobs = JSONArray()

            prefs.all.forEach { (key, value) ->
                if (key.startsWith("job_") && value is String) {
                    try {
                        jobs.put(JSONObject(value))
                    } catch (_: Exception) { }
                }
            }

            val data = JSONObject().apply {
                put("jobs", jobs)
                put("count", jobs.length())
            }

            ToolResult.Success(
                content = "Found ${jobs.length()} scheduled cron job(s)",
                data = data
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to list cron jobs: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun cancelJob(params: JSONObject, context: Context): ToolResult {
        val jobId = params.optString("job_id", "")
        if (jobId.isEmpty()) {
            return ToolResult.Error("job_id is required for cancel action.", "INVALID_PARAMS")
        }

        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val requestCode = jobId.hashCode()

            val intent = Intent(context, CronJobReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }

            // Remove from shared prefs
            val prefs = context.getSharedPreferences("guappa_cron_jobs", Context.MODE_PRIVATE)
            prefs.edit().remove("job_$jobId").apply()

            ToolResult.Success("Cron job '$jobId' cancelled")
        } catch (e: Exception) {
            ToolResult.Error("Failed to cancel cron job: ${e.message}", "EXECUTION_ERROR")
        }
    }
}

/**
 * BroadcastReceiver that handles cron job execution.
 * Must be declared in AndroidManifest.xml.
 */
class CronJobReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getStringExtra("job_id") ?: return
        val taskType = intent.getStringExtra("task_type") ?: return
        val taskDataStr = intent.getStringExtra("task_data") ?: "{}"

        when (taskType) {
            "notification" -> {
                val taskData = try { JSONObject(taskDataStr) } catch (_: Exception) { JSONObject() }
                val title = taskData.optString("title", "Guappa Reminder")
                val message = taskData.optString("message", "Scheduled task: $jobId")
                showNotification(context, jobId, title, message)
            }
            "reminder" -> {
                val taskData = try { JSONObject(taskDataStr) } catch (_: Exception) { JSONObject() }
                val message = taskData.optString("message", "Reminder: $jobId")
                showNotification(context, jobId, "Guappa Reminder", message)
            }
        }
    }

    private fun showNotification(context: Context, tag: String, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "guappa_cron",
                "Scheduled Tasks",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = android.app.Notification.Builder(
            context,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "guappa_cron" else ""
        )
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notif_info)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(tag.hashCode(), notification)
    }
}
