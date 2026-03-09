package com.guappa.app.channels

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.UUID

class MatrixChannel(
    private val homeserverUrl: String,
    private val accessToken: String,
    private val roomId: String
) : Channel {

    override val id: String = "matrix"
    override val name: String = "Matrix"
    override val isConfigured: Boolean
        get() = homeserverUrl.isNotBlank() && accessToken.isNotBlank() && roomId.isNotBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun send(message: String, metadata: Map<String, String>?): Boolean =
        withContext(Dispatchers.IO) {
            val txnId = UUID.randomUUID().toString()
            val encodedRoomId = java.net.URLEncoder.encode(roomId, "UTF-8")
            val useHtml = metadata?.get("format") == "html"
            val body = if (useHtml) {
                buildHtmlPayload(message, metadata?.get("formatted_body") ?: message)
            } else {
                buildTextPayload(message)
            }
            val url = "${homeserverUrl.trimEnd('/')}/_matrix/client/v3/rooms/$encodedRoomId/send/m.room.message/$txnId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .put(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            response.use { it.isSuccessful }
        }

    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${homeserverUrl.trimEnd('/')}/_matrix/client/v3/account/whoami"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            val response = client.newCall(request).execute()
            response.use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    private fun buildTextPayload(text: String): JSONObject = JSONObject().apply {
        put("msgtype", "m.text")
        put("body", text)
    }

    private fun buildHtmlPayload(plainBody: String, formattedBody: String): JSONObject =
        JSONObject().apply {
            put("msgtype", "m.text")
            put("body", plainBody)
            put("format", "org.matrix.custom.html")
            put("formatted_body", formattedBody)
        }
}
