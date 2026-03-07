package com.guappa.app.channels

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DiscordChannel(private val webhookUrl: String) : Channel {
    override val id: String = "discord"
    override val name: String = "Discord"
    override val isConfigured: Boolean get() = webhookUrl.isNotBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    override suspend fun send(message: String, metadata: Map<String, String>?): Boolean =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply { put("content", message) }
            val request = Request.Builder()
                .url(webhookUrl)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().isSuccessful
        }

    override suspend fun healthCheck(): Boolean = isConfigured
}
