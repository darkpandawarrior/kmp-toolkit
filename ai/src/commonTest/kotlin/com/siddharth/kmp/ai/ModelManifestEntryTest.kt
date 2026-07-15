package com.siddharth.kmp.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ModelManifestEntryTest {
    @Test
    fun download_url_is_derived_from_hugging_face_coordinates() {
        val entry =
            ModelManifestEntry(
                id = "gemma3-1b",
                displayName = "Gemma 3 1B",
                approxSizeMb = 555,
                fileName = "gemma3-1b-it.task",
                hfRepo = "litert-community/Gemma3-1B-IT",
                hfFile = "gemma3-1b-it.task",
            )
        assertEquals(
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it.task",
            entry.downloadUrl,
        )
    }

    @Test
    fun license_ack_defaults_to_false() {
        val entry =
            ModelManifestEntry(
                id = "m",
                displayName = "M",
                approxSizeMb = 1,
                fileName = "m.task",
                hfRepo = "org/repo",
                hfFile = "m.task",
            )
        assertFalse(entry.requiresLicenseAck)
    }
}
