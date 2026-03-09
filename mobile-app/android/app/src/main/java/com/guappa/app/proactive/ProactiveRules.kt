package com.guappa.app.proactive

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Configurable rules engine for proactive agent behavior.
 *
 * A [ProactiveRule] binds a trigger type to a set of conditions and an action.
 * When a trigger fires, [evaluate] checks all conditions; if all pass, the
 * action is returned for the caller to execute.
 *
 * Built-in default rules are provided for common scenarios (SMS, missed calls,
 * calendar, low battery). Custom rules can be added/removed at runtime.
 */

// ---------------------------------------------------------------------------
// Data model
// ---------------------------------------------------------------------------

data class ProactiveRule(
    val id: String,
    val trigger: TriggerType,
    val conditions: List<Condition>,
    val action: ProactiveAction,
    val enabled: Boolean = true,
    val cooldownMinutes: Int = 5
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("id", id)
        put("trigger", trigger.name)
        put("conditions", JSONArray(conditions.map { it.toJSON() }))
        put("action", action.toJSON())
        put("enabled", enabled)
        put("cooldownMinutes", cooldownMinutes)
    }

    companion object {
        fun fromJSON(json: JSONObject): ProactiveRule = ProactiveRule(
            id = json.getString("id"),
            trigger = TriggerType.valueOf(json.getString("trigger")),
            conditions = parseConditions(json.optJSONArray("conditions")),
            action = ProactiveAction.fromJSON(json.getJSONObject("action")),
            enabled = json.optBoolean("enabled", true),
            cooldownMinutes = json.optInt("cooldownMinutes", 5)
        )

        private fun parseConditions(array: JSONArray?): List<Condition> {
            if (array == null) return emptyList()
            return (0 until array.length()).map { i ->
                Condition.fromJSON(array.getJSONObject(i))
            }
        }
    }
}

sealed class Condition {
    abstract fun toJSON(): JSONObject

    /** Only trigger within a specific hour range. */
    data class TimeWindow(val startHour: Int, val endHour: Int) : Condition() {
        override fun toJSON() = JSONObject().apply {
            put("type", "TimeWindow")
            put("startHour", startHour)
            put("endHour", endHour)
        }
    }

    /** Only trigger on specific days of the week (Calendar.SUNDAY=1 .. Calendar.SATURDAY=7). */
    data class DayOfWeek(val days: Set<Int>) : Condition() {
        override fun toJSON() = JSONObject().apply {
            put("type", "DayOfWeek")
            put("days", JSONArray(days.toList()))
        }
    }

    /** Only trigger when battery is above the given percentage. */
    data class BatteryAbove(val percent: Int) : Condition() {
        override fun toJSON() = JSONObject().apply {
            put("type", "BatteryAbove")
            put("percent", percent)
        }
    }

    /** Only trigger when the app foreground/background state matches. */
    data class AppInBackground(val value: Boolean) : Condition() {
        override fun toJSON() = JSONObject().apply {
            put("type", "AppInBackground")
            put("value", value)
        }
    }

    /** Only trigger when the contact/sender matches the given pattern. */
    data class ContactMatch(val namePattern: String) : Condition() {
        override fun toJSON() = JSONObject().apply {
            put("type", "ContactMatch")
            put("namePattern", namePattern)
        }
    }

    companion object {
        fun fromJSON(json: JSONObject): Condition {
            return when (json.getString("type")) {
                "TimeWindow" -> TimeWindow(
                    startHour = json.getInt("startHour"),
                    endHour = json.getInt("endHour")
                )
                "DayOfWeek" -> {
                    val arr = json.getJSONArray("days")
                    val days = (0 until arr.length()).map { arr.getInt(it) }.toSet()
                    DayOfWeek(days)
                }
                "BatteryAbove" -> BatteryAbove(percent = json.getInt("percent"))
                "AppInBackground" -> AppInBackground(value = json.getBoolean("value"))
                "ContactMatch" -> ContactMatch(namePattern = json.getString("namePattern"))
                else -> throw IllegalArgumentException("Unknown condition type: ${json.getString("type")}")
            }
        }
    }
}

sealed class ProactiveAction {
    abstract fun toJSON(): JSONObject

    /** Send a templated chat message to the agent. */
    data class SendChat(val templateMessage: String) : ProactiveAction() {
        override fun toJSON() = JSONObject().apply {
            put("type", "SendChat")
            put("templateMessage", templateMessage)
        }
    }

    /** Send a notification on a specific channel. */
    data class SendNotification(val channel: String) : ProactiveAction() {
        override fun toJSON() = JSONObject().apply {
            put("type", "SendNotification")
            put("channel", channel)
        }
    }

    /** Run a background task with the given description. */
    data class RunTask(val taskDescription: String) : ProactiveAction() {
        override fun toJSON() = JSONObject().apply {
            put("type", "RunTask")
            put("taskDescription", taskDescription)
        }
    }

    /** Let the agent decide the appropriate action. */
    object AskAgent : ProactiveAction() {
        override fun toJSON() = JSONObject().apply {
            put("type", "AskAgent")
        }
    }

