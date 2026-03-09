package com.guappa.app.channels

import android.content.Context

object ChannelFactory {

    fun createChannel(type: String, config: Map<String, String>, context: Context? = null): Channel {
        return when (type.lowercase()) {
            "telegram" -> TelegramChannel(
                botToken = config["botToken"] ?: "",
                chatId = config["chatId"] ?: ""
            )
            "discord" -> DiscordChannel(
                webhookUrl = config["webhookUrl"] ?: ""
            )
            "slack" -> SlackChannel(
                webhookUrl = config["webhookUrl"] ?: ""
            )
            "email" -> {
                requireNotNull(context) { "Context is required for EmailChannel" }
                EmailChannel(
                    context = context,
                    defaultRecipient = config["recipient"] ?: ""
                )
            }
            "whatsapp" -> WhatsAppChannel(
                phoneNumberId = config["phoneNumberId"] ?: "",
                accessToken = config["accessToken"] ?: "",
                recipientPhone = config["recipientPhone"] ?: ""
            )
            "signal" -> SignalChannel(
                apiUrl = config["apiUrl"] ?: "",
                senderNumber = config["senderNumber"] ?: "",
                recipientNumber = config["recipientNumber"] ?: ""
            )
            "matrix" -> MatrixChannel(
                homeserverUrl = config["homeserverUrl"] ?: "",
                accessToken = config["accessToken"] ?: "",
                roomId = config["roomId"] ?: ""
            )
            "sms" -> SmsChannel(
                recipientPhone = config["recipientPhone"] ?: ""
            )
            else -> throw IllegalArgumentException("Unknown channel type: $type")
        }
    }

    val supportedTypes: List<String> = listOf(
        "telegram", "discord", "slack", "email",
        "whatsapp", "signal", "matrix", "sms"
    )
}
