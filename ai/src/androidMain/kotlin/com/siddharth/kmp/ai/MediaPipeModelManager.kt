package com.siddharth.kmp.ai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Manages the on-demand MediaPipe Gemma model file in app-private storage. The model binary is NEVER
 * committed to the repo — [download] is user-triggered (surfaced on the settings screen via
 * [ModelManager]) and lands in `filesDir/models/`, resuming from a `.tmp` if a prior attempt was cut off.
 *
 * The URL is manifest-derived ([ModelManifestEntry], gallery A5) and the fetch is a real resumable
 * transfer ([ResumableModelDownloader], gallery A4). The CALLER is responsible for user consent (see
 * [ModelManifestEntry.requiresLicenseAck]) and for running [download] inside a wifi-only WorkManager
 * foreground job — this class owns file state + progress, not scheduling.
 */
class MediaPipeModelManager(
    private val context: Context,
    private val spec: ModelManifestEntry = GEMMA_3_1B,
    private val downloader: ResumableModelDownloader = ResumableModelDownloader(),
) : ModelManager {
    private val flow = MutableStateFlow(snapshot())

    fun modelFile(): File = File(File(context.filesDir, MODELS_DIR).apply { mkdirs() }, spec.fileName)

    private fun tmpFile(): File = File(modelFile().parentFile, modelFile().name + ".tmp")

    fun isReady(): Boolean = modelFile().let { it.exists() && it.length() > 0 }

    override fun models(): List<ModelInfo> = listOf(snapshot())

    override fun observe(modelId: String): StateFlow<ModelInfo> = flow.asStateFlow()

    override suspend fun download(modelId: String) {
        if (modelId != spec.id) return
        if (isReady()) {
            flow.update { snapshot() }
            return
        }
        flow.update { it.copy(state = ModelDownloadState.DOWNLOADING, progress = 0f, downloadProgress = null, error = null) }
        val result =
            runCatching {
                downloader.download(spec.downloadUrl, modelFile()).collect { p ->
                    flow.update {
                        it.copy(state = ModelDownloadState.DOWNLOADING, progress = p.fraction, downloadProgress = p)
                    }
                }
            }
        flow.update {
            when {
                result.isSuccess && isReady() ->
                    it.copy(state = ModelDownloadState.READY, progress = 1f, downloadProgress = null, error = null)
                // A leftover .tmp means the transfer was interrupted — resumable on the next attempt.
                tmpFile().exists() ->
                    it.copy(
                        state = ModelDownloadState.PARTIALLY_DOWNLOADED,
                        downloadProgress = null,
                        error = result.exceptionOrNull()?.message,
                    )
                else ->
                    it.copy(
                        state = ModelDownloadState.FAILED,
                        progress = 0f,
                        downloadProgress = null,
                        error = result.exceptionOrNull()?.message ?: "download failed",
                    )
            }
        }
    }

    override suspend fun delete(modelId: String) {
        if (modelId != spec.id) return
        modelFile().delete()
        tmpFile().delete()
        flow.update { snapshot() }
    }

    private fun snapshot(): ModelInfo =
        ModelInfo(
            id = spec.id,
            displayName = spec.displayName,
            approxSizeMb = spec.approxSizeMb,
            state =
                when {
                    isReady() -> ModelDownloadState.READY
                    tmpFile().exists() -> ModelDownloadState.PARTIALLY_DOWNLOADED
                    else -> ModelDownloadState.ABSENT
                },
        )

    companion object {
        private const val MODELS_DIR = "models"

        /** Default manifest entry (Gemma 3 1B). Apps can pass their own [ModelManifestEntry] instead. */
        val GEMMA_3_1B =
            ModelManifestEntry(
                id = "gemma3-1b",
                displayName = "Gemma 3 1B (on-device)",
                approxSizeMb = 555,
                fileName = "gemma3-1b-it.task",
                hfRepo = "litert-community/Gemma3-1B-IT",
                hfFile = "gemma3-1b-it.task",
                requiresLicenseAck = true,
            )
    }
}
