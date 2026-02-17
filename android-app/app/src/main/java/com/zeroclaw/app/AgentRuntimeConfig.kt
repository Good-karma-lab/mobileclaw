package com.zeroclaw.app

data class AgentRuntimeConfig(
    val provider: String = "ollama",
    val model: String = "gpt-oss:20b",
    val apiUrl: String = "http://10.0.2.2:11434",
    val apiKey: String = "",
    val authMode: String = "api_key",
    val oauthAccessToken: String = "",
    val oauthRefreshToken: String = "",
    val oauthExpiresAtMs: Long = 0,
    val accountId: String = "",
    val enterpriseUrl: String = "",
    val temperature: Double = 0.1
)
