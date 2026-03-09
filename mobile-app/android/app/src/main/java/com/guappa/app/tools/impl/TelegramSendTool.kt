package com.guappa.app.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class TelegramSendTool : Tool {
    override val name = "telegram_send"
    override val description = "Send a Telegram message via deep link to a specific user by phone number or username"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "text": {
                    "type": "string",
                    "description": "Message text to send"
                },
                "phone_number": {
                    "type": "string",
                    "description": "Phone number of the recipient (international format, e.g. +1234567890)"
                },
                "username": {
                    "type": "string",
                    "description": "Telegram username of the recipient (without @)"
                }
            },
            "required": ["text"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val text = params.optString("text", "")
        if (text.isEmpty()) {
            return ToolResult.Error("Message text is required.", "INVALID_PARAMS")
        }

        val phoneNumber = params.optString("phone_number", "")
        val username = params.optString("username", "")
        val encodedText = Uri.encode(text)

        return try {
            val uri = when {
                username.isNotEmpty() -> {
                    Uri.parse("https://t.me/${username}?text=$encodedText")
                }
                phoneNumber.isNotEmpty() -> {
                    val cleanPhone = phoneNumber.replace(Regex("[^+\\d]"), "")
                    Uri.parse("https://t.me/$cleanPhone?text=$encodedText")
                }
                else -> {
                    // Open Telegram share with text, let user pick recipient
                    Uri.parse("tg://msg?text=$encodedText")
                }
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            val target = when {
                username.isNotEmpty() -> "user @$username"
                phoneNumber.isNotEmpty() -> "phone $phoneNumber"
                else -> "recipient chooser"
            }
            ToolResult.Success("Opened Telegram to send message to $target.")
        } catch (e: Exception) {
            ToolResult.Error("Failed to open Telegram: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
