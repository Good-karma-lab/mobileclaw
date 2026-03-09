package com.guappa.app.agent

import android.util.Log
import com.guappa.app.providers.CapabilityType
import com.guappa.app.providers.ChatMessage
import com.guappa.app.providers.ProviderRouter
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class PlanStepStatus { PENDING, RUNNING, DONE, FAILED }

data class PlanStep(
    val id: String = UUID.randomUUID().toString().take(8),
    val description: String,
    val toolNeeded: String? = null,
    val dependsOn: List<String> = emptyList(),
    var status: PlanStepStatus = PlanStepStatus.PENDING
)

/**
 * Task decomposition engine that breaks complex user requests into
 * ordered plan steps. Supports both LLM-based and heuristic decomposition.
 *
 * When a user request contains multiple actions (detected by verb count
 * or sequential keywords), the planner decomposes it into [PlanStep]s
 * with dependency ordering, then executes them respecting those dependencies.
 * Independent steps (no dependency overlap) execute in parallel.
 */
class GuappaPlanner(
    private val providerRouter: ProviderRouter?,
    private val messageBus: MessageBus
) {
    companion object {
        private const val TAG = "GuappaPlanner"
        private const val MAX_REPLAN_ATTEMPTS = 2

        // Keywords that indicate sequential multi-step intent
        private val SEQUENTIAL_KEYWORDS = listOf(
            "and then", "after that", "next", "finally",
            "first", "second", "third", "then", "lastly",
            "once that's done", "when done", "followed by",
            "afterwards", "subsequently"
        )

        // Common action verbs that signal distinct tasks
        private val ACTION_VERBS = listOf(
            "search", "find", "look up", "create", "make", "write",
            "send", "open", "close", "delete", "remove", "update",
            "edit", "check", "read", "download", "upload", "install",
            "set", "configure", "start", "stop", "run", "calculate",
            "summarize", "translate", "convert", "compare", "list"
        )

        private const val DECOMPOSE_PROMPT = """You are a task planner. Break the user request into discrete steps.
Return ONLY a JSON array where each element has:
- "id": short unique string (e.g. "s1", "s2")
- "description": what this step does
- "tool_needed": tool name if a tool is needed, or null
- "depends_on": array of step ids this step must wait for (empty if independent)

Keep steps minimal and actionable. Do not add unnecessary steps.
Available tools: device_info, clipboard, web_search, file_read, file_write, notifications, shell
If no tool is needed for a step, set tool_needed to null.

Example output:
[{"id":"s1","description":"Search for weather info","tool_needed":"web_search","depends_on":[]},{"id":"s2","description":"Summarize the results","tool_needed":null,"depends_on":["s1"]}]"""
    }

    /**
     * Returns true if the user request looks complex enough to benefit
     * from plan decomposition rather than a single ReAct loop.
     */
    fun isComplexRequest(userRequest: String): Boolean {
        val lower = userRequest.lowercase()

        // Check for sequential keywords
        val hasSequentialKeywords = SEQUENTIAL_KEYWORDS.any { lower.contains(it) }
        if (hasSequentialKeywords) return true

        // Count distinct action verbs
        val verbCount = ACTION_VERBS.count { verb -> lower.contains(verb) }
        if (verbCount >= 3) return true

        // Check for numbered lists (1. ... 2. ... or bullet points)
        val numberedPattern = Regex("""(?:^|\n)\s*(?:\d+[.)]\s|[-*]\s)""")
        if (numberedPattern.findAll(userRequest).count() >= 2) return true

        return false
    }

    /**
     * Decomposes a user request into ordered plan steps.
     * Tries LLM-based decomposition first; falls back to heuristic if unavailable.
     */
    suspend fun decompose(userRequest: String): List<PlanStep> {
        // Try LLM-based decomposition
        val router = providerRouter
        if (router != null) {
            try {
                return decomposeLLM(userRequest, router)
            } catch (e: Exception) {
                Log.w(TAG, "LLM decomposition failed, falling back to heuristic: ${e.message}")
            }
        }

        return decomposeHeuristic(userRequest)
    }

    private suspend fun decomposeLLM(userRequest: String, router: ProviderRouter): List<PlanStep> {
        val messages = listOf(
            ChatMessage(role = "system", content = DECOMPOSE_PROMPT),
            ChatMessage(role = "user", content = userRequest)
        )

        val response = router.chat(
            messages = messages,
            capability = CapabilityType.TEXT_CHAT,
            temperature = 0.3
        )

        val content = response.content?.trim() ?: return decomposeHeuristic(userRequest)
        return parsePlanJSON(content)
    }

    private fun parsePlanJSON(content: String): List<PlanStep> {
        // Extract JSON array from response (may contain markdown fences)
        val jsonStr = content
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
            .trim()

        val array = JSONArray(jsonStr)
        val steps = mutableListOf<PlanStep>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val dependsOn = mutableListOf<String>()
            obj.optJSONArray("depends_on")?.let { deps ->
                for (j in 0 until deps.length()) {
                    dependsOn.add(deps.getString(j))
                }
            }
            steps.add(PlanStep(
                id = obj.getString("id"),
                description = obj.getString("description"),
                toolNeeded = obj.optString("tool_needed").takeIf { it.isNotEmpty() && it != "null" },
                dependsOn = dependsOn
            ))
        }

        return steps
    }

    /**
     * Heuristic decomposition: splits by sequential keywords and sentence boundaries.
     */
    internal fun decomposeHeuristic(userRequest: String): List<PlanStep> {
        val lower = userRequest.lowercase()

        // Try splitting on sequential keywords
        val splitPattern = Regex(
            "(?i)(?:,?\\s*(?:and then|after that|then|next|finally|lastly|first|afterwards|subsequently|followed by)\\s*)",
            RegexOption.IGNORE_CASE
        )
        val parts = userRequest.split(splitPattern)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (parts.size >= 2) {
            return parts.mapIndexed { index, part ->
                PlanStep(
                    id = "s${index + 1}",
                    description = part.replaceFirstChar { it.uppercase() },
                    toolNeeded = inferToolFromDescription(part),
                    dependsOn = if (index > 0) listOf("s$index") else emptyList()
                )
            }
        }

        // Try splitting on numbered lists
        val numberedPattern = Regex("""(?:^|\n)\s*(?:(\d+)[.)]\s*|[-*]\s*)(.+)""")
        val numbered = numberedPattern.findAll(userRequest).toList()
        if (numbered.size >= 2) {
            return numbered.mapIndexed { index, match ->
                val desc = match.groupValues[2].trim()
                PlanStep(
                    id = "s${index + 1}",
                    description = desc.replaceFirstChar { it.uppercase() },
                    toolNeeded = inferToolFromDescription(desc),
                    dependsOn = if (index > 0) listOf("s$index") else emptyList()
                )
            }
        }

        // Single step — no decomposition needed
        return listOf(
            PlanStep(
                id = "s1",
                description = userRequest,
                toolNeeded = inferToolFromDescription(userRequest)
            )
        )
    }

    private fun inferToolFromDescription(description: String): String? {
        val lower = description.lowercase()
        return when {
            lower.contains("search") || lower.contains("look up") || lower.contains("find online") -> "web_search"
            lower.contains("read file") || lower.contains("open file") -> "file_read"
            lower.contains("write file") || lower.contains("save file") || lower.contains("create file") -> "file_write"
            lower.contains("clipboard") || lower.contains("copy") || lower.contains("paste") -> "clipboard"
            lower.contains("notification") || lower.contains("remind") || lower.contains("alert") -> "notifications"
            lower.contains("device") || lower.contains("battery") || lower.contains("storage") -> "device_info"
            else -> null
        }
    }

    /**
     * Executes a plan respecting dependency order. Independent steps
     * (no shared dependsOn) run in parallel. If a step fails, attempts
     * to re-plan remaining steps up to [MAX_REPLAN_ATTEMPTS] times.
     */
    suspend fun executePlan(
        steps: List<PlanStep>,
        session: GuappaSession,
        orchestratorLoop: suspend (session: GuappaSession, stepDescription: String) -> Unit
    ) {
        val mutableSteps = steps.toMutableList()
        var replanAttempts = 0

        publishPlanEvent("plan_started", mapOf(
            "session_id" to session.id,
            "total_steps" to mutableSteps.size
        ))

        while (mutableSteps.any { it.status == PlanStepStatus.PENDING }) {
            // Find steps whose dependencies are all DONE
            val ready = mutableSteps.filter { step ->
                step.status == PlanStepStatus.PENDING &&
                step.dependsOn.all { depId ->
                    mutableSteps.find { it.id == depId }?.status == PlanStepStatus.DONE
                }
            }

            if (ready.isEmpty()) {
                // Check for failed dependencies blocking progress
                val blocked = mutableSteps.filter { it.status == PlanStepStatus.PENDING }
                val hasFailedDeps = blocked.any { step ->
                    step.dependsOn.any { depId ->
                        mutableSteps.find { it.id == depId }?.status == PlanStepStatus.FAILED
                    }
                }

                if (hasFailedDeps && replanAttempts < MAX_REPLAN_ATTEMPTS) {
                    replanAttempts++
                    Log.d(TAG, "Re-planning remaining steps (attempt $replanAttempts)")

                    val remaining = blocked.map { it.description }
                    val replanned = replanSteps(remaining, replanAttempts)
                    // Replace remaining pending steps with replanned ones
                    mutableSteps.removeAll { it.status == PlanStepStatus.PENDING }
                    mutableSteps.addAll(replanned)

                    publishPlanEvent("plan_replanned", mapOf(
                        "session_id" to session.id,
                        "attempt" to replanAttempts,
                        "remaining_steps" to replanned.size
                    ))
                    continue
                }

                // No more recovery possible
                Log.w(TAG, "Plan execution stalled: blocked steps with failed dependencies")
                break
            }

            // Execute ready steps in parallel
            coroutineScope {
                ready.map { step ->
                    async {
                        executeStep(step, session, orchestratorLoop)
                    }
                }.awaitAll()
            }
        }

        val completedCount = mutableSteps.count { it.status == PlanStepStatus.DONE }
        val failedCount = mutableSteps.count { it.status == PlanStepStatus.FAILED }

        publishPlanEvent("plan_completed", mapOf(
            "session_id" to session.id,
            "completed" to completedCount,
            "failed" to failedCount,
            "total" to mutableSteps.size
        ))
    }

    private suspend fun executeStep(
        step: PlanStep,
        session: GuappaSession,
        orchestratorLoop: suspend (session: GuappaSession, stepDescription: String) -> Unit
    ) {
        step.status = PlanStepStatus.RUNNING

        publishPlanEvent("step_started", mapOf(
            "session_id" to session.id,
            "step_id" to step.id,
            "description" to step.description
        ))

        try {
            // Inject step context as a user message and run orchestrator
            session.addMessage(Message(
                role = "user",
                content = "[Plan Step ${step.id}] ${step.description}"
            ))
            orchestratorLoop(session, step.description)

            step.status = PlanStepStatus.DONE
            publishPlanEvent("step_completed", mapOf(
                "session_id" to session.id,
                "step_id" to step.id
            ))
        } catch (e: Exception) {
            step.status = PlanStepStatus.FAILED
            Log.e(TAG, "Step ${step.id} failed: ${e.message}")

            publishPlanEvent("step_failed", mapOf(
                "session_id" to session.id,
                "step_id" to step.id,
                "error" to (e.message ?: "unknown error")
            ))
        }
    }

    /**
     * Re-plans remaining step descriptions after a failure.
     * Creates fresh sequential steps without dependency on failed work.
     */
    private fun replanSteps(remainingDescriptions: List<String>, attempt: Int): List<PlanStep> {
        return remainingDescriptions.mapIndexed { index, desc ->
            PlanStep(
                id = "r${attempt}_s${index + 1}",
                description = desc,
                toolNeeded = inferToolFromDescription(desc),
                dependsOn = if (index > 0) listOf("r${attempt}_s$index") else emptyList()
            )
        }
    }

    private suspend fun publishPlanEvent(type: String, data: Map<String, Any?>) {
        messageBus.publish(BusMessage.SystemEvent(type = type, data = data))
    }
}
