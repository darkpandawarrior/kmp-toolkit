package com.siddharth.kmp.ai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Manages the on-demand MediaPipe Gemma model file in app-private storage. The model binary is NEVER
 * committed to the repo — [download] is user-triggered (surfaced on the settings screen via
 * [ModelManager]), wifi-only by default, and lands in `filesDir/models/`.
 *
 * File presence + [delete] are real; the actual byte fetch is intentionally not wired here (there is
 * no committed model-source URL or download-consent flow yet). See the TODO in [download].
 */
class MediaPipeModelManager(
    private val context: Context,
) : ModelManager {
    private val spec = GEMMA_3_1B
    private val flow = MutableStateFlow(snapshot())

    fun modelFile(): File = File(File(context.filesDir, MODELS_DIR).apply { mkdirs() }, spec.fileName)

    fun isReady(): Boolean = modelFile().let { it.exists() && it.length() > 0 }

    override fun models(): List<ModelInfo> = listOf(snapshot())

    override fun observe(modelId: String): StateFlow<ModelInfo> = flow.asStateFlow()

    override suspend fun download(modelId: String) {
        if (modelId != spec.id) return
        if (isReady()) {
            flow.update { snapshot() }
            return
        }
        flow.update { it.copy(state = ModelDownloadState.DOWNLOADING, progress = 0f, error = null) }
        // TODO(model-download): fetch spec.sourceUrl → modelFile() with a WorkManager wifi-only job +
        // explicit user consent + progress callbacks (flow.update { it.copy(progress = ...) }). No
        // source URL / consent UI is committed yet, so this reports FAILED rather than pretending.
        flow.update {
            it.copy(
                state = ModelDownloadState.FAILED,
                progress = 0f,
                error = "On-device model download isn't wired yet — using the heuristic tier.",
            )
        }
    }

    override suspend fun delete(modelId: String) {
        if (modelId != spec.id) return
        modelFile().delete()
        flow.update { snapshot() }
    }

    private fun snapshot(): ModelInfo =
        ModelInfo(
            id = spec.id,
            displayName = spec.displayName,
            approxSizeMb = spec.approxSizeMb,
            state = if (isReady()) ModelDownloadState.READY else ModelDownloadState.ABSENT,
        )

    private data class ModelSpec(
        val id: String,
        val displayName: String,
        val approxSizeMb: Int,
        val fileName: String,
    )

    private companion object {
        const val MODELS_DIR = "models"
        val GEMMA_3_1B =
            ModelSpec(
                id = "gemma3-1b",
                displayName = "Gemma 3 1B (on-device)",
                approxSizeMb = 555,
                fileName = "gemma3-1b-it.task",
            )
    }
}
