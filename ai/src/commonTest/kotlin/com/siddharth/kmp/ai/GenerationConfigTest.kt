package com.siddharth.kmp.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerationConfigTest {
    @Test
    fun `defaults are all null`() {
        val c = GenerationConfig()
        assertEquals(null, c.topK)
        assertEquals(null, c.topP)
        assertEquals(null, c.temperature)
        assertEquals(null, c.maxTokens)
        assertEquals(null, c.accelerator)
    }

    @Test
    fun `load-time-only fields do not request a sampler override`() {
        assertFalse(GenerationConfig().hasSamplerOverride)
        assertFalse(GenerationConfig(maxTokens = 256, accelerator = Accelerator.GPU).hasSamplerOverride)
    }

    @Test
    fun `any sampler field flips hasSamplerOverride`() {
        assertTrue(GenerationConfig(topK = 40).hasSamplerOverride)
        assertTrue(GenerationConfig(topP = 0.9f).hasSamplerOverride)
        assertTrue(GenerationConfig(temperature = 0.2f).hasSamplerOverride)
    }
}
