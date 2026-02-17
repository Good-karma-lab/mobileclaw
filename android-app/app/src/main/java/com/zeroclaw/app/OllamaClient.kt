package com.zeroclaw.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class OllamaClient(
    private val baseUrl: String = "http://10.0.2.2:11434",
    private val model: String = "gpt-oss:20b"
) {
    suspend fun chat(message: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("${baseUrl.trimEnd('/')}/api/chat")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 120_000
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }

            val payload = JSONObject()
                .put("model", model)
                .put("stream", false)
                .put("options", JSONObject().put("temperature", 0.0))
                .put(
                    "messages",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", message)
                    )
                )
                .toString()

            connection.outputStream.use { out ->
                out.write(payload.toByteArray())
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = BufferedReader(InputStreamReader(stream)).use { reader ->
                buildString {
                    var line = reader.readLine()
                    while (line != null) {
                        append(line)
                        line = reader.readLine()
                    }
                }
            }

            if (code !in 200..299) {
                error("Ollama HTTP $code: $response")
            }

            val json = JSONObject(response)
            json.optJSONObject("message")
                ?.optString("content")
                ?.takeIf { it.isNotBlank() }
                ?: "Ollama returned an empty message"
        }
    }
}
