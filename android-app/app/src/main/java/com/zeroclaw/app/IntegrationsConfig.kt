package com.zeroclaw.app

data class IntegrationsConfig(
    val telegramEnabled: Boolean = false,
    val telegramBotToken: String = "",
    val telegramChatId: String = "",
    val discordEnabled: Boolean = false,
    val discordBotToken: String = "",
    val slackEnabled: Boolean = false,
    val slackBotToken: String = "",
    val whatsappEnabled: Boolean = false,
    val whatsappAccessToken: String = "",
    val composioEnabled: Boolean = false,
    val composioApiKey: String = ""
)
