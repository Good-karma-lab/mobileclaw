package com.guappa.app.channels

import org.junit.Assert.*
import org.junit.Test

class DiscordChannelTest {

    @Test
    fun `channel id is discord`() {
        val channel = DiscordChannel(webhookUrl = "https://example.com/webhook")
        assertEquals("discord", channel.id)
        assertEquals("Discord", channel.name)
    }

    @Test
    fun `not configured with blank webhook`() {
        val channel = DiscordChannel(webhookUrl = "")
        assertFalse(channel.isConfigured)
    }

    @Test
    fun `configured with webhook url`() {
        val channel = DiscordChannel(webhookUrl = "https://discord.com/api/webhooks/123/abc")
        assertTrue(channel.isConfigured)
    }
}
