package com.guappa.app.tools.impl

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONArray
import org.json.JSONObject

class BluetoothTool : Tool {
    override val name = "bluetooth_control"
    override val description = "Get Bluetooth status, list paired devices, or open Bluetooth settings to toggle on/off"
    override val requiredPermissions = listOf("BLUETOOTH_CONNECT")
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "enum": ["status", "paired_devices", "toggle"],
                    "description": "Action: 'status' for Bluetooth state, 'paired_devices' to list bonded devices, 'toggle' to open settings"
                }
            },
            "required": ["action"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val action = params.optString("action", "")
        if (action.isEmpty()) {
            return ToolResult.Error("Action is required.", "INVALID_PARAMS")
        }

        return try {
            when (action) {
                "status" -> getStatus(context)
                "paired_devices" -> listPairedDevices(context)
                "toggle" -> openSettings(context)
                else -> ToolResult.Error("Invalid action: $action. Use 'status', 'paired_devices', or 'toggle'.", "INVALID_PARAMS")
            }
        } catch (e: SecurityException) {
            ToolResult.Error("Bluetooth permission denied: ${e.message}", "PERMISSION_DENIED")
        } catch (e: Exception) {
            ToolResult.Error("Bluetooth operation failed: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun getAdapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    private fun getStatus(context: Context): ToolResult {
        val adapter = getAdapter(context)
            ?: return ToolResult.Error("Bluetooth not available on this device.", "EXECUTION_ERROR")

        val isEnabled = adapter.isEnabled
        val state = when (adapter.state) {
            BluetoothAdapter.STATE_OFF -> "off"
            BluetoothAdapter.STATE_ON -> "on"
            BluetoothAdapter.STATE_TURNING_OFF -> "turning_off"
            BluetoothAdapter.STATE_TURNING_ON -> "turning_on"
            else -> "unknown"
        }

        val data = JSONObject().apply {
            put("enabled", isEnabled)
            put("state", state)
            put("name", adapter.name ?: "unknown")
            put("address", adapter.address ?: "unknown")
        }

        return ToolResult.Success(
            content = "Bluetooth is $state (name: ${adapter.name ?: "unknown"})",
            data = data
        )
    }

    @Suppress("MissingPermission")
    private fun listPairedDevices(context: Context): ToolResult {
        val adapter = getAdapter(context)
            ?: return ToolResult.Error("Bluetooth not available on this device.", "EXECUTION_ERROR")

        if (!adapter.isEnabled) {
            return ToolResult.Error("Bluetooth is disabled. Enable it first.", "EXECUTION_ERROR")
        }

        val bonded = adapter.bondedDevices ?: emptySet()
        val devicesArray = JSONArray()
        val deviceList = mutableListOf<String>()

        for (device in bonded) {
            val deviceJson = JSONObject().apply {
                put("name", device.name ?: "unknown")
                put("address", device.address ?: "unknown")
                put("type", when (device.type) {
                    1 -> "classic"
                    2 -> "le"
                    3 -> "dual"
                    else -> "unknown"
                })
                put("bond_state", when (device.bondState) {
                    10 -> "none"
                    11 -> "bonding"
                    12 -> "bonded"
                    else -> "unknown"
                })
            }
            devicesArray.put(deviceJson)
            deviceList.add(device.name ?: device.address)
        }

        val data = JSONObject().apply {
            put("count", bonded.size)
            put("devices", devicesArray)
        }

        return if (bonded.isEmpty()) {
            ToolResult.Success(content = "No paired Bluetooth devices found.", data = data)
        } else {
            ToolResult.Success(
                content = "Paired devices (${bonded.size}): ${deviceList.joinToString(", ")}",
                data = data
            )
        }
    }

    private fun openSettings(context: Context): ToolResult {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ToolResult.Success("Opened Bluetooth settings. User can toggle Bluetooth from there.")
    }
}
