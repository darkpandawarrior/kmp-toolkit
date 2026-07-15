package com.siddharth.kmp.ai

import android.content.Context
import android.os.Build
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest

// ponytail: EXPERIMENTAL — com.google.mlkit:genai-prompt:1.0.0-beta2 (ML Kit GenAI Prompt API,
// Gemini Nano). Compile-verified only, NOT device-verified: no Gemini-Nano-class hardware
// (Pixel 8+/AICore-eligible) is available in this environment. Mirrors Mileway's MlKitGenAiAnalyzer.
//
// [isAvailable] is a cheap device-tier floor (the API's own minSdk). The authoritative gate lives in
// [generate]: it only runs inference when checkStatus() == AVAILABLE (model resident).
// DOWNLOADABLE/DOWNLOADING/UNAVAILABLE all decline rather than trigger a multi-hundred-MB on-demand
// download mid-request — there's no download-progress UX yet, so pre-warming stays out of scope.
class MlKitGenAiOnDeviceLlm(
    @Suppress("unused") private val context: Context,
) : OnDeviceLlm {
    private val model: GenerativeModel by lazy { Generation.getClient() }

    override fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    // ImagePart(byte[]) is a direct constructor on this API — no manual Bitmap decode needed here.
    override val supportsImage: Boolean = true

    override suspend fun generate(prompt: String): String? = generate(listOf(LlmPart.Text(prompt)))

    override suspend fun generate(parts: List<LlmPart>): String? {
        if (!isAvailable()) return null
        return runCatching { runGeneration(parts) }.getOrNull()
    }

    private suspend fun runGeneration(parts: List<LlmPart>): String? {
        if (model.checkStatus() != FeatureStatus.AVAILABLE) return null
        val request = buildRequest(parts) ?: return null
        return model
            .generateContent(request)
            .candidates
            .firstOrNull()
            ?.text
            ?.takeIf { it.isNotBlank() }
    }

    // generateContentRequest() is NOT vararg on this API — it's two fixed overloads, TextPart-only
    // or ImagePart+TextPart (image first, text LAST — the model reads the trailing text as the
    // instruction anchoring the preceding image). Multiple images aren't representable by either
    // overload, so that shape is declined rather than silently dropping extras.
    private fun buildRequest(parts: List<LlmPart>): GenerateContentRequest? {
        val images = parts.filterIsInstance<LlmPart.Image>()
        if (images.size > 1) return null
        val text = parts.filterIsInstance<LlmPart.Text>().joinToString("\n") { it.text }
        val image = images.singleOrNull()
        if (image == null && text.isBlank()) return null
        return image?.let { generateContentRequest(ImagePart(it.bytes), TextPart(text)) {} }
            ?: generateContentRequest(TextPart(text)) {}
    }
}
