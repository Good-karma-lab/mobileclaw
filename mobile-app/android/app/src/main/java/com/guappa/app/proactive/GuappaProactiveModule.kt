package com.guappa.app.proactive

import android.util.Log
import com.facebook.react.bridge.*
import com.guappa.app.agent.MessageBus
import com.guappa.app.GuappaAgentService
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * React Native bridge module exposing the proactive engine to JavaScript.
 *
 * Canonical native module name: "GuappaProactive"
 *
 * Manages triggers, quiet hours, briefings, and notification history.
 */
class GuappaProactiveModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val TAG = "GuappaProactiveModule"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val engine: ProactiveEngine by lazy {
        val messageBus = GuappaAgentService.messageBus ?: MessageBus()
        ProactiveEngine(reactContext, messageBus)
    }

    private val prefs by lazy {
        reactContext.getSharedPreferences("guappa_proactive", android.content.Context.MODE_PRIVATE)
    }

    override fun getName(): String = "GuappaProactive"

    // ---- Triggers ----

    @ReactMethod
    fun getTriggers(promise: Promise) {
        try {
            val triggers = engine.getTriggers()
            val arr = JSONArray()
            for (trigger in triggers) {
                arr.put(trigger.toJSON())
            }
            promise.resolve(arr.toString())
        } catch (e: Exception) {
            promise.reject("TRIGGER_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun addTrigger(triggerJson: String, promise: Promise) {
        try {
            val json = JSONObject(triggerJson)
            val trigger = ProactiveTrigger(
                id = UUID.randomUUID().toString(),
                type = try {
                    TriggerType.valueOf(json.optString("type", "EVENT_BASED").uppercase())
                } catch (_: Exception) {
                    TriggerType.EVENT_BASED
                },
                name = json.optString("name", "Unnamed Trigger"),
                description = json.optString("description", ""),
                config = json.optJSONObject("config") ?: JSONObject(),
                action = json.optString("action", "notify"),
                enabled = json.optBoolean("enabled", true),
            )
            engine.registerTrigger(trigger)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("TRIGGER_ERROR", "Failed to add trigger: ${e.message}", e)
        }
    }

    @ReactMethod
    fun removeTrigger(triggerId: String, promise: Promise) {
        try {
            engine.removeTrigger(triggerId)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("TRIGGER_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun toggleTrigger(triggerId: String, enabled: Boolean, promise: Promise) {
        try {
            val triggers = engine.getTriggers()
            val trigger = triggers.find { it.id == triggerId }
            if (trigger != null) {
                engine.removeTrigger(triggerId)
                engine.registerTrigger(trigger.copy(enabled = enabled))
                promise.resolve(true)
            } else {
                promise.resolve(false)
            }
        } catch (e: Exception) {
            promise.reject("TRIGGER_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun evaluateTriggers(promise: Promise) {
        try {
            val fired = mutableListOf<String>()
            for (trigger in engine.getEnabledTriggers()) {
                if (engine.evaluateTrigger(trigger.id)) {
                    fired.add(trigger.id)
                }
            }
            promise.resolve(JSONArray(fired).toString())
        } catch (e: Exception) {
            promise.reject("TRIGGER_ERROR", e.message, e)
        }
    }

    // ---- Smart timing ----

    @ReactMethod
    fun setQuietHours(startHour: Int, endHour: Int, promise: Promise) {
        try {
            prefs.edit()
                .putInt("quiet_start", startHour)
                .putInt("quiet_end", endHour)
                .putBoolean("quiet_enabled", true)
                .apply()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("TIMING_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun getQuietHours(promise: Promise) {
        try {
            val json = JSONObject().apply {
                put("startHour", prefs.getInt("quiet_start", 22))
                put("endHour", prefs.getInt("quiet_end", 7))
                put("enabled", prefs.getBoolean("quiet_enabled", false))
            }
            promise.resolve(json.toString())
        } catch (e: Exception) {
            promise.reject("TIMING_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun isInQuietHours(promise: Promise) {
        try {
            val enabled = prefs.getBoolean("quiet_enabled", false)
            if (!enabled) {
                promise.resolve(false)
                return
            }
            val startHour = prefs.getInt("quiet_start", 22)
            val endHour = prefs.getInt("quiet_end", 7)
            val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

            val inQuiet = if (startHour < endHour) {
                currentHour in startHour until endHour
            } else {
                currentHour >= startHour || currentHour < endHour
            }
            promise.resolve(inQuiet)
        } catch (e: Exception) {
            promise.resolve(false)
        }
    }

    @ReactMethod
    fun setCooldown(channelId: String, cooldownMs: Int, promise: Promise) {
        try {
            prefs.edit().putLong("cooldown_$channelId", cooldownMs.toLong()).apply()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("TIMING_ERROR", e.message, e)
        }
    }

    // ---- Briefings ----

    @ReactMethod
    fun setMorningBriefing(enabled: Boolean, hour: Int, minute: Int, promise: Promise) {
        try {
            prefs.edit()
                .putBoolean("morning_enabled", enabled)
                .putInt("morning_hour", hour)
                .putInt("morning_minute", minute)
                .apply()

            if (enabled) {
                MorningBriefingWorker.schedule(reactContext, hour, minute)
            } else {
                MorningBriefingWorker.cancel(reactContext)
            }
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("BRIEFING_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun getMorningBriefingConfig(promise: Promise) {
        try {
            val json = JSONObject().apply {
                put("enabled", prefs.getBoolean("morning_enabled", false))
                put("hour", prefs.getInt("morning_hour", 7))
                put("minute", prefs.getInt("morning_minute", 30))
            }
            promise.resolve(json.toString())
        } catch (e: Exception) {
            promise.reject("BRIEFING_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun setEveningSummary(enabled: Boolean, hour: Int, minute: Int, promise: Promise) {
        try {
            prefs.edit()
                .putBoolean("evening_enabled", enabled)
                .putInt("evening_hour", hour)
                .putInt("evening_minute", minute)
                .apply()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("BRIEFING_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun getEveningSummaryConfig(promise: Promise) {
        try {
            val json = JSONObject().apply {
                put("enabled", prefs.getBoolean("evening_enabled", false))
                put("hour", prefs.getInt("evening_hour", 21))
                put("minute", prefs.getInt("evening_minute", 0))
            }
            promise.resolve(json.toString())
        } catch (e: Exception) {
            promise.reject("BRIEFING_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun generateBriefingNow(promise: Promise) {
        // Briefing generation requires the agent to be running
        promise.resolve("Briefing generation is only available when the agent is active.")
    }

    // ---- Notifications ----

    @ReactMethod
    fun getNotificationHistory(limit: Int, promise: Promise) {
        try {
            val historyJson = prefs.getString("notification_history", "[]") ?: "[]"
            val arr = JSONArray(historyJson)
            val result = JSONArray()
            val max = minOf(limit, arr.length())
            for (i in 0 until max) {
                result.put(arr.getJSONObject(i))
            }
            promise.resolve(result.toString())
        } catch (e: Exception) {
            promise.resolve("[]")
        }
    }

    @ReactMethod
    fun clearNotificationHistory(promise: Promise) {
        try {
            prefs.edit().putString("notification_history", "[]").apply()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("NOTIFICATION_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun setNotificationEnabled(channelId: String, enabled: Boolean, promise: Promise) {
        try {
            prefs.edit().putBoolean("notif_${channelId}_enabled", enabled).apply()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("NOTIFICATION_ERROR", e.message, e)
        }
    }
}
