package com.guappa.app.tools

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ToolRateLimiterTest {
    private lateinit var limiter: ToolRateLimiter

    @Before
    fun setup() {
        limiter = ToolRateLimiter()
    }

    @Test
    fun `first call is always allowed`() {
        assertTrue(limiter.checkLimit("web_search"))
    }

    @Test
    fun `default rate limit allows many calls`() {
        // Default is 30/min - a custom tool should pass many calls
        repeat(25) {
            assertTrue("Call $it should be allowed", limiter.checkLimit("custom_tool"))
        }
    }

    @Test
    fun `web_search is limited to 10 per minute`() {
        repeat(10) {
            assertTrue("Call $it should be allowed", limiter.checkLimit("web_search"))
        }
        assertFalse("11th call should be blocked", limiter.checkLimit("web_search"))
    }

    @Test
    fun `send_sms is limited to 5 per minute`() {
        repeat(5) {
            assertTrue(limiter.checkLimit("send_sms"))
        }
        assertFalse(limiter.checkLimit("send_sms"))
    }

    @Test
    fun `place_call is limited to 2 per minute`() {
        repeat(2) {
            assertTrue(limiter.checkLimit("place_call"))
        }
        assertFalse(limiter.checkLimit("place_call"))
    }

    @Test
    fun `getRemainingCalls reflects usage`() {
        val (minBefore, hourBefore) = limiter.getRemainingCalls("web_search")
        assertEquals(10, minBefore)
        assertEquals(50, hourBefore)

        limiter.checkLimit("web_search")
        val (minAfter, hourAfter) = limiter.getRemainingCalls("web_search")
        assertEquals(9, minAfter)
        assertEquals(49, hourAfter)
    }

    @Test
    fun `concurrent limit blocks at max`() {
        val tool = "test_tool"
        repeat(5) { limiter.markStarted(tool) }
        // checkLimit should reject due to concurrent limit
        assertFalse(limiter.checkLimit(tool))
    }

    @Test
    fun `markCompleted releases concurrent slot`() {
        val tool = "test_tool"
        repeat(5) { limiter.markStarted(tool) }
        limiter.markCompleted(tool)
        // Now under concurrent limit, should be allowed (if rate not exceeded)
        assertTrue(limiter.checkLimit(tool))
    }

    @Test
    fun `reset clears all state`() {
        repeat(10) { limiter.checkLimit("web_search") }
        assertFalse(limiter.checkLimit("web_search"))

        limiter.reset()
        assertTrue(limiter.checkLimit("web_search"))
    }

    @Test
    fun `cleanup removes old timestamps`() {
        limiter.checkLimit("some_tool")
        limiter.cleanup() // nothing to clean since timestamp is fresh
        assertTrue(limiter.checkLimit("some_tool"))
    }

    @Test
    fun `different tools have independent limits`() {
        repeat(10) { limiter.checkLimit("web_search") }
        assertFalse(limiter.checkLimit("web_search"))
        assertTrue(limiter.checkLimit("send_sms")) // different tool, independent limit
    }
}
