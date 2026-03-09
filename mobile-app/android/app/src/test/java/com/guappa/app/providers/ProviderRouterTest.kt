package com.guappa.app.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProviderRouterTest {
    private lateinit var router: ProviderRouter

    private fun fakeProvider(
        id: String,
        name: String = id,
        caps: Set<CapabilityType> = setOf(CapabilityType.TEXT_CHAT),
        healthResult: Boolean = true,
        chatContent: String = "response from $id"
    ): Provider = object : Provider {
        override val id = id
        override val name = name
        override val capabilities = caps
        override suspend fun fetchModels() = listOf(
            ModelInfo(id = "$id-model", name = "$name Model", provider = id, capabilities = caps)
        )
        override suspend fun chat(
            messages: List<ChatMessage>, tools: List<JSONObject>?, model: String?, temperature: Double
        ) = ChatResponse(content = chatContent, toolCalls = null, finishReason = "stop", usage = null)
        override fun streamChat(
            messages: List<ChatMessage>, tools: List<JSONObject>?, model: String?, temperature: Double
        ): Flow<String> = emptyFlow()
        override suspend fun healthCheck() = healthResult
    }

    @Before
    fun setup() {
        router = ProviderRouter()
    }

    @Test
    fun `registerProvider adds provider and auto-maps capabilities`() {
        val p = fakeProvider("openai", caps = setOf(CapabilityType.TEXT_CHAT, CapabilityType.VISION))
        router.registerProvider(p)

        assertEquals(p, router.getProvider("openai"))
        assertEquals(p, router.getProviderForCapability(CapabilityType.TEXT_CHAT))
        assertEquals(p, router.getProviderForCapability(CapabilityType.VISION))
    }

    @Test
    fun `first registered provider wins capability auto-map`() {
        val p1 = fakeProvider("p1", caps = setOf(CapabilityType.TEXT_CHAT))
        val p2 = fakeProvider("p2", caps = setOf(CapabilityType.TEXT_CHAT))
        router.registerProvider(p1)
        router.registerProvider(p2)

        assertEquals(p1, router.getProviderForCapability(CapabilityType.TEXT_CHAT))
    }

    @Test
    fun `setCapabilityProvider overrides auto-map`() {
        val p1 = fakeProvider("p1", caps = setOf(CapabilityType.TEXT_CHAT))
        val p2 = fakeProvider("p2", caps = setOf(CapabilityType.TEXT_CHAT))
        router.registerProvider(p1)
        router.registerProvider(p2)
        router.setCapabilityProvider(CapabilityType.TEXT_CHAT, "p2")

        assertEquals(p2, router.getProviderForCapability(CapabilityType.TEXT_CHAT))
    }

    @Test
    fun `setCapabilityProvider ignores unregistered provider`() {
        val p1 = fakeProvider("p1", caps = setOf(CapabilityType.TEXT_CHAT))
        router.registerProvider(p1)
        router.setCapabilityProvider(CapabilityType.TEXT_CHAT, "nonexistent")

        // Should remain p1
        assertEquals(p1, router.getProviderForCapability(CapabilityType.TEXT_CHAT))
    }

    @Test
    fun `getProvider returns null for unknown`() {
        assertNull(router.getProvider("unknown"))
    }

    @Test
    fun `getProviderForCapability returns null when no mapping`() {
        assertNull(router.getProviderForCapability(CapabilityType.IMAGE_GENERATION))
    }

    @Test
    fun `listProviders returns registered IDs`() {
        router.registerProvider(fakeProvider("a"))
        router.registerProvider(fakeProvider("b"))

        val list = router.listProviders()
        assertTrue(list.contains("a"))
        assertTrue(list.contains("b"))
        assertEquals(2, list.size)
    }

    @Test
    fun `chat routes to capability provider`() = runBlocking {
        router.registerProvider(fakeProvider("p1", chatContent = "hello from p1"))
        val response = router.chat(listOf(ChatMessage("user", "hi")))
        assertEquals("hello from p1", response.content)
    }

    @Test
    fun `chat throws when no provider for capability`() = runBlocking {
        try {
            router.chat(listOf(ChatMessage("user", "hi")), capability = CapabilityType.IMAGE_GENERATION)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("No provider registered"))
        }
    }

    @Test
    fun `healthCheck returns true for healthy provider`() = runBlocking {
        router.registerProvider(fakeProvider("healthy", healthResult = true))
        assertTrue(router.healthCheck("healthy"))
    }

    @Test
    fun `healthCheck returns false for unknown provider`() = runBlocking {
        assertFalse(router.healthCheck("unknown"))
    }

    @Test
    fun `fetchModels returns cached results on second call`() = runBlocking {
        var callCount = 0
        val p = object : Provider {
            override val id = "test"
            override val name = "Test"
            override val capabilities = setOf(CapabilityType.TEXT_CHAT)
            override suspend fun fetchModels(): List<ModelInfo> {
                callCount++
                return listOf(ModelInfo("m1", "Model 1", "test", capabilities))
            }
            override suspend fun chat(messages: List<ChatMessage>, tools: List<JSONObject>?, model: String?, temperature: Double) =
                ChatResponse(null, null, null, null)
            override fun streamChat(messages: List<ChatMessage>, tools: List<JSONObject>?, model: String?, temperature: Double) = emptyFlow<String>()
            override suspend fun healthCheck() = true
        }
        router.registerProvider(p)

        val models1 = router.fetchModels("test")
        val models2 = router.fetchModels("test")
        assertEquals(1, models1.size)
        assertEquals(1, models2.size)
        assertEquals(1, callCount) // Should be cached
    }

    @Test
    fun `fetchModels returns empty for unknown provider`() = runBlocking {
        assertTrue(router.fetchModels("unknown").isEmpty())
    }
}
