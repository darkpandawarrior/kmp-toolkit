package com.siddharth.kmp.ai

import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * [StructuredOutput] turns "parse a field out of free text" from a hand-rolled regex scrape (the
 * kind that breaks the first time a model adds a newline) into a typed, tolerant JSON parse with
 * one repair retry — see the theme's "Why" in the lane brief.
 */
class StructuredOutputTest {
    @Serializable
    data class JobFields(val title: String, val company: String)

    private class RecordingGenerator(private val replies: List<AiResult<String>>) {
        val prompts = mutableListOf<String>()
        private var index = 0

        suspend fun invoke(prompt: String): AiResult<String> {
            prompts.add(prompt)
            return replies[index++]
        }
    }

    @Test
    fun parses_a_clean_first_reply_with_no_retry() =
        runTest {
            val generator = RecordingGenerator(listOf(Result.Success("""{"title":"Engineer","company":"Acme"}""")))
            val structuredOutput = StructuredOutput(JobFields.serializer())

            val result = structuredOutput.ask("extract the job fields") { generator.invoke(it) }

            assertEquals(Result.Success(JobFields("Engineer", "Acme")), result)
            assertEquals(1, generator.prompts.size, "a clean reply must not trigger the repair retry")
        }

    @Test
    fun strips_a_markdown_json_fence_the_model_wrapped_the_reply_in() =
        runTest {
            val fenced = "```json\n{\"title\":\"Engineer\",\"company\":\"Acme\"}\n```"
            val generator = RecordingGenerator(listOf(Result.Success(fenced)))
            val structuredOutput = StructuredOutput(JobFields.serializer())

            val result = structuredOutput.ask("extract the job fields") { generator.invoke(it) }

            assertEquals(Result.Success(JobFields("Engineer", "Acme")), result)
        }

    @Test
    fun retries_once_with_the_parse_error_when_the_first_reply_is_not_valid_json() =
        runTest {
            val generator =
                RecordingGenerator(
                    listOf(
                        Result.Success("here is your answer: Engineer at Acme"),
                        Result.Success("""{"title":"Engineer","company":"Acme"}"""),
                    ),
                )
            val structuredOutput = StructuredOutput(JobFields.serializer())

            val result = structuredOutput.ask("extract the job fields") { generator.invoke(it) }

            assertEquals(Result.Success(JobFields("Engineer", "Acme")), result)
            assertEquals(2, generator.prompts.size, "a malformed first reply must trigger exactly one retry")
            assertEquals(
                true,
                generator.prompts[1].contains("here is your answer: Engineer at Acme"),
                "the repair prompt must show the model its own unparseable reply",
            )
        }

    @Test
    fun fails_typed_when_both_the_first_reply_and_the_repair_retry_are_unparseable() =
        runTest {
            val generator = RecordingGenerator(listOf(Result.Success("nope"), Result.Success("still nope")))
            val structuredOutput = StructuredOutput(JobFields.serializer())

            val result = structuredOutput.ask("extract the job fields") { generator.invoke(it) }

            assertIs<Result.Failure<AiFailure>>(result)
            assertEquals(2, generator.prompts.size)
        }

    @Test
    fun does_not_retry_when_the_underlying_generate_call_itself_fails() =
        runTest {
            val generator = RecordingGenerator(listOf(Result.Failure(AiFailure.Timeout)))
            val structuredOutput = StructuredOutput(JobFields.serializer())

            val result = structuredOutput.ask("extract the job fields") { generator.invoke(it) }

            assertEquals(Result.Failure(AiFailure.Timeout), result)
            assertEquals(1, generator.prompts.size, "a transport failure isn't a parse problem — no repair retry")
        }

    @Test
    fun propagates_the_retrys_own_generate_failure_when_the_repair_call_fails() =
        runTest {
            val generator = RecordingGenerator(listOf(Result.Success("nope"), Result.Failure(AiFailure.RateLimited)))
            val structuredOutput = StructuredOutput(JobFields.serializer())

            val result = structuredOutput.ask("extract the job fields") { generator.invoke(it) }

            assertEquals(Result.Failure(AiFailure.RateLimited), result)
        }

    @Test
    fun embeds_a_schema_hint_naming_every_field_in_the_prompt() =
        runTest {
            val generator = RecordingGenerator(listOf(Result.Success("""{"title":"Engineer","company":"Acme"}""")))
            val structuredOutput = StructuredOutput(JobFields.serializer())

            structuredOutput.ask("extract the job fields") { generator.invoke(it) }

            val sentPrompt = generator.prompts.single()
            assertEquals(true, sentPrompt.contains("title"))
            assertEquals(true, sentPrompt.contains("company"))
        }

    @Test
    fun structuredOutput_reified_helper_builds_from_the_type_argument_alone() =
        runTest {
            val generator = RecordingGenerator(listOf(Result.Success("""{"title":"Engineer","company":"Acme"}""")))

            val result = structuredOutput<JobFields>().ask("extract the job fields") { generator.invoke(it) }

            assertEquals(Result.Success(JobFields("Engineer", "Acme")), result)
        }
}
