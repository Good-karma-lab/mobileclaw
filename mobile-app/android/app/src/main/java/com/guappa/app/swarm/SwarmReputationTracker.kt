package com.guappa.app.swarm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tracks the agent's reputation in the World Wide Swarm.
 *
 * Reputation determines the agent's tier and access level:
 * - New (0-49): Fresh agent, limited to executor tasks
 * - Trusted (50-99): Proven executor, can participate in holons
 * - Veteran (100-499): Can inject tasks, eligible for Member tier
 * - Elite (500+): High-trust agent, eligible for Tier2/Tier1 elections
 *
 * Reputation events are stored locally and synced with the connector.
 */
class SwarmReputationTracker(private val context: Context) {
    private val TAG = "SwarmReputationTracker"
    private val PREFS_NAME = "guappa_swarm_reputation"

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // --- Observable state ---

    private val _score = MutableStateFlow(loadScore())
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _tier = MutableStateFlow(computeTier(loadScore()))
    val tier: StateFlow<ReputationTier> = _tier.asStateFlow()

    private val _events = MutableStateFlow(loadRecentEvents())
    val events: StateFlow<List<ReputationEvent>> = _events.asStateFlow()

    // --- Reputation modifiers ---

    fun onTaskCompleted(taskId: String, durationMs: Long) {
        val points = computeCompletionPoints(durationMs)
        addEvent(ReputationEvent(
            type = ReputationEventType.TASK_COMPLETED,
            points = points,
            description = "Completed task $taskId in ${durationMs / 1000}s",
            taskId = taskId,
        ))
        Log.d(TAG, "Task completed: +$points rep (total: ${_score.value})")
    }

    fun onTaskFailed(taskId: String, reason: String) {
        val penalty = -TASK_FAILURE_PENALTY
        addEvent(ReputationEvent(
            type = ReputationEventType.TASK_FAILED,
            points = penalty,
            description = "Task $taskId failed: $reason",
            taskId = taskId,
        ))
        Log.d(TAG, "Task failed: $penalty rep (total: ${_score.value})")
    }

    fun onHolonParticipation(holonId: String) {
        addEvent(ReputationEvent(
            type = ReputationEventType.HOLON_PARTICIPATION,
            points = HOLON_PARTICIPATION_POINTS,
            description = "Participated in holon $holonId",
        ))
    }

    fun onProposalAccepted(holonId: String) {
        addEvent(ReputationEvent(
            type = ReputationEventType.PROPOSAL_ACCEPTED,
            points = PROPOSAL_ACCEPTED_POINTS,
            description = "Proposal accepted in holon $holonId",
        ))
    }

    fun onUptimeReward() {
        addEvent(ReputationEvent(
            type = ReputationEventType.UPTIME_REWARD,
            points = UPTIME_REWARD_POINTS,
            description = "Continuous uptime reward",
        ))
    }

    /**
     * Update score from connector (authoritative source).
     */
    fun updateFromConnector(newScore: Int, events: List<ReputationEvent>? = null) {
        _score.value = newScore
        _tier.value = computeTier(newScore)
        persistScore(newScore)

        if (events != null) {
            val current = _events.value.toMutableList()
            current.addAll(0, events)
            // Keep only recent events
            val trimmed = current.take(MAX_EVENT_HISTORY)
            _events.value = trimmed
            persistEvents(trimmed)
        }
    }

    // --- Tier queries ---

    fun canInjectTasks(): Boolean = _score.value >= MEMBER_THRESHOLD
    fun canParticipateInElections(): Boolean = _score.value >= ELITE_THRESHOLD
    fun canSupervise(): Boolean = _tier.value >= ReputationTier.VETERAN

    // --- Internal ---

    private fun addEvent(event: ReputationEvent) {
        val newScore = maxOf(0, _score.value + event.points)
        _score.value = newScore
        _tier.value = computeTier(newScore)
        persistScore(newScore)

        val updatedEvents = listOf(event) + _events.value
        val trimmed = updatedEvents.take(MAX_EVENT_HISTORY)
        _events.value = trimmed
        persistEvents(trimmed)
    }

    private fun computeCompletionPoints(durationMs: Long): Int {
        // Faster completion = more points (capped)
        return when {
            durationMs < 5_000 -> TASK_COMPLETION_FAST_POINTS
            durationMs < 30_000 -> TASK_COMPLETION_NORMAL_POINTS
            durationMs < 120_000 -> TASK_COMPLETION_SLOW_POINTS
            else -> TASK_COMPLETION_MIN_POINTS
        }
    }

    private fun computeTier(score: Int): ReputationTier = when {
        score >= ELITE_THRESHOLD -> ReputationTier.ELITE
        score >= VETERAN_THRESHOLD -> ReputationTier.VETERAN
        score >= TRUSTED_THRESHOLD -> ReputationTier.TRUSTED
        else -> ReputationTier.NEW
    }

    // --- Persistence ---

    private fun loadScore(): Int = prefs.getInt("reputation_score", 0)

    private fun persistScore(score: Int) {
        prefs.edit().putInt("reputation_score", score).apply()
    }

    private fun loadRecentEvents(): List<ReputationEvent> {
        val raw = prefs.getString("reputation_events", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { ReputationEvent.fromJSON(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load reputation events: ${e.message}")
            emptyList()
        }
    }

    private fun persistEvents(events: List<ReputationEvent>) {
        val arr = JSONArray()
        events.forEach { arr.put(it.toJSON()) }
        prefs.edit().putString("reputation_events", arr.toString()).apply()
    }

    companion object {
        // Tier thresholds
        const val TRUSTED_THRESHOLD = 50
        const val VETERAN_THRESHOLD = 100
        const val ELITE_THRESHOLD = 500
        const val MEMBER_THRESHOLD = 100

        // Points
        const val TASK_COMPLETION_FAST_POINTS = 10
        const val TASK_COMPLETION_NORMAL_POINTS = 5
        const val TASK_COMPLETION_SLOW_POINTS = 3
        const val TASK_COMPLETION_MIN_POINTS = 1
        const val TASK_FAILURE_PENALTY = 5
        const val HOLON_PARTICIPATION_POINTS = 3
        const val PROPOSAL_ACCEPTED_POINTS = 10
        const val UPTIME_REWARD_POINTS = 1

        const val MAX_EVENT_HISTORY = 100
    }
}

enum class ReputationTier {
    NEW,
    TRUSTED,
    VETERAN,
    ELITE;

    val displayName: String
        get() = when (this) {
            NEW -> "New"
            TRUSTED -> "Trusted"
            VETERAN -> "Veteran"
            ELITE -> "Elite"
        }
}

enum class ReputationEventType {
    TASK_COMPLETED,
    TASK_FAILED,
    HOLON_PARTICIPATION,
    PROPOSAL_ACCEPTED,
    UPTIME_REWARD,
    PEER_ENDORSEMENT,
    PENALTY,
}

data class ReputationEvent(
    val type: ReputationEventType,
    val points: Int,
    val description: String,
    val taskId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("type", type.name)
        put("points", points)
        put("description", description)
        taskId?.let { put("task_id", it) }
        put("timestamp", timestamp)
    }

    companion object {
        fun fromJSON(json: JSONObject): ReputationEvent = ReputationEvent(
            type = try {
                ReputationEventType.valueOf(json.getString("type"))
            } catch (_: Exception) {
                ReputationEventType.TASK_COMPLETED
            },
            points = json.optInt("points", 0),
            description = json.optString("description", ""),
            taskId = json.optString("task_id", null),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
        )
    }
}
