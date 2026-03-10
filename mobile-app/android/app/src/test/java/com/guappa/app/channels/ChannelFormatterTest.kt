package com.guappa.app.channels

import org.junit.Assert.*
import org.junit.Test

class ChannelFormatterTest {

    @Test
    fun `telegram truncates long messages`() {
        val long = "a".repeat(5000)
        val result = ChannelFormatter.format("telegram", long)
        assertTrue(result.length <= 4001) // 4000 + ellipsis
    }

    @Test
    fun `discord truncates to 2000 chars`() {
        val long = "a".repeat(3000)
        val result = ChannelFormatter.format("discord", long)
        assertTrue(result.length <= 1951)
    }

    @Test
    fun `slack converts bold markdown`() {
        val result = ChannelFormatter.format("slack", "This is **bold** text")
        assertEquals("This is *bold* text", result)
    }

    @Test
    fun `slack converts links`() {
        val result = ChannelFormatter.format("slack", "Visit [Google](https://google.com)")
        assertEquals("Visit <https://google.com|Google>", result)
    }

    @Test
    fun `sms strips markdown`() {
        val result = ChannelFormatter.format("sms", "**Bold** and *italic* and `code`")
        assertEquals("Bold and italic and code", result)
    }

    @Test
    fun `sms converts links to text url pairs`() {
        val result = ChannelFormatter.format("sms", "[Click here](https://example.com)")
        assertEquals("Click here: https://example.com", result)
    }

    @Test
    fun `email passes through unchanged`() {
        val text = "# Header\n**Bold** [link](url)"
        assertEquals(text, ChannelFormatter.format("email", text))
    }

    @Test
    fun `unknown channel passes through unchanged`() {
        val text = "Hello world"
        assertEquals(text, ChannelFormatter.format("unknown", text))
    }
}
