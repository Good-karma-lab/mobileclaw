package com.guappa.app.tools

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ToolResult sealed class and serialization.
 */
class ToolResultTest {

    @Test
    fun `success result serializes correctly`() {
        val result = ToolResult.Success(content = "operation completed")
        val json = result.toJSON()
        assertEquals("success", json.getString("status"))
        assertEquals("operation completed", json.getString("content"))
    }

    @Test
    fun `success result with data includes data`() {
        val data = JSONObject().apply { put("key", "value") }
        val result = ToolResult.Success(content = "done", data = data)
        val json = result.toJSON()
        assertEquals("success", json.getString("status"))
        assertTrue(json.has("data"))
        assertEquals("value", json.getJSONObject("data").getString("key"))
    }

    @Test
    fun `error result serializes correctly`() {
        val result = ToolResult.Error(message = "permission denied", code = "PERM_DENIED")
        val json = result.toJSON()
        assertEquals("error", json.getString("status"))
        assertEquals("permission denied", json.getString("message"))
        assertEquals("PERM_DENIED", json.getString("code"))
        assertFalse(json.getBoolean("retryable"))
    }

    @Test
    fun `retryable error includes retryable flag`() {
        val result = ToolResult.Error(
            message = "rate limited",
            code = "RATE_LIMIT",
            retryable = true
        )
        val json = result.toJSON()
        assertTrue(json.getBoolean("retryable"))
    }

    @Test
    fun `needs approval serializes correctly`() {
        val result = ToolResult.NeedsApproval(
            description = "Send SMS to contact",
            approvalType = "user_confirm"
        )
        val json = result.toJSON()
        assertEquals("needs_approval", json.getString("status"))
        assertEquals("Send SMS to contact", json.getString("description"))
        assertEquals("user_confirm", json.getString("approval_type"))
    }

    @Test
    fun `default error code is TOOL_ERROR`() {
        val result = ToolResult.Error(message = "generic failure")
        assertEquals("TOOL_ERROR", result.code)
    }

    @Test
    fun `default approval type is user_confirm`() {
        val result = ToolResult.NeedsApproval(description = "test")
        assertEquals("user_confirm", result.approvalType)
    }
}
