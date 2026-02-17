package com.zeroclaw.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

private const val OPENAI_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
private const val OPENAI_ISSUER = "https://auth.openai.com"
private const val COPILOT_CLIENT_ID = "Ov23li8tweQw6odWQebz"

data class OAuthDeviceSession(
    val provider: String,
    val verificationUrl: String,
    val userCode: String,
    val deviceCode: String,
    val intervalSeconds: Int,
    val metadata: Map<String, String> = emptyMap()
)

data class OAuthTokenResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMs: Long,
    val accountId: String = "",
    val enterpriseUrl: String = ""
)

class OAuthManager {
    suspend fun startOpenAIDeviceFlow(): OAuthDeviceSession = withContext(Dispatchers.IO) {
        val response = postJson(
            url = "$OPENAI_ISSUER/api/accounts/deviceauth/usercode",
            body = JSONObject().put("client_id", OPENAI_CLIENT_ID)
        )

        OAuthDeviceSession(
            provider = "openai",
            verificationUrl = "$OPENAI_ISSUER/codex/device",
            userCode = response.optString("user_code"),
            deviceCode = response.optString("device_auth_id"),
            intervalSeconds = response.optString("interval", "5").toIntOrNull()?.coerceAtLeast(1) ?: 5
        )
    }

    suspend fun completeOpenAIDeviceFlow(session: OAuthDeviceSession): OAuthTokenResult = withContext(Dispatchers.IO) {
        while (true) {
            val response = postJson(
                url = "$OPENAI_ISSUER/api/accounts/deviceauth/token",
                body = JSONObject()
                    .put("device_auth_id", session.deviceCode)
                    .put("user_code", session.userCode)
            )

            if (response.has("authorization_code") && response.has("code_verifier")) {
                val token = postForm(
                    url = "$OPENAI_ISSUER/oauth/token",
                    form = mapOf(
                        "grant_type" to "authorization_code",
                        "code" to response.optString("authorization_code"),
                        "redirect_uri" to "$OPENAI_ISSUER/deviceauth/callback",
                        "client_id" to OPENAI_CLIENT_ID,
                        "code_verifier" to response.optString("code_verifier")
                    )
                )

                val access = token.optString("access_token")
                val refresh = token.optString("refresh_token")
                val expiresIn = token.optLong("expires_in", 3600L)
                val accountId = extractAccountId(token)

                return@withContext OAuthTokenResult(
                    accessToken = access,
                    refreshToken = refresh,
                    expiresAtMs = System.currentTimeMillis() + expiresIn * 1000,
                    accountId = accountId
                )
            }

            delay((session.intervalSeconds * 1000L) + 3000L)
        }

        error("OpenAI OAuth flow terminated unexpectedly")
    }

    suspend fun startCopilotDeviceFlow(enterpriseUrl: String): OAuthDeviceSession = withContext(Dispatchers.IO) {
        val domain = normalizeDomain(enterpriseUrl)
        val base = "https://$domain"
        val response = postJson(
            url = "$base/login/device/code",
            body = JSONObject()
                .put("client_id", COPILOT_CLIENT_ID)
                .put("scope", "read:user")
        )

        OAuthDeviceSession(
            provider = "copilot",
            verificationUrl = response.optString("verification_uri"),
            userCode = response.optString("user_code"),
            deviceCode = response.optString("device_code"),
            intervalSeconds = response.optInt("interval", 5).coerceAtLeast(1),
            metadata = mapOf("domain" to domain)
        )
    }

    suspend fun completeCopilotDeviceFlow(session: OAuthDeviceSession): OAuthTokenResult = withContext(Dispatchers.IO) {
        val domain = session.metadata["domain"].orEmpty().ifBlank { "github.com" }
        val tokenUrl = "https://$domain/login/oauth/access_token"

        while (true) {
            val response = postJson(
                url = tokenUrl,
                body = JSONObject()
                    .put("client_id", COPILOT_CLIENT_ID)
                    .put("device_code", session.deviceCode)
                    .put("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                extraHeaders = mapOf("Accept" to "application/json")
            )

            val access = response.optString("access_token")
            if (access.isNotBlank()) {
                return@withContext OAuthTokenResult(
                    accessToken = access,
                    refreshToken = access,
                    expiresAtMs = 0,
                    enterpriseUrl = if (domain == "github.com") "" else domain
                )
            }

            val error = response.optString("error")
            if (error.isNotBlank() && error != "authorization_pending" && error != "slow_down") {
                error("Copilot OAuth failed: $error")
            }

            val interval = response.optInt("interval", session.intervalSeconds).coerceAtLeast(session.intervalSeconds)
            delay((interval * 1000L) + 3000L)
        }

        error("Copilot OAuth flow terminated unexpectedly")
    }

    private fun normalizeDomain(urlOrDomain: String): String {
        val trimmed = urlOrDomain.trim()
        if (trimmed.isBlank()) return "github.com"
        return trimmed.removePrefix("https://").removePrefix("http://").trimEnd('/')
    }

    private fun postJson(url: String, body: JSONObject, extraHeaders: Map<String, String> = emptyMap()): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "mobileclaw-android")
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
            doOutput = true
        }

        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val raw = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        if (code !in 200..299 && raw.isBlank()) {
            error("OAuth request failed with HTTP $code")
        }
        return JSONObject(if (raw.isBlank()) "{}" else raw)
    }

    private fun postForm(url: String, form: Map<String, String>): JSONObject {
        val body = form.entries.joinToString("&") { "${it.key}=${urlEncode(it.value)}" }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", "mobileclaw-android")
            doOutput = true
        }

        connection.outputStream.use { it.write(body.toByteArray()) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val raw = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        if (code !in 200..299) {
            error("OAuth token exchange failed HTTP $code: $raw")
        }
        return JSONObject(raw)
    }

    private fun urlEncode(value: String): String {
        return java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    private fun extractAccountId(tokenResponse: JSONObject): String {
        val idToken = tokenResponse.optString("id_token")
        if (idToken.isBlank()) return ""
        return runCatching {
            val claims = idToken.split(".").getOrNull(1).orEmpty()
            val decoded = String(java.util.Base64.getUrlDecoder().decode(claims))
            val json = JSONObject(decoded)
            json.optString("chatgpt_account_id").ifBlank {
                json.optJSONObject("https://api.openai.com/auth")?.optString("chatgpt_account_id").orEmpty()
            }
        }.getOrDefault("")
    }
}
