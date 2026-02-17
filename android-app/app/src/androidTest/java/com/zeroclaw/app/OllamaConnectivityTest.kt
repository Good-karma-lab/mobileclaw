package com.zeroclaw.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class OllamaConnectivityTest {
    @Test
    fun ollamaServerAndModelAreReachable() = runBlocking {
        val connection = (URL("http://10.0.2.2:11434/api/tags").openConnection() as HttpURLConnection)
            .apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 30_000
            }

        val code = connection.responseCode
        val body = connection.inputStream.bufferedReader().readText()
        assertTrue(code in 200..299)
        assertTrue(body.contains("gpt-oss:20b"))
    }
}
