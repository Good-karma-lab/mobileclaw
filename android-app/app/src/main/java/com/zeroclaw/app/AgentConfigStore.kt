package com.zeroclaw.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AgentConfigStore(context: Context) {
    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "mobileclaw_agent_config",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun load(): AgentRuntimeConfig {
        return AgentRuntimeConfig(
            provider = prefs.getString("provider", "ollama").orEmpty().ifBlank { "ollama" },
            model = prefs.getString("model", "gpt-oss:20b").orEmpty().ifBlank { "gpt-oss:20b" },
            apiUrl = prefs.getString("api_url", "http://10.0.2.2:11434").orEmpty()
                .ifBlank { "http://10.0.2.2:11434" },
            apiKey = prefs.getString("api_key", "").orEmpty(),
            authMode = prefs.getString("auth_mode", "api_key").orEmpty().ifBlank { "api_key" },
            oauthAccessToken = prefs.getString("oauth_access_token", "").orEmpty(),
            oauthRefreshToken = prefs.getString("oauth_refresh_token", "").orEmpty(),
            oauthExpiresAtMs = prefs.getLong("oauth_expires_at_ms", 0L),
            accountId = prefs.getString("account_id", "").orEmpty(),
            enterpriseUrl = prefs.getString("enterprise_url", "").orEmpty(),
            temperature = prefs.getFloat("temperature", 0.1f).toDouble()
        )
    }

    fun save(config: AgentRuntimeConfig) {
        prefs.edit()
            .putString("provider", config.provider)
            .putString("model", config.model)
            .putString("api_url", config.apiUrl)
            .putString("api_key", config.apiKey)
            .putString("auth_mode", config.authMode)
            .putString("oauth_access_token", config.oauthAccessToken)
            .putString("oauth_refresh_token", config.oauthRefreshToken)
            .putLong("oauth_expires_at_ms", config.oauthExpiresAtMs)
            .putString("account_id", config.accountId)
            .putString("enterprise_url", config.enterpriseUrl)
            .putFloat("temperature", config.temperature.toFloat())
            .apply()
    }
}
