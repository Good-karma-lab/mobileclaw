package com.guappa.app.providers

import org.junit.Assert.*
import org.junit.Test

class TokenCounterTest {

    @Test
    fun `empty string returns 0`() {
        assertEquals(0, TokenCounter.count(""))
    }

    @Test
    fun `single word returns 1`() {
        assertEquals(1, TokenCounter.count("hello"))
    }

    @Test
    fun `simple sentence returns reasonable count`() {
        val count = TokenCounter.count("The quick brown fox jumps over the lazy dog")
        assertTrue("Expected 8-20 tokens, got $count", count in 8..20)
    }

    @Test
    fun `contractions counted correctly`() {
        val count = TokenCounter.count("I'm sure you're right and they'll agree")
        assertTrue("Expected 7-20 tokens, got $count", count in 7..20)
    }

    @Test
    fun `numbers counted`() {
        val count = TokenCounter.count("There are 42 apples and 100 oranges")
        assertTrue("Expected 6-15 tokens, got $count", count in 6..15)
    }

    @Test
    fun `estimateFast returns reasonable values`() {
        val text = "Hello world this is a test"
        val fast = TokenCounter.estimateFast(text)
        val precise = TokenCounter.count(text)
        assertTrue("Fast estimate $fast should be within 2x of precise $precise",
            fast in (precise / 2)..(precise * 2))
    }

    @Test
    fun `exceedsBudget works`() {
        val text = "This is a short sentence."
        assertFalse(TokenCounter.exceedsBudget(text, 100))
        assertTrue(TokenCounter.exceedsBudget(text, 1))
    }

    @Test
    fun `countMessages includes overhead`() {
        val messages = listOf(
            mapOf("role" to "user", "content" to "Hello"),
            mapOf("role" to "assistant", "content" to "Hi there")
        )
        val count = TokenCounter.countMessages(messages)
        assertTrue("Expected >10 tokens with overhead, got $count", count > 10)
    }
}
