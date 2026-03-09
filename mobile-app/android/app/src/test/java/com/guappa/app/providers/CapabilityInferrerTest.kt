package com.guappa.app.providers

import org.junit.Assert.*
import org.junit.Test

class CapabilityInferrerTest {

    @Test
    fun `all models get TEXT_CHAT`() {
        val caps = CapabilityInferrer.infer("some-random-model")
        assertTrue(caps.contains(CapabilityType.TEXT_CHAT))
    }

    @Test
    fun `gpt-4o gets vision and tool use`() {
        val caps = CapabilityInferrer.infer("gpt-4o")
        assertTrue(caps.contains(CapabilityType.VISION))
        assertTrue(caps.contains(CapabilityType.TOOL_USE))
        assertTrue(caps.contains(CapabilityType.STREAMING))
    }

    @Test
    fun `claude-3-haiku gets vision and tool use`() {
        val caps = CapabilityInferrer.infer("claude-3-haiku")
        assertTrue(caps.contains(CapabilityType.VISION))
        assertTrue(caps.contains(CapabilityType.TOOL_USE))
    }

    @Test
    fun `gemini models get vision`() {
        val caps = CapabilityInferrer.infer("gemini-2.0-flash")
        assertTrue(caps.contains(CapabilityType.VISION))
        assertTrue(caps.contains(CapabilityType.TOOL_USE))
    }

    @Test
    fun `dall-e gets image generation, no streaming`() {
        val caps = CapabilityInferrer.infer("dall-e-3")
        assertTrue(caps.contains(CapabilityType.IMAGE_GENERATION))
        assertFalse(caps.contains(CapabilityType.STREAMING))
    }

    @Test
    fun `whisper gets STT`() {
        val caps = CapabilityInferrer.infer("whisper-1")
        assertTrue(caps.contains(CapabilityType.AUDIO_STT))
    }

    @Test
    fun `text-embedding gets EMBEDDING, loses TEXT_CHAT`() {
        val caps = CapabilityInferrer.infer("text-embedding-3-small")
        assertTrue(caps.contains(CapabilityType.EMBEDDING))
        assertFalse(caps.contains(CapabilityType.TEXT_CHAT))
    }

    @Test
    fun `o1 gets REASONING`() {
        val caps = CapabilityInferrer.infer("o1")
        assertTrue(caps.contains(CapabilityType.REASONING))
    }

    @Test
    fun `deepseek-reasoner gets REASONING`() {
        val caps = CapabilityInferrer.infer("deepseek-reasoner")
        assertTrue(caps.contains(CapabilityType.REASONING))
    }

    @Test
    fun `codestral gets CODE`() {
        val caps = CapabilityInferrer.infer("codestral-latest")
        assertTrue(caps.contains(CapabilityType.CODE))
    }

    @Test
    fun `sonar gets SEARCH`() {
        val caps = CapabilityInferrer.infer("sonar-medium")
        assertTrue(caps.contains(CapabilityType.SEARCH))
    }

    @Test
    fun `sora gets VIDEO_GENERATION`() {
        val caps = CapabilityInferrer.infer("sora-v1")
        assertTrue(caps.contains(CapabilityType.VIDEO_GENERATION))
    }

    @Test
    fun `tts model gets AUDIO_TTS`() {
        val caps = CapabilityInferrer.infer("tts-1-hd")
        assertTrue(caps.contains(CapabilityType.AUDIO_TTS))
    }

    // Context length tests

    @Test
    fun `gpt-4o context is 128k`() {
        assertEquals(128_000, CapabilityInferrer.inferContextLength("gpt-4o"))
    }

    @Test
    fun `gemini-2 context is 1M`() {
        assertEquals(1_000_000, CapabilityInferrer.inferContextLength("gemini-2.0-flash"))
    }

    @Test
    fun `claude-3 context is 200k`() {
        assertEquals(200_000, CapabilityInferrer.inferContextLength("claude-3-sonnet"))
    }

    @Test
    fun `unknown model context defaults to 8192`() {
        assertEquals(8_192, CapabilityInferrer.inferContextLength("unknown-model"))
    }

    @Test
    fun `gpt-3_5 context is 16k`() {
        assertEquals(16_385, CapabilityInferrer.inferContextLength("gpt-3.5-turbo"))
    }
}
