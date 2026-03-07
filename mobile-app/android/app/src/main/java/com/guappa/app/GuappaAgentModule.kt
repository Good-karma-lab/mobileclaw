package com.guappa.app

import android.content.Intent
import android.os.Build
import android.util.Log
import com.facebook.react.bridge.*
import com.guappa.app.agent.*
import com.guappa.app.providers.*
import kotlinx.coroutines.*

/**
 * React Native bridge module for the pure Kotlin Guappa agent.
 *
 * All LLM interactions go through GuappaOrchestrator → ProviderRouter → real LLM API.
 * No Rust daemon, no JNI, no localhost gateway.
 */
class GuappaAgentModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val TAG = "GuappaAgentModule"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun getName(): String = "GuappaAgent"

    @ReactMethod
    fun startAgent(config: ReadableMap, promise: Promise) {
        try {
            Log.d(TAG, "startAgent() called from React Native")

            val intent = Intent(reactContext, GuappaAgentService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                reactContext.startForegroundService(intent)
            } else {
                reactContext.startService(intent)
            }

            // Give service a moment to initialize, then configure
            scope.launch {
                delay(200)
                val orchestrator = GuappaAgentService.orchestrator
                if (orchestrator != null) {
                    val router = ProviderRouter()

                    val provider = config.getString("provider") ?: "openai"
                    val apiKey = config.getString("apiKey") ?: ""
                    val apiUrl = config.getString("apiUrl") ?: ""
                    val model = config.getString("model") ?: ""

                    if (apiKey.isNotEmpty()) {
                        registerProvider(router, provider, apiKey, apiUrl, model)
                    }

                    orchestrator.configure(router, reactContext)
                    Log.i(TAG, "Orchestrator configured with provider=$provider model=$model")
                }
                promise.resolve(true)
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to start agent: ${e.message}"
            Log.e(TAG, errorMsg, e)
            promise.reject("START_FAILED", errorMsg, e)
        }
    }

    @ReactMethod
    fun sendMessage(text: String, sessionId: String?, promise: Promise) {
        val orchestrator = GuappaAgentService.orchestrator
        if (orchestrator == null) {
            promise.reject("AGENT_NOT_RUNNING", "Agent not started. Call startAgent first.")
            return
        }

        val bus = GuappaAgentService.messageBus
        if (bus == null) {
            promise.reject("AGENT_NOT_RUNNING", "Message bus not available")
            return
        }

        scope.launch {
            try {
                val responseDeferred = CompletableDeferred<String>()

                val collectJob = launch {
                    bus.messages.collect { msg ->
                        if (msg is BusMessage.AgentMessage && msg.isComplete) {
                            responseDeferred.complete(msg.text)
                            cancel()
                        }
                    }
                }

                bus.publish(BusMessage.UserMessage(
                    text = text,
                    sessionId = sessionId ?: ""
                ))

                val response = withTimeout(120_000) {
                    responseDeferred.await()
                }

                collectJob.cancel()
                promise.resolve(response)
            } catch (e: TimeoutCancellationException) {
                promise.reject("TIMEOUT", "Agent response timed out after 120s")
            } catch (e: Exception) {
                promise.reject("SEND_FAILED", "Failed to send message: ${e.message}", e)
            }
        }
    }

    @ReactMethod
    fun isAgentRunning(promise: Promise) {
        promise.resolve(GuappaAgentService.isRunning())
    }

    @ReactMethod
    fun stopAgent(promise: Promise) {
        try {
            val intent = Intent(reactContext, GuappaAgentService::class.java)
            reactContext.stopService(intent)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("STOP_FAILED", "Failed to stop agent: ${e.message}", e)
        }
    }

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
                val deviceInfo = buildString {
                    appendLine("=== Guappa Debug Info ===")
                    appendLine("Timestamp: $timestamp")
                    appendLine("App Version: ${BuildConfig.VERSION_NAME}")
                    appendLine("Build Type: ${BuildConfig.BUILD_TYPE}")
                    appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                    appendLine("ABIs: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
                    val runtime = Runtime.getRuntime()
                    appendLine("RAM Total: ${runtime.totalMemory() / 1024 / 1024}MB")
                    appendLine("RAM Free: ${runtime.freeMemory() / 1024 / 1024}MB")
                    appendLine("Processors: ${runtime.availableProcessors()}")
                    appendLine("Agent Running: ${GuappaAgentService.isRunning()}")
                }
                zos.putNextEntry(java.util.zip.ZipEntry("device-info.txt"))
                zos.write(deviceInfo.toByteArray())
                zos.closeEntry()

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

                try {
                    val process = Runtime.getRuntime()
                        .exec(arrayOf("logcat", "-d", "-t", "500", "--pid=${android.os.Process.myPid()}"))
                    val logcat = process.inputStream.bufferedReader().readText()
                    val safeLogcat = logcat
                        .replace(Regex("(api[_-]?key|token|secret|password)\\s*[:=]\\s*\\S+", RegexOption.IGNORE_CASE), "$1=***REDACTED***")
                    zos.putNextEntry(java.util.zip.ZipEntry("logcat.txt"))
                    zos.write(safeLogcat.toByteArray())
                    zos.closeEntry()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to capture logcat", e)
                }
            }

            promise.resolve(zipFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to collect debug info", e)
            promise.reject("DEBUG_INFO_FAILED", "Failed to collect debug info: ${e.message}", e)
        }
    }

    private fun registerProvider(
        router: ProviderRouter,
        providerName: String,
        apiKey: String,
        apiUrl: String,
        model: String
    ) {
        val provider: Provider = when (providerName) {
            "anthropic" -> AnthropicProvider(apiKey)
            "gemini" -> GoogleGeminiProvider(apiKey)
            else -> OpenAICompatibleProvider(
                id = providerName,
                name = providerName,
                apiKey = apiKey,
                baseUrl = apiUrl.ifEmpty { "https://openrouter.ai/api/v1" }
            )
        }
        router.registerProvider(provider)
    }

    override fun getConstants(): MutableMap<String, Any> {
        return mutableMapOf(
            "AGENT_MODE" to "kotlin"
        )
    }
}
