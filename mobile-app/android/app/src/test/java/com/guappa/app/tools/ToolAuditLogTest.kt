package com.guappa.app.tools

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ToolAuditLogTest {
    private lateinit var log: ToolAuditLog

    private fun makeExec(tool: String = "test_tool", session: String = "s1", ts: Long = System.currentTimeMillis()) =
        ToolExecution(
            toolName = tool,
            params = """{"key":"value"}""",
            result = "ok",
            timestamp = ts,
            durationMs = 100,
            sessionId = session
        )

    @Before
    fun setup() {
        log = ToolAuditLog(maxBufferSize = 10)
    }

    @Test
    fun `starts empty`() {
        assertEquals(0, log.size())
        assertTrue(log.getRecent().isEmpty())
    }

    @Test
    fun `log adds execution`() {
        log.log(makeExec())
        assertEquals(1, log.size())
    }

    @Test
    fun `getRecent returns limited results`() {
        repeat(5) { log.log(makeExec()) }
        assertEquals(3, log.getRecent(3).size)
        assertEquals(5, log.getRecent(10).size)
    }

    @Test
    fun `getByTool filters by tool name`() {
        log.log(makeExec(tool = "a"))
        log.log(makeExec(tool = "b"))
        log.log(makeExec(tool = "a"))
        assertEquals(2, log.getByTool("a").size)
        assertEquals(1, log.getByTool("b").size)
    }

    @Test
    fun `getBySession filters by session`() {
        log.log(makeExec(session = "s1"))
        log.log(makeExec(session = "s2"))
        log.log(makeExec(session = "s1"))
        assertEquals(2, log.getBySession("s1").size)
    }

    @Test
    fun `buffer evicts oldest when full`() {
        repeat(15) { i -> log.log(makeExec(tool = "tool_$i")) }
        assertEquals(10, log.size()) // max is 10
        // oldest entries should be evicted
        assertTrue(log.getByTool("tool_0").isEmpty())
        assertFalse(log.getByTool("tool_14").isEmpty())
    }

    @Test
    fun `clear empties buffer`() {
        repeat(5) { log.log(makeExec()) }
        log.clear()
        assertEquals(0, log.size())
    }

    @Test
    fun `toJson exports all entries`() {
        repeat(3) { log.log(makeExec(tool = "t$it")) }
        val json = log.toJson()
        assertEquals(3, json.getInt("total_count"))
        assertEquals(10, json.getInt("max_buffer_size"))
        assertTrue(json.has("exported_at"))
        assertEquals(3, json.getJSONArray("executions").length())
    }

    @Test
    fun `toJson entry contains all fields`() {
        log.log(makeExec(tool = "my_tool", session = "sess_1"))
        val entry = log.toJson().getJSONArray("executions").getJSONObject(0)
        assertEquals("my_tool", entry.getString("tool_name"))
        assertEquals("sess_1", entry.getString("session_id"))
        assertTrue(entry.has("params"))
        assertTrue(entry.has("result"))
        assertTrue(entry.has("timestamp"))
        assertTrue(entry.has("duration_ms"))
    }
}
