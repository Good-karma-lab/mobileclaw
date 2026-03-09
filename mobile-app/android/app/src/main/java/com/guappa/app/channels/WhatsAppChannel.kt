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

class WhatsAppChannel(
    private val phoneNumberId: String,
    private val accessToken: String,
    private val recipientPhone: String
) : Channel {

    override val id: String = "whatsapp"
    override val name: String = "WhatsApp"
    override val isConfigured: Boolean
        get() = phoneNumberId.isNotBlank() && accessToken.isNotBlank() && recipientPhone.isNotBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://graph.facebook.com/v18.0"

    override suspend fun send(message: String, metadata: Map<String, String>?): Boolean =
        withContext(Dispatchers.IO) {
            val mediaType = metadata?.get("type")
            val body = when (mediaType) {
                "image" -> buildImagePayload(metadata["url"] ?: "", metadata["caption"] ?: message)
                "document" -> buildDocumentPayload(metadata["url"] ?: "", metadata["filename"] ?: "file", message)
                else -> buildTextPayload(message)
            }
            val request = Request.Builder()
                .url("$baseUrl/$phoneNumberId/messages")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            response.use { it.isSuccessful }
        }

    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/$phoneNumberId")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            val response = client.newCall(request).execute()
            response.use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    private fun buildTextPayload(text: String): JSONObject = JSONObject().apply {
        put("messaging_product", "whatsapp")
        put("to", recipientPhone)
        put("type", "text")
        put("text", JSONObject().apply {
            put("body", text)
        })
    }

    private fun buildImagePayload(url: String, caption: String): JSONObject = JSONObject().apply {
        put("messaging_product", "whatsapp")
        put("to", recipientPhone)
        put("type", "image")
        put("image", JSONObject().apply {
            put("link", url)
            put("caption", caption)
        })
    }

    private fun buildDocumentPayload(url: String, filename: String, caption: String): JSONObject =
        JSONObject().apply {
            put("messaging_product", "whatsapp")
            put("to", recipientPhone)
            put("type", "document")
            put("document", JSONObject().apply {
                put("link", url)
                put("filename", filename)
                put("caption", caption)
            })
        }
}
