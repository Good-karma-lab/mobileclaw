package com.guappa.app.tools.impl

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReminderTool : Tool {
    override val name = "set_reminder"
    override val description = "Set a one-time reminder that triggers a notification at a specified time. " +
        "Supports absolute time (date/time) or relative delay (in minutes)."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "message": {
                    "type": "string",
                    "description": "The reminder message to display"
                },
                "delay_minutes": {
                    "type": "integer",
                    "description": "Minutes from now to trigger the reminder (use this OR date/time)"
                },
                "date": {
                    "type": "string",
                    "description": "Target date in yyyy-MM-dd format (use with time)"
                },
                "time": {
                    "type": "string",
                    "description": "Target time in HH:mm format (24-hour)"
                },
                "title": {
                    "type": "string",
                    "description": "Optional title for the reminder notification (default: 'Guappa Reminder')"
                }
            },
            "required": ["message"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val message = params.optString("message", "")
        if (message.isEmpty()) {
            return ToolResult.Error("Reminder message is required.", "INVALID_PARAMS")
        }

        val title = params.optString("title", "Guappa Reminder")
        val delayMinutes = params.optInt("delay_minutes", 0)
        val dateStr = params.optString("date", "")
        val timeStr = params.optString("time", "")

        val triggerTimeMs: Long = when {
            delayMinutes > 0 -> {
                System.currentTimeMillis() + (delayMinutes.toLong() * 60 * 1000)
            }
            timeStr.isNotEmpty() -> {
                try {
                    val cal = Calendar.getInstance()
                    if (dateStr.isNotEmpty()) {
                        val dateParts = dateStr.split("-")
                        cal.set(Calendar.YEAR, dateParts[0].toInt())
                        cal.set(Calendar.MONTH, dateParts[1].toInt() - 1)
                        cal.set(Calendar.DAY_OF_MONTH, dateParts[2].toInt())
                    }
                    val timeParts = timeStr.split(":")
                    cal.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                    cal.set(Calendar.MINUTE, timeParts[1].toInt())
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)

                    // If the time is in the past today, schedule for tomorrow
                    if (dateStr.isEmpty() && cal.timeInMillis <= System.currentTimeMillis()) {
                        cal.add(Calendar.DAY_OF_MONTH, 1)
                    }
                    cal.timeInMillis
                } catch (e: Exception) {
                    return ToolResult.Error(
                        "Invalid date/time format. Use date: yyyy-MM-dd, time: HH:mm",
                        "INVALID_PARAMS"
                    )
                }
            }
            else -> {
                return ToolResult.Error(
                    "Either delay_minutes or time must be provided.",
                    "INVALID_PARAMS"
                )
            }
        }

        if (triggerTimeMs <= System.currentTimeMillis()) {
            return ToolResult.Error("Reminder time must be in the future.", "INVALID_PARAMS")
        }

        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val requestCode = message.hashCode() xor triggerTimeMs.toInt()

            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("title", title)
                putExtra("message", message)
                putExtra("request_code", requestCode)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent
                    )
                } else {
                    // Fall back to inexact alarm
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent
                )
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val triggerStr = dateFormat.format(Date(triggerTimeMs))

            val data = JSONObject().apply {
                put("title", title)
                put("message", message)
                put("trigger_time", triggerStr)
                put("trigger_time_ms", triggerTimeMs)
                put("request_code", requestCode)
            }

            ToolResult.Success(
                content = "Reminder set for $triggerStr: $message",
                data = data
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to set reminder: ${e.message}", "EXECUTION_ERROR")
        }
    }
}

/**
 * BroadcastReceiver that handles reminder notifications.
 * Must be declared in AndroidManifest.xml.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Guappa Reminder"
        val message = intent.getStringExtra("message") ?: return
        val requestCode = intent.getIntExtra("request_code", 0)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "guappa_reminders",
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Guappa agent reminders"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = Notification.Builder(
            context,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "guappa_reminders" else ""
        )
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(requestCode, notification)
    }
}
