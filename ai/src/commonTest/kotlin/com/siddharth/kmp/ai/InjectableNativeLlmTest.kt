package com.siddharth.kmp.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InjectableNativeLlmTest {
    @Test
    fun isAvailable_isFalse_whenNothingInjected() {
        assertFalse(InjectableNativeLlm().isAvailable())
    }

    @Test
    fun generate_declinesToNull_whenNothingInjected() =
        runTest {
            assertNull(InjectableNativeLlm().generate("hi"))
        }

    @Test
    fun generate_delegates_whenInjectedAndAvailable() =
        runTest {
            val seam = InjectableNativeLlm()
            seam.generator =
                object : NativeLlm {
                    override fun isAvailable() = true

                    override suspend fun generate(prompt: String) = "echo: $prompt"

                    override fun generateStream(
                        prompt: String,
                        callback: NativeLlmStreamCallback,
                    ) = error("not exercised by this test")
                }

            assertTrue(seam.isAvailable())
            assertEquals("echo: hi", seam.generate("hi"))
        }

    @Test
    fun generate_declinesToNull_whenInjectedButUnavailable() =
        runTest {
            val seam = InjectableNativeLlm()
            seam.generator =
                object : NativeLlm {
                    override fun isAvailable() = false

                    override suspend fun generate(prompt: String) = error("must not be called when unavailable")

                    override fun generateStream(
                        prompt: String,
                        callback: NativeLlmStreamCallback,
                    ) = error("must not be called when unavailable")
                }

            assertFalse(seam.isAvailable())
            assertNull(seam.generate("hi"))
        }

    @Test
    fun generateStream_completesImmediately_whenNothingInjected() {
        val recorder = RecordingCallback()

        val handle = InjectableNativeLlm().generateStream("hi", recorder)
        handle.cancel() // must not throw even though nothing is running

        assertTrue(recorder.completed)
        assertTrue(recorder.partials.isEmpty())
        assertFalse(recorder.errored)
    }

    @Test
    fun generateStream_completesImmediately_whenInjectedButUnavailable() {
        val recorder = RecordingCallback()
        val seam = InjectableNativeLlm()
        seam.generator =
            object : NativeLlm {
                override fun isAvailable() = false

                override suspend fun generate(prompt: String) = error("must not be called")

                override fun generateStream(
                    prompt: String,
                    callback: NativeLlmStreamCallback,
                ) = error("must not be called when unavailable")
            }

        seam.generateStream("hi", recorder)

        assertTrue(recorder.completed)
        assertTrue(recorder.partials.isEmpty())
    }

    @Test
    fun generateStream_delegatesToTheInjectedGenerator_includingCancellation() {
        val recorder = RecordingCallback()
        var cancelled = false
        val seam = InjectableNativeLlm()
        seam.generator =
            object : NativeLlm {
                override fun isAvailable() = true

                override suspend fun generate(prompt: String) = error("not exercised by this test")

                override fun generateStream(
                    prompt: String,
                    callback: NativeLlmStreamCallback,
                ): NativeLlmCancelHandle {
                    callback.onPartial("chunk for $prompt")
                    callback.onComplete()
                    return NativeLlmCancelHandle { cancelled = true }
                }
            }

        val handle = seam.generateStream("hi", recorder)
        handle.cancel()

        assertEquals(listOf("chunk for hi"), recorder.partials)
        assertTrue(recorder.completed)
        assertTrue(cancelled)
    }

    private class RecordingCallback : NativeLlmStreamCallback {
        val partials = mutableListOf<String>()
        var completed = false
        var errored = false

        override fun onPartial(text: String) {
            partials += text
        }

        override fun onComplete() {
            completed = true
        }

        override fun onError() {
            errored = true
        }
    }
}
