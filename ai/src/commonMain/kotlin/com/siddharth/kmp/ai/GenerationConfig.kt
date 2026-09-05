package com.siddharth.kmp.ai

/**
 * Optional load-time tuning for an on-device LLM. Every field is nullable — null means "leave the
 * backend's own default alone". Backends apply what their engine exposes and ignore the rest:
 *  - **MediaPipe (Gemma)** — [maxTokens] + [accelerator] + the [topK] ceiling are load-time options;
 *    the [topK]/[topP]/[temperature] sampler is applied per inference session.
 *  - **ML Kit GenAI (Gemini Nano)** — [topK]/[temperature]/[maxTokens] are wired per-request
 *    ([MlKitGenAiOnDeviceLlm]'s `buildRequest`); [topP]/[accelerator] have no equivalent on this API
 *    and are ignored.
 *  - **Foundation Models (iOS)** — no Swift bridge exists yet ([FoundationModelsOnDeviceLlm] is a
 *    hard stub, always unavailable), so there is no real call site to honor this on; ignored until
 *    that bridge lands. Kept uniform so a caller wires one config regardless of backend.
 */
data class GenerationConfig(
    val topK: Int? = null,
    val topP: Float? = null,
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val accelerator: Accelerator? = null,
)

/** True when any sampler field is set — i.e. the backend must override its default decoding. */
val GenerationConfig.hasSamplerOverride: Boolean
    get() = topK != null || topP != null || temperature != null

/**
 * Preferred compute unit for on-device inference. A hint, not a guarantee: a backend maps it to the
 * nearest unit its engine offers and degrades silently — MediaPipe (tasks-genai) has no NPU backend
 * so [NPU] falls back to the engine default, and Gemini Nano always runs on the NPU/AICore regardless.
 */
enum class Accelerator { CPU, GPU, NPU }
