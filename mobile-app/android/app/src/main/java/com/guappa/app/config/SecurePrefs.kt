package com.guappa.app.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {
    const val AGENT_CONFIG_JSON_KEY = "agent_config_json"
    const val INTEGRATIONS_CONFIG_JSON_KEY = "integrations_config_json"

    private const val CONFIG_PREFS = "guappa_config_secure"
    private const val RUNTIME_PREFS = "guappa_runtime_bridge_secure"

    fun config(context: Context): SharedPreferences {
        return securePrefs(context, CONFIG_PREFS)
    }

    fun runtime(context: Context): SharedPreferences {
        return securePrefs(context, RUNTIME_PREFS)
    }

    private fun securePrefs(context: Context, name: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
