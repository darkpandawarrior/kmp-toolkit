package com.siddharth.kmp.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
 * transfer ([ResumableModelDownloader], gallery A4). [download] itself refuses to start when
 * [ModelManifestEntry.requiresLicenseAck] is set and the caller didn't pass `licenseAcknowledged =
 * true`, or when the active network is metered — a caller SHOULD still schedule this inside a
 * wifi-only WorkManager foreground job (for the FGS notification and to survive process death
 * mid-transfer), but a caller that skips that no longer burns 555MB of cellular data or a gated
 * model's license by omission; this class is the last line of defense, not the only one.
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

    override suspend fun download(
        modelId: String,
        licenseAcknowledged: Boolean,
    ) {
        if (modelId != spec.id) return
        if (isReady()) {
            flow.update { snapshot() }
            return
        }
        if (spec.requiresLicenseAck && !licenseAcknowledged) {
            flow.update { it.copy(state = ModelDownloadState.FAILED, error = "License not acknowledged") }
            return
        }
        if (!isOnUnmeteredNetwork()) {
            flow.update { it.copy(state = ModelDownloadState.FAILED, error = "Wi-Fi (unmetered network) required") }
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

    // ponytail: a live capability check (not a sticky "user said wifi-only" pref) — cheap, no
    // ACCESS_NETWORK_STATE runtime prompt needed (it's a normal, install-time-granted permission),
    // and correct across the common case of a caller not bothering with WorkManager constraints at
    // all. Doesn't cover a metered-wifi hotspot the OS itself can't tell apart from a real one.
    private fun isOnUnmeteredNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
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
