package com.guappa.app.config

import android.util.Log

/**
 * Hot-swaps voice engine configuration without restarting the agent service.
 * Handles STT engine switching (Deepgram ↔ Whisper ↔ Android built-in)
 * and TTS engine switching (Android built-in ↔ Deepgram Aura-2).
 */
class VoiceHotSwap {
    companion object {
        private const val TAG = "VoiceHotSwap"
    }

    /**
     * Called when voice configuration changes.
     * The actual engine switching happens on the TypeScript side via voiceEngineManager.
     * This Kotlin side handles cleanup of any native resources.
     */
    fun onSTTEngineChanged(newEngine: String) {
        Log.i(TAG, "STT engine changed to: $newEngine")
        // Android SpeechRecognizer instances are managed by AndroidSTTModule
        // Whisper models managed by whisper.rn JS layer
        // Deepgram WebSocket managed by useVoiceRecording hook
    }

    fun onTTSEngineChanged(newEngine: String) {
        Log.i(TAG, "TTS engine changed to: $newEngine")
        // expo-speech (Android TextToSpeech) handled by React Native
        // Deepgram Aura-2 handled by deepgramTTS.ts
    }

    fun onVoiceModelChanged(modelId: String) {
        Log.i(TAG, "Voice model changed to: $modelId")
    }
}
