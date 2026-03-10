package com.guappa.app.channels

import android.content.Context
import android.content.Intent
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
 * EmailChannel — sends email via SMTP relay API, falls back to Intent.
 *
 * Outbound: POST to a configurable SMTP relay endpoint (e.g., SendGrid, Mailgun)
 *           or falls back to Android Intent.ACTION_SEND
 * Inbound:  Not yet implemented (would need IMAP or webhook)
 */
class EmailChannel(
    private val context: Context,
    private val defaultRecipient: String = "",
    private val smtpRelayUrl: String = "",
    private val smtpRelayApiKey: String = "",
    private val fromAddress: String = ""
) : Channel {
    override val id: String = "email"
    override val name: String = "Email"
    override val isConfigured: Boolean get() = true

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _incoming = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 64)

    override suspend fun send(message: String, metadata: Map<String, String>?): Boolean {
        val to = metadata?.get("to") ?: defaultRecipient
        val subject = metadata?.get("subject") ?: "Message from GUAPPA"

        return if (smtpRelayUrl.isNotBlank() && smtpRelayApiKey.isNotBlank()) {
            sendViaRelay(to, subject, message)
        } else {
            sendViaIntent(to, subject, message)
        }
    }

    private suspend fun sendViaRelay(to: String, subject: String, body: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("from", fromAddress.ifBlank { "guappa@local" })
                    put("to", to)
                    put("subject", subject)
                    put("text", body)
                }
                val request = Request.Builder()
                    .url(smtpRelayUrl)
                    .header("Authorization", "Bearer $smtpRelayApiKey")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                val response = client.newCall(request).execute()
                val success = response.isSuccessful
                Log.d(TAG, "Email relay send to=$to success=$success")
                success
            } catch (e: Exception) {
                Log.e(TAG, "Relay send failed: ${e.message}")
                false
            }
        }

    private fun sendViaIntent(to: String, subject: String, body: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(
                Intent.createChooser(intent, "Send email")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Intent send failed: ${e.message}")
            false
        }
    }

    override fun incoming(): Flow<IncomingMessage> = _incoming

    override suspend fun healthCheck(): Boolean = isConfigured

    companion object {
        private const val TAG = "EmailChannel"
    }
}
