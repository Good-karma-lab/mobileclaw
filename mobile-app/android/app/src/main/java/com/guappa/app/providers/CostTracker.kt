package com.guappa.app.providers

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks per-request cost estimation and cumulative usage budget.
 * Pricing data is approximate and based on public pricing as of March 2026.
 */
class CostTracker {

    data class UsageRecord(
        val providerId: String,
        val modelId: String,
        val promptTokens: Int,
        val completionTokens: Int,
        val estimatedCostUsd: Double,
        val timestamp: Long = System.currentTimeMillis(),
    )

    data class CostSummary(
        val totalCostUsd: Double,
        val totalPromptTokens: Long,
        val totalCompletionTokens: Long,
        val requestCount: Int,
        val byProvider: Map<String, Double>,
    )

    // Pricing per 1M tokens: Pair(input, output)
    private val pricing = mapOf(
        // OpenAI
        "gpt-4o" to Pair(2.50, 10.00),
        "gpt-4o-mini" to Pair(0.15, 0.60),
        "gpt-4-turbo" to Pair(10.00, 30.00),
        "gpt-3.5-turbo" to Pair(0.50, 1.50),
        "o1" to Pair(15.00, 60.00),
        "o1-mini" to Pair(3.00, 12.00),
        "o3-mini" to Pair(1.10, 4.40),
        // Anthropic
        "claude-opus-4" to Pair(15.00, 75.00),
        "claude-sonnet-4" to Pair(3.00, 15.00),
        "claude-haiku-4" to Pair(0.80, 4.00),
        "claude-3.5-sonnet" to Pair(3.00, 15.00),
        "claude-3-haiku" to Pair(0.25, 1.25),
        // Google
        "gemini-2.0-flash" to Pair(0.10, 0.40),
        "gemini-2.0-pro" to Pair(1.25, 5.00),
        "gemini-1.5-pro" to Pair(1.25, 5.00),
        "gemini-1.5-flash" to Pair(0.075, 0.30),
        // DeepSeek
        "deepseek-chat" to Pair(0.14, 0.28),
        "deepseek-reasoner" to Pair(0.55, 2.19),
        // Mistral
        "mistral-large" to Pair(2.00, 6.00),
        "mistral-small" to Pair(0.20, 0.60),
        // Groq
        "llama-3.3-70b" to Pair(0.59, 0.79),
        "llama-3.1-8b" to Pair(0.05, 0.08),
        // xAI
        "grok-2" to Pair(2.00, 10.00),
        "grok-3-mini" to Pair(0.30, 0.50),
    )

    private val records = mutableListOf<UsageRecord>()
    private val totalCostCents = AtomicLong(0)
    private val providerCosts = ConcurrentHashMap<String, AtomicLong>()

    @Synchronized
    fun recordUsage(providerId: String, modelId: String, usage: TokenUsage?) {
        if (usage == null) return

        val cost = estimateCost(modelId, usage.promptTokens, usage.completionTokens)
        val record = UsageRecord(
            providerId = providerId,
            modelId = modelId,
            promptTokens = usage.promptTokens,
            completionTokens = usage.completionTokens,
            estimatedCostUsd = cost,
        )
        records.add(record)
        totalCostCents.addAndGet((cost * 100_000).toLong()) // store as micro-cents for precision
        providerCosts.getOrPut(providerId) { AtomicLong(0) }
            .addAndGet((cost * 100_000).toLong())
    }

    fun estimateCost(modelId: String, promptTokens: Int, completionTokens: Int): Double {
        val modelKey = findPricingKey(modelId) ?: return 0.0
        val (inputPer1M, outputPer1M) = pricing[modelKey] ?: return 0.0
        return (promptTokens * inputPer1M + completionTokens * outputPer1M) / 1_000_000.0
    }

    @Synchronized
    fun getSummary(): CostSummary {
        return CostSummary(
            totalCostUsd = totalCostCents.get() / 100_000.0,
            totalPromptTokens = records.sumOf { it.promptTokens.toLong() },
            totalCompletionTokens = records.sumOf { it.completionTokens.toLong() },
            requestCount = records.size,
            byProvider = providerCosts.mapValues { it.value.get() / 100_000.0 },
        )
    }

    @Synchronized
    fun getRecentRecords(limit: Int = 50): List<UsageRecord> {
        return records.takeLast(limit)
    }

    @Synchronized
    fun reset() {
        records.clear()
        totalCostCents.set(0)
        providerCosts.clear()
    }

    private fun findPricingKey(modelId: String): String? {
        val lower = modelId.lowercase()
        return pricing.keys.firstOrNull { lower.contains(it) }
    }
}
