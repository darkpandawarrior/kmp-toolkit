package com.siddharth.kmp.ai

import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [CompositeOnDeviceLlm] is the ONE [OnDeviceLlm] every app actually gets from `onDeviceLlmModule()`
 * — so its `generateStream` must delegate to the chosen backend's own streaming implementation
 * (real per-token output, real mid-generation cancellation), not silently fall back to replaying
 * [OnDeviceLlm.generate] as a single emission the way the bare interface default does. That
 * fallback would mean a backend with genuine token streaming (ML Kit GenAI, MediaPipe after this
 * change) never actually streams for any real caller, and a "Stop" button would never reach a
 * generation in progress.
 */
class StreamCancellationTest {
    /** Emits [chunks] one at a time; records how many were actually collected before it stopped. */
    private class StreamingBackend(
        private val chunks: List<String>,
        private val available: Boolean = true,
        override val supportsImage: Boolean = false,
    ) : OnDeviceLlm {
        var emittedCount = 0
            private set
        var ranToCompletion = false
            private set

        override fun isAvailable(): Boolean = available

        override suspend fun generate(prompt: String): AiResult<String> = Result.Success(chunks.joinToString(""))

        override fun generateStream(prompt: String): Flow<String> =
            flow {
                for ((index, chunk) in chunks.withIndex()) {
                    emittedCount = index + 1
                    emit(chunk)
                }
                ranToCompletion = true
            }

        // Real backends interpret parts (e.g. MlKitGenAiOnDeviceLlm's ImagePart+TextPart request);
        // this fake only needs to prove CompositeOnDeviceLlm picked and streamed IT, so it ignores
        // the content and reuses the same chunk sequence regardless of overload called.
        override fun generateStream(parts: List<LlmPart>): Flow<String> = generateStream(prompt = "ignored")
    }

    @Test
    fun generateStream_delegatesToChosenBackends_realStream_notSingleEmission() =
        runTest {
            val backend = StreamingBackend(listOf("Hel", "lo"))
            val composite = CompositeOnDeviceLlm(listOf(backend))

            assertEquals(listOf("Hel", "lo"), composite.generateStream("hi").toList())
            assertTrue(backend.ranToCompletion)
        }

    @Test
    fun generateStream_skipsUnavailableBackend_usesNextOne() =
        runTest {
            val down = StreamingBackend(listOf("unused"), available = false)
            val up = StreamingBackend(listOf("ok"))
            val composite = CompositeOnDeviceLlm(listOf(down, up))

            assertEquals(listOf("ok"), composite.generateStream("hi").toList())
            assertEquals(0, down.emittedCount, "an unavailable backend must never be streamed from")
        }

    @Test
    fun generateStream_noBackendAvailable_emitsNothing() =
        runTest {
            val composite = CompositeOnDeviceLlm(emptyList())
            assertTrue(composite.generateStream("hi").toList().isEmpty())
        }

    @Test
    fun generateStream_parts_skipsTextOnlyBackend_whenAnImagePartIsPresent() =
        runTest {
            val textOnly = StreamingBackend(listOf("should-not-win"), supportsImage = false)
            val multimodal = StreamingBackend(listOf("image-ok"), supportsImage = true)
            val composite = CompositeOnDeviceLlm(listOf(textOnly, multimodal))

            val parts = listOf(LlmPart.Image(byteArrayOf(1)), LlmPart.Text("describe"))
            assertEquals(listOf("image-ok"), composite.generateStream(parts).toList())
            assertEquals(0, textOnly.emittedCount)
        }

    /**
     * The whole point of streaming instead of a single-shot `generate()`: a caller can stop
     * collecting mid-reply (the user tapped Stop) and the backend must actually stop producing —
     * not keep computing every remaining token in the background because nothing is reading them.
     */
    @Test
    fun generateStream_collectorStopsEarly_backendStopsBeforeItFinishes() =
        runTest {
            val backend = StreamingBackend(List(5) { "chunk$it" })
            val composite = CompositeOnDeviceLlm(listOf(backend))

            val collected = composite.generateStream("hi").take(2).toList()

            assertEquals(listOf("chunk0", "chunk1"), collected)
            assertEquals(2, backend.emittedCount, "must not have been asked for chunks past the cancel point")
            assertFalse(backend.ranToCompletion, "collector stopping early must stop the backend's generation")
        }
}
