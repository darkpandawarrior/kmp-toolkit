package com.siddharth.kmp.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Lifecycle of a downloadable on-device model (e.g. MediaPipe Gemma). */
enum class ModelDownloadState { ABSENT, DOWNLOADING, READY, FAILED }

/** A downloadable on-device model, surfaced to the settings screen. */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val approxSizeMb: Int,
    val state: ModelDownloadState,
    /** 0f..1f while [state] is DOWNLOADING; otherwise 0f. */
    val progress: Float = 0f,
    val error: String? = null,
)

/**
 * The settings-screen-facing control surface for optional downloadable on-device models. Kept tiny
 * on purpose (list / observe / download / delete). Backends that need no download (ML Kit Gemini
 * Nano is managed by AICore; Foundation Models by the OS) don't appear here — only models the app
 * fetches itself (MediaPipe Gemma) do.
 *
 * Model binaries are NEVER shipped in the repo: [download] is user-triggered, wifi-only by default,
 * into app-private storage.
 */
interface ModelManager {
    /** Current snapshot of every manageable model. */
    fun models(): List<ModelInfo>

    /** Observe one model's state (drives the download-progress UI). */
    fun observe(modelId: String): Flow<ModelInfo>

    /** Start (or resume) downloading [modelId]. Suspends until it completes or fails. */
    suspend fun download(modelId: String)

    /** Remove a downloaded model from app-private storage. */
    suspend fun delete(modelId: String)
}

/** Default for platforms/targets with no downloadable models (JVM/desktop, iOS today). */
object NoModelManager : ModelManager {
    override fun models(): List<ModelInfo> = emptyList()

    override fun observe(modelId: String): Flow<ModelInfo> = flowOf(ModelInfo(modelId, modelId, 0, ModelDownloadState.ABSENT))

    override suspend fun download(modelId: String) = Unit

    override suspend fun delete(modelId: String) = Unit
}
