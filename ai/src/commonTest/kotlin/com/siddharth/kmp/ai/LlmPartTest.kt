package com.siddharth.kmp.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [LlmPart.Image] structural equality (ByteArray has no content-aware equals of its own, so the
 * data class needs a manual override) and [OnDeviceLlm.generate]'s default `List<LlmPart> ->
 * String` mapping, which every text-only actual relies on to gain the multimodal call site for
 * free without implementing it.
 */
class LlmPartTest {
    private class TextOnlyBackend(
        private val output: String?,
    ) : OnDeviceLlm {
        var calls = 0

        override fun isAvailable() = true

        override suspend fun generate(prompt: String): String? {
            calls++
            return output
        }
    }

    @Test
    fun image_parts_over_equal_bytes_are_equal() {
        val a = LlmPart.Image(byteArrayOf(1, 2, 3))
        val b = LlmPart.Image(byteArrayOf(1, 2, 3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun image_parts_over_different_bytes_are_not_equal() {
        assertEquals(false, LlmPart.Image(byteArrayOf(1)) == LlmPart.Image(byteArrayOf(2)))
    }

    @Test
    fun default_generate_parts_maps_single_text_part_onto_generate_string() =
        runTest {
            val backend = TextOnlyBackend(output = "answer")
            assertEquals("answer", backend.generate(listOf(LlmPart.Text("prompt"))))
            assertEquals(1, backend.calls)
        }

    @Test
    fun default_generate_parts_declines_when_an_image_part_is_present() =
        runTest {
            val backend = TextOnlyBackend(output = "answer")
            assertNull(backend.generate(listOf(LlmPart.Image(byteArrayOf(1)), LlmPart.Text("prompt"))))
            assertEquals(0, backend.calls)
        }
}
