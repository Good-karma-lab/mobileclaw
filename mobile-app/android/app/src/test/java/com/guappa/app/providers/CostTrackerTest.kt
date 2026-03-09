package com.guappa.app.providers

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CostTrackerTest {
    private lateinit var tracker: CostTracker

    @Before
    fun setup() {
        tracker = CostTracker()
    }

    @Test
    fun `estimateCost returns correct cost for known model`() {
        // gpt-4o: input $2.50/1M, output $10.00/1M
        val cost = tracker.estimateCost("gpt-4o", 1000, 500)
        // (1000 * 2.50 + 500 * 10.00) / 1_000_000 = 0.0075
        assertEquals(0.0075, cost, 0.0001)
    }

    @Test
    fun `estimateCost returns zero for unknown model`() {
        assertEquals(0.0, tracker.estimateCost("totally-unknown", 1000, 500), 0.0001)
    }

    @Test
    fun `estimateCost matches model by substring`() {
        // "my-gpt-4o-deployment" should match "gpt-4o"
        val cost = tracker.estimateCost("my-gpt-4o-deployment", 1000, 500)
        assertTrue(cost > 0.0)
    }

    @Test
    fun `recordUsage tracks cumulative costs`() {
        val usage = TokenUsage(promptTokens = 1000, completionTokens = 500, totalTokens = 1500)
        tracker.recordUsage("openai", "gpt-4o", usage)
        tracker.recordUsage("openai", "gpt-4o", usage)

        val summary = tracker.getSummary()
        assertEquals(2, summary.requestCount)
        assertEquals(2000L, summary.totalPromptTokens)
        assertEquals(1000L, summary.totalCompletionTokens)
        assertTrue(summary.totalCostUsd > 0.0)
        assertTrue(summary.byProvider.containsKey("openai"))
    }

    @Test
    fun `recordUsage ignores null usage`() {
        tracker.recordUsage("openai", "gpt-4o", null)
        val summary = tracker.getSummary()
        assertEquals(0, summary.requestCount)
    }

    @Test
    fun `reset clears all state`() {
        tracker.recordUsage("openai", "gpt-4o", TokenUsage(100, 50, 150))
        tracker.reset()
        val summary = tracker.getSummary()
        assertEquals(0, summary.requestCount)
        assertEquals(0.0, summary.totalCostUsd, 0.0001)
    }

    @Test
    fun `getRecentRecords returns latest records`() {
        repeat(5) { i ->
            tracker.recordUsage("p$i", "gpt-4o", TokenUsage(100, 50, 150))
        }
        val recent = tracker.getRecentRecords(3)
        assertEquals(3, recent.size)
    }

    @Test
    fun `cheap model costs less than expensive model`() {
        val cheapCost = tracker.estimateCost("gpt-4o-mini", 1000, 500)
        val expensiveCost = tracker.estimateCost("gpt-4-turbo", 1000, 500)
        assertTrue(cheapCost < expensiveCost)
    }

    @Test
    fun `claude pricing is tracked`() {
        val cost = tracker.estimateCost("claude-3.5-sonnet", 1000, 500)
        assertTrue(cost > 0.0)
    }
}
