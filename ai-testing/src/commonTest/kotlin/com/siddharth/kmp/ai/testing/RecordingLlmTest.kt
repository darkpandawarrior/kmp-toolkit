package com.siddharth.kmp.ai.testing

import com.siddharth.kmp.ai.LlmPart
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.Result
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class RecordingLlmTest {
    @Test
    fun generate_recordsThePromptAndForwardsToTheDelegate() =
        runTest {
            val fake = FakeOnDeviceLlm().apply { enqueueSuccess("hi") }
            val recorder = RecordingLlm(fake)

            val result = recorder.generate("summarize this")

            assertEquals(Result.Success("hi"), result)
            assertEquals("summarize this", recorder.lastPrompt)
            assertEquals(listOf(listOf(LlmPart.Text("summarize this"))), recorder.calls)
        }

    @Test
    fun generate_withParts_recordsTheRawPartsList() =
        runTest {
            val recorder = RecordingLlm(FakeOnDeviceLlm().apply { enqueueSuccess("ok") })
            val parts = listOf(LlmPart.Image(byteArrayOf(1, 2)), LlmPart.Text("describe"))

            recorder.generate(parts)

            assertEquals(listOf(parts), recorder.calls)
            // lastPrompt is only populated for the single-Text-part shape.
            assertNull(recorder.lastPrompt)
        }

    @Test
    fun generateStream_alsoRecordsAndForwards() =
        runTest {
            val fake = FakeOnDeviceLlm().apply { enqueueStreamChunks("a", "b") }
            val recorder = RecordingLlm(fake)

            val chunks = recorder.generateStream("stream me").toList()

            assertEquals(listOf("a", "b"), chunks)
            assertEquals("stream me", recorder.lastPrompt)
        }

    @Test
    fun calls_accumulatesAcrossMultipleInvocations_inOrder() =
        runTest {
            val recorder = RecordingLlm(FakeOnDeviceLlm())
            recorder.generate("one")
            recorder.generate("two")
            assertEquals(2, recorder.calls.size)
            assertEquals("two", recorder.lastPrompt)
        }

    @Test
    fun nonRecordingMembers_delegateThroughUnchanged() {
        val fake = FakeOnDeviceLlm(available = false)
        val recorder = RecordingLlm(fake)
        assertFalse(recorder.isAvailable())
    }

    @Test
    fun failure_isForwardedThroughUnchanged() =
        runTest {
            val fake = FakeOnDeviceLlm().apply { enqueueFailure(AiFailure.ModelNotResident) }
            val recorder = RecordingLlm(fake)
            assertEquals(Result.Failure(AiFailure.ModelNotResident), recorder.generate("x"))
        }
}
