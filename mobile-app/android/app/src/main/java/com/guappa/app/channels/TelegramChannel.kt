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

class TelegramChannel(
    private val botToken: String,
    private val chatId: String
) : Channel {

    companion object {
        private const val TAG = "TelegramChannel"
    }

    override val id: String = "telegram"
    override val name: String = "Telegram"
    override val isConfigured: Boolean get() = botToken.isNotBlank() && chatId.isNotBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Long-polling client with longer read timeout
    private val pollingClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS) // > long_poll_timeout
        .build()

    private var pollingJob: Job? = null
    private var lastUpdateId: Long = 0
    private val _incoming = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 64)

    override fun incoming(): Flow<IncomingMessage> = _incoming

    override suspend fun startReceiving() {
        if (pollingJob?.isActive == true) return
        if (!isConfigured) return

        pollingJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            Log.i(TAG, "Starting Telegram long polling")
            while (isActive) {
                try {
                    pollUpdates()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Polling error, retrying in 5s", e)
                    delay(5000)
                }
            }
        }
    }

    override suspend fun stopReceiving() {
        pollingJob?.cancel()
        pollingJob = null
        Log.i(TAG, "Stopped Telegram long polling")
    }

    private suspend fun pollUpdates() {
        val url = buildString {
            append("https://api.telegram.org/bot$botToken/getUpdates")
            append("?timeout=30")
            append("&allowed_updates=[\"message\"]")
            if (lastUpdateId > 0) append("&offset=${lastUpdateId + 1}")
        }

        val request = Request.Builder().url(url).build()
        val response = withContext(Dispatchers.IO) {
            pollingClient.newCall(request).execute()
        }

        val body = response.body?.string() ?: return
        val json = JSONObject(body)

        if (!json.optBoolean("ok", false)) return

        val result = json.optJSONArray("result") ?: return
        for (i in 0 until result.length()) {
            val update = result.getJSONObject(i)
            lastUpdateId = update.getLong("update_id")

            val message = update.optJSONObject("message") ?: continue
            val text = message.optString("text", "")
            if (text.isBlank()) continue

            val from = message.optJSONObject("from")
            val senderId = from?.optLong("id", 0)?.toString() ?: "unknown"
            val senderName = buildString {
                append(from?.optString("first_name", "") ?: "")
                val lastName = from?.optString("last_name", "")
                if (!lastName.isNullOrBlank()) append(" $lastName")
            }.ifBlank { "Unknown" }

            val msgChatId = message.optJSONObject("chat")?.optLong("id", 0)?.toString() ?: ""

            Log.d(TAG, "Received message from $senderName: $text")

            _incoming.tryEmit(IncomingMessage(
                channelId = id,
                senderId = senderId,
                senderName = senderName,
                text = text,
                timestamp = (message.optLong("date", 0) * 1000),
                metadata = mapOf("chat_id" to msgChatId)
            ))
        }
    }

    override suspend fun send(message: String, metadata: Map<String, String>?): Boolean =
        withContext(Dispatchers.IO) {
            val targetChatId = metadata?.get("chat_id") ?: chatId
            val body = JSONObject().apply {
                put("chat_id", targetChatId)
                put("text", message)
                put("parse_mode", "Markdown")
            }
            val request = Request.Builder()
                .url("https://api.telegram.org/bot$botToken/sendMessage")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        }

    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.telegram.org/bot$botToken/getMe")
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    override fun reset() {
        runBlocking { stopReceiving() }
        lastUpdateId = 0
    }
}
