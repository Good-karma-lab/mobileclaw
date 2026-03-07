package com.guappa.app.channels

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class ChannelHub {
    private val TAG = "ChannelHub"
    private val channels = ConcurrentHashMap<String, Channel>()

    fun registerChannel(channel: Channel) {
        channels[channel.id] = channel
        Log.d(TAG, "Registered channel: ${channel.id}")
    }

    fun getChannel(id: String): Channel? = channels[id]

    fun getConfiguredChannels(): List<Channel> =
        channels.values.filter { it.isConfigured }

    fun listChannels(): List<Channel> = channels.values.toList()

    suspend fun broadcast(message: String, channelIds: List<String>? = null) {
        val targets = if (channelIds != null) {
            channelIds.mapNotNull { channels[it] }
        } else {
            getConfiguredChannels()
        }

        for (channel in targets) {
            try {
                channel.send(message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send to ${channel.id}: ${e.message}")
            }
        }
    }
}
