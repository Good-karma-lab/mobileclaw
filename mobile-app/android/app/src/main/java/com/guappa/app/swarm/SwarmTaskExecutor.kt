package com.guappa.app.swarm

import android.util.Log
import com.guappa.app.agent.BusMessage
import com.guappa.app.agent.MessageBus
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Executes swarm tasks by routing them through the GUAPPA agent loop.
 *
 * Responsibilities:
 * - Accept tasks from the poller or event stream
 * - Execute via the agent orchestrator
 * - Report results back to the swarm connector
 * - Enforce timeout and concurrency limits
 * - Track execution metrics for reputation
 */
class SwarmTaskExecutor(
    private val connector: SwarmConnectorClient,
    private val messageBus: MessageBus,
    private val reputationTracker: SwarmReputationTracker,
    private val config: SwarmConfig = SwarmConfig(),
) {
    private val TAG = "SwarmTaskExecutor"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Currently executing tasks, keyed by task ID. */
    private val activeTasks = ConcurrentHashMap<String, Job>()
    private val activeCount = AtomicInteger(0)

    /** Completed task results for reporting. */
    private val completedResults = ConcurrentHashMap<String, SwarmTaskResult>()

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 120_000L // 2 minutes per task
        private const val MAX_RESULT_CONTENT_LENGTH = 50_000 // chars
    }

    /**
     * Accept and execute a task. Returns false if rejected (at capacity).
     */
    suspend fun executeTask(task: SwarmTask): Boolean {
        if (activeCount.get() >= config.maxConcurrentTasks) {
            Log.w(TAG, "Rejecting task ${task.id}: at max concurrency (${config.maxConcurrentTasks})")
            reportFailure(task.id, "Agent at maximum task capacity")
            return false
        }

        // Check capability match
        if (task.requiredCapabilities.isNotEmpty()) {
            val myCapabilities = config.capabilities
            val missing = task.requiredCapabilities.filter { it !in myCapabilities }
            if (missing.isNotEmpty()) {
                Log.w(TAG, "Rejecting task ${task.id}: missing capabilities $missing")
                reportFailure(task.id, "Missing capabilities: $missing")
                return false
            }
        }

        Log.d(TAG, "Accepting task ${task.id}: ${task.description.take(80)}")
        activeCount.incrementAndGet()

        val job = scope.launch {
            val startTime = System.currentTimeMillis()
            try {
                val timeoutMs = task.deadline?.let {
                    val remaining = it - System.currentTimeMillis()
                    if (remaining <= 0) throw IllegalStateException("Task deadline already passed")
                    minOf(remaining, DEFAULT_TIMEOUT_MS)
                } ?: DEFAULT_TIMEOUT_MS

                val result = withTimeout(timeoutMs) {
                    executeViaAgent(task)
                }

                val durationMs = System.currentTimeMillis() - startTime
                val taskResult = SwarmTaskResult(
                    taskId = task.id,
                    status = TaskStatus.COMPLETED,
                    content = result.take(MAX_RESULT_CONTENT_LENGTH),
                    durationMs = durationMs,
                )

                reportSuccess(taskResult)
                reputationTracker.onTaskCompleted(task.id, durationMs)
                completedResults[task.id] = taskResult

                Log.d(TAG, "Task ${task.id} completed in ${durationMs}ms")
            } catch (e: TimeoutCancellationException) {
                val durationMs = System.currentTimeMillis() - startTime
                Log.w(TAG, "Task ${task.id} timed out after ${durationMs}ms")
                reportFailure(task.id, "Task execution timed out")
                reputationTracker.onTaskFailed(task.id, "timeout")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val durationMs = System.currentTimeMillis() - startTime
                Log.e(TAG, "Task ${task.id} failed: ${e.message}")
                reportFailure(task.id, "Execution failed: ${e.message}")
                reputationTracker.onTaskFailed(task.id, e.message ?: "unknown")
            } finally {
                activeCount.decrementAndGet()
                activeTasks.remove(task.id)
            }
        }

        activeTasks[task.id] = job
        return true
    }

    /**
     * Execute a task via the GUAPPA agent by publishing it as a user message.
     * The agent loop processes it and we collect the response.
     */
    private suspend fun executeViaAgent(task: SwarmTask): String {
        val resultDeferred = CompletableDeferred<String>()
        val sessionId = "swarm_${task.id}"

        // Collect agent response
        val collectorJob = scope.launch {
            messageBus.messages.collect { message ->
                if (message is BusMessage.AgentMessage &&
                    message.sessionId == sessionId &&
                    message.isComplete
                ) {
                    resultDeferred.complete(message.text)
                }
            }
        }

        try {
            // Publish the task as a user message to the agent
            messageBus.publish(
                BusMessage.UserMessage(
                    text = buildTaskPrompt(task),
                    sessionId = sessionId,
                )
            )

            // Notify UI
            messageBus.publish(
                BusMessage.SystemEvent(
                    type = "swarm_task_started",
                    data = mapOf(
                        "task_id" to task.id,
                        "description" to task.description.take(100),
                    )
                )
            )

            // Wait for agent to complete
            return resultDeferred.await()
        } finally {
            collectorJob.cancel()
        }
    }

    private fun buildTaskPrompt(task: SwarmTask): String {
        return buildString {
            appendLine("[Swarm Task ${task.id}]")
            appendLine("Complexity: ${task.complexity}")
            if (task.requiredCapabilities.isNotEmpty()) {
                appendLine("Required capabilities: ${task.requiredCapabilities.joinToString()}")
            }
            appendLine()
            appendLine(task.description)
        }
    }

    private suspend fun reportSuccess(result: SwarmTaskResult) {
        try {
            val payload = result.toJSON()
            connector.sendMessage(
                SwarmMessage(
                    type = SwarmMessageType.TASK_RESPONSE,
                    fromPeerId = "", // Will be filled by SwarmManager
                    payload = payload,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report task success: ${e.message}")
        }
    }

    private suspend fun reportFailure(taskId: String, error: String) {
        try {
            val result = SwarmTaskResult(
                taskId = taskId,
                status = TaskStatus.FAILED,
                content = "",
                error = error,
            )
            connector.sendMessage(
                SwarmMessage(
                    type = SwarmMessageType.TASK_RESPONSE,
                    fromPeerId = "",
                    payload = result.toJSON(),
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report task failure: ${e.message}")
        }
    }

    /**
     * Cancel a running task.
     */
    fun cancelTask(taskId: String) {
        activeTasks[taskId]?.cancel()
        activeTasks.remove(taskId)
        Log.d(TAG, "Cancelled task: $taskId")
    }

    /**
     * Get the number of currently active tasks.
     */
    fun getActiveTaskCount(): Int = activeCount.get()

    /**
     * Get IDs of all active tasks.
     */
    fun getActiveTaskIds(): Set<String> = activeTasks.keys.toSet()

    /**
     * Get a completed task result.
     */
    fun getCompletedResult(taskId: String): SwarmTaskResult? = completedResults[taskId]

    fun shutdown() {
        activeTasks.values.forEach { it.cancel() }
        activeTasks.clear()
        scope.cancel()
    }
}
