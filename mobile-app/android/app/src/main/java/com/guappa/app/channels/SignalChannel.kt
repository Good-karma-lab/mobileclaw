package com.guappa.app.channels

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SignalChannel(
    private val apiUrl: String,
    private val senderNumber: String,
    private val recipientNumber: String
) : Channel {

    override val id: String = "signal"
    override val name: String = "Signal"
    override val isConfigured: Boolean
        get() = apiUrl.isNotBlank() && senderNumber.isNotBlank() && recipientNumber.isNotBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun send(message: String, metadata: Map<String, String>?): Boolean =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("message", message)
                put("number", senderNumber)
                put("recipients", JSONArray().apply { put(recipientNumber) })
                val attachmentUrl = metadata?.get("attachment")
                if (attachmentUrl != null) {
                    put("base64_attachments", JSONArray().apply { put(attachmentUrl) })
                }
            }
            val request = Request.Builder()
                .url("${apiUrl.trimEnd('/')}/v2/send")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            response.use { it.isSuccessful }
        }

    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${apiUrl.trimEnd('/')}/v1/about")
                .build()
            val response = client.newCall(request).execute()
            response.use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }
}
