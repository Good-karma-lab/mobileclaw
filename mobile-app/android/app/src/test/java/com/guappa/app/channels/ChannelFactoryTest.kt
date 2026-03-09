package com.guappa.app.channels

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ChannelFactory — testing supported types and creation.
 */
class ChannelFactoryTest {

    @Test
    fun `supported types includes all expected channels`() {
        val types = ChannelFactory.supportedTypes
        assertTrue(types.contains("telegram"))
        assertTrue(types.contains("discord"))
        assertTrue(types.contains("slack"))
        assertTrue(types.contains("email"))
        assertTrue(types.contains("whatsapp"))
        assertTrue(types.contains("signal"))
        assertTrue(types.contains("matrix"))
        assertTrue(types.contains("sms"))
    }

    @Test
    fun `supported types count is 8`() {
        assertEquals(8, ChannelFactory.supportedTypes.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown channel type throws`() {
        ChannelFactory.createChannel("nonexistent", emptyMap())
    }

    @Test
    fun `telegram channel created with correct ID`() {
        val channel = ChannelFactory.createChannel("telegram", mapOf(
            "botToken" to "test_token",
            "chatId" to "12345"
        ))
        assertEquals("telegram", channel.id)
        assertEquals("Telegram", channel.name)
    }

    @Test
    fun `discord channel created with correct ID`() {
        val channel = ChannelFactory.createChannel("discord", mapOf(
            "webhookUrl" to "https://discord.com/api/webhooks/test"
        ))
        assertEquals("discord", channel.id)
    }

    @Test
    fun `slack channel created with correct ID`() {
        val channel = ChannelFactory.createChannel("slack", mapOf(
            "webhookUrl" to "https://hooks.slack.com/test"
        ))
        assertEquals("slack", channel.id)
    }

    @Test
    fun `sms channel created with correct ID`() {
        val channel = ChannelFactory.createChannel("sms", mapOf(
            "recipientPhone" to "+1234567890"
        ))
        assertEquals("sms", channel.id)
    }

    @Test
    fun `unconfigured telegram channel reports not configured`() {
        val channel = ChannelFactory.createChannel("telegram", mapOf(
            "botToken" to "",
            "chatId" to ""
        ))
        assertFalse(channel.isConfigured)
    }

    @Test
    fun `configured telegram channel reports configured`() {
        val channel = ChannelFactory.createChannel("telegram", mapOf(
            "botToken" to "valid_token",
            "chatId" to "12345"
        ))
        assertTrue(channel.isConfigured)
    }
}
