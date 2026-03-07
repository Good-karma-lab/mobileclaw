package com.guappa.app.tools.impl

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class GetBatteryTool : Tool {
    override val name = "get_battery"
    override val description = "Get the current battery level and charging status of the device"
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
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val chargingSource = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> "None"
            }

            val temperature = batteryStatus.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE, -1
            ) / 10.0

            val data = JSONObject().apply {
                put("percentage", percentage)
                put("is_charging", isCharging)
                put("charging_source", chargingSource)
                put("temperature_celsius", temperature)
            }

            val chargingText = if (isCharging) "charging via $chargingSource" else "not charging"
            ToolResult.Success(
                content = "Battery: ${percentage}%, $chargingText, ${temperature}\u00B0C",
                data = data
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to read battery: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
