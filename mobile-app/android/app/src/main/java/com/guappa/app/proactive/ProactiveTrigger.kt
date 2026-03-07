package com.guappa.app.proactive

import org.json.JSONObject

enum class TriggerType {
    TIME_BASED,
    EVENT_BASED,
    LOCATION_BASED,
    CONDITION_BASED
}

data class ProactiveTrigger(
    val id: String,
    val type: TriggerType,
    val name: String,
    val description: String,
    val config: JSONObject,
    val action: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("name", name)
        put("description", description)
        put("config", config)
        put("action", action)
        put("enabled", enabled)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJSON(json: JSONObject): ProactiveTrigger = ProactiveTrigger(
            id = json.getString("id"),
            type = TriggerType.valueOf(json.getString("type")),
            name = json.getString("name"),
            description = json.optString("description", ""),
            config = json.optJSONObject("config") ?: JSONObject(),
            action = json.getString("action"),
            enabled = json.optBoolean("enabled", true),
            createdAt = json.optLong("createdAt", System.currentTimeMillis())
        )
    }
}
