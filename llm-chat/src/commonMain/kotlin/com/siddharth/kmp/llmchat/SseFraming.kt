package com.siddharth.kmp.llmchat

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * Decodes one SSE `data:` payload per provider's stream event shape. `ignoreUnknownKeys` because
 * each vendor's stream event carries fields (`index`, `usage`, `model`, ...) beyond the one or two
 * this module actually reads — same relaxed decoding the client's `ContentNegotiation` already uses
 * for the non-streaming response body, just for a manual `decodeFromString` call instead.
 */
internal val sseJson = Json { ignoreUnknownKeys = true }

/**
 * Minimal SSE (Server-Sent Events) `data:` frame parser, shared by [AnthropicProvider],
 * [OpenAiProvider] and [GeminiProvider]'s `completeStream`. Each vendor's streaming endpoint
 * differs in *body* JSON shape but frames it the same way — `data: <payload>` lines, blank lines
 * as event separators, and (OpenAI only) a `data: [DONE]` sentinel closing the stream — so this
 * yields just the payload strings; each provider still JSON-decodes its own shape.
 *
 * // ponytail: `data:` lines only — no `event:`/`id:`/multi-line data continuation, since none of
 * // the three vendors' chat-completion SSE uses them. Widen if a fourth provider's stream does.
 */
internal fun parseSseFrames(lines: Flow<String>): Flow<String> =
    flow {
        lines.collect { line ->
            if (!line.startsWith("data:")) return@collect
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty() || payload == "[DONE]") return@collect
            emit(payload)
        }
    }

/** Reads [this] as UTF-8 lines, the raw input [parseSseFrames] expects. */
internal fun ByteReadChannel.asLineFlow(): Flow<String> =
    flow {
        while (true) {
            val line = readLine() ?: break
            emit(line)
        }
    }
