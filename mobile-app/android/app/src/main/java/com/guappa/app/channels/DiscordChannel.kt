package com.guappa.app.channels

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * DiscordChannel — send via webhook, receive via Bot Gateway WebSocket.
 *
 * Outbound: POST to webhook URL.
 * Inbound: connects to `wss://gateway.discord.gg/?v=10&encoding=json`,
 *   authenticates with bot token, listens for MESSAGE_CREATE events.
 */
class DiscordChannel(
    private val webhookUrl: String,
    private val botToken: String = "",
    private val listenChannelId: String = ""
) : Channel {
    override val id: String = "discord"
    override val name: String = "Discord"
    override val isConfigured: Boolean get() = webhookUrl.isNotBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _incoming = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 64)
    private var gatewayJob: Job? = null
    private var heartbeatJob: Job? = null
    private var gatewayWs: WebSocket? = null
    private var lastSequence: Int? = null

    // ── Outbound (webhook) ──

    override suspend fun send(message: String, metadata: Map<String, String>?): Boolean =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply { put("content", message) }
            val request = Request.Builder()
                .url(webhookUrl)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().isSuccessful
        }

    // ── Inbound (gateway) ──

    override fun incoming(): Flow<IncomingMessage> = _incoming

    fun startGateway(scope: CoroutineScope) {
        if (botToken.isBlank()) {
            Log.w(TAG, "No bot token — gateway disabled")
            return
        }
        gatewayJob?.cancel()
        gatewayJob = scope.launch(Dispatchers.IO) {
            connectGateway()
        }
    }

    fun stopGateway() {
        heartbeatJob?.cancel()
        gatewayJob?.cancel()
        gatewayWs?.close(1000, "shutdown")
        gatewayWs = null
    }

    private fun connectGateway() {
        val request = Request.Builder()
            .url(GATEWAY_URL)
            .build()

        gatewayWs = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleGatewayMessage(webSocket, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Gateway failure: ${t.message}")
                // Reconnect after delay
                gatewayJob?.let { job ->
                    if (job.isActive) {
                        Thread.sleep(5000)
                        connectGateway()
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Gateway closed: $code $reason")
            }
        })
    }

    private fun handleGatewayMessage(ws: WebSocket, text: String) {
        try {
            val json = JSONObject(text)
            val op = json.optInt("op")
            val seq = json.opt("s")
            if (seq != null && seq != JSONObject.NULL) {
                lastSequence = seq as? Int
            }

            when (op) {
                10 -> { // Hello — start heartbeating
                    val interval = json.getJSONObject("d").getLong("heartbeat_interval")
                    startHeartbeat(ws, interval)
                    // Send Identify
                    sendIdentify(ws)
                }
                11 -> { // Heartbeat ACK
                    // OK
                }
                0 -> { // Dispatch
                    val eventName = json.optString("t")
                    if (eventName == "MESSAGE_CREATE") {
                        val d = json.getJSONObject("d")
                        val channelId = d.optString("channel_id")
                        val content = d.optString("content")
                        val author = d.optJSONObject("author")
                        val authorName = author?.optString("username") ?: "unknown"
                        val isBot = author?.optBoolean("bot") ?: false

                        // Skip bot messages and filter to listen channel
                        if (!isBot && (listenChannelId.isBlank() || channelId == listenChannelId)) {
                            _incoming.tryEmit(
                                IncomingMessage(
                                    senderId = author?.optString("id") ?: "",
                                    senderName = authorName,
                                    text = content,
                                    timestamp = System.currentTimeMillis(),
                                    channelId = id,
                                    metadata = mapOf("discord_channel_id" to channelId)
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse gateway message", e)
        }
    }

    private fun sendIdentify(ws: WebSocket) {
        val identify = JSONObject().apply {
            put("op", 2)
            put("d", JSONObject().apply {
                put("token", botToken)
                put("intents", 512 + 32768) // GUILD_MESSAGES + MESSAGE_CONTENT
                put("properties", JSONObject().apply {
                    put("os", "android")
                    put("browser", "guappa")
                    put("device", "guappa")
                })
            })
        }
        ws.send(identify.toString())
    }

    private fun startHeartbeat(ws: WebSocket, intervalMs: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val hb = JSONObject().apply {
                    put("op", 1)
                    put("d", lastSequence ?: JSONObject.NULL)
                }
                ws.send(hb.toString())
                delay(intervalMs)
            }
        }
    }

    override suspend fun healthCheck(): Boolean = isConfigured

    companion object {
        private const val TAG = "DiscordChannel"
        private const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"
    }
}
