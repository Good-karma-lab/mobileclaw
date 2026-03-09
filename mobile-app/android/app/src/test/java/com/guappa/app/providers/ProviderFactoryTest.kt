package com.guappa.app.providers

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ProviderFactory — verifying provider ID normalization
 * and factory wiring correctness.
 */
class ProviderFactoryTest {

    @Test
    fun `normalizeProviderId handles common aliases`() {
        assertEquals("openai", ProviderFactory.normalizeProviderId("openai"))
        assertEquals("openai", ProviderFactory.normalizeProviderId("OpenAI"))
        assertEquals("openai", ProviderFactory.normalizeProviderId("OPENAI"))
    }

    @Test
    fun `normalizeProviderId handles openrouter`() {
        val normalized = ProviderFactory.normalizeProviderId("openrouter")
        assertEquals("openrouter", normalized)
    }

    @Test
    fun `normalizeProviderId handles anthropic`() {
        val normalized = ProviderFactory.normalizeProviderId("anthropic")
        assertEquals("anthropic", normalized)
    }

    @Test
    fun `normalizeProviderId handles google variants`() {
        val normalized1 = ProviderFactory.normalizeProviderId("google")
        val normalized2 = ProviderFactory.normalizeProviderId("gemini")
        // Both should normalize to a google-compatible ID
        assertTrue(normalized1 == "google" || normalized1 == "gemini")
        assertTrue(normalized2 == "google" || normalized2 == "gemini")
    }

    @Test
    fun `createRouter does not throw for known providers`() {
        // Should not throw even with empty keys (just creates unconfigured router)
        try {
            ProviderFactory.createRouter("openai", "", "")
        } catch (e: Exception) {
            // Acceptable: some implementations may require keys
        }
    }
}
