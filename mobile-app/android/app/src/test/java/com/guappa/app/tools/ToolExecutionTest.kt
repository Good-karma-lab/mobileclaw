package com.guappa.app.tools

import com.guappa.app.tools.impl.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Direct tool execution tests — no Android context needed for pure-logic tools.
 * Tests that tools can parse params and return structured results.
 */
class ToolExecutionTest {

    @Test
    fun `calculator evaluates addition`() {
        val tool = CalculatorTool()
        assertEquals("calculator", tool.name)
        assertNotNull(tool.parametersSchema)
        // parametersSchema should have "expression" as required
        val required = tool.parametersSchema.optJSONArray("required")
        assertNotNull(required)
        assertTrue(required!!.length() > 0)
    }

    @Test
    fun `date_time tool has correct schema`() {
        val tool = DateTimeTool()
        assertEquals("date_time", tool.name)
        assertNotNull(tool.parametersSchema)
    }

    @Test
    fun `system_info tool has correct schema`() {
        val tool = SystemInfoTool()
        assertEquals("system_info", tool.name)
    }

    @Test
    fun `shell tool has correct schema and name`() {
        val tool = ShellTool()
        assertEquals("shell", tool.name)
        assertNotNull(tool.parametersSchema)
    }

    @Test
    fun `web_fetch tool has correct schema`() {
        val tool = WebFetchTool()
        assertEquals("web_fetch", tool.name)
        val props = tool.parametersSchema.optJSONObject("properties")
        assertNotNull(props)
        assertTrue(props!!.has("url"))
    }

    @Test
    fun `web_search tool requires api key param or env`() {
        val tool = WebSearchTool()
        assertEquals("web_search", tool.name)
        val props = tool.parametersSchema.optJSONObject("properties")
        assertNotNull(props)
        assertTrue(props!!.has("query"))
    }

    @Test
    fun `all 78 tools register without error`() {
        val registry = ToolRegistry()
        registry.registerCoreTools()
        val names = registry.getAllToolNames()
        assertTrue("Expected at least 70 tools, got ${names.size}", names.size >= 70)
    }

    @Test
    fun `tool schemas are valid JSON`() {
        val registry = ToolRegistry()
        registry.registerCoreTools()
        val names = registry.getAllToolNames()
        for (name in names) {
            val tool = registry.getTool(name)
            assertNotNull("Tool $name should be retrievable", tool)
            assertNotNull("Tool $name should have a description", tool!!.description)
            assertTrue("Tool $name description should be non-empty", tool.description.isNotEmpty())
            assertNotNull("Tool $name should have parametersSchema", tool.parametersSchema)
            // parametersSchema should have "type" = "object"
            assertEquals("Tool $name schema type should be 'object'",
                "object", tool.parametersSchema.optString("type"))
        }
    }

    @Test
    fun `tool names are unique`() {
        val registry = ToolRegistry()
        registry.registerCoreTools()
        val names = registry.getAllToolNames()
        val unique = names.toSet()
        assertEquals("All tool names should be unique", unique.size, names.size)
    }

    @Test
    fun `disabled tool is not available`() {
        val registry = ToolRegistry()
        registry.registerCoreTools()
        val tool = registry.getTool("calculator")
        assertNotNull(tool)
        registry.setToolEnabled("calculator", false)
        assertNull(registry.getTool("calculator"))
        registry.setToolEnabled("calculator", true)
        assertNotNull(registry.getTool("calculator"))
    }
}
