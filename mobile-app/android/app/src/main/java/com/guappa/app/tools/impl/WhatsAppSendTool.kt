package com.guappa.app.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class WhatsAppSendTool : Tool {
    override val name = "whatsapp_send"
    override val description = "Send a WhatsApp message via deep link to a specific phone number"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "phone_number": {
                    "type": "string",
                    "description": "Recipient phone number in international format (e.g. +1234567890)"
                },
                "text": {
                    "type": "string",
                    "description": "Message text to send"
                }
            },
            "required": ["phone_number", "text"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val phoneNumber = params.optString("phone_number", "")
        if (phoneNumber.isEmpty()) {
            return ToolResult.Error("Phone number is required.", "INVALID_PARAMS")
        }

        val text = params.optString("text", "")
        if (text.isEmpty()) {
            return ToolResult.Error("Message text is required.", "INVALID_PARAMS")
        }

        return try {
            // Strip non-digit chars except leading +, then remove +
            val cleanPhone = phoneNumber.replace(Regex("[^\\d]"), "")
            val encodedText = Uri.encode(text)

            // Try WhatsApp app Intent first
            val waIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("whatsapp://send?phone=$cleanPhone&text=$encodedText")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val resolved = context.packageManager.resolveActivity(waIntent, 0)
            if (resolved != null) {
                context.startActivity(waIntent)
                ToolResult.Success("Opened WhatsApp to send message to $phoneNumber.")
            } else {
                // Fall back to web API
                val webUri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedText")
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                ToolResult.Success("Opened WhatsApp web link to send message to $phoneNumber.")
            }
        } catch (e: Exception) {
            ToolResult.Error("Failed to send WhatsApp message: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
