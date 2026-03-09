package com.guappa.app.tools.impl

import android.annotation.SuppressLint
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
import org.json.JSONArray
import org.json.JSONObject

class GeofenceTool : Tool {
    override val name = "geofence"
    override val description = "Set up, list, or remove geofence alerts that trigger when entering or leaving a geographic area"
    override val requiredPermissions = listOf("ACCESS_FINE_LOCATION", "ACCESS_BACKGROUND_LOCATION")
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "description": "Action to perform: 'create', 'list', or 'remove'"
                },
                "fence_id": {
                    "type": "string",
                    "description": "Unique identifier for the geofence (required for create/remove)"
                },
                "latitude": {
                    "type": "number",
                    "description": "Center latitude of the geofence (required for create)"
                },
                "longitude": {
                    "type": "number",
                    "description": "Center longitude of the geofence (required for create)"
                },
                "radius_meters": {
                    "type": "number",
                    "description": "Radius of the geofence in meters (default: 200, min: 50, max: 50000)"
                },
                "trigger_on": {
                    "type": "string",
                    "description": "When to trigger: 'enter', 'exit', or 'both' (default: 'both')"
                },
                "message": {
                    "type": "string",
                    "description": "Notification message when geofence is triggered (required for create)"
                },
                "label": {
                    "type": "string",
                    "description": "Human-readable label for the location (optional, e.g. 'Home', 'Office')"
                }
            },
            "required": ["action"]
        }
    """.trimIndent())

    companion object {
        private const val PREFS_NAME = "guappa_geofences"
    }

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val action = params.optString("action", "")

        return when (action) {
            "create" -> createGeofence(params, context)
            "list" -> listGeofences(context)
            "remove" -> removeGeofence(params, context)
            else -> ToolResult.Error(
                "Invalid action: '$action'. Use 'create', 'list', or 'remove'.",
                "INVALID_PARAMS"
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun createGeofence(params: JSONObject, context: Context): ToolResult {
        val fenceId = params.optString("fence_id", "")
        if (fenceId.isEmpty()) {
            return ToolResult.Error("fence_id is required for create action.", "INVALID_PARAMS")
        }

        val latitude = params.optDouble("latitude", Double.NaN)
        val longitude = params.optDouble("longitude", Double.NaN)
        if (latitude.isNaN() || longitude.isNaN()) {
            return ToolResult.Error("latitude and longitude are required for create action.", "INVALID_PARAMS")
        }

        if (latitude < -90 || latitude > 90) {
            return ToolResult.Error("latitude must be between -90 and 90.", "INVALID_PARAMS")
        }
        if (longitude < -180 || longitude > 180) {
            return ToolResult.Error("longitude must be between -180 and 180.", "INVALID_PARAMS")
        }

        val radiusMeters = params.optDouble("radius_meters", 200.0).coerceIn(50.0, 50000.0)
        val triggerOn = params.optString("trigger_on", "both")
        val message = params.optString("message", "")
        if (message.isEmpty()) {
            return ToolResult.Error("message is required for create action.", "INVALID_PARAMS")
        }
        val label = params.optString("label", "")

        return try {
            // Store geofence config in shared prefs
            // In production this would use Google Play Services GeofencingClient
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            val fenceData = JSONObject().apply {
                put("fence_id", fenceId)
                put("latitude", latitude)
                put("longitude", longitude)
                put("radius_meters", radiusMeters)
                put("trigger_on", triggerOn)
                put("message", message)
                put("label", label)
                put("active", true)
                put("created_at", System.currentTimeMillis())
            }

            prefs.edit().putString("fence_$fenceId", fenceData.toString()).apply()

            val locationDesc = if (label.isNotEmpty()) "'$label' ($latitude, $longitude)" else "($latitude, $longitude)"
            ToolResult.Success(
                content = "Geofence '$fenceId' created at $locationDesc with ${radiusMeters.toInt()}m radius. " +
                    "Trigger: $triggerOn. Message: $message",
                data = fenceData
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to create geofence: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun listGeofences(context: Context): ToolResult {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val fences = JSONArray()

            prefs.all.forEach { (key, value) ->
                if (key.startsWith("fence_") && value is String) {
                    try {
                        fences.put(JSONObject(value))
                    } catch (_: Exception) { }
                }
            }

            val data = JSONObject().apply {
                put("geofences", fences)
                put("count", fences.length())
            }

            ToolResult.Success(
                content = "Found ${fences.length()} geofence(s)",
                data = data
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to list geofences: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun removeGeofence(params: JSONObject, context: Context): ToolResult {
        val fenceId = params.optString("fence_id", "")
        if (fenceId.isEmpty()) {
            return ToolResult.Error("fence_id is required for remove action.", "INVALID_PARAMS")
        }

        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = "fence_$fenceId"

            if (!prefs.contains(key)) {
                return ToolResult.Error("Geofence '$fenceId' not found.", "NOT_FOUND")
            }

            prefs.edit().remove(key).apply()
            ToolResult.Success("Geofence '$fenceId' removed")
        } catch (e: Exception) {
            ToolResult.Error("Failed to remove geofence: ${e.message}", "EXECUTION_ERROR")
        }
    }
}

/**
 * BroadcastReceiver that handles geofence transition events.
 * Must be declared in AndroidManifest.xml.
 * In production, integrate with Google Play Services GeofencingClient.
 */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val fenceId = intent.getStringExtra("fence_id") ?: return
        val transitionType = intent.getStringExtra("transition_type") ?: "enter"
        val message = intent.getStringExtra("message") ?: "Geofence alert: $fenceId"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "guappa_geofence",
                "Geofence Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Geofence transition alerts"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val title = when (transitionType) {
            "enter" -> "Arrived at geofence"
            "exit" -> "Left geofence"
            else -> "Geofence alert"
        }

        val notification = Notification.Builder(
            context,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "guappa_geofence" else ""
        )
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(fenceId.hashCode(), notification)
    }
}
