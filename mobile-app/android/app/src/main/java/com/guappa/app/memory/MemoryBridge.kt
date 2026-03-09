package com.guappa.app.memory

import android.util.Log
import com.facebook.react.bridge.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * React Native bridge module exposing the memory system to JavaScript.
 *
 * Methods:
 *   getMemories(category?, tier?)  -> array of facts
 *   addMemory(key, value, category, tier?, importance?)  -> fact object
 *   searchMemories(query)          -> array of facts
 *   deleteMemory(id)               -> boolean
 *   getSessionHistory(limit?)      -> array of sessions
 *   createSession(title?)          -> session object
 *   endSession(sessionId, summary?) -> boolean
 *   getSessionMessages(sessionId)  -> array of messages
 *   getTasks()                     -> array of tasks
 *   addTask(title, description?, priority?, dueDate?)  -> task object
 *   updateTaskStatus(taskId, status) -> boolean
 *   deleteTask(taskId)             -> boolean
 *   getMemoryStats()               -> stats object
 *   runCleanup()                   -> cleanup result
 */
class MemoryBridge(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val TAG = "MemoryBridge"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memoryManager by lazy { MemoryManager.getInstance(reactContext) }

    override fun getName(): String = "GuappaMemory"

    // =====================================================================
    //  Memory Facts
    // =====================================================================

    @ReactMethod
    fun getMemories(category: String?, tier: String?, promise: Promise) {
        scope.launch {
            try {
                val cat = category?.takeIf { it.isNotBlank() }
                val t = tier?.takeIf { it.isNotBlank() }
                val facts = memoryManager.getMemories(cat, t)
                promise.resolve(factsToArray(facts))
            } catch (e: Exception) {
                promise.reject("MEMORY_ERROR", "Failed to get memories: ${e.message}", e)
            }
        }
    }

    @ReactMethod
    fun addMemory(key: String, value: String, category: String, tier: String?, importance: Double, promise: Promise) {
        scope.launch {
            try {
                val effectiveTier = tier?.takeIf { it.isNotBlank() } ?: "short_term"
                val fact = memoryManager.addFact(
                    key = key,
                    value = value,
                    category = category,
                    tier = effectiveTier,
                    importance = importance.toFloat()
                )
                promise.resolve(factToMap(fact))
            } catch (e: Exception) {
                promise.reject("MEMORY_ERROR", "Failed to add memory: ${e.message}", e)
            }
        }
    }

    @ReactMethod
    fun searchMemories(query: String, promise: Promise) {
        scope.launch {
            try {
                val facts = memoryManager.searchMemories(query)
                promise.resolve(factsToArray(facts))
            } catch (e: Exception) {
                promise.reject("MEMORY_ERROR", "Failed to search memories: ${e.message}", e)
            }
        }
    }

    @ReactMethod
    fun semanticSearch(query: String, limit: Int, promise: Promise) {
        scope.launch {
            try {
                val results = memoryManager.semanticSearch(query, limit)
                val array = Arguments.createArray()
                for (result in results) {
                    val map = Arguments.createMap().apply {
                        putString("id", result.id)
                        putString("content", result.content)
                        putString("source", result.source)
                        putString("category", result.category)
                        putDouble("score", result.score)
                    }
                    array.pushMap(map)
                }
                promise.resolve(array)
            } catch (e: Exception) {
                promise.reject("MEMORY_ERROR", "Semantic search failed: ${e.message}", e)
            }
        }
    }

    @ReactMethod
    fun deleteMemory(id: String, promise: Promise) {
        scope.launch {
            try {
                memoryManager.deleteFact(id)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("MEMORY_ERROR", "Failed to delete memory: ${e.message}", e)
            }
        }
    }

    // =====================================================================
    //  Sessions
    // =====================================================================

    @ReactMethod
    fun getSessionHistory(limit: Int, promise: Promise) {
        scope.launch {
            try {
                val effectiveLimit = if (limit > 0) limit else 20
                val sessions = memoryManager.getRecentSessions(effectiveLimit)
                val array = Arguments.createArray()
                for (session in sessions) {
                    array.pushMap(sessionToMap(session))
                }
                promise.resolve(array)
            } catch (e: Exception) {
                promise.reject("MEMORY_ERROR", "Failed to get session history: ${e.message}", e)
            }
        }
    }

    @ReactMethod
    fun createSession(title: String?, promise: Promise) {
        scope.launch {
            try {
                val session = memoryManager.createSession(title ?: "")
                promise.resolve(sessionToMap(session))
            } catch (e: Exception) {
                promise.reject("MEMORY_ERROR", "Failed to create session: ${e.message}", e)
            }
        }
    }

    @ReactMethod
    fun endSession(sessionId: String, summary: String?, promise: Promise) {
        scope.launch {
            try {
                memoryManager.endSession(sessionId, summary)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("MEMORY_ERROR", "Failed to end session: ${e.message}", e)
            }
        }
    }

    @ReactMethod
    fun getSessionMessages(sessionId: String, promise: Promise) {
        scope.launch {
            try {
                val messages = memoryManager.getSessionMessages(sessionId)
                val array = Arguments.createArray()
                for (msg in messages) {
                    val map = Arguments.createMap().apply {
                        putString("id", msg.id)
                        putString("sessionId", msg.sessionId)
                        putString("role", msg.role)
                        putString("content", msg.content)
                        putDouble("timestamp", msg.timestamp.toDouble())
                        putInt("tokenCount", msg.tokenCount)
                    }
                    array.pushMap(map)
                }
                promise.resolve(array)
            } catch (e: Exception) {
                promise.reject("MEMORY_ERROR", "Failed to get session messages: ${e.message}", e)
            }
        }
    }

    // =====================================================================
    //  Tasks
    // =====================================================================

    @ReactMethod
    fun getTasks(promise: Promise) {
        scope.launch {
            try {
                val tasks = memoryManager.getAllTasks()
                val array = Arguments.createArray()
                for (task in tasks) {
                    array.pushMap(taskToMap(task))
                }
                promise.resolve(array)
            } catch (e: Exception) {
                promise.reject("MEMORY_ERROR", "Failed to get tasks: ${e.message}", e)
            }
        }
    }

    @ReactMethod
    fun getActiveTasks(promise: Promise) {
        scope.launch {
            try {
                val tasks = memoryManager.getActiveTasks()
                val array = Arguments.createArray()
                for (task in tasks) {
                    array.pushMap(taskToMap(task))
                }
                promise.resolve(array)
            } catch (e: Exception) {
                promise.reject("MEMORY_ERROR", "Failed to get active tasks: ${e.message}", e)
            }
        }
    }

    @ReactMethod
    fun addTask(title: String, description: String?, priority: Int, dueDate: Double, promise: Promise) {
        scope.launch {
            try {
                val dueDateLong = if (dueDate > 0) dueDate.toLong() else null
                val task = memoryManager.addTask(
                    title = title,
                    description = description ?: "",
                    priority = priority,
                    dueDate = dueDateLong
                )
                promise.resolve(taskToMap(task))
            } catch (e: Exception) {
                promise.reject("MEMORY_ERROR", "Failed to add task: ${e.message}", e)
            }
        }
    }

    @ReactMethod
    fun updateTaskStatus(taskId: String, status: String, promise: Promise) {
        scope.launch {
            try {
                memoryManager.updateTaskStatus(taskId, status)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("MEMORY_ERROR", "Failed to update task: ${e.message}", e)
            }
        }
    }

    @ReactMethod
    fun deleteTask(taskId: String, promise: Promise) {
        scope.launch {
            try {
                memoryManager.deleteTask(taskId)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("MEMORY_ERROR", "Failed to delete task: ${e.message}", e)
            }
        }
    }

    // =====================================================================
    //  Episodes
    // =====================================================================

    @ReactMethod
    fun getEpisodes(limit: Int, promise: Promise) {
        scope.launch {
            try {
                val effectiveLimit = if (limit > 0) limit else 20
                val episodes = memoryManager.getRecentEpisodes(effectiveLimit)
                val array = Arguments.createArray()
                for (ep in episodes) {
                    val map = Arguments.createMap().apply {
                        putString("id", ep.id)
                        putString("sessionId", ep.sessionId)
                        putString("summary", ep.summary)
                        putString("emotion", ep.emotion)
                        putString("outcome", ep.outcome)
                        putDouble("timestamp", ep.timestamp.toDouble())
                    }
                    array.pushMap(map)
                }
                promise.resolve(array)
            } catch (e: Exception) {
                promise.reject("MEMORY_ERROR", "Failed to get episodes: ${e.message}", e)
            }
        }
    }

    // =====================================================================
    //  Stats & Maintenance
    // =====================================================================

    @ReactMethod
    fun getMemoryStats(promise: Promise) {
        scope.launch {
            try {
                val stats = memoryManager.getStats()
                val map = Arguments.createMap().apply {
                    putInt("totalFacts", stats.totalFacts)
                    putInt("shortTermFacts", stats.shortTermFacts)
                    putInt("longTermFacts", stats.longTermFacts)
                    putInt("totalEpisodes", stats.totalEpisodes)
                    putInt("activeSessions", stats.activeSessions)
                }
                promise.resolve(map)
            } catch (e: Exception) {
                promise.reject("MEMORY_ERROR", "Failed to get stats: ${e.message}", e)
            }
        }
    }

    @ReactMethod
    fun runCleanup(promise: Promise) {
        scope.launch {
            try {
                val deleted = memoryManager.runCleanup()
                val map = Arguments.createMap().apply {
                    putInt("deletedFacts", deleted)
                }
                promise.resolve(map)
            } catch (e: Exception) {
                promise.reject("MEMORY_ERROR", "Cleanup failed: ${e.message}", e)
            }
        }
    }

    @ReactMethod
    fun runPromotion(promise: Promise) {
        scope.launch {
            try {
                val promoted = memoryManager.runPromotion()
                val map = Arguments.createMap().apply {
                    putInt("promotedFacts", promoted)
                }
                promise.resolve(map)
            } catch (e: Exception) {
                promise.reject("MEMORY_ERROR", "Promotion failed: ${e.message}", e)
            }
        }
    }

    // Required for React Native event emitter compatibility
    @ReactMethod
    fun addListener(eventName: String) { }

    @ReactMethod
    fun removeListeners(count: Int) { }

    // =====================================================================
    //  Conversion Helpers
    // =====================================================================

    private fun factToMap(fact: MemoryFactEntity): WritableMap {
        return Arguments.createMap().apply {
            putString("id", fact.id)
            putString("key", fact.key)
            putString("value", fact.value)
            putString("category", fact.category)
            putString("tier", fact.tier)
            putDouble("importance", fact.importance.toDouble())
            putDouble("createdAt", fact.createdAt.toDouble())
            putDouble("accessedAt", fact.accessedAt.toDouble())
            putInt("accessCount", fact.accessCount)
        }
    }

    private fun factsToArray(facts: List<MemoryFactEntity>): WritableArray {
        val array = Arguments.createArray()
        for (fact in facts) {
            array.pushMap(factToMap(fact))
        }
        return array
    }

    private fun sessionToMap(session: SessionEntity): WritableMap {
        return Arguments.createMap().apply {
            putString("id", session.id)
            putString("title", session.title)
            putDouble("startedAt", session.startedAt.toDouble())
            if (session.endedAt != null) {
                putDouble("endedAt", session.endedAt.toDouble())
            } else {
                putNull("endedAt")
            }
            if (session.summary != null) {
                putString("summary", session.summary)
            } else {
                putNull("summary")
            }
            putInt("tokenCount", session.tokenCount)
        }
    }

    private fun taskToMap(task: TaskEntity): WritableMap {
        return Arguments.createMap().apply {
            putString("id", task.id)
            putString("title", task.title)
            putString("status", task.status)
            putInt("priority", task.priority)
            if (task.dueDate != null) {
                putDouble("dueDate", task.dueDate.toDouble())
            } else {
                putNull("dueDate")
            }
            putString("description", task.description)
            putDouble("createdAt", task.createdAt.toDouble())
        }
    }
}
