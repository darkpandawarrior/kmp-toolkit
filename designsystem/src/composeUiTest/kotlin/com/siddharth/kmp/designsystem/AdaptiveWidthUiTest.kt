package com.siddharth.kmp.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Measures the centre-and-cap modifiers for real. These are pure `Modifier` chains with no function
 * to assert against — the only honest check is to lay them out and read the bounds back, which is
 * what the `composeUiTest` source set is for.
 *
 * Every case appends `fillMaxWidth()` after the modifier under test, and asserts an **exact** width.
 * Both matter: `centredCap` ends in `wrapContentWidth`, so a childless Box measures 0dp and a
 * `width <= cap` assertion passes without testing anything. That false pass is easy to write and
 * invisible in a green run.
 */
@OptIn(ExperimentalTestApi::class)
class AdaptiveWidthUiTest {
    private fun ComposeUiTest.measure(tag: String): Dp =
        onNodeWithTag(tag).getUnclippedBoundsInRoot().let { it.right - it.left }

    @Test
    fun proseIsCappedWellShortOfAWideWindow() = runComposeUiTest {
        setContent {
            AdaptiveTheme(formFactor = FormFactor.Desktop, windowType = WindowType.Expanded) {
                Box(Modifier.size(2560.dp, 400.dp)) {
                    Box(Modifier.readableWidth().fillMaxWidth().testTag("prose"))
                }
            }
        }
        // Uncapped this is 2560dp — roughly 200 characters a line.
        assertEquals(640.dp, measure("prose"))
    }

    @Test
    fun theTenFootCapIsWiderThanTheDesktopOne() = runComposeUiTest {
        // Compared at the SAME container width rather than by absolute cap: the harness root is
        // 1024dp, so a 1100dp cap can never be reached and asserting it would only measure the
        // harness. At 900dp the desktop cap bites and the television cap does not, which is the
        // actual claim — a TV sets body at 18sp+, so the same character count needs more dp.
        setContent {
            Box(Modifier.size(900.dp, 400.dp)) {
                AdaptiveTheme(formFactor = FormFactor.Tv, windowType = WindowType.Compact) {
                    Box(Modifier.readableWidth().fillMaxWidth().testTag("tv"))
                }
            }
        }
        assertEquals(900.dp, measure("tv"))
    }

    @Test
    fun theDesktopCapBitesAtTheSameWidthTheTelevisionOneDoesNot() = runComposeUiTest {
        setContent {
            Box(Modifier.size(900.dp, 400.dp)) {
                AdaptiveTheme(formFactor = FormFactor.Desktop, windowType = WindowType.Expanded) {
                    Box(Modifier.readableWidth().fillMaxWidth().testTag("desktop"))
                }
            }
        }
        assertEquals(640.dp, measure("desktop"))
    }

    @Test
    fun aWatchIsNeverCapped() = runComposeUiTest {
        setContent {
            AdaptiveTheme(formFactor = FormFactor.Watch, windowType = WindowType.Compact) {
                Box(Modifier.size(200.dp, 200.dp)) {
                    Box(Modifier.readableWidth().fillMaxWidth().testTag("prose"))
                }
            }
        }
        // Already narrower than any sensible measure; capping would only waste the screen.
        assertEquals(200.dp, measure("prose"))
    }

    @Test
    fun contentWidthCapsWiderThanProse() = runComposeUiTest {
        // Both in one composition at one width, so the assertion is the relationship between them
        // rather than two absolute numbers: a card grid reads fine where a paragraph would not.
        setContent {
            AdaptiveTheme(formFactor = FormFactor.Desktop, windowType = WindowType.Expanded) {
                Box(Modifier.size(900.dp, 400.dp)) {
                    Box(Modifier.contentWidth().fillMaxWidth().testTag("grid"))
                    Box(Modifier.readableWidth().fillMaxWidth().testTag("prose2"))
                }
            }
        }
        assertEquals(900.dp, measure("grid"), "contentWidth must not bite at 900dp")
        assertEquals(640.dp, measure("prose2"), "readableWidth must bite at 900dp")
    }

    @Test
    fun screenPaddingInsetsContentByTheSurfaceToken() = runComposeUiTest {
        setContent {
            AdaptiveTheme(formFactor = FormFactor.Handheld, windowType = WindowType.Compact) {
                Box(Modifier.size(400.dp, 400.dp)) {
                    Box(Modifier.screenPadding().fillMaxWidth().testTag("body"))
                }
            }
        }
        // CompactTokens.screenPadding is 16dp a side; the harness reports no system insets.
        assertEquals(368.dp, measure("body"))
    }
}
