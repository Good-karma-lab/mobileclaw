package com.guappa.app.proactive

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/**
 * Smart notification timing — prevents notification spam and respects user quiet time.
 *
 * Features:
 * - Quiet hours (configurable, default 22:00–07:00)
 * - System DND detection
 * - Per-channel cooldown throttling
 */
class SmartTiming(
    private var quietHoursStart: Int = 22,
    private var quietHoursEnd: Int = 7
) {
    private val TAG = "SmartTiming"

    /** channelId -> last notification timestamp (epoch ms) */
    private val lastNotificationTime = ConcurrentHashMap<String, Long>()

    /** Default cooldown per channel (ms). Can be overridden via [setCooldown]. */
    private val channelCooldowns = ConcurrentHashMap<String, Long>().apply {
        put(NotificationChannels.CHAT, 0L)             // no throttle for chat
        put(NotificationChannels.TASKS, 30_000L)        // 30s between task updates
        put(NotificationChannels.QUESTIONS, 10_000L)    // 10s between questions
        put(NotificationChannels.ALERTS, 0L)            // never throttle alerts
        put(NotificationChannels.PROACTIVE, 300_000L)   // 5min between proactive
        put(NotificationChannels.SERVICE, 600_000L)     // 10min between service updates
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns true if current time falls within quiet hours.
     *
     * Supports overnight ranges (e.g. 22:00–07:00) and same-day ranges (e.g. 13:00–14:00).
     */
    fun isQuietHours(start: Int = quietHoursStart, end: Int = quietHoursEnd): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (start <= end) {
            // Same-day range, e.g. 09:00–17:00
            hour in start until end
        } else {
            // Overnight range, e.g. 22:00–07:00
            hour >= start || hour < end
        }
    }

    /**
     * Returns true if Android Do Not Disturb is currently active.
     */
    fun isDndActive(context: Context): Boolean {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val filter = nm.currentInterruptionFilter
            filter != NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check DND status: ${e.message}")
            false
        }
    }

    /**
     * Returns true if a notification on [channelId] should be suppressed
     * because it was sent too recently (within [cooldownMs]).
     *
     * If [cooldownMs] is null, the default cooldown for the channel is used.
     */
    fun shouldThrottle(channelId: String, cooldownMs: Long? = null): Boolean {
        val cooldown = cooldownMs ?: channelCooldowns[channelId] ?: 0L
        if (cooldown <= 0L) return false

        val now = System.currentTimeMillis()
        val last = lastNotificationTime[channelId] ?: 0L
        return (now - last) < cooldown
    }

    /**
     * Comprehensive check: should a notification be allowed right now?
     *
     * Returns true if delivery is allowed. Alerts bypass quiet hours and DND.
     */
    fun shouldDeliver(context: Context, channelId: String): Boolean {
        // Alerts always deliver
        if (channelId == NotificationChannels.ALERTS) return true

        // Block during quiet hours (except alerts)
        if (isQuietHours()) {
            Log.d(TAG, "Suppressed $channelId: quiet hours")
            return false
        }

        // Block during DND (except alerts)
        if (isDndActive(context)) {
            Log.d(TAG, "Suppressed $channelId: DND active")
            return false
        }

        // Throttle check
        if (shouldThrottle(channelId)) {
            Log.d(TAG, "Throttled $channelId: cooldown not elapsed")
            return false
        }

        return true
    }

    /**
     * Record that a notification was just sent on [channelId].
     * Must be called after successful delivery for throttle tracking.
     */
    fun recordDelivery(channelId: String) {
        lastNotificationTime[channelId] = System.currentTimeMillis()
    }

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    fun setQuietHours(start: Int, end: Int) {
        quietHoursStart = start.coerceIn(0, 23)
        quietHoursEnd = end.coerceIn(0, 23)
        Log.d(TAG, "Quiet hours set: $quietHoursStart:00 – $quietHoursEnd:00")
    }

    fun setCooldown(channelId: String, cooldownMs: Long) {
        channelCooldowns[channelId] = cooldownMs.coerceAtLeast(0L)
    }

    fun resetThrottles() {
        lastNotificationTime.clear()
    }
}
