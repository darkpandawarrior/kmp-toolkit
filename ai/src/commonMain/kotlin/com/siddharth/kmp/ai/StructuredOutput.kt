package com.siddharth.kmp.ai

import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/** Shared by every [StructuredOutput] instance — same tolerant-parse convention `:llm-chat`'s HTTP providers already use for their own response bodies (see `SseFraming.kt`'s `sseJson`). */
private val structuredOutputJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/** ```json ... ``` (or a bare fence) around an otherwise-valid reply — the single most common way a chat-tuned model wraps structured output it was asked not to explain. */
private val jsonFence = Regex("```(?:json)?\\s*([\\s\\S]*?)```")

/**
 * Turns "parse a typed [T] out of a model's free-text reply" from a hand-rolled regex scrape (the
 * kind that breaks the first time a model adds a newline or an explanatory sentence) into one
 * shared, tested path: a schema hint embedded in the prompt, a tolerant JSON parse of the reply,
 * one repair retry that shows the model its own unparseable output, and a typed [AiFailure]
 * otherwise.
 *
 * Works with either AI seam — pass `onDeviceLlm::generate` or an `:llm-chat` `AiProvider`
 * (`{ prompt -> provider.complete(listOf(AiMessage(AiMessage.Role.USER, prompt))).map { ... } }`),
 * since both already speak [AiResult]. PromptGuard, if the caller wants it, runs inside whichever
 * `generate` implementation the caller supplies — this class only owns the schema/parse/retry
 * loop, not prompt safety.
 */
class StructuredOutput<T>(private val serializer: KSerializer<T>) {
    /**
     * Runs [prompt] (with a schema hint appended) through [generate]. Parses the reply as [T]; on
     * a malformed reply, retries once with the bad reply and a "fix your JSON" nudge appended. A
     * failure from [generate] itself (network, timeout, no key) is not a parse problem and is
     * returned as-is with no retry — retrying a call that already told you why it failed just
     * spends another round trip for the same answer.
     */
    suspend fun ask(
        prompt: String,
        generate: suspend (String) -> AiResult<String>,
    ): AiResult<T> {
        val hintedPrompt = "$prompt\n\n${schemaHint()}"
        val firstReply = generate(hintedPrompt)
        if (firstReply is Result.Failure) return firstReply
        val firstText = (firstReply as Result.Success).data
        val firstParsed = parse(firstText)
        if (firstParsed is Result.Success) return firstParsed

        // ponytail: exactly one repair retry — a model that can't produce valid JSON once isn't
        // reliably going to on a second nudge either; bump this if that assumption proves wrong
        // for a real backend.
        val retryReply = generate(repairPrompt(hintedPrompt, firstText))
        if (retryReply is Result.Failure) return retryReply
        return parse((retryReply as Result.Success).data)
    }

    private fun parse(reply: String): AiResult<T> =
        try {
            Result.Success(structuredOutputJson.decodeFromString(serializer, extractJson(reply)))
        } catch (_: SerializationException) {
            // ponytail: collapsed to EmptyReply — the enum is frozen by design (both AI seams
            // share it), and "the model's reply carried no usable text" already describes an
            // unparseable one. Add a dedicated case if a caller ever needs the parse error itself.
            Result.Failure(AiFailure.EmptyReply)
        }

    private fun extractJson(reply: String): String {
        val trimmed = reply.trim()
        val fenced = jsonFence.find(trimmed)?.groupValues?.get(1)
        return (fenced ?: trimmed).trim()
    }

    private fun repairPrompt(hintedPrompt: String, badReply: String): String =
        "$hintedPrompt\n\nYour previous reply could not be parsed as that JSON shape:\n$badReply\n" +
            "Reply again with ONLY corrected JSON matching the shape above."

    private fun schemaHint(): String {
        val descriptor = serializer.descriptor
        val fields =
            (0 until descriptor.elementsCount).joinToString(", ") { i ->
                "\"${descriptor.getElementName(i)}\": ${kindHint(descriptor.getElementDescriptor(i))}"
            }
        return "Reply with ONLY a JSON object: {$fields}. No prose, no markdown fences, no explanation."
    }

    private fun kindHint(descriptor: SerialDescriptor): String =
        when (descriptor.kind) {
            PrimitiveKind.STRING -> "string"
            PrimitiveKind.BOOLEAN -> "boolean"
            PrimitiveKind.INT, PrimitiveKind.LONG, PrimitiveKind.SHORT, PrimitiveKind.BYTE,
            PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE,
            -> "number"
            StructureKind.LIST -> "array"
            StructureKind.MAP -> "object"
            else -> "value"
        }
}

/** Convenience for the common case: build a [StructuredOutput] from a reified `@Serializable` type, no explicit `.serializer()` call at the use site. */
inline fun <reified T> structuredOutput(): StructuredOutput<T> = StructuredOutput(serializer())
