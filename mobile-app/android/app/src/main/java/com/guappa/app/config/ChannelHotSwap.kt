package com.guappa.app.config

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Hot-swaps channel connections (Telegram, Discord, Slack) without restarting.
 *
 * On channel config change:
 * 1. Preserves pending outbound messages in a queue
 * 2. Disconnects old channel connections
 * 3. Connects new channels with updated tokens
 * 4. Drains the preserved message queue to new connections
 *
 * Thread-safe: uses a mutex to serialize swap operations.
 */
class ChannelHotSwap(
    private val channelManager: ChannelManagerAdapter? = null
) : ConfigChangeHandler {
    private val TAG = "ChannelHotSwap"
    private val swapMutex = Mutex()

    /**
     * Messages queued during a swap. They will be drained to the new
     * channel connection once the swap completes.
     */
    private val pendingMessages = ConcurrentLinkedQueue<PendingChannelMessage>()

    override val name: String = "ChannelHotSwap"

    override fun handles(section: ConfigSection): Boolean =
        section == ConfigSection.CHANNELS

    override suspend fun onConfigChanged(section: ConfigSection, config: Any) {
        val channelsConfig = config as? ChannelsConfig ?: return
        swapChannels(channelsConfig)
    }

    suspend fun swapChannels(config: ChannelsConfig) = swapMutex.withLock {
        val manager = channelManager ?: run {
            Log.d(TAG, "No channel manager available; skip swap")
            return@withLock
        }

        Log.d(TAG, "Channel hot-swap starting")

        // Determine which channels changed
        val activeChannels = manager.getActiveChannelIds()

        // Telegram
        handleChannelSwap(
            manager = manager,
            channelId = "telegram",
            enabled = config.telegramEnabled,
            token = config.telegramBotToken,
            wasActive = "telegram" in activeChannels,
        )

        // Discord
        handleChannelSwap(
            manager = manager,
            channelId = "discord",
            enabled = config.discordEnabled,
            token = config.discordBotToken,
            wasActive = "discord" in activeChannels,
        )

        // Slack
        handleChannelSwap(
            manager = manager,
            channelId = "slack",
            enabled = config.slackEnabled,
            token = config.slackBotToken,
            wasActive = "slack" in activeChannels,
        )

        // Drain any messages that were queued during the swap
        drainPendingMessages(manager)

        Log.d(TAG, "Channel hot-swap complete")
    }

    private suspend fun handleChannelSwap(
        manager: ChannelManagerAdapter,
        channelId: String,
        enabled: Boolean,
        token: String,
        wasActive: Boolean,
    ) {
        when {
            // Channel newly enabled
            enabled && !wasActive && token.isNotBlank() -> {
                Log.d(TAG, "Connecting channel: $channelId")
                manager.connectChannel(channelId, token)
            }
            // Channel disabled
            !enabled && wasActive -> {
                Log.d(TAG, "Disconnecting channel: $channelId")
                manager.disconnectChannel(channelId)
            }
            // Channel still enabled but token may have changed
            enabled && wasActive && token.isNotBlank() -> {
                Log.d(TAG, "Reconnecting channel with new token: $channelId")
                manager.disconnectChannel(channelId)
                manager.connectChannel(channelId, token)
            }
        }
    }

    /**
     * Queue a message while a swap is in progress.
     * Messages will be drained once the new connection is established.
     */
    fun queueMessage(channelId: String, content: String) {
        pendingMessages.add(PendingChannelMessage(channelId, content))
    }

    private suspend fun drainPendingMessages(manager: ChannelManagerAdapter) {
        var drained = 0
        while (pendingMessages.isNotEmpty()) {
            val message = pendingMessages.poll() ?: break
            try {
                manager.sendMessage(message.channelId, message.content)
                drained++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to drain message to ${message.channelId}: ${e.message}")
                // Re-queue on failure
                pendingMessages.add(message)
                break
            }
        }
        if (drained > 0) {
            Log.d(TAG, "Drained $drained pending messages")
        }
    }
}

data class PendingChannelMessage(
    val channelId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Abstraction over the actual channel manager to keep ChannelHotSwap
 * loosely coupled. Implementors bridge to the real channel system.
 */
interface ChannelManagerAdapter {
    fun getActiveChannelIds(): Set<String>
    suspend fun connectChannel(channelId: String, token: String)
    suspend fun disconnectChannel(channelId: String)
    suspend fun sendMessage(channelId: String, content: String)
}
