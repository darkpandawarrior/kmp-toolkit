package com.siddharth.kmp.llmchat

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SseFramingTest {
    @Test
    fun parseSseFrames_yieldsDataPayloads_inOrder() =
        runTest {
            val lines = flowOf("""data: {"a":1}""", "", """data: {"a":2}""")

            assertEquals(listOf("""{"a":1}""", """{"a":2}"""), parseSseFrames(lines).toList())
        }

    @Test
    fun parseSseFrames_stopsAt_openAiDoneSentinel() =
        runTest {
            val lines = flowOf("""data: {"a":1}""", "data: [DONE]", """data: {"a":2}""")

            // [DONE] itself is filtered; anything after it (there never legitimately is anything,
            // OpenAI closes the connection there) is still parsed rather than causing an error.
            assertEquals(listOf("""{"a":1}""", """{"a":2}"""), parseSseFrames(lines).toList())
        }

    @Test
    fun parseSseFrames_ignoresNonDataFields_andBlankLines() =
        runTest {
            val lines = flowOf("event: message_start", ": a comment", "", """data: {"a":1}""")

            assertEquals(listOf("""{"a":1}"""), parseSseFrames(lines).toList())
        }

    @Test
    fun parseSseFrames_ignoresEmptyDataPayload() =
        runTest {
            val lines = flowOf("data:", "data: ", """data: {"a":1}""")

            assertEquals(listOf("""{"a":1}"""), parseSseFrames(lines).toList())
        }

    @Test
    fun parseSseFrames_emptyStream_yieldsNoPayloads() =
        runTest {
            assertEquals(emptyList(), parseSseFrames(flowOf<String>()).toList())
        }
}
