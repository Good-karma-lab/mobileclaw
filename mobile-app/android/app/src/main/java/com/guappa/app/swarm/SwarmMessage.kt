package com.guappa.app.swarm

import org.json.JSONObject

enum class SwarmMessageType {
    TASK_REQUEST,
    TASK_RESPONSE,
    CAPABILITY_ANNOUNCE,
    HEARTBEAT,
    PEER_DISCOVERY,
    HOLON_INVITE,
    HOLON_VOTE,
    BROADCAST
}

data class SwarmMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: SwarmMessageType,
    val fromPeerId: String,
    val toPeerId: String? = null,  // null = broadcast
    val payload: JSONObject = JSONObject(),
    val timestamp: Long = System.currentTimeMillis(),
    val signature: String? = null
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("from_peer_id", fromPeerId)
        toPeerId?.let { put("to_peer_id", it) }
        put("payload", payload)
        put("timestamp", timestamp)
        signature?.let { put("signature", it) }
    }

    companion object {
        fun fromJSON(json: JSONObject): SwarmMessage {
            return SwarmMessage(
                id = json.getString("id"),
                type = SwarmMessageType.valueOf(json.getString("type")),
                fromPeerId = json.getString("from_peer_id"),
                toPeerId = if (json.has("to_peer_id")) json.getString("to_peer_id") else null,
                payload = json.optJSONObject("payload") ?: JSONObject(),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                signature = if (json.has("signature")) json.getString("signature") else null
            )
        }
    }
}
