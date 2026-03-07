package com.guappa.app.channels

import android.content.Context
import android.content.Intent

class EmailChannel(private val context: Context, private val defaultRecipient: String = "") : Channel {
    override val id: String = "email"
    override val name: String = "Email"
    override val isConfigured: Boolean get() = true

    override suspend fun send(message: String, metadata: Map<String, String>?): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(metadata?.get("to") ?: defaultRecipient))
            putExtra(Intent.EXTRA_SUBJECT, metadata?.get("subject") ?: "Message from GUAPPA")
            putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(Intent.createChooser(intent, "Send email").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) { false }
    }

    override suspend fun healthCheck(): Boolean = true
}
