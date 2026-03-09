package com.guappa.app.proactive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.util.Log
import com.guappa.app.agent.MessageBus
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Central trigger registration and dispatch manager.
 *
 * Manages all trigger sources (SMS, call, calendar, battery, network, screen, location)
 * and routes incoming events through [ProactiveEngine.evaluateTrigger] before dispatching
 * to [EventReactor].
 *
 * Singleton pattern: use [TriggerManager.getInstance] to obtain the shared instance.
 *
 * Usage:
 *   val manager = TriggerManager.getInstance(context, messageBus, proactiveEngine)
 *   manager.enableTriggerType(TriggerSource.BATTERY)
 *   manager.onTrigger(TriggerSource.BATTERY, mapOf("battery_level" to 15))
 */
class TriggerManager private constructor(
    private val context: Context,
    private val messageBus: MessageBus,
    private val proactiveEngine: ProactiveEngine
) {
    companion object {
        private const val TAG = "TriggerManager"

        @Volatile
        private var instance: TriggerManager? = null

        fun getInstance(
            context: Context,
            messageBus: MessageBus,
            proactiveEngine: ProactiveEngine
        ): TriggerManager {
            return instance ?: synchronized(this) {
                instance ?: TriggerManager(
                    context.applicationContext,
                    messageBus,
                    proactiveEngine
                ).also { instance = it }
            }
        }

        /**
         * Returns the existing instance, or null if not yet initialized.
         */
        fun getInstanceOrNull(): TriggerManager? = instance
    }

    /**
     * Trigger source types that can be individually enabled/disabled.
     */
    enum class TriggerSource {
        SMS,
        CALL,
        CALENDAR,
        BATTERY,
        NETWORK,
        SCREEN,
        LOCATION
    }

    private val enabledSources = ConcurrentHashMap<TriggerSource, Boolean>()
    private val registeredReceivers = ConcurrentHashMap<TriggerSource, BroadcastReceiver>()

    private var calendarObserver: CalendarObserver? = null

    val eventReactor by lazy {
        EventReactor(context, messageBus, proactiveEngine)
    }

    init {
        // All sources disabled by default until explicitly enabled
        TriggerSource.values().forEach { source ->
            enabledSources[source] = false
        }
    }

    // -----------------------------------------------------------------------
    // Enable/Disable trigger sources
    // -----------------------------------------------------------------------

    /**
     * Enable a specific trigger source. Registers the corresponding receiver/observer.
     */
    fun enableTriggerType(source: TriggerSource) {
        if (enabledSources[source] == true) {
            Log.d(TAG, "Trigger source already enabled: $source")
            return
        }

        enabledSources[source] = true
        registerSource(source)
        Log.d(TAG, "Enabled trigger source: $source")
    }

    /**
     * Disable a specific trigger source. Unregisters the corresponding receiver/observer.
     */
    fun disableTriggerType(source: TriggerSource) {
        if (enabledSources[source] != true) return

        enabledSources[source] = false
        unregisterSource(source)
        Log.d(TAG, "Disabled trigger source: $source")
    }

    /**
     * Returns true if the given trigger source is enabled.
     */
    fun isEnabled(source: TriggerSource): Boolean = enabledSources[source] == true

    /**
     * Returns all currently enabled trigger sources.
     */
    fun getEnabledSources(): List<TriggerSource> =
        enabledSources.entries.filter { it.value }.map { it.key }

    /**
     * Enable all trigger sources.
     */
    fun enableAll() {
        TriggerSource.values().forEach { enableTriggerType(it) }
    }

    /**
     * Disable all trigger sources.
     */
    fun disableAll() {
        TriggerSource.values().forEach { disableTriggerType(it) }
    }

    // -----------------------------------------------------------------------
    // Event dispatch
    // -----------------------------------------------------------------------

    /**
     * Called when a trigger event is received from any source.
     * Checks whether the trigger should react (via ProactiveEngine) and
     * dispatches to EventReactor if appropriate.
     *
     * @param source The source type of the trigger.
     * @param data Event-specific data map.
     */
    fun onTrigger(source: TriggerSource, data: Map<String, Any?>) {
        if (enabledSources[source] != true) {
            Log.d(TAG, "Trigger source disabled, ignoring: $source")
            return
        }

        val eventType = data["event_type"]?.toString() ?: source.name.lowercase()
        Log.d(TAG, "Trigger received: source=$source, eventType=$eventType")

        // Check with ProactiveEngine if we should react
        val eventJson = JSONObject().apply {
            put("event_type", eventType)
            for ((key, value) in data) {
                put(key, value)
            }
        }

        proactiveEngine.handleEvent(eventType, eventJson)

        // Also dispatch to EventReactor for specific handling
        dispatchToReactor(source, data)
    }

    private fun dispatchToReactor(source: TriggerSource, data: Map<String, Any?>) {
        try {
            when (source) {
                TriggerSource.SMS -> {
                    val sender = data["sender"]?.toString() ?: return
                    val body = data["body"]?.toString() ?: ""
                    eventReactor.onSmsReceived(sender, body)
                }
                TriggerSource.CALL -> {
                    val number = data["number"]?.toString() ?: return
                    val timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis()
                    eventReactor.onMissedCall(number, timestamp)
                }
                TriggerSource.CALENDAR -> {
                    val title = data["event_title"]?.toString() ?: return
                    val minutes = (data["minutes_before"] as? Int) ?: 15
                    eventReactor.onCalendarReminder(title, minutes)
                }
                TriggerSource.BATTERY,
                TriggerSource.NETWORK,
                TriggerSource.SCREEN,
                TriggerSource.LOCATION -> {
                    // These are handled by ProactiveEngine.handleEvent above
                    Log.d(TAG, "System trigger dispatched via ProactiveEngine: $source")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching trigger $source to reactor", e)
        }
    }

    // -----------------------------------------------------------------------
    // Source registration
    // -----------------------------------------------------------------------

    private fun registerSource(source: TriggerSource) {
        try {
            when (source) {
                TriggerSource.BATTERY -> {
                    val receiver = BatteryReceiver.register(context)
                    registeredReceivers[source] = receiver
                }
                TriggerSource.CALENDAR -> {
                    val observer = CalendarObserver(context)
                    observer.register()
                    calendarObserver = observer
                }
                TriggerSource.SMS,
                TriggerSource.CALL,
                TriggerSource.NETWORK,
                TriggerSource.SCREEN,
                TriggerSource.LOCATION -> {
                    // These require separate BroadcastReceivers or listeners
                    // that are registered via AndroidManifest or dedicated setup.
                    Log.d(TAG, "Source $source requires manifest registration or separate setup")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register source: $source", e)
            enabledSources[source] = false
        }
    }

    private fun unregisterSource(source: TriggerSource) {
        try {
            when (source) {
                TriggerSource.BATTERY -> {
                    registeredReceivers.remove(source)?.let { receiver ->
                        context.unregisterReceiver(receiver)
                    }
                }
                TriggerSource.CALENDAR -> {
                    calendarObserver?.unregister()
                    calendarObserver = null
                }
                TriggerSource.SMS,
                TriggerSource.CALL,
                TriggerSource.NETWORK,
                TriggerSource.SCREEN,
                TriggerSource.LOCATION -> {
                    registeredReceivers.remove(source)?.let { receiver ->
                        context.unregisterReceiver(receiver)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering source $source: ${e.message}")
        }
    }

    /**
     * Shut down all trigger sources and release resources.
     * Call during service/app shutdown.
     */
    fun shutdown() {
        disableAll()
        eventReactor.shutdown()
        instance = null
        Log.d(TAG, "TriggerManager shut down")
    }
}
