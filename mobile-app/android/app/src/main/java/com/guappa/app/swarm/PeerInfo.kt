package com.guappa.app.swarm

import org.json.JSONArray
import org.json.JSONObject

data class PeerInfo(
    val peerId: String,
    val displayName: String,
    val capabilities: List<String>,
    val address: String,
    val lastSeen: Long = System.currentTimeMillis(),
    val latencyMs: Long = -1,
    val isOnline: Boolean = true
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("peer_id", peerId)
        put("display_name", displayName)
        put("capabilities", JSONArray(capabilities))
        put("address", address)
        put("last_seen", lastSeen)
        put("latency_ms", latencyMs)
        put("is_online", isOnline)
    }

    companion object {
        fun fromJSON(json: JSONObject): PeerInfo {
            val caps = mutableListOf<String>()
            val capsArray = json.optJSONArray("capabilities")
            if (capsArray != null) {
                for (i in 0 until capsArray.length()) {
                    caps.add(capsArray.getString(i))
                }
            }
            return PeerInfo(
                peerId = json.getString("peer_id"),
                displayName = json.optString("display_name", "Unknown"),
                capabilities = caps,
                address = json.optString("address", ""),
                lastSeen = json.optLong("last_seen", System.currentTimeMillis()),
                latencyMs = json.optLong("latency_ms", -1),
                isOnline = json.optBoolean("is_online", true)
            )
        }
    }
}
