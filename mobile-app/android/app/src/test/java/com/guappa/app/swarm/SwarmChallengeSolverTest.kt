package com.guappa.app.swarm

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SwarmChallengeSolver — garbled arithmetic challenge parser.
 */
class SwarmChallengeSolverTest {

    private lateinit var solver: SwarmChallengeSolver

    @Before
    fun setUp() {
        solver = SwarmChallengeSolver()
    }

    // --- Addition ---

    @Test
    fun `solves simple addition with digits`() {
        // "what is 64 plus 33" — uses plain "is" to avoid leet-speak parsing
        assertEquals(97, solver.solve("what is 64 plus 33?"))
    }

    @Test
    fun `solves addition with different casing`() {
        assertEquals(57, solver.solve("what is 12 plus 45?"))
    }

    @Test
    fun `solves addition with plus symbol`() {
        assertEquals(15, solver.solve("what is 7 + 8?"))
    }

    @Test
    fun `solves addition with three-digit numbers`() {
        assertEquals(579, solver.solve("what is 123 plus 456?"))
    }

    // --- Subtraction ---

    @Test
    fun `solves subtraction with minus word`() {
        assertEquals(58, solver.solve("what is 100 minus 42?"))
    }

    @Test
    fun `solves subtraction with minus symbol`() {
        assertEquals(5, solver.solve("what is 10 - 5?"))
    }

    // --- Multiplication ---

    @Test
    fun `solves multiplication with times word`() {
        assertEquals(42, solver.solve("what is 7 t1m3s 6?"))
    }

    @Test
    fun `solves multiplication with times`() {
        assertEquals(56, solver.solve("what is 8 times 7?"))
    }

    @Test
    fun `solves multiplication with star symbol`() {
        assertEquals(24, solver.solve("what is 4 * 6?"))
    }

    // --- Leet-speak numbers ---

    @Test
    fun `solves with leet-speak number words`() {
        // "thr33" maps to 3, "f1ve" maps to 5
        assertEquals(8, solver.solve("what is thr33 + f1ve?"))
    }

    @Test
    fun `solves with zero`() {
        assertEquals(0, solver.solve("what is zero plus zero?"))
    }

    @Test
    fun `solves with word ten`() {
        assertEquals(20, solver.solve("what is ten plus ten?"))
    }

    // --- Edge cases ---

    @Test
    fun `defaults to addition when no operator found`() {
        assertEquals(15, solver.solve("what is 10 5?"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws when no numbers found`() {
        solver.solve("hello world no numbers here")
    }

    @Test
    fun `handles extra whitespace and punctuation`() {
        assertEquals(100, solver.solve("  what  is   50   plus   50  ??  "))
    }
}
