package com.guappa.app.tools.impl

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class BatteryInfoTool : Tool {
    override val name = "battery_info"
    override val description = "Get detailed battery information including level, charging status, health, temperature, voltage, and technology"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {},
            "required": []
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)

            if (batteryStatus == null) {
                return ToolResult.Error("Could not read battery status.", "EXECUTION_ERROR")
            }

            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percentage = if (scale > 0) (level * 100) / scale else -1

            val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val statusText = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
                else -> "unknown"
            }
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val chargingSource = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> "None"
            }

            val health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            val healthText = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                BatteryManager.BATTERY_HEALTH_COLD -> "cold"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
                else -> "unknown"
            }

            val temperature = batteryStatus.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE, -1
            ) / 10.0

            val voltage = batteryStatus.getIntExtra(
                BatteryManager.EXTRA_VOLTAGE, -1
            )
            val voltageV = voltage / 1000.0

            val technology = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "unknown"

            val data = JSONObject().apply {
                put("percentage", percentage)
                put("status", statusText)
                put("is_charging", isCharging)
                put("charging_source", chargingSource)
                put("health", healthText)
                put("temperature_celsius", temperature)
                put("voltage_v", voltageV)
                put("technology", technology)
            }

            val chargingText = if (isCharging) "charging via $chargingSource" else "not charging"
            ToolResult.Success(
                content = "Battery: ${percentage}%, $statusText, health: $healthText, " +
                    "${temperature}\u00B0C, ${voltageV}V, $technology",
                data = data
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to read battery info: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
