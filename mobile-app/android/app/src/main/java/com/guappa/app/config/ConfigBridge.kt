package com.guappa.app.config

import android.content.Context
import android.util.Log
import com.facebook.react.bridge.*
import com.guappa.app.agent.GuappaConfig
import com.guappa.app.providers.ProviderRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ConfigBridge(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val TAG = "ConfigBridge"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs by lazy {
        reactContext.getSharedPreferences("guappa_config", Context.MODE_PRIVATE)
    }

    override fun getName(): String = "GuappaConfig"

    @ReactMethod
    fun getConfig(promise: Promise) {
        try {
            val map = Arguments.createMap()
            for ((key, value) in prefs.all) {
                when (value) {
                    is String -> map.putString(key, value)
                    is Boolean -> map.putBoolean(key, value)
                    is Int -> map.putInt(key, value)
                    is Float -> map.putDouble(key, value.toDouble())
                    is Long -> map.putDouble(key, value.toDouble())
                    else -> map.putString(key, value?.toString())
                }
            }
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("CONFIG_ERROR", "Failed to get config: ${e.message}", e)
        }
    }

    @ReactMethod
    fun updateConfig(key: String, value: String, promise: Promise) {
        try {
            prefs.edit().putString(key, value).apply()
            Log.d(TAG, "Config updated: $key")
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("CONFIG_ERROR", "Failed to update config: ${e.message}", e)
        }
    }

    @ReactMethod
    fun getProviderModels(providerId: String, promise: Promise) {
        scope.launch {
            try {
                val models = Arguments.createArray()
                // Provider models will be fetched via ProviderRouter when wired
                promise.resolve(models)
            } catch (e: Exception) {
                promise.reject("MODELS_ERROR", "Failed to fetch models: ${e.message}", e)
            }
        }
    }

    @ReactMethod
    fun getProviderHealth(providerId: String, promise: Promise) {
        scope.launch {
            try {
                // Will be wired to ProviderRouter health check
                promise.resolve(false)
            } catch (e: Exception) {
                promise.reject("HEALTH_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun reloadRuntime(promise: Promise) {
        try {
            Log.d(TAG, "Runtime reload requested")
            // Signal config change to agent components via broadcast
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("RELOAD_ERROR", "Failed to reload: ${e.message}", e)
        }
    }
}
