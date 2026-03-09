package com.guappa.app.channels

import android.util.Log
import com.facebook.react.bridge.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * React Native bridge module exposing the channel hub to JavaScript.
 *
 * Canonical native module name: "GuappaChannels"
 *
 * Provides channel listing, configuration, health checks,
 * message sending, broadcast, and allowlist management.
 */
class GuappaChannelsModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val TAG = "GuappaChannelsModule"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val channelHub = ChannelHub()
    private val prefs by lazy {
        reactContext.getSharedPreferences("guappa_channels", android.content.Context.MODE_PRIVATE)
    }

    init {
        loadConfiguredChannels()
    }

    override fun getName(): String = "GuappaChannels"

    /**
     * Load previously configured channels from SharedPreferences.
     */
    private fun loadConfiguredChannels() {
        for (type in ChannelFactory.supportedTypes) {
            val configJson = prefs.getString("channel_$type", null) ?: continue
            try {
                val json = JSONObject(configJson)
                val configMap = mutableMapOf<String, String>()
                for (key in json.keys()) {
                    configMap[key] = json.optString(key, "")
                }
                val channel = ChannelFactory.createChannel(type, configMap, reactContext)
                channelHub.registerChannel(channel)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load channel $type: ${e.message}")
            }
        }
    }

    @ReactMethod
    fun listChannels(promise: Promise) {
        try {
            val arr = JSONArray()
            // List all supported channel types, marking which are configured
            for (type in ChannelFactory.supportedTypes) {
                val channel = channelHub.getChannel(type)
                arr.put(JSONObject().apply {
                    put("id", type)
                    put("name", getChannelDisplayName(type))
                    put("isConfigured", channel?.isConfigured ?: false)
                    put("isConnected", channelHub.getChannelHealth(type))
                    put("lastHealthCheck", JSONObject.NULL)
                    put("healthStatus", if (channelHub.getChannelHealth(type)) "healthy" else "unknown")
                })
            }
            promise.resolve(arr.toString())
        } catch (e: Exception) {
            promise.reject("CHANNEL_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun configureChannel(channelId: String, config: String, promise: Promise) {
        try {
            val json = JSONObject(config)
            val configMap = mutableMapOf<String, String>()
            for (key in json.keys()) {
                configMap[key] = json.optString(key, "")
            }

            val channel = ChannelFactory.createChannel(channelId, configMap, reactContext)
            channelHub.registerChannel(channel)

            // Persist config
            prefs.edit().putString("channel_$channelId", config).apply()

            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("CHANNEL_ERROR", "Failed to configure channel: ${e.message}", e)
        }
    }

    @ReactMethod
    fun removeChannel(channelId: String, promise: Promise) {
        try {
            prefs.edit().remove("channel_$channelId").apply()
            // Re-register with empty config to show as unconfigured
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("CHANNEL_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun testChannel(channelId: String, promise: Promise) {
        scope.launch {
            try {
                val channel = channelHub.getChannel(channelId)
                if (channel == null || !channel.isConfigured) {
                    val result = JSONObject().apply {
                        put("channelId", channelId)
                        put("healthy", false)
                        put("latencyMs", 0)
                        put("error", if (channel == null) "Channel not found" else "Channel not configured")
                    }
                    promise.resolve(result.toString())
                    return@launch
                }

                val startTime = System.currentTimeMillis()
                val healthy = channel.healthCheck()
                val latencyMs = System.currentTimeMillis() - startTime

                val result = JSONObject().apply {
                    put("channelId", channelId)
                    put("healthy", healthy)
                    put("latencyMs", latencyMs)
                    if (!healthy) put("error", "Health check failed")
                }
                promise.resolve(result.toString())
            } catch (e: Exception) {
                val result = JSONObject().apply {
                    put("channelId", channelId)
                    put("healthy", false)
                    put("latencyMs", 0)
                    put("error", e.message ?: "Unknown error")
                }
                promise.resolve(result.toString())
            }
        }
    }

    @ReactMethod
    fun sendMessage(channelId: String, message: String, promise: Promise) {
        scope.launch {
            try {
                val channel = channelHub.getChannel(channelId)
                if (channel == null || !channel.isConfigured) {
                    promise.reject("CHANNEL_ERROR", "Channel '$channelId' not configured")
                    return@launch
                }
                channel.send(message)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("SEND_ERROR", "Failed to send: ${e.message}", e)
            }
        }
    }

    @ReactMethod
    fun broadcastMessage(message: String, promise: Promise) {
        scope.launch {
            try {
                val sent = mutableListOf<String>()
                val failed = JSONArray()

                for (channel in channelHub.getConfiguredChannels()) {
                    try {
                        channel.send(message)
                        sent.add(channel.id)
                    } catch (e: Exception) {
                        failed.put(JSONObject().apply {
                            put("channelId", channel.id)
                            put("error", e.message ?: "Unknown error")
                        })
                    }
                }

                val result = JSONObject().apply {
                    put("sent", JSONArray(sent))
                    put("failed", failed)
                }
                promise.resolve(result.toString())
            } catch (e: Exception) {
                promise.reject("BROADCAST_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun getChannelStatus(channelId: String, promise: Promise) {
        try {
            val channel = channelHub.getChannel(channelId)
            val result = JSONObject().apply {
                put("id", channelId)
                put("name", getChannelDisplayName(channelId))
                put("isConfigured", channel?.isConfigured ?: false)
                put("isConnected", channelHub.getChannelHealth(channelId))
                put("lastHealthCheck", JSONObject.NULL)
                put("healthStatus", if (channelHub.getChannelHealth(channelId)) "healthy" else "unknown")
            }
            promise.resolve(result.toString())
        } catch (e: Exception) {
            promise.reject("CHANNEL_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun setAllowlist(channelId: String, allowedIds: String, promise: Promise) {
        try {
            val arr = JSONArray(allowedIds)
            val idSet = (0 until arr.length()).map { arr.getString(it) }.toSet()
            channelHub.setAllowlist(channelId, idSet)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("CHANNEL_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun getAllowlist(channelId: String, promise: Promise) {
        promise.resolve("[]")
    }

    private fun getChannelDisplayName(type: String): String = when (type) {
        "telegram" -> "Telegram"
        "discord" -> "Discord"
        "slack" -> "Slack"
        "email" -> "Email"
        "whatsapp" -> "WhatsApp"
        "signal" -> "Signal"
        "matrix" -> "Matrix"
        "sms" -> "SMS"
        else -> type.replaceFirstChar { it.uppercase() }
    }
}
