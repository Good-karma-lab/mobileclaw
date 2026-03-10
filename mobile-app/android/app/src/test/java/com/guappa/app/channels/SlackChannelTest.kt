package com.guappa.app.channels

import org.junit.Assert.*
import org.junit.Test

class SlackChannelTest {

    @Test
    fun `channel id is slack`() {
        val channel = SlackChannel(webhookUrl = "https://hooks.slack.com/services/T00/B00/xxx")
        assertEquals("slack", channel.id)
        assertEquals("Slack", channel.name)
    }

    @Test
    fun `not configured with blank webhook`() {
        val channel = SlackChannel(webhookUrl = "")
        assertFalse(channel.isConfigured)
    }

    @Test
    fun `configured with webhook url`() {
        val channel = SlackChannel(webhookUrl = "https://hooks.slack.com/services/T00/B00/xxx")
        assertTrue(channel.isConfigured)
    }
}
