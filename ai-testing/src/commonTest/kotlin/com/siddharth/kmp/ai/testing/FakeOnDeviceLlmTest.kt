package com.siddharth.kmp.ai.testing

import com.siddharth.kmp.ai.LlmPart
import com.siddharth.kmp.result.AiCapabilities
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.Result
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeOnDeviceLlmTest {
    @Test
    fun generate_answersEmptyReply_whenNothingWasQueued() =
        runTest {
            val llm = FakeOnDeviceLlm()
            assertEquals(Result.Failure(AiFailure.EmptyReply), llm.generate("prompt"))
        }

    @Test
    fun generate_drainsQueuedOutcomesInOrder_thenRepeatsTheLastOne() =
        runTest {
            val llm = FakeOnDeviceLlm()
            llm.enqueueSuccess("first")
            llm.enqueueFailure(AiFailure.ModelNotResident)
            llm.enqueueSuccess("last")

            assertEquals(Result.Success("first"), llm.generate("a"))
            assertEquals(Result.Failure(AiFailure.ModelNotResident), llm.generate("b"))
            assertEquals(Result.Success("last"), llm.generate("c"))
            // Queue is drained to its final entry — every subsequent call keeps getting it, not EmptyReply.
            assertEquals(Result.Success("last"), llm.generate("d"))
        }

    @Test
    fun generate_withParts_sharesTheSameQueueAsGenerate_withPrompt() =
        runTest {
            val llm = FakeOnDeviceLlm()
            llm.enqueueSuccess("via-parts")
            assertEquals(Result.Success("via-parts"), llm.generate(listOf(LlmPart.Text("x"))))
        }

    @Test
    fun generateStream_emitsQueuedChunksInOrder_thenRepeatsTheLastScript() =
        runTest {
            val llm = FakeOnDeviceLlm()
            llm.enqueueStreamChunks("he", "llo")
            llm.enqueueStreamChunks("world")

            assertEquals(listOf("he", "llo"), llm.generateStream("a").toList())
            assertEquals(listOf("world"), llm.generateStream("b").toList())
            assertEquals(listOf("world"), llm.generateStream("c").toList())
        }

    @Test
    fun generateStream_withNothingQueued_completesEmpty() =
        runTest {
            assertEquals(emptyList<String>(), FakeOnDeviceLlm().generateStream("x").toList())
        }

    @Test
    fun isAvailable_reflectsSetAvailable() {
        val llm = FakeOnDeviceLlm(available = false)
        assertFalse(llm.isAvailable())
        llm.setAvailable(true)
        assertTrue(llm.isAvailable())
    }

    @Test
    fun capabilities_defaultsToTheRealInterfaceDefault_derivedFromIsAvailable() =
        runTest {
            val llm = FakeOnDeviceLlm(available = false)
            assertEquals(AiFailure.NotSupportedOnPlatform, llm.capabilities().unavailableReason)
        }

    @Test
    fun capabilities_usesTheOverrideWhenOneIsSet() =
        runTest {
            val caps = AiCapabilities(streaming = true, multimodal = true, honoredConfigFields = setOf("topK"), unavailableReason = null)
            val llm = FakeOnDeviceLlm()
            llm.setCapabilities(caps)
            assertEquals(caps, llm.capabilities())
        }
}
