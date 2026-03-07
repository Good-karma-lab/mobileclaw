package com.guappa.app.channels

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    override val id: String = "telegram"
    override val name: String = "Telegram"
    override val isConfigured: Boolean get() = botToken.isNotBlank() && chatId.isNotBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun send(message: String, metadata: Map<String, String>?): Boolean =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("chat_id", chatId)
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
}
