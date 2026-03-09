package com.guappa.app

import android.content.Intent
import android.os.Build
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.guappa.app.agent.*
import com.guappa.app.config.SecurePrefs
import com.guappa.app.providers.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.merge
import org.json.JSONObject

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
    private var eventRelayJob: Job? = null

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

            scope.launch {
                val orchestrator = waitForOrchestrator()
                    ?: throw IllegalStateException("GuappaAgentService did not initialize in time")

                val provider = config.getString("provider") ?: "openai"
                val apiKey = config.getString("apiKey") ?: ""
                val apiUrl = config.getString("apiUrl") ?: ""
                val model = config.getString("model") ?: ""

                val router = ProviderFactory.createRouter(provider, apiKey, apiUrl)

                orchestrator.configure(
                    router = router,
                    ctx = reactContext,
                    model = model,
                    temperature = config.getDouble("temperature")
                )
                ensureEventRelay()
                Log.i(TAG, "Orchestrator configured with provider=$provider model=$model")
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
                ensureEventRelay()
                val responseDeferred = CompletableDeferred<String>()
                val effectiveSessionId = orchestrator.resolveSessionId(sessionId)

                val collectJob = launch {
                    bus.messages.collect { msg ->
                        if (
                            msg is BusMessage.AgentMessage &&
                            msg.isComplete &&
                            msg.sessionId == effectiveSessionId &&
                            !responseDeferred.isCompleted
                        ) {
                            responseDeferred.complete(msg.text)
                            cancel()
                        }
                    }
                }

                bus.publish(BusMessage.UserMessage(
                    text = text,
                    sessionId = effectiveSessionId
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
    fun sendMessageStream(text: String, sessionId: String?, promise: Promise) {
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
                ensureEventRelay()
                val effectiveSessionId = orchestrator.resolveSessionId(sessionId)
                bus.publish(BusMessage.UserMessage(text = text, sessionId = effectiveSessionId))
                promise.resolve(effectiveSessionId)
            } catch (e: Exception) {
                promise.reject("SEND_FAILED", "Failed to send message: ${e.message}", e)
            }
        }
    }

    @ReactMethod
    fun addListener(eventName: String) {
        ensureEventRelay()
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        // Required for React Native event emitters.
    }

    @ReactMethod
    fun isAgentRunning(promise: Promise) {
        promise.resolve(GuappaAgentService.isRunning())
    }

    @ReactMethod
    fun stopAgent(promise: Promise) {
        try {
            eventRelayJob?.cancel()
            eventRelayJob = null
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

                val prefs = SecurePrefs.config(context)
                val configSnapshot = buildString {
                    appendLine("=== Config Snapshot (Redacted) ===")
                    for ((key, value) in prefs.all) {
                        val safeValue = when {
                            key == SecurePrefs.AGENT_CONFIG_JSON_KEY ||
                                key == SecurePrefs.INTEGRATIONS_CONFIG_JSON_KEY -> "***REDACTED***"
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

    private suspend fun waitForOrchestrator(timeoutMs: Long = 5_000L): GuappaOrchestrator? {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            GuappaAgentService.orchestrator?.let { return it }
            delay(100)
        }
        return GuappaAgentService.orchestrator
    }

    override fun getConstants(): MutableMap<String, Any> {
        return mutableMapOf(
            "AGENT_MODE" to "kotlin"
        )
    }

    private fun ensureEventRelay() {
        if (eventRelayJob?.isActive == true) {
            return
        }

        val bus = GuappaAgentService.messageBus ?: return
        eventRelayJob = scope.launch {
            merge(bus.messages, bus.urgentMessages).collect { message ->
                when (message) {
                    is BusMessage.AgentMessage -> emitAgentEvent(message)
                    is BusMessage.SystemEvent -> emitSystemEvent(message)
                    else -> Unit
                }
            }
        }
    }

    private fun emitAgentEvent(message: BusMessage.AgentMessage) {
        val payload = Arguments.createMap().apply {
            putString("type", if (message.isComplete) "agent_complete" else "agent_chunk")
            putString("sessionId", message.sessionId)
            putString("text", message.text)
            putBoolean("isStreaming", message.isStreaming)
            putBoolean("isComplete", message.isComplete)
        }
        sendEvent("guappa_agent_event", payload)
    }

    private fun emitSystemEvent(message: BusMessage.SystemEvent) {
        val payload = Arguments.createMap().apply {
            putString("type", if (message.type == "tool_executed") "tool_event" else "system_event")
            putString("eventType", message.type)
            putString("sessionId", message.data["session_id"]?.toString() ?: "")
            putString("tool", message.data["tool"]?.toString() ?: "")
            putString("detail", JSONObject(message.data).toString())
            putBoolean("success", message.data["success"] as? Boolean ?: false)
        }
        sendEvent("guappa_agent_event", payload)
    }

    private fun sendEvent(eventName: String, payload: WritableMap) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, payload)
    }
}
