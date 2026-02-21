package com.mobileclaw.app

import android.content.Intent
import android.os.Build
import android.util.Log
import com.facebook.react.bridge.*
import com.mobileclaw.app.BuildConfig

/**
 * React Native bridge module for ZeroClaw daemon control.
 *
 * Exposes methods to React Native for:
 * - Starting/stopping/restarting the daemon service
 * - Checking daemon status
 * - Getting daemon URL (always localhost:8000)
 */
class ZeroClawDaemonModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val TAG = "ZeroClawDaemonModule"

    override fun getName(): String = "ZeroClawDaemon"

    /**
     * Start the ZeroClaw daemon service
     *
     * @param config ReadableMap with optional keys: apiKey, model, telegramToken
     */
    @ReactMethod
    fun startDaemon(config: ReadableMap, promise: Promise) {
        try {
            Log.d(TAG, "startDaemon() called from React Native")

            val intent = Intent(reactContext, ZeroClawDaemonService::class.java)
            intent.action = ZeroClawDaemonService.ACTION_START
            intent.putExtra(ZeroClawDaemonService.EXTRA_API_KEY, config.getString("apiKey") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_MODEL, config.getString("model") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_TELEGRAM_TOKEN, config.getString("telegramToken") ?: "")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                reactContext.startForegroundService(intent)
            } else {
                reactContext.startService(intent)
            }

            promise.resolve(true)
            Log.d(TAG, "Daemon start requested successfully")

        } catch (e: Exception) {
            val errorMsg = "Failed to start daemon: ${e.message}"
            Log.e(TAG, errorMsg, e)
            promise.reject("START_FAILED", errorMsg, e)
        }
    }

    /**
     * Stop the ZeroClaw daemon service
     */
    @ReactMethod
    fun stopDaemon(promise: Promise) {
        try {
            Log.d(TAG, "stopDaemon() called from React Native")

            val intent = Intent(reactContext, ZeroClawDaemonService::class.java)
            intent.action = ZeroClawDaemonService.ACTION_STOP
            reactContext.startService(intent)

            promise.resolve(true)
            Log.d(TAG, "Daemon stop requested successfully")

        } catch (e: Exception) {
            val errorMsg = "Failed to stop daemon: ${e.message}"
            Log.e(TAG, errorMsg, e)
            promise.reject("STOP_FAILED", errorMsg, e)
        }
    }

    /**
     * Restart the ZeroClaw daemon service
     */
    @ReactMethod
    fun restartDaemon(promise: Promise) {
        try {
            Log.d(TAG, "restartDaemon() called from React Native")

            val intent = Intent(reactContext, ZeroClawDaemonService::class.java)
            intent.action = ZeroClawDaemonService.ACTION_RESTART

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                reactContext.startForegroundService(intent)
            } else {
                reactContext.startService(intent)
            }

            promise.resolve(true)
            Log.d(TAG, "Daemon restart requested successfully")

        } catch (e: Exception) {
            val errorMsg = "Failed to restart daemon: ${e.message}"
            Log.e(TAG, errorMsg, e)
            promise.reject("RESTART_FAILED", errorMsg, e)
        }
    }

    /**
     * Check if daemon is currently running
     */
    @ReactMethod
    fun isDaemonRunning(promise: Promise) {
        try {
            val running = ZeroClawDaemonService.isRunning()
            Log.d(TAG, "isDaemonRunning() = $running")
            promise.resolve(running)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking daemon status", e)
            promise.reject("STATUS_CHECK_FAILED", "Failed to check daemon status: ${e.message}", e)
        }
    }

    /**
     * Get the daemon URL (always localhost:8000 for embedded daemon)
     */
    @ReactMethod
    fun getDaemonUrl(promise: Promise) {
        try {
            val url = "http://127.0.0.1:8000"
            Log.d(TAG, "getDaemonUrl() = $url")
            promise.resolve(url)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting daemon URL", e)
            promise.reject("URL_FAILED", "Failed to get daemon URL: ${e.message}", e)
        }
    }

    /**
     * Get comprehensive daemon status
     */
    @ReactMethod
    fun getDaemonStatus(promise: Promise) {
        try {
            val status = Arguments.createMap().apply {
                putBoolean("running", ZeroClawDaemonService.isRunning())
                putString("url", "http://127.0.0.1:8000")
                putString("mode", "embedded")
                putString("version", BuildConfig.VERSION_NAME)
            }

            Log.d(TAG, "getDaemonStatus() = $status")
            promise.resolve(status)

        } catch (e: Exception) {
            Log.e(TAG, "Error getting daemon status", e)
            promise.reject("STATUS_FAILED", "Failed to get daemon status: ${e.message}", e)
        }
    }

    /**
     * Process a message directly through the agent runtime (via JNI)
     *
     * This bypasses HTTP and calls the Rust agent directly.
     */
    @ReactMethod
    fun processMessage(message: String, promise: Promise) {
        try {
            val handle = ZeroClawDaemonService.getAgentHandle()
            if (handle == 0L) {
                promise.reject("AGENT_NOT_RUNNING", "Agent is not running")
                return
            }

            // Process message via JNI
            val response = ZeroClawBackend.processMessage(handle, message)
            promise.resolve(response)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing message", e)
            promise.reject("PROCESS_FAILED", "Failed to process message: ${e.message}", e)
        }
    }

    /**
     * Execute a tool directly (via JNI)
     */
    @ReactMethod
    fun executeTool(toolName: String, paramsJson: String, promise: Promise) {
        try {
            val handle = ZeroClawDaemonService.getAgentHandle()
            if (handle == 0L) {
                promise.reject("AGENT_NOT_RUNNING", "Agent is not running")
                return
            }

            // Execute tool via JNI
            val resultJson = ZeroClawBackend.executeTool(handle, toolName, paramsJson)
            promise.resolve(resultJson)

        } catch (e: Exception) {
            Log.e(TAG, "Error executing tool", e)
            promise.reject("TOOL_FAILED", "Failed to execute tool: ${e.message}", e)
        }
    }

    /**
     * Constants exported to React Native
     */
    override fun getConstants(): MutableMap<String, Any> {
        return mutableMapOf(
            "DEFAULT_URL" to "http://127.0.0.1:8000",
            "DEFAULT_PORT" to 8000,
            "DAEMON_MODE" to "embedded"
        )
    }
}
