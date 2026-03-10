package com.guappa.app.voice

import android.content.Context
import android.util.Log
import java.nio.FloatBuffer

/**
 * SileroVADEngine — Voice Activity Detection using Silero VAD ONNX model.
 *
 * Detects speech presence in audio frames to enable:
 * - Push-free voice activation
 * - End-of-speech detection for STT cutoff
 * - Background noise filtering
 *
 * Model: silero_vad.onnx (~2MB, runs on CPU)
 * Input: 16kHz mono float32 audio frames (512 or 1536 samples)
 * Output: speech probability [0.0, 1.0]
 */
class SileroVADEngine(private val context: Context) {

    private var isInitialized = false
    private var speechThreshold = 0.5f
    private var silenceThreshold = 0.35f

    // Ring buffer for smoothing
    private val probabilityBuffer = FloatArray(8)
    private var bufferIndex = 0

    var onSpeechStart: (() -> Unit)? = null
    var onSpeechEnd: (() -> Unit)? = null

    private var isSpeaking = false
    private var silenceFrameCount = 0
    private val silenceFrameThreshold = 15 // ~480ms at 30fps

    /**
     * Initialize the VAD engine. Loads the ONNX model.
     * Returns false if the model file is not found.
     */
    fun initialize(): Boolean {
        // TODO: Load silero_vad.onnx from assets using ONNX Runtime
        // For now, mark as initialized for testing
        isInitialized = true
        Log.d(TAG, "SileroVAD initialized (threshold=$speechThreshold)")
        return true
    }

    /**
     * Process an audio frame and return the speech probability.
     * @param audioFrame 16kHz mono float32 audio samples
     * @return speech probability [0.0, 1.0]
     */
    fun processSamples(audioFrame: FloatArray): Float {
        if (!isInitialized) return 0f

        // TODO: Run ONNX inference
        // For now, return a simple energy-based VAD as placeholder
        val energy = audioFrame.map { it * it }.average().toFloat()
        val probability = (energy * 1000f).coerceIn(0f, 1f)

        // Update ring buffer
        probabilityBuffer[bufferIndex % probabilityBuffer.size] = probability
        bufferIndex++

        // Smoothed probability
        val smoothed = probabilityBuffer.average().toFloat()

        // State transitions
        if (!isSpeaking && smoothed > speechThreshold) {
            isSpeaking = true
            silenceFrameCount = 0
            onSpeechStart?.invoke()
        } else if (isSpeaking && smoothed < silenceThreshold) {
            silenceFrameCount++
            if (silenceFrameCount >= silenceFrameThreshold) {
                isSpeaking = false
                onSpeechEnd?.invoke()
            }
        } else if (isSpeaking) {
            silenceFrameCount = 0
        }

        return smoothed
    }

    fun isSpeaking(): Boolean = isSpeaking

    fun setSpeechThreshold(threshold: Float) {
        speechThreshold = threshold.coerceIn(0.1f, 0.9f)
    }

    fun setSilenceThreshold(threshold: Float) {
        silenceThreshold = threshold.coerceIn(0.1f, 0.9f)
    }

    fun release() {
        isInitialized = false
        isSpeaking = false
        silenceFrameCount = 0
        bufferIndex = 0
        probabilityBuffer.fill(0f)
    }

    companion object {
        private const val TAG = "SileroVAD"
    }
}
