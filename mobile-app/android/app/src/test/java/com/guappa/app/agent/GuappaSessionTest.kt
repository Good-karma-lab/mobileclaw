package com.guappa.app.agent

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class GuappaSessionTest {

    @Test
    fun `new session has IDLE state`() {
        val session = GuappaSession()
        assertEquals(SessionState.IDLE, session.state.value)
    }

    @Test
    fun `session types are available`() {
        val chatSession = GuappaSession(type = SessionType.CHAT)
        val bgSession = GuappaSession(type = SessionType.BACKGROUND_TASK)
        val triggerSession = GuappaSession(type = SessionType.TRIGGER)
        val sysSession = GuappaSession(type = SessionType.SYSTEM)
        assertEquals(SessionType.CHAT, chatSession.type)
        assertEquals(SessionType.BACKGROUND_TASK, bgSession.type)
        assertEquals(SessionType.TRIGGER, triggerSession.type)
        assertEquals(SessionType.SYSTEM, sysSession.type)
    }

    @Test
    fun `addMessage updates state to ACTIVE`() {
        val session = GuappaSession()
        session.addMessage(Message(role = "user", content = "hello", tokenCount = 5))
        assertEquals(SessionState.ACTIVE, session.state.value)
        assertEquals(1, session.messages.size)
    }

    @Test
    fun `contextUsageRatio calculated correctly`() {
        val session = GuappaSession(maxTokens = 100)
        session.addMessage(Message(role = "user", content = "test", tokenCount = 25))
        assertEquals(0.25f, session.contextUsageRatio, 0.01f)
    }

    @Test
    fun `needsCompaction respects threshold`() {
        val session = GuappaSession(maxTokens = 100)
        session.addMessage(Message(role = "user", content = "test", tokenCount = 80))
        assertTrue(session.needsCompaction(0.7f))
        assertFalse(session.needsCompaction(0.9f))
    }

    @Test
    fun `compactWith replaces old messages with summary`() {
        val session = GuappaSession(maxTokens = 1000)
        for (i in 1..10) {
            session.addMessage(Message(role = "user", content = "msg $i", tokenCount = 10))
        }
        session.compactWith("Summary of conversation", keepRecent = 3)
        assertEquals(4, session.messages.size) // 1 summary + 3 recent
        assertEquals("[Conversation summary]\nSummary of conversation", session.messages[0].content)
    }

    @Test
    fun `TTL expiration works`() {
        val session = GuappaSession(ttlMs = 1) // 1ms TTL
        session.addMessage(Message(role = "user", content = "test"))
        Thread.sleep(10)
        assertTrue(session.isExpired)
    }

    @Test
    fun `zero TTL never expires`() {
        val session = GuappaSession(ttlMs = 0)
        assertFalse(session.isExpired)
    }

    @Test
    fun `checkpoint and restore roundtrip`() {
        val session = GuappaSession(type = SessionType.TRIGGER, maxTokens = 50000, ttlMs = 60000)
        session.addMessage(Message(role = "user", content = "hello world", tokenCount = 3))
        session.addMessage(Message(role = "assistant", content = "hi there", tokenCount = 2))

        val json = session.checkpoint()
        val restored = GuappaSession.fromCheckpoint(json)

        assertEquals(session.id, restored.id)
        assertEquals(SessionType.TRIGGER, restored.type)
        assertEquals(60000L, restored.ttlMs)
        assertEquals(2, restored.messages.size)
        assertEquals("hello world", restored.messages[0].content)
    }

    @Test
    fun `Message toJson and fromJson roundtrip`() {
        val msg = Message(
            role = "user",
            content = "test message",
            tokenCount = 5,
            imageAttachments = listOf("/path/to/img.jpg")
        )
        val json = msg.toJson()
        val restored = Message.fromJson(json)

        assertEquals(msg.role, restored.role)
        assertEquals(msg.content, restored.content)
        assertEquals(msg.tokenCount, restored.tokenCount)
        assertEquals(1, restored.imageAttachments.size)
        assertTrue(restored.hasImages)
    }

    @Test
    fun `close sets state to CLOSED`() {
        val session = GuappaSession()
        session.close()
        assertEquals(SessionState.CLOSED, session.state.value)
    }

    @Test
    fun `getContextMessages includes system prompt`() {
        val session = GuappaSession()
        session.addMessage(Message(role = "user", content = "hi"))
        val ctx = session.getContextMessages("You are Guappa")
        assertEquals("system", ctx[0].role)
        assertEquals("You are Guappa", ctx[0].content)
        assertEquals(2, ctx.size)
    }
}
