package com.siddharth.kmp.ai

import android.content.Context
import android.os.Build
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
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

    override suspend fun generate(prompt: String): String? {
        if (!isAvailable()) return null
        return runCatching { runGeneration(prompt) }.getOrNull()
    }

    private suspend fun runGeneration(prompt: String): String? {
        if (model.checkStatus() != FeatureStatus.AVAILABLE) return null
        val request = generateContentRequest(TextPart(prompt)) {}
        return model
            .generateContent(request)
            .candidates
            .firstOrNull()
            ?.text
            ?.takeIf { it.isNotBlank() }
    }
}
