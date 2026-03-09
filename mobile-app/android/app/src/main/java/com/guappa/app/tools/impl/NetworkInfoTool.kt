package com.guappa.app.tools.impl

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface

class NetworkInfoTool : Tool {
    override val name = "network_info"
    override val description = "Get current network status: connection type, WiFi SSID, signal strength, IP address, and link speed."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {},
            "required": []
        }
    """.trimIndent())

    @Suppress("DEPRECATION")
    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

            val isConnected = capabilities != null
            val hasWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val hasCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            val hasEthernet = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
            val hasVpn = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

            val connectionType = when {
                hasWifi -> "WiFi"
                hasCellular -> "Cellular"
                hasEthernet -> "Ethernet"
                !isConnected -> "Disconnected"
                else -> "Unknown"
            }

            val downstreamBandwidth = capabilities?.linkDownstreamBandwidthKbps ?: 0
            val upstreamBandwidth = capabilities?.linkUpstreamBandwidthKbps ?: 0

            // WiFi details
            var ssid = ""
            var wifiSignal = 0
            var linkSpeed = 0
            var frequency = 0

            if (hasWifi) {
                try {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val wifiInfo = wifiManager.connectionInfo
                    ssid = wifiInfo.ssid?.replace("\"", "") ?: "unknown"
                    wifiSignal = wifiInfo.rssi
                    linkSpeed = wifiInfo.linkSpeed
                    frequency = wifiInfo.frequency
                } catch (_: SecurityException) {
                    ssid = "<permission required>"
                }
            }

            // IP address
            val ipAddress = getLocalIpAddress()

            val data = JSONObject().apply {
                put("connected", isConnected)
                put("connection_type", connectionType)
                put("has_wifi", hasWifi)
                put("has_cellular", hasCellular)
                put("has_ethernet", hasEthernet)
                put("has_vpn", hasVpn)
                put("downstream_bandwidth_kbps", downstreamBandwidth)
                put("upstream_bandwidth_kbps", upstreamBandwidth)
                put("ip_address", ipAddress)
                if (hasWifi) {
                    put("wifi_ssid", ssid)
                    put("wifi_signal_rssi", wifiSignal)
                    put("wifi_link_speed_mbps", linkSpeed)
                    put("wifi_frequency_mhz", frequency)
                }
            }

            val summary = buildString {
                appendLine("Network Status: $connectionType")
                if (!isConnected) {
                    appendLine("Not connected to any network.")
                } else {
                    appendLine("IP Address: $ipAddress")
                    appendLine("Bandwidth: ${downstreamBandwidth / 1000} Mbps down / ${upstreamBandwidth / 1000} Mbps up")
                    if (hasWifi) {
                        appendLine("WiFi SSID: $ssid")
                        appendLine("Signal: $wifiSignal dBm")
                        appendLine("Link Speed: $linkSpeed Mbps")
                        appendLine("Frequency: $frequency MHz (${if (frequency > 4900) "5GHz" else "2.4GHz"})")
                    }
                    if (hasVpn) appendLine("VPN: Active")
                }
            }

            ToolResult.Success(content = summary.trim(), data = data)
        } catch (e: Exception) {
            ToolResult.Error("Failed to get network info: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "unknown"
            for (intf in interfaces.asSequence()) {
                for (addr in intf.inetAddresses.asSequence()) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "unknown"
                    }
                }
            }
        } catch (_: Exception) { }
        return "unknown"
    }
}
