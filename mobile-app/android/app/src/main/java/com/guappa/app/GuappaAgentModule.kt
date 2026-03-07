package com.guappa.app

import android.content.Intent
import android.os.Build
import android.util.Log
import com.facebook.react.bridge.*
import com.guappa.app.BuildConfig

/**
 * React Native bridge module for ZeroClaw daemon control.
 *
 * Exposes methods to React Native for:
 * - Starting/stopping/restarting the daemon service
 * - Checking daemon status
 * - Getting daemon URL (always localhost:8000)
 */
class GuappaAgentModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val TAG = "GuappaAgentModule"

    override fun getName(): String = "GuappaAgent"

    /**
     * Start the ZeroClaw daemon service
     *
     * @param config ReadableMap with optional keys: apiKey, model, telegramToken, telegramChatId,
     *               discordBotToken, slackBotToken, composioApiKey
     */
    @ReactMethod
    fun startDaemon(config: ReadableMap, promise: Promise) {
        try {
            Log.d(TAG, "startDaemon() called from React Native")

            val intent = Intent(reactContext, ZeroClawDaemonService::class.java)
            intent.action = ZeroClawDaemonService.ACTION_START
            intent.putExtra(ZeroClawDaemonService.EXTRA_API_KEY, config.getString("apiKey") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_PROVIDER, config.getString("provider") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_MODEL, config.getString("model") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_API_URL, config.getString("apiUrl") ?: "")
            intent.putExtra(
                ZeroClawDaemonService.EXTRA_TEMPERATURE,
                if (config.hasKey("temperature")) config.getDouble("temperature") else 0.1,
            )
            intent.putExtra(ZeroClawDaemonService.EXTRA_TELEGRAM_TOKEN, config.getString("telegramToken") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_TELEGRAM_CHAT_ID, config.getString("telegramChatId") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_DISCORD_BOT_TOKEN, config.getString("discordBotToken") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_SLACK_BOT_TOKEN, config.getString("slackBotToken") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_COMPOSIO_API_KEY, config.getString("composioApiKey") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_BRAVE_API_KEY, config.getString("braveApiKey") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_LOCAL_MODEL_PATH, config.getString("localModelPath") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_THINKING_MODE, if (config.hasKey("thinkingMode") && config.getBoolean("thinkingMode")) "true" else "false")

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
     * Restart daemon with explicit config payload.
     *
     * This is used after UI settings changes so the runtime always
     * restarts with fresh provider/channel credentials.
     */
    @ReactMethod
    fun restartDaemonWithConfig(config: ReadableMap, promise: Promise) {
        try {
            Log.d(TAG, "restartDaemonWithConfig() called from React Native")

            val intent = Intent(reactContext, ZeroClawDaemonService::class.java)
            intent.action = ZeroClawDaemonService.ACTION_RESTART
            intent.putExtra(ZeroClawDaemonService.EXTRA_API_KEY, config.getString("apiKey") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_PROVIDER, config.getString("provider") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_MODEL, config.getString("model") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_API_URL, config.getString("apiUrl") ?: "")
            intent.putExtra(
                ZeroClawDaemonService.EXTRA_TEMPERATURE,
                if (config.hasKey("temperature")) config.getDouble("temperature") else 0.1,
            )
            intent.putExtra(ZeroClawDaemonService.EXTRA_TELEGRAM_TOKEN, config.getString("telegramToken") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_TELEGRAM_CHAT_ID, config.getString("telegramChatId") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_DISCORD_BOT_TOKEN, config.getString("discordBotToken") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_SLACK_BOT_TOKEN, config.getString("slackBotToken") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_COMPOSIO_API_KEY, config.getString("composioApiKey") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_BRAVE_API_KEY, config.getString("braveApiKey") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_LOCAL_MODEL_PATH, config.getString("localModelPath") ?: "")
            intent.putExtra(ZeroClawDaemonService.EXTRA_THINKING_MODE, if (config.hasKey("thinkingMode") && config.getBoolean("thinkingMode")) "true" else "false")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                reactContext.startForegroundService(intent)
            } else {
                reactContext.startService(intent)
            }

            promise.resolve(true)
            Log.d(TAG, "Daemon restart with config requested successfully")
        } catch (e: Exception) {
            val errorMsg = "Failed to restart daemon with config: ${e.message}"
            Log.e(TAG, errorMsg, e)
            promise.reject("RESTART_WITH_CONFIG_FAILED", errorMsg, e)
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
     * Collect debug info, package as ZIP, return file path.
     * API keys are redacted with ***REDACTED***.
     */
    @ReactMethod
    fun collectDebugInfo(promise: Promise) {
        try {
            Log.d(TAG, "collectDebugInfo() called")
            val context = reactContext.applicationContext
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd-HHmmss", java.util.Locale.US)
                .format(java.util.Date())
            val zipName = "guappa-debug-$timestamp.zip"
            val cacheDir = context.cacheDir
            val zipFile = java.io.File(cacheDir, zipName)

            java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zos ->
                // 1. Device & app info
                val deviceInfo = buildString {
                    appendLine("=== Guappa Debug Info ===")
                    appendLine("Timestamp: $timestamp")
                    appendLine("App Version: ${BuildConfig.VERSION_NAME}")
                    appendLine("Build Type: ${BuildConfig.BUILD_TYPE}")
                    appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                    appendLine("SoC: ${android.os.Build.HARDWARE}")
                    appendLine("ABIs: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
                    val runtime = Runtime.getRuntime()
                    appendLine("RAM Total: ${runtime.totalMemory() / 1024 / 1024}MB")
                    appendLine("RAM Free: ${runtime.freeMemory() / 1024 / 1024}MB")
                    appendLine("RAM Max: ${runtime.maxMemory() / 1024 / 1024}MB")
                    appendLine("Processors: ${runtime.availableProcessors()}")
                }
                zos.putNextEntry(java.util.zip.ZipEntry("device-info.txt"))
                zos.write(deviceInfo.toByteArray())
                zos.closeEntry()

                // 2. Config snapshot (API keys redacted)
                val prefs = context.getSharedPreferences("guappa_config", android.content.Context.MODE_PRIVATE)
                val configSnapshot = buildString {
                    appendLine("=== Config Snapshot (Redacted) ===")
                    for ((key, value) in prefs.all) {
                        val safeValue = when {
                            key.contains("key", ignoreCase = true) ||
                            key.contains("token", ignoreCase = true) ||
                            key.contains("secret", ignoreCase = true) ||
                            key.contains("password", ignoreCase = true) -> "***REDACTED***"
                            else -> value?.toString() ?: "null"
                        }
                        appendLine("$key = $safeValue")
                    }
                }
                zos.putNextEntry(java.util.zip.ZipEntry("config-redacted.txt"))
                zos.write(configSnapshot.toByteArray())
                zos.closeEntry()

                // 3. Provider health status
                val healthInfo = buildString {
                    appendLine("=== Provider Health ===")
                    appendLine("Daemon running: ${ZeroClawDaemonService.isRunning()}")
                    appendLine("Daemon URL: http://127.0.0.1:8000")
                    try {
                        val url = java.net.URL("http://127.0.0.1:8000/health")
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 2000
                        conn.readTimeout = 2000
                        appendLine("Health endpoint: ${conn.responseCode}")
                        conn.disconnect()
                    } catch (e: Exception) {
                        appendLine("Health endpoint: unreachable (${e.message})")
                    }
                }
                zos.putNextEntry(java.util.zip.ZipEntry("provider-health.txt"))
                zos.write(healthInfo.toByteArray())
                zos.closeEntry()

                // 4. Logcat (last 500 lines, filtered to app)
                try {
                    val process = Runtime.getRuntime()
                        .exec(arrayOf("logcat", "-d", "-t", "500", "--pid=${android.os.Process.myPid()}"))
                    val logcat = process.inputStream.bufferedReader().readText()
                    // Redact any API keys that might appear in logs
                    val safeLogcat = logcat
                        .replace(Regex("(api[_-]?key|token|secret|password)\\s*[:=]\\s*\\S+", RegexOption.IGNORE_CASE), "$1=***REDACTED***")
                    zos.putNextEntry(java.util.zip.ZipEntry("logcat.txt"))
                    zos.write(safeLogcat.toByteArray())
                    zos.closeEntry()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to capture logcat", e)
                }

                // 5. Permissions status
                val permInfo = buildString {
                    appendLine("=== Android Permissions ===")
                    val perms = arrayOf(
                        "RECORD_AUDIO", "CAMERA", "READ_CONTACTS", "READ_CALENDAR",
                        "ACCESS_FINE_LOCATION", "CALL_PHONE", "SEND_SMS", "READ_SMS",
                        "READ_EXTERNAL_STORAGE", "POST_NOTIFICATIONS", "READ_PHONE_STATE"
                    )
                    for (perm in perms) {
                        val fullPerm = "android.permission.$perm"
                        val granted = context.checkSelfPermission(fullPerm) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        appendLine("$perm: ${if (granted) "GRANTED" else "DENIED"}")
                    }
                }
                zos.putNextEntry(java.util.zip.ZipEntry("permissions.txt"))
                zos.write(permInfo.toByteArray())
                zos.closeEntry()
            }

            Log.d(TAG, "Debug info written to ${zipFile.absolutePath}")
            promise.resolve(zipFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to collect debug info", e)
            promise.reject("DEBUG_INFO_FAILED", "Failed to collect debug info: ${e.message}", e)
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