    companion object {
        fun fromJSON(json: JSONObject): ProactiveAction {
            return when (json.getString("type")) {
                "SendChat" -> SendChat(templateMessage = json.getString("templateMessage"))
                "SendNotification" -> SendNotification(channel = json.getString("channel"))
                "RunTask" -> RunTask(taskDescription = json.getString("taskDescription"))
                "AskAgent" -> AskAgent
                else -> throw IllegalArgumentException("Unknown action type: ${json.getString("type")}")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Rules engine
// ---------------------------------------------------------------------------

/**
 * Evaluates proactive rules against the current device/app context.
 */
class ProactiveRulesEngine(private val context: Context) {

    companion object {
        private const val TAG = "ProactiveRulesEngine"
    }

    /**
     * Evaluates whether all conditions of a [rule] are met.
     * If [eventData] is provided, it is used for contact matching.
     *
     * @return true if all conditions pass (or rule has no conditions).
     */
    fun evaluate(rule: ProactiveRule, eventData: Map<String, Any?> = emptyMap()): Boolean {
        if (!rule.enabled) return false
        if (rule.conditions.isEmpty()) return true

        return rule.conditions.all { condition ->
            evaluateCondition(condition, eventData)
        }
    }

    private fun evaluateCondition(condition: Condition, eventData: Map<String, Any?>): Boolean {
        return try {
            when (condition) {
                is Condition.TimeWindow -> evaluateTimeWindow(condition)
                is Condition.DayOfWeek -> evaluateDayOfWeek(condition)
                is Condition.BatteryAbove -> evaluateBatteryAbove(condition)
                is Condition.AppInBackground -> evaluateAppInBackground(condition)
                is Condition.ContactMatch -> evaluateContactMatch(condition, eventData)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to evaluate condition $condition: ${e.message}")
            false
        }
    }

    private fun evaluateTimeWindow(condition: Condition.TimeWindow): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (condition.startHour <= condition.endHour) {
            hour in condition.startHour until condition.endHour
        } else {
            hour >= condition.startHour || hour < condition.endHour
        }
    }

    private fun evaluateDayOfWeek(condition: Condition.DayOfWeek): Boolean {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return today in condition.days
    }

    private fun evaluateBatteryAbove(condition: Condition.BatteryAbove): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return level >= condition.percent
    }

    private fun evaluateAppInBackground(condition: Condition.AppInBackground): Boolean {
        val isBackground = try {
            val lifecycle = ProcessLifecycleOwner.get().lifecycle
            !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        } catch (e: Exception) {
            true // Assume background if we cannot determine
        }
        return isBackground == condition.value
    }

    private fun evaluateContactMatch(
        condition: Condition.ContactMatch,
        eventData: Map<String, Any?>
    ): Boolean {
        val sender = eventData["sender"]?.toString()
            ?: eventData["number"]?.toString()
            ?: return false
        return try {
            Regex(condition.namePattern, RegexOption.IGNORE_CASE).containsMatchIn(sender)
        } catch (e: Exception) {
            sender.contains(condition.namePattern, ignoreCase = true)
        }
    }

    // -----------------------------------------------------------------------
    // Default rules
    // -----------------------------------------------------------------------

    /**
     * Returns the default set of built-in proactive rules.
     */
    fun defaultRules(): List<ProactiveRule> = listOf(
        ProactiveRule(
            id = "default_sms_reply",
            trigger = TriggerType.EVENT_BASED,
            conditions = listOf(
                Condition.TimeWindow(startHour = 7, endHour = 23)
            ),
            action = ProactiveAction.AskAgent,
            cooldownMinutes = 1
        ),
        ProactiveRule(
            id = "default_missed_call",
            trigger = TriggerType.EVENT_BASED,
            conditions = listOf(
                Condition.TimeWindow(startHour = 7, endHour = 23)
            ),
            action = ProactiveAction.SendNotification(channel = NotificationChannels.ALERTS),
            cooldownMinutes = 2
        ),
        ProactiveRule(
            id = "default_calendar_reminder",
            trigger = TriggerType.EVENT_BASED,
            conditions = emptyList(),
            action = ProactiveAction.SendNotification(channel = NotificationChannels.PROACTIVE),
            cooldownMinutes = 10
        ),
        ProactiveRule(
            id = "default_low_battery",
            trigger = TriggerType.CONDITION_BASED,
            conditions = listOf(
                Condition.AppInBackground(value = true)
            ),
            action = ProactiveAction.SendNotification(channel = NotificationChannels.PROACTIVE),
            cooldownMinutes = 60
        ),
        ProactiveRule(
            id = "default_daily_summary",
            trigger = TriggerType.TIME_BASED,
            conditions = listOf(
                Condition.TimeWindow(startHour = 20, endHour = 22),
                Condition.BatteryAbove(percent = 15)
            ),
            action = ProactiveAction.RunTask(taskDescription = "Generate daily summary"),
            cooldownMinutes = 720  // once per 12 hours
        )
    )
}
