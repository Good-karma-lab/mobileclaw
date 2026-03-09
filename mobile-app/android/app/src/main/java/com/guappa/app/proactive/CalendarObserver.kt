package com.guappa.app.proactive

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import com.guappa.app.GuappaAgentService
import com.guappa.app.agent.BusMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ContentObserver that watches CalendarContract for changes.
 *
 * When calendar data is modified (events added/updated/deleted),
 * fires a trigger via the MessageBus so the agent can react
 * (e.g., update briefings, suggest schedule adjustments).
 *
 * Usage:
 *   val observer = CalendarObserver(context)
 *   observer.register()     // start watching
 *   observer.unregister()   // stop watching
 */
class CalendarObserver(
    private val context: Context,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    private val TAG = "CalendarObserver"
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Debounce window — calendar providers often fire multiple onChange calls
     * for a single user action. We suppress duplicates within this window.
     */
    private var lastChangeTimestamp: Long = 0L
    private val debounceMs: Long = 5_000L

    private var registered = false

    override fun onChange(selfChange: Boolean) {
        onChange(selfChange, null)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        val now = System.currentTimeMillis()
        if (now - lastChangeTimestamp < debounceMs) {
            Log.d(TAG, "Calendar change debounced (within ${debounceMs}ms)")
            return
        }
        lastChangeTimestamp = now

        Log.d(TAG, "Calendar changed — uri=$uri, selfChange=$selfChange")

        val bus = GuappaAgentService.messageBus ?: run {
            Log.w(TAG, "MessageBus not available; ignoring calendar change")
            return
        }

        scope.launch {
            bus.publish(
                BusMessage.TriggerEvent(
                    trigger = "calendar_changed",
                    data = mapOf(
                        "event_type" to "calendar_changed",
                        "uri" to (uri?.toString() ?: ""),
                        "timestamp" to now,
                        "message" to "Calendar data changed. Check for upcoming events or schedule updates."
                    )
                )
            )
        }
    }

    /**
     * Register to observe calendar changes.
     * Requires READ_CALENDAR permission to be granted.
     */
    fun register() {
        if (registered) {
            Log.d(TAG, "Already registered")
            return
        }
        try {
            context.contentResolver.registerContentObserver(
                CalendarContract.Events.CONTENT_URI,
                true,
                this
            )
            registered = true
            Log.d(TAG, "Registered for calendar changes")
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot observe calendar — permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register calendar observer", e)
        }
    }

    /**
     * Unregister from calendar changes.
     */
    fun unregister() {
        if (!registered) return
        try {
            context.contentResolver.unregisterContentObserver(this)
            registered = false
            Log.d(TAG, "Unregistered from calendar changes")
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering calendar observer: ${e.message}")
        }
    }

    val isRegistered: Boolean get() = registered
}
