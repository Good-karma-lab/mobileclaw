package com.guappa.app.config

import android.util.Log

/**
 * Validates configuration values before they are applied.
 */
object ConfigValidator {
    private const val TAG = "ConfigValidator"

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList()
    )

    fun validateApiKey(providerName: String, key: String): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (key.isBlank()) {
            errors.add("API key for $providerName is empty")
            return ValidationResult(false, errors)
        }

        // Provider-specific key format validation
        when (providerName.lowercase()) {
            "anthropic" -> {
                if (!key.startsWith("sk-ant-")) {
                    warnings.add("Anthropic keys typically start with 'sk-ant-'")
                }
            }
            "openai" -> {
                if (!key.startsWith("sk-")) {
                    warnings.add("OpenAI keys typically start with 'sk-'")
                }
            }
            "deepgram" -> {
                if (key.length < 20) {
                    errors.add("Deepgram API key appears too short")
                }
            }
            "openrouter" -> {
                if (!key.startsWith("sk-or-")) {
                    warnings.add("OpenRouter keys typically start with 'sk-or-'")
                }
            }
        }

        return ValidationResult(errors.isEmpty(), errors, warnings)
    }

    fun validateBaseUrl(url: String): ValidationResult {
        val errors = mutableListOf<String>()

        if (url.isBlank()) {
            errors.add("Base URL is empty")
            return ValidationResult(false, errors)
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            errors.add("Base URL must start with http:// or https://")
        }

        if (url.endsWith("/")) {
            return ValidationResult(true, warnings = listOf("Base URL has trailing slash — may cause double-slash in API paths"))
        }

        return ValidationResult(errors.isEmpty(), errors)
    }

    fun validateModelName(model: String): ValidationResult {
        if (model.isBlank()) {
            return ValidationResult(false, listOf("Model name is empty"))
        }
        if (model.contains(" ")) {
            return ValidationResult(false, listOf("Model name contains spaces"))
        }
        return ValidationResult(true)
    }

    fun validatePort(port: Int): ValidationResult {
        return if (port in 1..65535) {
            ValidationResult(true)
        } else {
            ValidationResult(false, listOf("Port must be between 1 and 65535"))
        }
    }

    /**
     * Validate a full configuration object.
     */
    fun validateConfig(config: Map<String, Any?>): ValidationResult {
        val allErrors = mutableListOf<String>()
        val allWarnings = mutableListOf<String>()

        val providerName = config["provider_name"] as? String
        val apiKey = config["api_key"] as? String
        val baseUrl = config["base_url"] as? String
        val model = config["model"] as? String

        if (providerName != null && apiKey != null) {
            val r = validateApiKey(providerName, apiKey)
            allErrors.addAll(r.errors)
            allWarnings.addAll(r.warnings)
        }

        if (baseUrl != null) {
            val r = validateBaseUrl(baseUrl)
            allErrors.addAll(r.errors)
            allWarnings.addAll(r.warnings)
        }

        if (model != null) {
            val r = validateModelName(model)
            allErrors.addAll(r.errors)
            allWarnings.addAll(r.warnings)
        }

        if (allWarnings.isNotEmpty()) {
            Log.w(TAG, "Config warnings: ${allWarnings.joinToString("; ")}")
        }

        return ValidationResult(allErrors.isEmpty(), allErrors, allWarnings)
    }
}
