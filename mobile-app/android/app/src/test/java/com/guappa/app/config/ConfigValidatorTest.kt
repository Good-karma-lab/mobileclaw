package com.guappa.app.config

import org.junit.Assert.*
import org.junit.Test

class ConfigValidatorTest {

    @Test
    fun `valid OpenRouter key passes`() {
        val result = ConfigValidator.validateApiKey("openrouter", "sk-or-v1-abc123")
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `empty key fails`() {
        val result = ConfigValidator.validateApiKey("openai", "")
        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `wrong prefix generates warning`() {
        val result = ConfigValidator.validateApiKey("anthropic", "wrong-prefix-key")
        assertTrue(result.isValid) // Warning, not error
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `valid URL passes`() {
        val result = ConfigValidator.validateBaseUrl("https://openrouter.ai/api/v1")
        assertTrue(result.isValid)
    }

    @Test
    fun `URL without scheme fails`() {
        val result = ConfigValidator.validateBaseUrl("openrouter.ai/api")
        assertFalse(result.isValid)
    }

    @Test
    fun `trailing slash URL gives warning`() {
        val result = ConfigValidator.validateBaseUrl("https://api.example.com/")
        assertTrue(result.isValid)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `valid model name passes`() {
        val result = ConfigValidator.validateModelName("gpt-4o-mini")
        assertTrue(result.isValid)
    }

    @Test
    fun `model name with spaces fails`() {
        val result = ConfigValidator.validateModelName("gpt 4o mini")
        assertFalse(result.isValid)
    }

    @Test
    fun `valid port passes`() {
        assertTrue(ConfigValidator.validatePort(8080).isValid)
        assertTrue(ConfigValidator.validatePort(443).isValid)
    }

    @Test
    fun `invalid port fails`() {
        assertFalse(ConfigValidator.validatePort(0).isValid)
        assertFalse(ConfigValidator.validatePort(70000).isValid)
    }

    @Test
    fun `validateConfig checks all fields`() {
        val config = mapOf<String, Any?>(
            "provider_name" to "openrouter",
            "api_key" to "sk-or-v1-test123",
            "base_url" to "https://openrouter.ai/api/v1",
            "model" to "minimax/minimax-m2.5"
        )
        val result = ConfigValidator.validateConfig(config)
        assertTrue(result.isValid)
    }
}
