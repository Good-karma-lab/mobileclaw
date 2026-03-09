package com.guappa.app.tools.impl

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.StatusBarNotification
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONArray
import org.json.JSONObject

class AppNotificationsTool : Tool {
    override val name = "app_notifications"
    override val description = "Read recent notifications from a specific app or all apps. Requires notification listener access."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "package_name": {
                    "type": "string",
                    "description": "Filter notifications by app package name (optional, returns all if omitted)"
                },
                "limit": {
                    "type": "integer",
                    "description": "Maximum number of notifications to return (default: 20, max: 50)"
                }
            },
            "required": []
        }
    """.trimIndent())

    override fun isAvailable(context: Context): Boolean {
        return isNotificationListenerEnabled(context)
    }

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val packageName = params.optString("package_name", "")
        val limit = params.optInt("limit", 20).coerceIn(1, 50)

        if (!isNotificationListenerEnabled(context)) {
            return ToolResult.Error(
                "Notification listener access is not granted. Please enable it in Settings > Apps > Special access > Notification access.",
                "PERMISSION_DENIED"
            )
        }

        return try {
            val notifications = GuappaNotificationListener.getActiveNotifications()
            if (notifications == null) {
                return ToolResult.Error(
                    "Notification listener service is not connected. Please restart the app.",
                    "SERVICE_UNAVAILABLE"
                )
            }

            val filtered = if (packageName.isNotEmpty()) {
                notifications.filter { it.packageName == packageName }
            } else {
                notifications.toList()
            }.sortedByDescending { it.postTime }.take(limit)

            val items = JSONArray()
            for (sbn in filtered) {
                val extras = sbn.notification.extras
                val item = JSONObject().apply {
                    put("package_name", sbn.packageName)
                    put("title", extras.getCharSequence("android.title")?.toString() ?: "")
                    put("text", extras.getCharSequence("android.text")?.toString() ?: "")
                    put("post_time", sbn.postTime)
                    put("is_ongoing", sbn.isOngoing)
                    put("id", sbn.id)
                    put("key", sbn.key)
                }
                items.put(item)
            }

            val data = JSONObject().apply {
                put("notifications", items)
                put("count", items.length())
            }

            val filterText = if (packageName.isNotEmpty()) " from $packageName" else ""
            ToolResult.Success(
                content = "Found ${items.length()} notification(s)$filterText",
                data = data
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to read notifications: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val componentName = ComponentName(context, GuappaNotificationListener::class.java)
        return flat.contains(componentName.flattenToString())
    }
}

/**
 * Singleton accessor for the notification listener service.
 * The actual service class must extend NotificationListenerService
 * and be declared in AndroidManifest.xml.
 */
object GuappaNotificationListener {
    @Volatile
    private var activeNotifications: Array<StatusBarNotification>? = null

    fun updateNotifications(notifications: Array<StatusBarNotification>) {
        activeNotifications = notifications
    }

    fun getActiveNotifications(): Array<StatusBarNotification>? = activeNotifications
}
