package com.siddharth.kmp.ai

/**
 * One piece of multimodal input to [OnDeviceLlm.generate]. `ByteArray` (not a platform bitmap
 * type) keeps this commonMain-safe — each platform actual decodes the bytes itself.
 *
 * Audio is deliberately not modeled yet (no backend needs it) — add a case here when one does.
 */
sealed interface LlmPart {
    data class Text(val text: String) : LlmPart

    data class Image(val bytes: ByteArray, val mime: String = "image/png") : LlmPart {
        // ByteArray has no structural equals/hashCode of its own (Kotlin data classes fall back to
        // reference identity for array properties) — override so two Images over equal bytes compare equal.
        override fun equals(other: Any?): Boolean =
            this === other ||
                (other is Image && mime == other.mime && bytes.contentEquals(other.bytes))

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + mime.hashCode()
    }
}
