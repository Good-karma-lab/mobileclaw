package com.guappa.app.proactive

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tracks sent notifications for cooldown logic and history queries.
 *
 * Stores notification records in-memory with SharedPreferences persistence.
 * Each record includes: id, channel, timestamp, type, and dismissed flag.
 *
 * Usage:
 *   val history = NotificationHistory(context)
 *   history.record(notifId = 2001, channelId = "guappa_tasks", type = "task_completed")
 *   history.lastNotificationWithin("guappa_tasks", minutes = 5)  // true/false
 *   history.getRecentByChannel("guappa_tasks", limit = 10)
 */
class NotificationHistory(context: Context) {

    companion object {
        private const val TAG = "NotifHistory"
        private const val PREFS_NAME = "guappa_notification_history"
        private const val KEY_RECORDS = "records"
        private const val MAX_RECORDS = 500
    }

    data class NotificationRecord(
        val id: Int,
        val channelId: String,
        val timestamp: Long,
        val type: String,
        val dismissed: Boolean = false
    ) {
        fun toJSON(): JSONObject = JSONObject().apply {
            put("id", id)
            put("channelId", channelId)
            put("timestamp", timestamp)
            put("type", type)
            put("dismissed", dismissed)
        }

        companion object {
            fun fromJSON(json: JSONObject): NotificationRecord = NotificationRecord(
                id = json.getInt("id"),
                channelId = json.getString("channelId"),
                timestamp = json.getLong("timestamp"),
                type = json.optString("type", ""),
                dismissed = json.optBoolean("dismissed", false)
            )
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val records = mutableListOf<NotificationRecord>()
    private val lock = Any()

    init {
        loadRecords()
    }

    /**
     * Records that a notification was sent.
     */
    fun record(notifId: Int, channelId: String, type: String = "") {
        val entry = NotificationRecord(
            id = notifId,
            channelId = channelId,
            timestamp = System.currentTimeMillis(),
            type = type
        )
        synchronized(lock) {
            records.add(entry)
            // Evict oldest if over capacity
            while (records.size > MAX_RECORDS) {
                records.removeAt(0)
            }
            saveRecords()
        }
        Log.d(TAG, "Recorded notification: id=$notifId, channel=$channelId, type=$type")
    }

    /**
     * Marks a notification as dismissed.
     */
    fun markDismissed(notifId: Int) {
        synchronized(lock) {
            val index = records.indexOfLast { it.id == notifId }
            if (index >= 0) {
                records[index] = records[index].copy(dismissed = true)
                saveRecords()
            }
        }
    }

    /**
     * Returns true if a notification was sent on [channelId] within the last [minutes].
     */
    fun lastNotificationWithin(channelId: String, minutes: Int): Boolean {
        val cutoff = System.currentTimeMillis() - (minutes * 60_000L)
        synchronized(lock) {
            return records.any { it.channelId == channelId && it.timestamp >= cutoff }
        }
    }

    /**
     * Returns the most recent notification records for a specific channel.
     */
    fun getRecentByChannel(channelId: String, limit: Int = 10): List<NotificationRecord> {
        synchronized(lock) {
            return records
                .filter { it.channelId == channelId }
                .sortedByDescending { it.timestamp }
                .take(limit)
        }
    }

    /**
     * Returns the most recent notification records across all channels.
     */
    fun getRecent(limit: Int = 20): List<NotificationRecord> {
        synchronized(lock) {
            return records
                .sortedByDescending { it.timestamp }
                .take(limit)
        }
    }

    /**
     * Returns the timestamp of the last notification on the given channel,
     * or null if no notification has been sent on that channel.
     */
    fun lastNotificationTimestamp(channelId: String): Long? {
        synchronized(lock) {
            return records
                .filter { it.channelId == channelId }
                .maxByOrNull { it.timestamp }
                ?.timestamp
        }
    }

    /**
     * Returns the count of notifications sent on [channelId] today.
     */
    fun todayCountByChannel(channelId: String): Int {
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        synchronized(lock) {
            return records.count { it.channelId == channelId && it.timestamp >= todayStart }
        }
    }

    /**
     * Clears all records.
     */
    fun clear() {
        synchronized(lock) {
            records.clear()
            saveRecords()
        }
    }

    private fun saveRecords() {
        try {
            val array = JSONArray()
            for (record in records) {
                array.put(record.toJSON())
            }
            prefs.edit().putString(KEY_RECORDS, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save notification history", e)
        }
    }

    private fun loadRecords() {
        val json = prefs.getString(KEY_RECORDS, null) ?: return
        try {
            val array = JSONArray(json)
            synchronized(lock) {
                records.clear()
                for (i in 0 until array.length()) {
                    records.add(NotificationRecord.fromJSON(array.getJSONObject(i)))
                }
            }
            Log.d(TAG, "Loaded ${records.size} notification history records")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load notification history", e)
        }
    }
}
