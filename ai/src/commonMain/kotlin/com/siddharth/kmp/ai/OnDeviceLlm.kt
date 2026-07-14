package com.siddharth.kmp.ai

import org.koin.core.module.Module

/**
 * A single-shot on-device text LLM tier. Kept deliberately tiny — one availability gate and one
 * text-in/text-out call — so each platform's actual (ML Kit GenAI on Android, Foundation Models on
 * iOS, unavailable elsewhere) is a thin wrapper, and [DefaultJobIntelligence] never has to know
 * which backend ran.
 *
 * [generate] returns null on any failure or when the model isn't resident, so the caller degrades
 * to the heuristic tier instead of throwing.
 */
interface OnDeviceLlm {
    /**
     * Cheap, synchronous floor — true only when the platform *could* run inference. NOT the
     * authoritative runtime check (model residency is async); [generate] still guards internally.
     */
    fun isAvailable(): Boolean

    /** Runs [prompt] on-device. Returns the model's text, or null when unavailable/declined/failed. */
    suspend fun generate(prompt: String): String?
}

/** The common fallback tier: no on-device model. Desktop/JVM/wasm and any pre-AI device land here. */
object UnavailableOnDeviceLlm : OnDeviceLlm {
    override fun isAvailable(): Boolean = false

    override suspend fun generate(prompt: String): String? = null
}

/**
 * Per-platform Koin bindings for the on-device LLM tier. commonMain's [aiModule] includes this;
 * the actual decides which [OnDeviceLlm] gets bound (ML Kit / Foundation Models / unavailable).
 */
expect fun onDeviceLlmModule(): Module
