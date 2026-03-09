package com.guappa.app.config

import android.util.Log
import com.facebook.react.bridge.*
import com.guappa.app.providers.ProviderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ConfigBridge(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val TAG = "ConfigBridge"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs by lazy { SecurePrefs.config(reactContext) }

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
    fun getProviderModels(config: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val providerName = config.getString("provider")?.trim().orEmpty()
                val apiKey = config.getString("apiKey")?.trim().orEmpty()
                val apiUrl = config.getString("apiUrl")?.trim().orEmpty()

                if (providerName.isBlank() || providerName == "local") {
                    promise.resolve(Arguments.createArray())
                    return@launch
                }

                val normalizedProviderName = ProviderFactory.normalizeProviderId(providerName)
                val router = ProviderFactory.createRouter(normalizedProviderName, apiKey, apiUrl)
                val modelArray = Arguments.createArray()
                for (model in router.fetchModels(normalizedProviderName)) {
                    val item = Arguments.createMap()
                    item.putString("id", model.id)
                    item.putString("name", model.name)
                    modelArray.pushMap(item)
                }

                promise.resolve(modelArray)
            } catch (e: Exception) {
                promise.reject("MODELS_ERROR", "Failed to fetch models: ${e.message}", e)
            }
        }
    }

    @ReactMethod
    fun getProviderHealth(config: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val providerName = config.getString("provider")?.trim().orEmpty()
                val apiKey = config.getString("apiKey")?.trim().orEmpty()
                val apiUrl = config.getString("apiUrl")?.trim().orEmpty()

                if (providerName.isBlank() || providerName == "local") {
                    promise.resolve(false)
                    return@launch
                }

                val normalizedProviderName = ProviderFactory.normalizeProviderId(providerName)
                val router = ProviderFactory.createRouter(normalizedProviderName, apiKey, apiUrl)
                promise.resolve(router.healthCheck(normalizedProviderName))
            } catch (e: Exception) {
                promise.reject("HEALTH_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun getSecureString(key: String, promise: Promise) {
        try {
            promise.resolve(prefs.getString(key, null))
        } catch (e: Exception) {
            promise.reject("CONFIG_ERROR", "Failed to get secure value: ${e.message}", e)
        }
    }

    @ReactMethod
    fun setSecureString(key: String, value: String, promise: Promise) {
        try {
            prefs.edit().putString(key, value).apply()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("CONFIG_ERROR", "Failed to set secure value: ${e.message}", e)
        }
    }

    @ReactMethod
    fun removeSecureString(key: String, promise: Promise) {
        try {
            prefs.edit().remove(key).apply()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("CONFIG_ERROR", "Failed to remove secure value: ${e.message}", e)
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
