package com.siddharth.kmp.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Renders [OtpField] for real and drives it through the test harness. This is the first
 * `runComposeUiTest` in the family — it runs on the Android host JVM, the iOS simulator and headless
 * Chrome from a single source set, which is exactly the reach Robolectric and Roborazzi don't have.
 */
@OptIn(ExperimentalTestApi::class)
class OtpFieldUiTest {
    @Test
    fun typingDigitsDrivesTheValueAndFiresCompleteOnce() = runComposeUiTest {
        var value = ""
        var completed: String? = null
        var completions = 0

        setContent {
            var code by remember { mutableStateOf("") }
            OtpField(
                value = code,
                onValueChange = { code = it; value = it },
                onComplete = { completed = it; completions++ },
                length = 4,
                modifier = Modifier.testTag("otp"),
            )
        }

        onNodeWithTag("otp").performTextInput("12")
        assertEquals("12", value)
        assertNull(completed, "must not complete before the code is full")

        onNodeWithTag("otp").performTextInput("34")
        assertEquals("1234", value)
        assertEquals("1234", completed)
        assertEquals(1, completions)
    }

    @Test
    fun nonDigitsAreStrippedAndOverflowIsTruncated() = runComposeUiTest {
        var value = ""
        setContent {
            var code by remember { mutableStateOf("") }
            OtpField(
                value = code,
                onValueChange = { code = it; value = it },
                length = 4,
                modifier = Modifier.testTag("otp"),
            )
        }

        // The notification-paste path, end to end rather than just through sanitizeOtp.
        onNodeWithTag("otp").performTextReplacement("G-4821 is your code")
        assertEquals("4821", value)
    }

    @Test
    fun pageIndicatorRendersNothingForASinglePage() = runComposeUiTest {
        setContent {
            PageIndicator(currentPage = 0, pageCount = 1, modifier = Modifier.testTag("dots"))
        }
        onNodeWithTag("dots").assertDoesNotExist()
    }
}
