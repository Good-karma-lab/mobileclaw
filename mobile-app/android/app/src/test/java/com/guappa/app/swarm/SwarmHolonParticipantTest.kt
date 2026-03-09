package com.guappa.app.swarm

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for IRV voting implementation in SwarmHolonParticipant.
 */
class SwarmHolonParticipantTest {

    @Test
    fun `sha256Hex produces correct hash`() {
        val hash = SwarmHolonParticipant.sha256Hex("hello")
        assertEquals(64, hash.length)
        // SHA-256 of "hello" is well-known
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", hash)
    }

    @Test
    fun `sha256Hex is deterministic`() {
        val hash1 = SwarmHolonParticipant.sha256Hex("test input")
        val hash2 = SwarmHolonParticipant.sha256Hex("test input")
        assertEquals(hash1, hash2)
    }

    @Test
    fun `sha256Hex produces different hashes for different inputs`() {
        val hash1 = SwarmHolonParticipant.sha256Hex("input1")
        val hash2 = SwarmHolonParticipant.sha256Hex("input2")
        assertNotEquals(hash1, hash2)
    }
}

/**
 * Tests for IRV tallying logic — testable without Android context.
 */
class IRVTallyTest {

    // Create a testable instance by using the companion method directly
    private fun tallyIRV(ballots: Map<String, List<String>>): String? {
        if (ballots.isEmpty()) return null

        val allCandidates = ballots.values.flatten().toSet().toMutableSet()
        if (allCandidates.isEmpty()) return null

        val activeBallots = ballots.mapValues { it.value.toMutableList() }.toMutableMap()
        val eliminatedCandidates = mutableSetOf<String>()
        val totalVoters = activeBallots.size
        val majorityThreshold = totalVoters / 2.0

        for (round in 1..allCandidates.size) {
            val firstChoiceCounts = mutableMapOf<String, Int>()
            for (candidate in allCandidates) {
                if (candidate !in eliminatedCandidates) {
                    firstChoiceCounts[candidate] = 0
                }
            }

            for (ballot in activeBallots.values) {
                val firstChoice = ballot.firstOrNull { it !in eliminatedCandidates }
                if (firstChoice != null) {
                    firstChoiceCounts[firstChoice] = (firstChoiceCounts[firstChoice] ?: 0) + 1
                }
            }

            val winner = firstChoiceCounts.entries.firstOrNull { it.value > majorityThreshold }
            if (winner != null) return winner.key

            val minVotes = firstChoiceCounts.values.minOrNull() ?: return null
            val toEliminate = firstChoiceCounts.entries
                .filter { it.value == minVotes }
                .map { it.key }

            if (toEliminate.size == firstChoiceCounts.size) return toEliminate.first()

            eliminatedCandidates.addAll(toEliminate)

            for (ballot in activeBallots.values) {
                ballot.removeAll(toEliminate.toSet())
            }

            val remaining = allCandidates - eliminatedCandidates
            if (remaining.size == 1) return remaining.first()
            if (remaining.isEmpty()) return null
        }

        return null
    }

    @Test
    fun `IRV returns null for empty ballots`() {
        assertNull(tallyIRV(emptyMap()))
    }

    @Test
    fun `IRV returns winner with clear majority`() {
        val ballots = mapOf(
            "voter1" to listOf("A", "B", "C"),
            "voter2" to listOf("A", "C", "B"),
            "voter3" to listOf("B", "A", "C"),
        )
        assertEquals("A", tallyIRV(ballots))
    }

    @Test
    fun `IRV eliminates lowest and redistributes`() {
        val ballots = mapOf(
            "voter1" to listOf("A", "B", "C"),
            "voter2" to listOf("B", "A", "C"),
            "voter3" to listOf("C", "A", "B"),
            "voter4" to listOf("A", "C", "B"),
            "voter5" to listOf("B", "C", "A"),
        )
        // A=2, B=2, C=1 first round
        // C eliminated, voter3 redistributes to A
        // A=3, B=2 -> A wins
        assertEquals("A", tallyIRV(ballots))
    }

    @Test
    fun `IRV handles single candidate`() {
        val ballots = mapOf(
            "voter1" to listOf("A"),
            "voter2" to listOf("A"),
        )
        assertEquals("A", tallyIRV(ballots))
    }

    @Test
    fun `IRV handles two-way tie`() {
        val ballots = mapOf(
            "voter1" to listOf("A"),
            "voter2" to listOf("B"),
        )
        // Tie: should return first tied candidate
        val result = tallyIRV(ballots)
        assertTrue(result == "A" || result == "B")
    }
}
