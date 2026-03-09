package com.guappa.app.agent

import com.guappa.app.config.GuappaConfigStore
import com.guappa.app.config.PersonaConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * Personality and behavior system for the Guappa agent.
 *
 * Generates system prompts incorporating persona traits and post-processes
 * responses for consistency with the active persona. Supports preset
 * personas and custom configuration via [GuappaConfigStore].
 */
class GuappaPersona(
    private val configStore: GuappaConfigStore? = null
) {
    companion object {
        private val PRESETS: Map<String, PersonaProfile> = mapOf(
            "default" to PersonaProfile(
                name = "Guappa",
                personalityTraits = listOf("helpful", "friendly", "concise", "tech-savvy"),
                communicationStyle = "conversational",
                formalityLevel = "casual",
                humorLevel = "light",
                expertiseAreas = listOf("technology", "productivity", "general knowledge"),
                prohibitedTopics = emptyList()
            ),
            "professional" to PersonaProfile(
                name = "Guappa",
                personalityTraits = listOf("precise", "thorough", "respectful", "knowledgeable"),
                communicationStyle = "formal",
                formalityLevel = "high",
                humorLevel = "minimal",
                expertiseAreas = listOf("business", "technology", "analysis"),
                prohibitedTopics = listOf("slang", "casual banter")
            ),
            "casual" to PersonaProfile(
                name = "Guappa",
                personalityTraits = listOf("laid-back", "friendly", "approachable", "empathetic"),
                communicationStyle = "informal",
                formalityLevel = "low",
                humorLevel = "moderate",
                expertiseAreas = listOf("general knowledge", "entertainment", "lifestyle"),
                prohibitedTopics = emptyList()
            ),
            "creative" to PersonaProfile(
                name = "Guappa",
                personalityTraits = listOf("imaginative", "expressive", "curious", "playful"),
                communicationStyle = "vivid",
                formalityLevel = "low",
                humorLevel = "high",
                expertiseAreas = listOf("writing", "brainstorming", "design", "storytelling"),
                prohibitedTopics = emptyList()
            ),
            "technical" to PersonaProfile(
                name = "Guappa",
                personalityTraits = listOf("analytical", "precise", "methodical", "detail-oriented"),
                communicationStyle = "technical",
                formalityLevel = "medium",
                humorLevel = "minimal",
                expertiseAreas = listOf("programming", "system design", "debugging", "data analysis"),
                prohibitedTopics = listOf("vague generalizations")
            )
        )
    }

    /**
     * Resolves the active [PersonaProfile] from config store or defaults.
     */
    fun getActiveProfile(): PersonaProfile {
        val config = configStore?.personaConfig?.value
        return if (config != null) {
            resolveFromConfig(config)
        } else {
            PRESETS["default"]!!
        }
    }

    /**
     * Generates a full system prompt incorporating the active persona.
     * This should be used as the system message in LLM calls.
     */
    fun getSystemPrompt(): String {
        val profile = getActiveProfile()
        return buildSystemPrompt(profile)
    }

    /**
     * Post-processes an LLM response to enforce persona consistency.
     * Applies light adjustments based on formality and communication style.
     */
    fun adaptResponse(response: String): String {
        val profile = getActiveProfile()
        var adapted = response

        // Filter prohibited topics — replace with a polite deflection
        for (topic in profile.prohibitedTopics) {
            if (adapted.lowercase().contains(topic.lowercase())) {
                adapted = adapted.replace(
                    Regex("(?i)${Regex.escape(topic)}"),
                    "[topic outside my scope]"
                )
            }
        }

        // Adjust formality level
        adapted = when (profile.formalityLevel) {
            "high" -> adaptForHighFormality(adapted)
            "low" -> adaptForLowFormality(adapted)
            else -> adapted
        }

        return adapted
    }

    /**
     * Returns all available preset names.
     */
    fun getPresetNames(): List<String> = PRESETS.keys.toList()

    /**
     * Returns a preset profile by name, or null if not found.
     */
    fun getPreset(name: String): PersonaProfile? = PRESETS[name.lowercase()]

    // --- Internal helpers ---

    private fun resolveFromConfig(config: PersonaConfig): PersonaProfile {
        // Check if personality field matches a preset name
        val preset = PRESETS[config.personality.lowercase()]
        if (preset != null) {
            return preset.copy(
                name = config.name.ifEmpty { preset.name }
            )
        }

        // Build custom profile from config
        return PersonaProfile(
            name = config.name.ifEmpty { "Guappa" },
            personalityTraits = parseTraitList(config.personality),
            communicationStyle = mapVerbosityToStyle(config.verbosity),
            formalityLevel = "medium",
            humorLevel = "light",
            expertiseAreas = listOf("general knowledge"),
            prohibitedTopics = emptyList(),
            customSystemPrompt = config.systemPrompt.takeIf { it.isNotBlank() }
        )
    }

    private fun parseTraitList(personality: String): List<String> {
        // Support comma-separated trait lists in the personality field
        return personality.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("helpful") }
    }

    private fun mapVerbosityToStyle(verbosity: String): String {
        return when (verbosity.lowercase()) {
            "concise", "brief", "minimal" -> "concise"
            "verbose", "detailed" -> "detailed"
            else -> "conversational"
        }
    }

    private fun buildSystemPrompt(profile: PersonaProfile): String {
        // If a custom system prompt is set, use it as the base
        if (!profile.customSystemPrompt.isNullOrBlank()) {
            return profile.customSystemPrompt
        }

        val sb = StringBuilder()
        sb.appendLine("You are ${profile.name}, a helpful AI assistant running on an Android device.")
        sb.appendLine("You have access to device tools to help the user. Use tools when appropriate to fulfill user requests.")
        sb.appendLine()

        // Personality
        sb.appendLine("Personality: ${profile.personalityTraits.joinToString(", ")}.")

        // Communication style
        val styleDesc = when (profile.communicationStyle) {
            "concise" -> "Keep your responses brief and to the point."
            "detailed" -> "Provide thorough, detailed explanations when helpful."
            "formal" -> "Communicate in a polished, professional manner."
            "informal" -> "Keep things relaxed and approachable."
            "vivid" -> "Use expressive, creative language to make responses engaging."
            "technical" -> "Use precise technical language and structured explanations."
            else -> "Communicate in a natural, conversational tone."
        }
        sb.appendLine(styleDesc)

        // Formality
        when (profile.formalityLevel) {
            "high" -> sb.appendLine("Maintain a formal, professional tone at all times.")
            "low" -> sb.appendLine("Feel free to use a casual, relaxed tone.")
        }

        // Humor
        when (profile.humorLevel) {
            "high" -> sb.appendLine("Feel free to use humor and wit in your responses.")
            "moderate" -> sb.appendLine("Light humor is welcome when appropriate.")
            "minimal" -> sb.appendLine("Keep responses focused and factual.")
        }

        // Expertise areas
        if (profile.expertiseAreas.isNotEmpty()) {
            sb.appendLine("Your areas of expertise include: ${profile.expertiseAreas.joinToString(", ")}.")
        }

        // Prohibited topics
        if (profile.prohibitedTopics.isNotEmpty()) {
            sb.appendLine("Avoid the following topics: ${profile.prohibitedTopics.joinToString(", ")}.")
        }

        return sb.toString().trimEnd()
    }

    private fun adaptForHighFormality(text: String): String {
        // Simple formality adaptations
        return text
            .replace(Regex("\\bwanna\\b"), "want to")
            .replace(Regex("\\bgonna\\b"), "going to")
            .replace(Regex("\\bgotta\\b"), "have to")
            .replace(Regex("\\byeah\\b"), "yes")
            .replace(Regex("\\bnope\\b"), "no")
            .replace(Regex("\\bcool\\b"), "great")
    }

    private fun adaptForLowFormality(text: String): String {
        // No aggressive de-formalization — just ensure it stays natural
        return text
    }
}

/**
 * Immutable snapshot of a persona's characteristics.
 */
data class PersonaProfile(
    val name: String,
    val personalityTraits: List<String>,
    val communicationStyle: String,
    val formalityLevel: String,
    val humorLevel: String,
    val expertiseAreas: List<String>,
    val prohibitedTopics: List<String>,
    val customSystemPrompt: String? = null
)
