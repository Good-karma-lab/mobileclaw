package com.zeroclaw.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class IntegrationsConfigStore(context: Context) {
    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "mobileclaw_integrations_config",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun load(): IntegrationsConfig {
        return IntegrationsConfig(
            telegramEnabled = prefs.getBoolean("telegram_enabled", false),
            telegramBotToken = prefs.getString("telegram_bot_token", "").orEmpty(),
            telegramChatId = prefs.getString("telegram_chat_id", "").orEmpty(),
            discordEnabled = prefs.getBoolean("discord_enabled", false),
            discordBotToken = prefs.getString("discord_bot_token", "").orEmpty(),
            slackEnabled = prefs.getBoolean("slack_enabled", false),
            slackBotToken = prefs.getString("slack_bot_token", "").orEmpty(),
            whatsappEnabled = prefs.getBoolean("whatsapp_enabled", false),
            whatsappAccessToken = prefs.getString("whatsapp_access_token", "").orEmpty(),
            composioEnabled = prefs.getBoolean("composio_enabled", false),
            composioApiKey = prefs.getString("composio_api_key", "").orEmpty()
        )
    }

    fun save(config: IntegrationsConfig) {
        prefs.edit()
            .putBoolean("telegram_enabled", config.telegramEnabled)
            .putString("telegram_bot_token", config.telegramBotToken)
            .putString("telegram_chat_id", config.telegramChatId)
            .putBoolean("discord_enabled", config.discordEnabled)
            .putString("discord_bot_token", config.discordBotToken)
            .putBoolean("slack_enabled", config.slackEnabled)
            .putString("slack_bot_token", config.slackBotToken)
            .putBoolean("whatsapp_enabled", config.whatsappEnabled)
            .putString("whatsapp_access_token", config.whatsappAccessToken)
            .putBoolean("composio_enabled", config.composioEnabled)
            .putString("composio_api_key", config.composioApiKey)
            .apply()
    }
}
