package com.guappa.app.agent

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

enum class TaskStatus { PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

data class TaskState(
    val id: String,
    val title: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val progress: Int = 0,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val result: String? = null,
    val error: String? = null
)

/**
 * Concurrent task lifecycle manager for the Guappa agent.
 *
 * Manages a pool of concurrent tasks with configurable limits, a FIFO
 * overflow queue, per-task timeout, and observable state updates via
 * [StateFlow]. Publishes lifecycle events to [MessageBus] so the
 * React Native bridge can observe task state changes.
 */
class TaskManager(
    private val messageBus: MessageBus,
    private val maxConcurrentTasks: Int = 3,
    private val defaultTimeoutMs: Long = 5 * 60 * 1000L // 5 minutes
) {
    companion object {
        private const val TAG = "TaskManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    // Active job tracking
    private val activeJobs = mutableMapOf<String, Job>()
    private val taskQueue = ConcurrentLinkedQueue<QueuedTask>()

    // Observable task states
    private val _tasks = MutableStateFlow<Map<String, TaskState>>(emptyMap())
    val tasks: StateFlow<Map<String, TaskState>> = _tasks.asStateFlow()

    // Convenience flow: list of active (non-terminal) tasks
    val activeTasks: Flow<List<TaskState>>
        get() = _tasks.map { map ->
            map.values.filter { it.status in listOf(TaskStatus.PENDING, TaskStatus.RUNNING, TaskStatus.PAUSED) }
        }

    /**
     * Submits a new task for execution. If the concurrent limit is reached,
     * the task is queued (FIFO) and started when a slot opens.
     *
     * @return The task ID for tracking.
     */
    suspend fun submitTask(
        title: String,
        timeoutMs: Long = defaultTimeoutMs,
        action: suspend () -> String
    ): String {
        val taskId = UUID.randomUUID().toString().take(12)

        val state = TaskState(
            id = taskId,
            title = title,
            status = TaskStatus.PENDING
        )
        updateTaskState(taskId, state)

        publishTaskEvent("task_submitted", taskId, mapOf("title" to title))

        mutex.withLock {
            if (activeJobs.size < maxConcurrentTasks) {
                launchTask(taskId, timeoutMs, action)
            } else {
                taskQueue.offer(QueuedTask(taskId, title, timeoutMs, action))
                Log.d(TAG, "Task $taskId queued (active: ${activeJobs.size}, queued: ${taskQueue.size})")
            }
        }

        return taskId
    }

    /**
     * Cancels a task by ID. If the task is queued, removes it from the queue.
     * If running, cancels the coroutine.
     *
     * @return true if the task was found and cancelled.
     */
    suspend fun cancelTask(taskId: String): Boolean {
        mutex.withLock {
            // Check queue first
            val removed = taskQueue.removeAll { it.id == taskId }
            if (removed) {
                updateTaskState(taskId, getTaskState(taskId)?.copy(
                    status = TaskStatus.CANCELLED,
                    completedAt = System.currentTimeMillis()
                ))
                publishTaskEvent("task_cancelled", taskId, emptyMap())
                return true
            }

            // Check active jobs
            val job = activeJobs.remove(taskId)
            if (job != null) {
                job.cancel(CancellationException("Task cancelled by user"))
                updateTaskState(taskId, getTaskState(taskId)?.copy(
                    status = TaskStatus.CANCELLED,
                    completedAt = System.currentTimeMillis()
                ))
                publishTaskEvent("task_cancelled", taskId, emptyMap())
                drainQueue()
                return true
            }
        }

        return false
    }

    /**
     * Returns the current state of all active (non-terminal) tasks.
     */
    fun getActiveTasks(): List<TaskState> {
        return _tasks.value.values.filter {
            it.status in listOf(TaskStatus.PENDING, TaskStatus.RUNNING, TaskStatus.PAUSED)
        }
    }

    /**
     * Returns the state of a specific task, or null if not found.
     */
    fun getTaskStatus(taskId: String): TaskState? {
        return _tasks.value[taskId]
    }

    /**
     * Updates the progress of a running task (0-100).
     * Can be called by the task action to report incremental progress.
     */
    suspend fun updateProgress(taskId: String, progress: Int) {
        val clamped = progress.coerceIn(0, 100)
        val current = getTaskState(taskId) ?: return
        if (current.status == TaskStatus.RUNNING) {
            updateTaskState(taskId, current.copy(progress = clamped))
            publishTaskEvent("task_progress", taskId, mapOf("progress" to clamped))
        }
    }

    /**
     * Stops accepting new tasks and cancels all running/queued tasks.
     */
    fun shutdown() {
        taskQueue.clear()
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        scope.cancel()
        Log.d(TAG, "TaskManager shut down")
    }

    // --- Internal ---

    private fun getTaskState(taskId: String): TaskState? = _tasks.value[taskId]

    private fun updateTaskState(taskId: String, state: TaskState?) {
        if (state == null) return
        _tasks.value = _tasks.value.toMutableMap().apply { put(taskId, state) }
    }

    private fun launchTask(
        taskId: String,
        timeoutMs: Long,
        action: suspend () -> String
    ) {
        val job = scope.launch {
            updateTaskState(taskId, getTaskState(taskId)?.copy(
                status = TaskStatus.RUNNING,
                startedAt = System.currentTimeMillis()
            ))
            publishTaskEvent("task_started", taskId, emptyMap())

            try {
                val result = withTimeout(timeoutMs) {
                    action()
                }

                updateTaskState(taskId, getTaskState(taskId)?.copy(
                    status = TaskStatus.COMPLETED,
                    progress = 100,
                    completedAt = System.currentTimeMillis(),
                    result = result
                ))
                publishTaskEvent("task_completed", taskId, mapOf("result" to result))
            } catch (e: TimeoutCancellationException) {
                updateTaskState(taskId, getTaskState(taskId)?.copy(
                    status = TaskStatus.FAILED,
                    completedAt = System.currentTimeMillis(),
                    error = "Task timed out after ${timeoutMs / 1000}s"
                ))
                publishTaskEvent("task_failed", taskId, mapOf("error" to "timeout"))
                Log.w(TAG, "Task $taskId timed out")
            } catch (e: CancellationException) {
                // Already handled in cancelTask
                throw e
            } catch (e: Exception) {
                updateTaskState(taskId, getTaskState(taskId)?.copy(
                    status = TaskStatus.FAILED,
                    completedAt = System.currentTimeMillis(),
                    error = e.message ?: "Unknown error"
                ))
                publishTaskEvent("task_failed", taskId, mapOf("error" to (e.message ?: "unknown")))
                Log.e(TAG, "Task $taskId failed: ${e.message}")
            } finally {
                mutex.withLock {
                    activeJobs.remove(taskId)
                    drainQueue()
                }
            }
        }

        activeJobs[taskId] = job
    }

    /**
     * Drains queued tasks into available slots. Must be called under [mutex].
     */
    private fun drainQueue() {
        while (activeJobs.size < maxConcurrentTasks) {
            val queued = taskQueue.poll() ?: break
            Log.d(TAG, "Dequeuing task ${queued.id} (active: ${activeJobs.size})")
            launchTask(queued.id, queued.timeoutMs, queued.action)
        }
    }

    private suspend fun publishTaskEvent(type: String, taskId: String, data: Map<String, Any?>) {
        val eventData = data.toMutableMap().apply {
            put("task_id", taskId)
            getTaskState(taskId)?.let { state ->
                put("title", state.title)
                put("status", state.status.name.lowercase())
            }
        }
        messageBus.publish(BusMessage.SystemEvent(type = type, data = eventData))
    }

    /**
     * Internal representation for queued tasks awaiting an execution slot.
     */
    private data class QueuedTask(
        val id: String,
        val title: String,
        val timeoutMs: Long,
        val action: suspend () -> String
    )
}
