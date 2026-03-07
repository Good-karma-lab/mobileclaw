package com.guappa.app.tools.impl

import android.content.Context
import android.telephony.SmsManager
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class SendSmsTool : Tool {
    override val name = "send_sms"
    override val description = "Send an SMS text message to a phone number"
    override val requiredPermissions = listOf("SEND_SMS")
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "phone_number": {
                    "type": "string",
                    "description": "The recipient phone number"
                },
                "message": {
                    "type": "string",
                    "description": "The text message to send"
                }
            },
            "required": ["phone_number", "message"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val phoneNumber = params.optString("phone_number", "")
        val message = params.optString("message", "")

        if (phoneNumber.isEmpty()) {
            return ToolResult.Error("Phone number is required.", "INVALID_PARAMS")
        }
        if (message.isEmpty()) {
            return ToolResult.Error("Message text is required.", "INVALID_PARAMS")
        }

        return try {
            @Suppress("DEPRECATION")
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            ToolResult.Success("SMS sent to $phoneNumber (${message.length} chars)")
        } catch (e: Exception) {
            ToolResult.Error("Failed to send SMS: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
