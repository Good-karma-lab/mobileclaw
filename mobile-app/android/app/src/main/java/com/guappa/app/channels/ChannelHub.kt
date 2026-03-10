package com.guappa.app.channels

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.math.pow

class ChannelHub {
    private val TAG = "ChannelHub"
    private val channels = ConcurrentHashMap<String, Channel>()
    private val channelHealth = ConcurrentHashMap<String, Boolean>()
    private val allowlists = ConcurrentHashMap<String, Set<String>>()
    private val reconnectAttempts = ConcurrentHashMap<String, Int>()

    private var healthCheckJob: Job? = null
    private val healthCheckIntervalMs = 5 * 60 * 1000L // 5 minutes
    private val maxReconnectAttempts = 8
    private val baseBackoffMs = 1000L
    private val maxBackoffMs = 5 * 60 * 1000L // 5 minutes cap

    fun registerChannel(channel: Channel) {
        channels[channel.id] = channel
        channelHealth[channel.id] = false
        reconnectAttempts[channel.id] = 0
        Log.d(TAG, "Registered channel: ${channel.id}")
    }

    fun getChannel(id: String): Channel? = channels[id]

    fun getConfiguredChannels(): List<Channel> =
        channels.values.filter { it.isConfigured }

    fun listChannels(): List<Channel> = channels.values.toList()

    fun getChannelHealth(id: String): Boolean = channelHealth[id] ?: false

    fun getAllHealth(): Map<String, Boolean> = channelHealth.toMap()

    // --- Allowlist enforcement (deny-by-default) ---

    fun setAllowlist(channelId: String, allowedSenders: Set<String>) {
        allowlists[channelId] = allowedSenders
        Log.d(TAG, "Allowlist set for $channelId: ${allowedSenders.size} entries")
    }

    fun clearAllowlist(channelId: String) {
        allowlists.remove(channelId)
        Log.d(TAG, "Allowlist cleared for $channelId")
    }

    fun isAllowed(channelId: String, senderId: String): Boolean {
        val allowlist = allowlists[channelId]
        // Deny-by-default: if an allowlist is set, only listed senders are permitted
        if (allowlist != null) {
            return senderId in allowlist
        }
        // No allowlist configured means channel is unrestricted
        return true
    }

    // --- Broadcast with allowlist check ---

    suspend fun broadcast(message: String, channelIds: List<String>? = null) {
        val targets = if (channelIds != null) {
            channelIds.mapNotNull { channels[it] }
        } else {
            getConfiguredChannels()
        }

        for (channel in targets) {
            try {
                val formatted = ChannelFormatter.format(channel.id, message)
                channel.send(formatted)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send to ${channel.id}: ${e.message}")
            }
        }
    }

    // --- Health check polling ---

    fun startHealthCheckPolling(scope: CoroutineScope) {
        stopHealthCheckPolling()
        healthCheckJob = scope.launch {
            Log.d(TAG, "Health check polling started (interval: ${healthCheckIntervalMs}ms)")
            while (isActive) {
                checkAllChannelsHealth()
                delay(healthCheckIntervalMs)
            }
        }
    }

    fun stopHealthCheckPolling() {
        healthCheckJob?.cancel()
        healthCheckJob = null
        Log.d(TAG, "Health check polling stopped")
    }

    suspend fun checkAllChannelsHealth() {
        for (channel in getConfiguredChannels()) {
            try {
                val healthy = channel.healthCheck()
                val wasHealthy = channelHealth[channel.id]
                channelHealth[channel.id] = healthy

                if (healthy) {
                    reconnectAttempts[channel.id] = 0
                    if (wasHealthy == false) {
                        Log.i(TAG, "Channel ${channel.id} recovered")
                    }
                } else {
                    Log.w(TAG, "Channel ${channel.id} health check failed")
                    attemptReconnect(channel)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Health check error for ${channel.id}: ${e.message}")
                channelHealth[channel.id] = false
                attemptReconnect(channel)
            }
        }
    }

    // --- Auto-reconnect with exponential backoff ---

    private suspend fun attemptReconnect(channel: Channel) {
        val attempts = reconnectAttempts.getOrDefault(channel.id, 0)
        if (attempts >= maxReconnectAttempts) {
            Log.e(TAG, "Channel ${channel.id} exceeded max reconnect attempts ($maxReconnectAttempts), giving up")
            return
        }

        val backoffMs = min(baseBackoffMs * 2.0.pow(attempts).toLong(), maxBackoffMs)
        reconnectAttempts[channel.id] = attempts + 1
        Log.d(TAG, "Reconnect attempt ${attempts + 1}/$maxReconnectAttempts for ${channel.id} in ${backoffMs}ms")

        delay(backoffMs)

        try {
            val healthy = channel.healthCheck()
            channelHealth[channel.id] = healthy
            if (healthy) {
                reconnectAttempts[channel.id] = 0
                Log.i(TAG, "Channel ${channel.id} reconnected successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Reconnect failed for ${channel.id}: ${e.message}")
        }
    }

    fun resetReconnectAttempts(channelId: String) {
        reconnectAttempts[channelId] = 0
    }
}
