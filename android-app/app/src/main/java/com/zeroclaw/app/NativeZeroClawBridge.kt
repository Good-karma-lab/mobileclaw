package com.zeroclaw.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Primary bridge path (no fallback chain).
 *
 * Supports Ollama chat API and OpenAI-compatible chat completions
 * (including OpenRouter).
 */
class NativeZeroClawBridge {
    suspend fun chat(message: String, config: AgentRuntimeConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val provider = config.provider.trim().lowercase()
            when (provider) {
                "ollama" -> sendOllamaChat(message, config)
                "openrouter", "openai", "copilot", "github-copilot" -> sendOpenAiCompatibleChat(message, config)
                "anthropic" -> sendAnthropicChat(message, config)
                "gemini", "google", "google-gemini" -> sendGeminiChat(message, config)
                else -> error("Unsupported provider in mobile bridge: $provider")
            }
        }
    }

    private fun sendOllamaChat(message: String, config: AgentRuntimeConfig): String {
        val base = config.apiUrl.trim().ifBlank { "http://10.0.2.2:11434" }
        val response = postJson(
            url = "${base.trimEnd('/')}/api/chat",
            body = JSONObject()
                .put("model", config.model)
                .put("stream", false)
                .put("options", JSONObject().put("temperature", config.temperature))
                .put(
                    "messages",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", message)
                    )
                ),
            bearerToken = null
        )

        return response.optJSONObject("message")
            ?.optString("content")
            ?.takeIf { it.isNotBlank() }
            ?: "Ollama returned an empty response"
    }

    private fun sendOpenAiCompatibleChat(message: String, config: AgentRuntimeConfig): String {
        if (config.provider.trim().equals("openai", ignoreCase = true) && config.authMode.trim() == "oauth_token") {
            return sendOpenAICodexSubscriptionChat(message, config)
        }

        val base = when {
            config.apiUrl.trim().isNotBlank() -> config.apiUrl.trim()
            config.provider.trim().equals("openrouter", ignoreCase = true) -> "https://openrouter.ai/api/v1"
            config.provider.trim().equals("copilot", ignoreCase = true) ||
                config.provider.trim().equals("github-copilot", ignoreCase = true) -> "https://api.githubcopilot.com"
            else -> "https://api.openai.com/v1"
        }

        val token = credentialForBearer(config)
        if (token.isBlank()) {
            error("API token is required for ${config.provider}")
        }

        val response = postJson(
            url = "${base.trimEnd('/')}/chat/completions",
            body = JSONObject()
                .put("model", config.model)
                .put("temperature", config.temperature)
                .put(
                    "messages",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", message)
                    )
                ),
            bearerToken = token,
            extraHeaders = if (config.provider.trim().equals("copilot", ignoreCase = true) ||
                config.provider.trim().equals("github-copilot", ignoreCase = true)
            ) {
                val headers = mutableMapOf(
                    "Openai-Intent" to "conversation-edits",
                    "x-initiator" to "user"
                )
                if (config.enterpriseUrl.trim().isNotBlank()) {
                    headers["X-GitHub-Enterprise-Host"] = config.enterpriseUrl.trim()
                }
                headers
            } else {
                emptyMap()
            }
        )

        return response.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.takeIf { it.isNotBlank() }
            ?: "Provider returned an empty response"
    }

    private fun sendOpenAICodexSubscriptionChat(message: String, config: AgentRuntimeConfig): String {
        val token = refreshOpenAiAccessTokenIfNeeded(config)
        if (token.isBlank()) {
            error("OAuth access token is required for OpenAI subscription mode")
        }

        val endpoint = config.apiUrl.trim().ifBlank { "https://chatgpt.com/backend-api/codex/responses" }

        val response = postJson(
            url = endpoint,
            body = JSONObject()
                .put("model", config.model)
                .put("input", JSONArray().put(JSONObject().put("role", "user").put("content", message))),
            bearerToken = token,
            extraHeaders = mapOf(
                "ChatGPT-Account-Id" to config.accountId.trim()
            )
        )

        val outputText = response.optString("output_text", "")
        if (outputText.isNotBlank()) {
            return outputText
        }

        return response.optJSONArray("output")
            ?.optJSONObject(0)
            ?.optJSONArray("content")
            ?.optJSONObject(0)
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }
            ?: "OpenAI subscription endpoint returned an empty response"
    }

    private fun refreshOpenAiAccessTokenIfNeeded(config: AgentRuntimeConfig): String {
        val current = config.oauthAccessToken.trim()
        if (current.isBlank()) return ""
        val expires = config.oauthExpiresAtMs
        if (expires <= 0 || System.currentTimeMillis() < expires - 60_000) {
            return current
        }

        val refresh = config.oauthRefreshToken.trim()
        if (refresh.isBlank()) {
            return current
        }

        return runCatching {
            val response = postForm(
                url = "https://auth.openai.com/oauth/token",
                form = mapOf(
                    "grant_type" to "refresh_token",
                    "refresh_token" to refresh,
                    "client_id" to "app_EMoamEEZ73f0CkXaXp7hrann"
                )
            )
            response.optString("access_token").ifBlank { current }
        }.getOrDefault(current)
    }

    private fun sendAnthropicChat(message: String, config: AgentRuntimeConfig): String {
        val base = config.apiUrl.trim().ifBlank { "https://api.anthropic.com/v1" }
        val apiKey = credentialForApiKey(config)
        val oauthToken = credentialForBearer(config)

        if (apiKey.isBlank() && oauthToken.isBlank()) {
            error("API token is required for anthropic")
        }

        val useOauth = config.authMode.trim() == "oauth_token" && oauthToken.isNotBlank()

        val response = postJson(
            url = "${base.trimEnd('/')}/messages",
            body = JSONObject()
                .put("model", config.model)
                .put("max_tokens", 1024)
                .put("temperature", config.temperature)
                .put(
                    "messages",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", message)
                    )
                ),
            bearerToken = null,
            extraHeaders = mapOf(
                "x-api-key" to if (useOauth) "" else apiKey,
                "anthropic-version" to "2023-06-01",
                "Authorization" to if (useOauth) "Bearer $oauthToken" else "",
                "anthropic-beta" to if (useOauth) "oauth-2025-04-20" else ""
            )
        )

        val content = response.optJSONArray("content") ?: return "Anthropic returned empty content"
        if (content.length() == 0) return "Anthropic returned empty content"
        return content.optJSONObject(0)?.optString("text")?.ifBlank { "Anthropic returned empty text" }
            ?: "Anthropic returned empty text"
    }

    private fun sendGeminiChat(message: String, config: AgentRuntimeConfig): String {
        val base = config.apiUrl.trim().ifBlank { "https://generativelanguage.googleapis.com/v1beta" }
        val bearer = credentialForBearer(config)
        val token = credentialForApiKey(config)
        val useBearer = bearer.isNotBlank()
        if (token.isBlank()) {
            if (!useBearer) {
                error("API token or OAuth token is required for gemini")
            }
        }

        val modelPath = config.model.trim().ifBlank { "gemini-1.5-pro" }
        val url = if (useBearer) {
            "${base.trimEnd('/')}/models/$modelPath:generateContent"
        } else {
            "${base.trimEnd('/')}/models/$modelPath:generateContent?key=$token"
        }

        val response = postJson(
            url = url,
            body = JSONObject()
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", message))
                        )
                    )
                )
                .put(
                    "generationConfig",
                    JSONObject().put("temperature", config.temperature)
                ),
            bearerToken = if (useBearer) bearer else null
        )

        return response.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }
            ?: "Gemini returned an empty response"
    }

    private fun postJson(
        url: String,
        body: JSONObject,
        bearerToken: String?,
        extraHeaders: Map<String, String> = emptyMap()
    ): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 300_000
            setRequestProperty("Content-Type", "application/json")
            if (!bearerToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $bearerToken")
            }
            if (url.contains("openrouter.ai")) {
                setRequestProperty("HTTP-Referer", "https://mobileclaw.app")
                setRequestProperty("X-Title", "MobileClaw")
            }
            extraHeaders.forEach { (name, value) ->
                if (value.isNotBlank()) {
                    setRequestProperty(name, value)
                }
            }
            doOutput = true
        }

        connection.outputStream.use { out ->
            out.write(body.toString().toByteArray())
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val raw = BufferedReader(InputStreamReader(stream)).use { reader ->
            buildString {
                var line = reader.readLine()
                while (line != null) {
                    append(line)
                    line = reader.readLine()
                }
            }
        }

        if (code !in 200..299) {
            error("HTTP $code: $raw")
        }

        return JSONObject(raw)
    }

    private fun postForm(url: String, form: Map<String, String>): JSONObject {
        val body = form.entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            doOutput = true
        }

        connection.outputStream.use { it.write(body.toByteArray()) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val raw = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        if (code !in 200..299) {
            error("HTTP $code: $raw")
        }
        return JSONObject(raw)
    }

    private fun credentialForApiKey(config: AgentRuntimeConfig): String {
        return when (config.authMode.trim()) {
            "oauth_token" -> ""
            else -> config.apiKey.trim()
        }
    }

    private fun credentialForBearer(config: AgentRuntimeConfig): String {
        return when (config.authMode.trim()) {
            "oauth_token" -> config.oauthAccessToken.trim()
            else -> config.apiKey.trim()
        }
    }
}
