package com.siddharth.kmp.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.Result
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Device-verifies [MediaPipeOnDeviceLlm] against a real [Context]/filesystem — the class's own
 * ceiling comment says "compile-verified only, NOT device-verified" because inference needs a
 * downloaded model file this repo never ships. A managed device / emulator never has one either,
 * so these tests exercise the one contract that IS testable without one: [isAvailable] and
 * [generate] must read the real on-disk absence honestly (`ModelNotResident`), never crash trying
 * to load a model that isn't there.
 */
@RunWith(AndroidJUnit4::class)
class MediaPipeOnDeviceLlmTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun freshLlm(): MediaPipeOnDeviceLlm {
        val manager = MediaPipeModelManager(context)
        // A fresh app install/test run never has the ~555MB model downloaded — assert the
        // precondition these tests depend on, rather than silently passing against a leftover file
        // from a previous test run on the same managed device.
        assertFalse(manager.isReady(), "test assumes no model file is present on this device")
        return MediaPipeOnDeviceLlm(context, manager)
    }

    @Test
    fun isAvailable_isFalse_onARealDeviceWithNoModelDownloaded() {
        assertFalse(freshLlm().isAvailable())
    }

    @Test
    fun generate_declinesWithModelNotResident_ratherThanTryingToLoadAMissingFile() =
        runTest {
            assertEquals(Result.Failure(AiFailure.ModelNotResident), freshLlm().generate("hello"))
        }

    @Test
    fun capabilities_reportsModelNotResident_andTheFullSamplerShapeItSupportsWhenReady() =
        runTest {
            val caps = freshLlm().capabilities()
            assertEquals(AiFailure.ModelNotResident, caps.unavailableReason)
            assertTrue(caps.streaming)
            assertEquals(setOf("topK", "topP", "temperature", "maxTokens", "accelerator"), caps.honoredConfigFields)
        }

    @Test
    fun generateStream_completesEmpty_ratherThanCrashing_whenNoModelIsResident() =
        runTest {
            assertTrue(freshLlm().generateStream("hello").toList().isEmpty())
        }
}
