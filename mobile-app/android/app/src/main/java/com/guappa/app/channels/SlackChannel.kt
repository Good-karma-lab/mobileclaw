package com.guappa.app.channels

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * SlackChannel — send via webhook, receive via Slack Web API (conversations.history polling).
 *
 * Outbound: POST to webhook URL.
 * Inbound: polls `conversations.history` with a Bot token to fetch new messages.
 */
class SlackChannel(
    private val webhookUrl: String,
    private val botToken: String = "",
    private val channelId: String = ""
) : Channel {
    override val id: String = "slack"
    override val name: String = "Slack"
    override val isConfigured: Boolean get() = webhookUrl.isNotBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _incoming = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 64)
    private var pollingJob: Job? = null
    private var lastTimestamp: String = "0"

    // ── Outbound (webhook) ──

    override suspend fun send(message: String, metadata: Map<String, String>?): Boolean =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply { put("text", message) }
            val request = Request.Builder()
                .url(webhookUrl)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().isSuccessful
        }

    // ── Inbound (polling conversations.history) ──

    override fun incoming(): Flow<IncomingMessage> = _incoming

    fun startPolling(scope: CoroutineScope, intervalMs: Long = 5000L) {
        if (botToken.isBlank() || channelId.isBlank()) {
            Log.w(TAG, "No bot token or channel ID — polling disabled")
            return
        }
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    pollMessages()
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error: ${e.message}")
                }
                delay(intervalMs)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
    }

    private fun pollMessages() {
        val url = "$API_BASE/conversations.history?channel=$channelId&oldest=$lastTimestamp&limit=20"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $botToken")
            .get()
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return
        val json = JSONObject(body)
        if (!json.optBoolean("ok")) return

        val messages = json.optJSONArray("messages") ?: return
        for (i in 0 until messages.length()) {
            val msg = messages.getJSONObject(i)
            val ts = msg.optString("ts", "0")
            val text = msg.optString("text", "")
            val user = msg.optString("user", "")
            val subtype = msg.optString("subtype", "")

            // Skip bot messages and subtypes
            if (subtype.isNotEmpty() || user.isBlank()) continue

            if (ts > lastTimestamp) {
                lastTimestamp = ts
                _incoming.tryEmit(
                    IncomingMessage(
                        senderId = user,
                        senderName = user, // would need users.info for display name
                        text = text,
                        timestamp = (ts.toDoubleOrNull()?.times(1000))?.toLong() ?: System.currentTimeMillis(),
                        channelId = id,
                        metadata = mapOf("slack_channel" to channelId, "slack_ts" to ts)
                    )
                )
            }
        }
    }

    override suspend fun healthCheck(): Boolean = isConfigured

    companion object {
        private const val TAG = "SlackChannel"
        private const val API_BASE = "https://slack.com/api"
    }
}
