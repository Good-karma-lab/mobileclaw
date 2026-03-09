package com.guappa.app.proactive

import android.app.Application
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.Lifecycle
import com.guappa.app.agent.BusMessage
import com.guappa.app.agent.MessageBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Reports task completion and failure via notification when the app is in background.
 *
 * Subscribes to the [MessageBus] and filters for SystemEvent messages with
 * types "task_completed" and "task_failed". When the app lifecycle indicates
 * a background state, it creates a BigTextStyle notification on the
 * [NotificationChannels.TASKS] channel.
 *
 * Usage:
 *   val reporter = TaskCompletionReporter(application, messageBus)
 *   reporter.start()   // begin listening
 *   reporter.stop()    // stop listening and release resources
 */
class TaskCompletionReporter(
    private val application: Application,
    private val messageBus: MessageBus
) {
    companion object {
        private const val TAG = "TaskCompletionReporter"
        private const val EVENT_TASK_COMPLETED = "task_completed"
        private const val EVENT_TASK_FAILED = "task_failed"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val notificationManager by lazy { GuappaNotificationManager(application) }

    /**
     * Start listening for task completion/failure events on the MessageBus.
     */
    fun start() {
        Log.d(TAG, "Started listening for task events")

        // Listen on normal messages
        scope.launch {
            messageBus.messages.collect { message -> handleMessage(message) }
        }

        // Also listen on urgent messages
        scope.launch {
            messageBus.urgentMessages.collect { message -> handleMessage(message) }
        }
    }

    /**
     * Stop listening and cancel the coroutine scope.
     */
    fun stop() {
        scope.cancel()
        Log.d(TAG, "Stopped listening for task events")
    }

    private fun handleMessage(message: BusMessage) {
        when (message) {
            is BusMessage.SystemEvent -> {
                when (message.type) {
                    EVENT_TASK_COMPLETED -> onTaskCompleted(message.data)
                    EVENT_TASK_FAILED -> onTaskFailed(message.data)
                }
            }
            is BusMessage.ToolResult -> {
                // Also report tool results as task completions when in background
                if (isAppInBackground()) {
                    val status = if (message.success) "Completed" else "Failed"
                    notificationManager.showTaskNotification(
                        taskName = message.toolName,
                        status = status,
                        details = message.result.take(500)
                    )
                }
            }
            else -> { /* Ignore other message types */ }
        }
    }

    private fun onTaskCompleted(data: Map<String, Any?>) {
        if (!isAppInBackground()) {
            Log.d(TAG, "App in foreground; skipping task completion notification")
            return
        }

        val taskName = data["task_name"]?.toString() ?: data["title"]?.toString() ?: "Task"
        val result = data["result"]?.toString() ?: data["message"]?.toString() ?: "Completed successfully"
        val sessionId = data["session_id"]?.toString() ?: ""

        Log.d(TAG, "Task completed in background: $taskName")

        notificationManager.showTaskNotification(
            taskName = taskName,
            status = "Completed",
            details = result.take(500)
        )
    }

    private fun onTaskFailed(data: Map<String, Any?>) {
        if (!isAppInBackground()) {
            Log.d(TAG, "App in foreground; skipping task failure notification")
            return
        }

        val taskName = data["task_name"]?.toString() ?: data["title"]?.toString() ?: "Task"
        val error = data["error"]?.toString() ?: data["message"]?.toString() ?: "An error occurred"

        Log.d(TAG, "Task failed in background: $taskName")

        notificationManager.showTaskNotification(
            taskName = taskName,
            status = "Failed",
            details = "Error: ${error.take(500)}"
        )
    }

    /**
     * Checks if the app is currently in the background using ProcessLifecycleOwner.
     * Returns true if the lifecycle is not at least STARTED (i.e., no visible activity).
     */
    private fun isAppInBackground(): Boolean {
        return try {
            val lifecycle = ProcessLifecycleOwner.get().lifecycle
            !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check lifecycle state: ${e.message}")
            // Default to true (assume background) if we cannot determine state
            true
        }
    }
}
