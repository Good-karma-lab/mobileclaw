package com.zeroclaw.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RustBridgeConnectivityTest {
    @Test
    fun rustBridgeRejectsUnsupportedProviderWithoutFallback() = runBlocking {
        val bridge = RustAgentBridge()
        val result = runCatching {
            bridge.sendMessage(
                "health check",
                AgentRuntimeConfig(provider = "unsupported_provider")
            )
        }
        assertFalse(result.isSuccess)
    }
}
