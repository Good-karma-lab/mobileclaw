package com.guappa.app.tools

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ToolRegistryTest {
    private lateinit var registry: ToolRegistry

    private fun fakeTool(name: String, desc: String = "Test tool"): Tool = object : Tool {
        override val name = name
        override val description = desc
        override val parametersSchema = JSONObject().put("type", "object")
        override val requiredPermissions = emptyList<String>()
        override fun isAvailable(context: android.content.Context) = true
        override suspend fun execute(params: JSONObject, context: android.content.Context) =
            ToolResult.Success("ok")
    }

    @Before
    fun setup() {
        registry = ToolRegistry()
    }

    @Test
    fun `register and retrieve tool`() {
        val tool = fakeTool("my_tool")
        registry.register(tool)
        assertEquals(tool, registry.getTool("my_tool"))
    }

    @Test
    fun `getTool returns null for unknown`() {
        assertNull(registry.getTool("nonexistent"))
    }

    @Test
    fun `disabled tool returns null from getTool`() {
        registry.register(fakeTool("my_tool"))
        registry.setToolEnabled("my_tool", false)
        assertNull(registry.getTool("my_tool"))
    }

    @Test
    fun `re-enabling tool makes it available again`() {
        registry.register(fakeTool("my_tool"))
        registry.setToolEnabled("my_tool", false)
        registry.setToolEnabled("my_tool", true)
        assertNotNull(registry.getTool("my_tool"))
    }

    @Test
    fun `getAllToolNames includes disabled tools`() {
        registry.register(fakeTool("a"))
        registry.register(fakeTool("b"))
        registry.setToolEnabled("b", false)
        val all = registry.getAllToolNames()
        assertTrue(all.contains("a"))
        assertTrue(all.contains("b"))
    }

    @Test
    fun `getEnabledToolNames excludes disabled tools`() {
        registry.register(fakeTool("a"))
        registry.register(fakeTool("b"))
        registry.setToolEnabled("b", false)
        val enabled = registry.getEnabledToolNames()
        assertTrue(enabled.contains("a"))
        assertFalse(enabled.contains("b"))
    }

    @Test
    fun `registerCoreTools registers many tools`() {
        registry.registerCoreTools()
        val names = registry.getAllToolNames()
        assertTrue(names.size > 50) // We know there are 70+ tools
        assertTrue(names.contains("set_alarm"))
        assertTrue(names.contains("web_search"))
        assertTrue(names.contains("calculator"))
    }

    @Test
    fun `overwriting a tool replaces it`() {
        registry.register(fakeTool("my_tool", "v1"))
        registry.register(fakeTool("my_tool", "v2"))
        assertEquals("v2", registry.getTool("my_tool")?.description)
    }
}
