package com.guappa.app.proactive

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.guappa.app.agent.BusMessage
import com.guappa.app.agent.MessageBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class ProactiveEngine(
    private val context: Context,
    private val messageBus: MessageBus
) {
    private val TAG = "ProactiveEngine"
    private val triggers = ConcurrentHashMap<String, ProactiveTrigger>()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("guappa_triggers", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        loadTriggers()
    }

    fun registerTrigger(trigger: ProactiveTrigger) {
        triggers[trigger.id] = trigger
        saveTriggers()
        Log.d(TAG, "Registered trigger: ${trigger.name}")
    }

    fun removeTrigger(id: String) {
        triggers.remove(id)
        saveTriggers()
    }

    fun getTriggers(): List<ProactiveTrigger> = triggers.values.toList()

    fun getEnabledTriggers(): List<ProactiveTrigger> =
        triggers.values.filter { it.enabled }

    fun evaluateTrigger(triggerId: String, eventData: JSONObject? = null): Boolean {
        val trigger = triggers[triggerId] ?: return false
        if (!trigger.enabled) return false

        return when (trigger.type) {
            TriggerType.EVENT_BASED -> {
                val expectedEvent = trigger.config.optString("event_type", "")
                val actualEvent = eventData?.optString("event_type", "") ?: ""
                expectedEvent == actualEvent
            }
            TriggerType.CONDITION_BASED -> {
                val condition = trigger.config.optString("condition", "")
                val value = eventData?.optString("value", "") ?: ""
                when (condition) {
                    "battery_low" -> (value.toIntOrNull() ?: 100) < 20
                    "wifi_connected" -> value == "true"
                    else -> false
                }
            }
            else -> false
        }
    }

    fun onTriggerFired(trigger: ProactiveTrigger) {
        scope.launch {
            Log.d(TAG, "Trigger fired: ${trigger.name}, action: ${trigger.action}")
            messageBus.publish(
                BusMessage.TriggerEvent(
                    trigger = trigger.name,
                    data = mapOf(
                        "trigger_id" to trigger.id,
                        "trigger_name" to trigger.name,
                        "action" to trigger.action
                    )
                )
            )
        }
    }

    fun handleEvent(eventType: String, eventData: JSONObject? = null) {
        for (trigger in getEnabledTriggers()) {
            if (trigger.type == TriggerType.EVENT_BASED) {
                val data = (eventData ?: JSONObject()).apply {
                    put("event_type", eventType)
                }
                if (evaluateTrigger(trigger.id, data)) {
                    onTriggerFired(trigger)
                }
            }
        }
    }

    private fun saveTriggers() {
        val array = JSONArray()
        for (trigger in triggers.values) {
            array.put(trigger.toJSON())
        }
        prefs.edit().putString("triggers", array.toString()).apply()
    }

    private fun loadTriggers() {
        val json = prefs.getString("triggers", null) ?: return
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val trigger = ProactiveTrigger.fromJSON(array.getJSONObject(i))
                triggers[trigger.id] = trigger
            }
            Log.d(TAG, "Loaded ${triggers.size} triggers")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load triggers", e)
        }
    }
}
