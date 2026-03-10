package com.guappa.app.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Handles configuration schema migrations across app versions.
 * Each migration is a version bump with a lambda that transforms stored config.
 */
object ConfigMigrator {
    private const val TAG = "ConfigMigrator"
    private const val CURRENT_CONFIG_VERSION = 2
    private const val PREFS_NAME = "guappa_config_migration"
    private const val KEY_VERSION = "config_schema_version"

    /**
     * Run pending migrations on the config store.
     * Call this once during app startup, before reading config.
     */
    fun migrateIfNeeded(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentVersion = prefs.getInt(KEY_VERSION, 0)

            if (currentVersion >= CURRENT_CONFIG_VERSION) return

            Log.i(TAG, "Migrating config from v$currentVersion to v$CURRENT_CONFIG_VERSION")

            for (version in (currentVersion + 1)..CURRENT_CONFIG_VERSION) {
                migrate(context, version)
            }

            prefs.edit().putInt(KEY_VERSION, CURRENT_CONFIG_VERSION).apply()
            Log.i(TAG, "Config migration complete")
        } catch (e: Exception) {
            Log.e(TAG, "Config migration failed", e)
        }
    }

    private fun migrate(context: Context, toVersion: Int) {
        when (toVersion) {
            1 -> {
                Log.d(TAG, "Migration to v1: initial stamp")
            }
            2 -> {
                Log.d(TAG, "Migration to v2: upgrade Deepgram model reference")
                // nova-2→nova-3 upgrade done in TypeScript code directly
            }
        }
    }
}
