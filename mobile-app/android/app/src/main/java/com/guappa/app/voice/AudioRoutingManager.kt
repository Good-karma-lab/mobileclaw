package com.guappa.app.voice

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * AudioRoutingManager — routes audio to speaker, earpiece, or Bluetooth.
 *
 * Manages SCO connection for Bluetooth headset audio,
 * speaker/earpiece switching, and device enumeration.
 */
class AudioRoutingManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    enum class AudioRoute {
        SPEAKER,
        EARPIECE,
        BLUETOOTH_SCO,
        WIRED_HEADSET,
        AUTO
    }

    private var currentRoute = AudioRoute.AUTO
    private var scoActive = false

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                    when (state) {
                        AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                            Log.d(TAG, "SCO connected")
                            scoActive = true
                            onRouteChanged?.invoke(AudioRoute.BLUETOOTH_SCO)
                        }
                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                            Log.d(TAG, "SCO disconnected")
                            scoActive = false
                            onRouteChanged?.invoke(currentRoute)
                        }
                    }
                }
            }
        }
    }

    var onRouteChanged: ((AudioRoute) -> Unit)? = null

    fun init() {
        val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        context.registerReceiver(btReceiver, filter)
    }

    fun destroy() {
        try {
            context.unregisterReceiver(btReceiver)
        } catch (_: Exception) {}
        if (scoActive) stopBluetoothSco()
    }

    /**
     * Set the audio output route.
     */
    fun setRoute(route: AudioRoute) {
        currentRoute = route
        when (route) {
            AudioRoute.SPEAKER -> {
                stopBluetoothSco()
                audioManager.isSpeakerphoneOn = true
                audioManager.mode = AudioManager.MODE_NORMAL
            }
            AudioRoute.EARPIECE -> {
                stopBluetoothSco()
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            }
            AudioRoute.BLUETOOTH_SCO -> {
                audioManager.isSpeakerphoneOn = false
                startBluetoothSco()
            }
            AudioRoute.WIRED_HEADSET -> {
                stopBluetoothSco()
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = AudioManager.MODE_NORMAL
            }
            AudioRoute.AUTO -> {
                // Auto-detect best route
                when {
                    isBluetoothHeadsetConnected() -> setRoute(AudioRoute.BLUETOOTH_SCO)
                    isWiredHeadsetConnected() -> setRoute(AudioRoute.WIRED_HEADSET)
                    else -> setRoute(AudioRoute.SPEAKER)
                }
            }
        }
        Log.d(TAG, "Route set to $route")
    }

    fun getCurrentRoute(): AudioRoute = if (scoActive) AudioRoute.BLUETOOTH_SCO else currentRoute

    /**
     * List available audio output devices.
     */
    fun getAvailableRoutes(): List<AudioRoute> {
        val routes = mutableListOf(AudioRoute.SPEAKER, AudioRoute.EARPIECE)
        if (isBluetoothHeadsetConnected()) routes.add(AudioRoute.BLUETOOTH_SCO)
        if (isWiredHeadsetConnected()) routes.add(AudioRoute.WIRED_HEADSET)
        return routes
    }

    private fun startBluetoothSco() {
        if (!scoActive) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        }
    }

    private fun stopBluetoothSco() {
        if (scoActive) {
            audioManager.isBluetoothScoOn = false
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            scoActive = false
        }
    }

    private fun isBluetoothHeadsetConnected(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
    }

    private fun isWiredHeadsetConnected(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    companion object {
        private const val TAG = "AudioRoutingManager"
    }
}
