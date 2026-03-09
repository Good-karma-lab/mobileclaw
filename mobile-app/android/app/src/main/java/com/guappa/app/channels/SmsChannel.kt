package com.guappa.app.channels

import android.telephony.SmsManager
import android.util.Log

class SmsChannel(
    private val recipientPhone: String
) : Channel {

    override val id: String = "sms"
    override val name: String = "SMS"
    override val isConfigured: Boolean
        get() = recipientPhone.isNotBlank()

    private val TAG = "SmsChannel"

    @Suppress("DEPRECATION")
    override suspend fun send(message: String, metadata: Map<String, String>?): Boolean {
        return try {
            val smsManager = SmsManager.getDefault()
            val recipient = metadata?.get("to") ?: recipientPhone
            val maxSinglePartLength = 160
            if (message.length <= maxSinglePartLength) {
                smsManager.sendTextMessage(recipient, null, message, null, null)
            } else {
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(recipient, null, parts, null, null)
                Log.d(TAG, "Sent multi-part SMS: ${parts.size} parts")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS: ${e.message}")
            false
        }
    }

    @Suppress("DEPRECATION")
    override suspend fun healthCheck(): Boolean {
        return try {
            val smsManager = SmsManager.getDefault()
            smsManager != null
        } catch (e: Exception) {
            Log.e(TAG, "SMS health check failed: ${e.message}")
            false
        }
    }
}
