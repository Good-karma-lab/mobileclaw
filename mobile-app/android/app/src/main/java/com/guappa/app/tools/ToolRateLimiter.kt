package com.guappa.app.tools

import java.util.concurrent.ConcurrentHashMap

data class RateLimit(
    val perMinute: Int,
    val perHour: Int
)

class ToolRateLimiter {
    private val callTimestamps = ConcurrentHashMap<String, MutableList<Long>>()
    private val concurrentCounts = ConcurrentHashMap<String, Int>()

    private val toolRateLimits = mapOf(
        "web_search" to RateLimit(perMinute = 10, perHour = 50),
        "send_sms" to RateLimit(perMinute = 5, perHour = 20),
        "place_call" to RateLimit(perMinute = 2, perHour = 10),
        "run_shell" to RateLimit(perMinute = 10, perHour = 50),
        "generate_image" to RateLimit(perMinute = 5, perHour = 20)
    )

    private val defaultRateLimit = RateLimit(perMinute = 30, perHour = 200)

    private val maxConcurrent = 5

    fun checkLimit(toolName: String): Boolean {
        val now = System.currentTimeMillis()
        val limit = toolRateLimits[toolName] ?: defaultRateLimit

        val timestamps = callTimestamps.getOrPut(toolName) { mutableListOf() }

        synchronized(timestamps) {
            // Cleanup entries older than 1 hour
            val oneHourAgo = now - 3_600_000L
            timestamps.removeAll { it < oneHourAgo }

            // Check per-hour limit
            if (timestamps.size >= limit.perHour) {
                return false
            }

            // Check per-minute limit
            val oneMinuteAgo = now - 60_000L
            val recentCount = timestamps.count { it >= oneMinuteAgo }
            if (recentCount >= limit.perMinute) {
                return false
            }

            // Check concurrent execution limit
            val currentConcurrent = concurrentCounts.getOrDefault(toolName, 0)
            if (currentConcurrent >= maxConcurrent) {
                return false
            }

            // Record this call
            timestamps.add(now)
            return true
        }
    }

    fun markStarted(toolName: String) {
        concurrentCounts.compute(toolName) { _, current ->
            (current ?: 0) + 1
        }
    }

    fun markCompleted(toolName: String) {
        concurrentCounts.compute(toolName) { _, current ->
            val updated = (current ?: 1) - 1
            if (updated <= 0) null else updated
        }
    }

    fun getRemainingCalls(toolName: String): Pair<Int, Int> {
        val now = System.currentTimeMillis()
        val limit = toolRateLimits[toolName] ?: defaultRateLimit
        val timestamps = callTimestamps[toolName] ?: return Pair(limit.perMinute, limit.perHour)

        synchronized(timestamps) {
            val oneHourAgo = now - 3_600_000L
            val oneMinuteAgo = now - 60_000L

            val hourCount = timestamps.count { it >= oneHourAgo }
            val minuteCount = timestamps.count { it >= oneMinuteAgo }

            return Pair(
                (limit.perMinute - minuteCount).coerceAtLeast(0),
                (limit.perHour - hourCount).coerceAtLeast(0)
            )
        }
    }

    fun cleanup() {
        val oneHourAgo = System.currentTimeMillis() - 3_600_000L
        callTimestamps.forEach { (toolName, timestamps) ->
            synchronized(timestamps) {
                timestamps.removeAll { it < oneHourAgo }
                if (timestamps.isEmpty()) {
                    callTimestamps.remove(toolName)
                }
            }
        }
    }

    fun reset() {
        callTimestamps.clear()
        concurrentCounts.clear()
    }
}
