package com.guappa.app.agent

import org.junit.Assert.*
import org.junit.Test

class MessageBusTest {

    @Test
    fun `BusMessage subtypes have correct defaults`() {
        val user = BusMessage.UserMessage(text = "hi")
        assertEquals(MessagePriority.NORMAL, user.priority)
        assertTrue(user.timestamp > 0)
        assertTrue(user.imageAttachments.isEmpty())

        val agent = BusMessage.AgentMessage(text = "hello")
        assertFalse(agent.isStreaming)
        assertFalse(agent.isComplete)

        val tool = BusMessage.ToolResult(toolName = "calc", result = "42", success = true)
        assertEquals(MessagePriority.NORMAL, tool.priority)

        val trigger = BusMessage.TriggerEvent(trigger = "sms")
        assertEquals(MessagePriority.URGENT, trigger.priority)
    }

    @Test
    fun `SystemEvent has correct type`() {
        val event = BusMessage.SystemEvent(type = "network_changed", data = mapOf("state" to "wifi"))
        assertEquals("network_changed", event.type)
        assertEquals(MessagePriority.NORMAL, event.priority)
        assertEquals("wifi", event.data["state"])
    }

    @Test
    fun `UserMessage supports image attachments`() {
        val msg = BusMessage.UserMessage(
            text = "look at this",
            imageAttachments = listOf("/path/img1.jpg", "/path/img2.jpg")
        )
        assertEquals(2, msg.imageAttachments.size)
    }

    @Test
    fun `AgentMessage streaming state`() {
        val streaming = BusMessage.AgentMessage(text = "chunk", isStreaming = true, isComplete = false)
        assertTrue(streaming.isStreaming)
        assertFalse(streaming.isComplete)

        val complete = BusMessage.AgentMessage(text = "done", isStreaming = false, isComplete = true)
        assertFalse(complete.isStreaming)
        assertTrue(complete.isComplete)
    }
}
