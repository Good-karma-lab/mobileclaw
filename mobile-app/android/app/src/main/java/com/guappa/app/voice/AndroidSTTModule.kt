package com.guappa.app.voice

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

/**
 * Native module wrapping Android's built-in SpeechRecognizer.
 * Provides zero-cost, zero-download STT as a fallback when no cloud API key is configured.
 * Supports offline mode via EXTRA_PREFER_OFFLINE on compatible devices.
 */
class AndroidSTTModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val TAG = "AndroidSTT"
        const val NAME = "AndroidSTT"
    }

    override fun getName() = NAME

    private var recognizer: SpeechRecognizer? = null
    private var isListening = false

    @ReactMethod
    fun isAvailable(promise: Promise) {
        promise.resolve(SpeechRecognizer.isRecognitionAvailable(reactContext))
    }

    @ReactMethod
    fun startListening(options: ReadableMap, promise: Promise) {
        if (isListening) {
            promise.reject("ALREADY_LISTENING", "SpeechRecognizer is already active")
            return
        }

        val activity = reactContext.currentActivity
        if (activity == null) {
            promise.reject("NO_ACTIVITY", "No activity available")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(reactContext)) {
            promise.reject("NOT_AVAILABLE", "Speech recognition not available on this device")
            return
        }

        try {
            activity.runOnUiThread {
                try {
                    recognizer?.destroy()
                    recognizer = SpeechRecognizer.createSpeechRecognizer(reactContext)

                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, options.getString("language") ?: "en-US")
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

                        if (options.hasKey("offline") && options.getBoolean("offline")) {
                            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        }
                    }

                    recognizer?.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            isListening = true
                            sendEvent("androidSTT_ready", Arguments.createMap())
                        }

                        override fun onBeginningOfSpeech() {
                            sendEvent("androidSTT_speechStart", Arguments.createMap())
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            val payload = Arguments.createMap().apply {
                                putDouble("rmsDb", rmsdB.toDouble())
                            }
                            sendEvent("androidSTT_rmsChanged", payload)
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            sendEvent("androidSTT_speechEnd", Arguments.createMap())
                        }

                        override fun onError(error: Int) {
                            isListening = false
                            val errorMsg = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                                SpeechRecognizer.ERROR_SERVER -> "Server error"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                                else -> "Unknown error ($error)"
                            }
                            val payload = Arguments.createMap().apply {
                                putInt("errorCode", error)
                                putString("errorMessage", errorMsg)
                            }
                            sendEvent("androidSTT_error", payload)
                        }

                        override fun onResults(results: Bundle?) {
                            isListening = false
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                            val payload = Arguments.createMap().apply {
                                putString("transcript", matches?.firstOrNull() ?: "")
                                putBoolean("isFinal", true)
                                putDouble("confidence", confidences?.firstOrNull()?.toDouble() ?: 0.0)
                            }
                            sendEvent("androidSTT_result", payload)
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val payload = Arguments.createMap().apply {
                                putString("transcript", matches?.firstOrNull() ?: "")
                                putBoolean("isFinal", false)
                            }
                            sendEvent("androidSTT_result", payload)
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })

                    recognizer?.startListening(intent)
                    promise.resolve(true)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start recognition", e)
                    promise.reject("START_FAILED", e.message, e)
                }
            }
        } catch (e: Exception) {
            promise.reject("START_FAILED", e.message, e)
        }
    }

    @ReactMethod
    fun stopListening(promise: Promise) {
        try {
            recognizer?.stopListening()
            isListening = false
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("STOP_FAILED", e.message, e)
        }
    }

    @ReactMethod
    fun cancel(promise: Promise) {
        try {
            recognizer?.cancel()
            isListening = false
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("CANCEL_FAILED", e.message, e)
        }
    }

    @ReactMethod
    fun destroy(promise: Promise) {
        try {
            recognizer?.destroy()
            recognizer = null
            isListening = false
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("DESTROY_FAILED", e.message, e)
        }
    }

    @ReactMethod
    fun addListener(@Suppress("UNUSED_PARAMETER") eventName: String) {
        // Required for RN event emitter
    }

    @ReactMethod
    fun removeListeners(@Suppress("UNUSED_PARAMETER") count: Int) {
        // Required for RN event emitter
    }

    private fun sendEvent(eventName: String, params: WritableMap) {
        try {
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send event $eventName", e)
        }
    }

    override fun onCatalystInstanceDestroy() {
        recognizer?.destroy()
        recognizer = null
        isListening = false
        super.onCatalystInstanceDestroy()
    }
}
