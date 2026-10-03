package com.siddharth.kmp.ai

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.Result
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Device-verifies [MlKitGenAiOnDeviceLlm] against the real ML Kit GenAI Prompt API client — the
 * class's own ceiling comment says "compile-verified only, NOT device-verified" because full
 * generation needs Gemini-Nano-class hardware (Pixel 8+/AICore-eligible) a managed device or
 * emulator doesn't have. [capabilities] and [generate] already wrap the real `checkStatus()`/
 * `generateContent()` calls in `runCatching` specifically so a non-AICore device declines
 * honestly rather than throwing — these tests device-verify that exact guarantee: a real call into
 * the SDK on real hardware never surfaces an exception to the caller, only a typed [AiFailure].
 */
@RunWith(AndroidJUnit4::class)
class MlKitGenAiOnDeviceLlmTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun isAvailable_matchesTheDocumentedSdkFloor() {
        val llm = MlKitGenAiOnDeviceLlm(context)
        assertEquals(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O, llm.isAvailable())
    }

    @Test
    fun capabilities_neverThrows_andReportsAnHonestReason_onARealNonAICoreDevice() =
        runTest {
            val caps = MlKitGenAiOnDeviceLlm(context).capabilities()
            // A managed device/emulator is never AICore-eligible, so the real checkStatus() call
            // must land in one of these two branches, not the (unreachable here) success case.
            assertTrue(
                caps.unavailableReason == AiFailure.ModelNotResident || caps.unavailableReason == AiFailure.NotSupportedOnPlatform,
                "expected an honest unavailable reason, got ${caps.unavailableReason}",
            )
        }

    @Test
    fun generate_declinesWithATypedFailure_ratherThanThrowing_onARealNonAICoreDevice() =
        runTest {
            val result = MlKitGenAiOnDeviceLlm(context).generate("hello")
            assertTrue(result is Result.Failure, "expected a typed AiFailure, got $result")
        }

    @Test
    fun generateStream_completesWithoutThrowing_onARealNonAICoreDevice() =
        runTest {
            // No assertion on the emitted chunks themselves (device-dependent) — the point is that
            // collecting this Flow to completion never throws on real hardware.
            MlKitGenAiOnDeviceLlm(context).generateStream("hello").toList()
        }
}
