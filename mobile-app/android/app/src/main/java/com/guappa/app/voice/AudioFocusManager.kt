package com.guappa.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * AudioFocusManager — manages audio focus for voice interactions.
 *
 * Requests transient focus during STT/TTS, handles ducking,
 * and releases focus when done.
 */
class AudioFocusManager(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
                hasFocus = true
                onFocusGained?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus lost permanently")
                hasFocus = false
                onFocusLost?.invoke(true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost transiently")
                hasFocus = false
                onFocusLost?.invoke(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus: should duck")
                onDuck?.invoke()
            }
        }
    }

    var onFocusGained: (() -> Unit)? = null
    var onFocusLost: ((Boolean) -> Unit)? = null
    var onDuck: (() -> Unit)? = null

    /**
     * Request audio focus for voice interaction (STT or TTS).
     * Uses AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE to pause other audio.
     */
    fun requestFocus(forTTS: Boolean = false): Boolean {
        val attrs = AudioAttributes.Builder()
            .setUsage(
                if (forTTS) AudioAttributes.USAGE_ASSISTANT
                else AudioAttributes.USAGE_VOICE_COMMUNICATION
            )
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()

        focusRequest = request

        val result = audioManager.requestAudioFocus(request)
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(TAG, "requestFocus(forTTS=$forTTS) → granted=$hasFocus")
        return hasFocus
    }

    /**
     * Release audio focus after voice interaction completes.
     */
    fun releaseFocus() {
        focusRequest?.let { req ->
            audioManager.abandonAudioFocusRequest(req)
            Log.d(TAG, "Audio focus released")
        }
        focusRequest = null
        hasFocus = false
    }

    fun hasFocus(): Boolean = hasFocus

    companion object {
        private const val TAG = "AudioFocusManager"
    }
}
