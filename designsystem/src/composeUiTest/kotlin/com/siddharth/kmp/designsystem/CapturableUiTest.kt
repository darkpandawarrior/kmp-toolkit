package com.siddharth.kmp.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Two things here can break while the build stays green: the modifier can stop routing drawing
 * through the layer, and the pre-draw guard can stop firing. Both are asserted below.
 *
 * The assertions are deliberately on `hasRecorded` rather than on captured pixels. Reading pixels
 * back is `suspend`, and driving a suspend call from inside `runComposeUiTest` means nesting a
 * second coroutine runner inside the compose test clock, which deadlocks on the wasm and iOS
 * runners this source set actually executes on. `toImageBitmap()` itself is Compose's code and is
 * tested upstream; what is ours — and what these cover — is whether the layer was recorded at all.
 */
@OptIn(ExperimentalTestApi::class)
class CapturableUiTest {
    @Test
    fun modifierRecordsTheSubtreeOnDraw() = runComposeUiTest {
        lateinit var controller: CaptureController
        setContent {
            controller = rememberCaptureController()
            Box(
                Modifier
                    .size(20.dp)
                    .capturable(controller)
                    .background(Color.Red),
            )
        }
        waitForIdle()
        assertTrue(
            controller.hasRecorded,
            "capturable() drew without recording into the layer — capture would return a blank image",
        )
    }

    @Test
    fun controllerRefusesToCaptureContentThatNeverDrew() = runComposeUiTest {
        lateinit var controller: CaptureController
        setContent {
            // Controller created but capturable() deliberately never applied to anything.
            controller = rememberCaptureController()
            Box(Modifier.size(20.dp).background(Color.Blue))
        }
        waitForIdle()
        assertFalse(
            controller.hasRecorded,
            "nothing was marked capturable, so the layer must not be considered recorded",
        )
    }
}
