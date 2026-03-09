package com.guappa.app.tools.impl

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class WifiTool : Tool {
    override val name = "wifi_control"
    override val description = "Get WiFi status and connected network name, or open WiFi settings to toggle WiFi"
    override val requiredPermissions = listOf("ACCESS_WIFI_STATE", "ACCESS_NETWORK_STATE")
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "enum": ["status", "toggle"],
                    "description": "Action: 'status' to get WiFi info, 'toggle' to open WiFi settings panel"
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
                "status" -> getWifiStatus(context)
                "toggle" -> toggleWifi(context)
                else -> ToolResult.Error("Invalid action: $action. Use 'status' or 'toggle'.", "INVALID_PARAMS")
            }
        } catch (e: Exception) {
            ToolResult.Error("WiFi operation failed: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun getWifiStatus(context: Context): ToolResult {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return ToolResult.Error("WifiManager not available.", "EXECUTION_ERROR")

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return ToolResult.Error("ConnectivityManager not available.", "EXECUTION_ERROR")

        val isEnabled = wifiManager.isWifiEnabled
        val data = JSONObject().apply {
            put("enabled", isEnabled)
        }

        if (isEnabled) {
            @Suppress("DEPRECATION")
            val wifiInfo = wifiManager.connectionInfo
            val ssid = wifiInfo?.ssid?.removeSurrounding("\"") ?: "unknown"
            val rssi = wifiInfo?.rssi ?: 0
            val linkSpeed = wifiInfo?.linkSpeed ?: 0
            val signalLevel = WifiManager.calculateSignalLevel(rssi, 5)
            val ipAddress = wifiInfo?.ipAddress ?: 0

            val network = connectivityManager.activeNetwork
            val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
            val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            val ip = if (ipAddress != 0) {
                "${ipAddress and 0xff}.${ipAddress shr 8 and 0xff}.${ipAddress shr 16 and 0xff}.${ipAddress shr 24 and 0xff}"
            } else {
                "none"
            }

            data.put("connected", ssid != "<unknown ssid>" && ssid != "unknown")
            data.put("ssid", ssid)
            data.put("rssi", rssi)
            data.put("signal_level", signalLevel)
            data.put("link_speed_mbps", linkSpeed)
            data.put("ip_address", ip)
            data.put("has_internet", hasInternet)

            val connected = if (ssid != "<unknown ssid>" && ssid != "unknown") {
                "connected to '$ssid' (signal: $signalLevel/4, speed: ${linkSpeed}Mbps, IP: $ip)"
            } else {
                "enabled but not connected to any network"
            }
            return ToolResult.Success(
                content = "WiFi is $connected",
                data = data
            )
        }

        return ToolResult.Success(
            content = "WiFi is disabled",
            data = data
        )
    }

    private fun toggleWifi(context: Context): ToolResult {
        // On Android 10+ apps cannot directly toggle WiFi.
        // We open the WiFi settings panel for the user.
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val intent = android.content.Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolResult.Success("Opened WiFi settings panel. User can toggle WiFi from there.")
            } else {
                val intent = android.content.Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolResult.Success("Opened WiFi settings. User can toggle WiFi from there.")
            }
        } catch (e: Exception) {
            ToolResult.Error("Failed to open WiFi settings: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
