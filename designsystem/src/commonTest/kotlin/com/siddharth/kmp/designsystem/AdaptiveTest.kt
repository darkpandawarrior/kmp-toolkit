package com.siddharth.kmp.designsystem

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdaptiveTest {
    @Test
    fun handheldAndDesktopUseMaterialWidthClasses() {
        listOf(FormFactor.Handheld, FormFactor.Desktop).forEach { f ->
            assertEquals(WindowType.Compact, windowTypeFor(0.dp, f))
            assertEquals(WindowType.Compact, windowTypeFor(599.dp, f))
            assertEquals(WindowType.Medium, windowTypeFor(600.dp, f))
            assertEquals(WindowType.Medium, windowTypeFor(839.dp, f))
            assertEquals(WindowType.Expanded, windowTypeFor(840.dp, f))
            assertEquals(WindowType.Expanded, windowTypeFor(3840.dp, f))
        }
    }

    @Test
    fun watchIsAlwaysCompact() {
        listOf(0.dp, 192.dp, 227.dp, 4000.dp).forEach {
            assertEquals(WindowType.Compact, windowTypeFor(it, FormFactor.Watch))
        }
    }

    @Test
    fun realAndroidTvResolvesToTheStandardTenFootSet() {
        // Every real Android TV normalises to ~960dp wide regardless of panel resolution, so it must
        // land on TvTokens — the standard set, not a cut-down one. This is the regression that the
        // source repo's 1400/2600dp breakpoints would have introduced.
        val tv = windowTypeFor(960.dp, FormFactor.Tv)
        assertEquals(WindowType.Compact, tv)
        assertEquals(TvTokens, tokensFor(FormFactor.Tv, tv))
        assertTrue(tokensFor(FormFactor.Tv, tv).body.value >= 18f, "10-foot body must clear 18sp")
    }

    @Test
    fun tvBreakpointsCoverDesktopAsTvWindows() {
        assertEquals(WindowType.Compact, windowTypeFor(1279.dp, FormFactor.Tv))
        assertEquals(WindowType.Medium, windowTypeFor(1280.dp, FormFactor.Tv))
        assertEquals(WindowType.Expanded, windowTypeFor(1920.dp, FormFactor.Tv))
    }

    @Test
    fun everyFormFactorWindowTypePairResolves() {
        FormFactor.entries.forEach { f ->
            WindowType.entries.forEach { w ->
                val t = tokensFor(f, w)
                assertTrue(t.screenPadding.value > 0f, "$f/$w has no screen padding")
                assertTrue(t.gridColumns >= 1, "$f/$w has no grid columns")
                assertTrue(t.focusScale >= 1f, "$f/$w must not shrink focused items")
            }
        }
    }

    @Test
    fun onlyTvCarriesOverscanAndFocusScale() {
        FormFactor.entries.forEach { f ->
            WindowType.entries.forEach { w ->
                val t = tokensFor(f, w)
                if (f == FormFactor.Tv) {
                    assertTrue(t.overscanPadding.value > 0f, "TV must reserve an overscan band")
                    assertTrue(t.focusScale > 1f, "TV focus must be visible from a sofa")
                } else {
                    assertEquals(0.dp, t.overscanPadding, "$f must not reserve overscan")
                    assertEquals(1f, t.focusScale, "$f is not focus-driven")
                }
            }
        }
    }

    @Test
    fun tenFootTypeOutgrowsArmsLengthType() {
        // A TV read from three metres needs bigger type than a desktop monitor at the same
        // WindowType — this is the whole reason FormFactor is a separate axis from width.
        WindowType.entries.forEach { w ->
            val tv = tokensFor(FormFactor.Tv, w)
            val desk = tokensFor(FormFactor.Desktop, w)
            assertTrue(tv.body.value > desk.body.value, "TV body must outgrow desktop body at $w")
            assertTrue(tv.title.value > desk.title.value, "TV title must outgrow desktop title at $w")
            assertTrue(tv.screenPadding > desk.screenPadding, "TV needs a wider safe inset at $w")
        }
    }

    @Test
    fun watchIsTheTightestSurface() {
        val watch = WatchTokens
        assertTrue(watch.screenPadding < CompactTokens.screenPadding)
        assertTrue(watch.body.value < CompactTokens.body.value)
        assertEquals(1, watch.gridColumns, "a wrist has room for one column")
    }

    @Test
    fun eachLadderGrowsMonotonically() {
        val ladders = mapOf(
            "handheld" to listOf(CompactTokens, MediumTokens, ExpandedTokens),
            "tv" to listOf(TvTokens, TvMediumTokens, TvExpandedTokens),
        )
        ladders.forEach { (name, ladder) ->
            ladder.zipWithNext { small, large ->
                assertTrue(large.screenPadding > small.screenPadding, "$name screenPadding")
                assertTrue(large.itemSpacing > small.itemSpacing, "$name itemSpacing")
                assertTrue(large.sectionSpacing > small.sectionSpacing, "$name sectionSpacing")
                assertTrue(large.toolbarHeight > small.toolbarHeight, "$name toolbarHeight")
                assertTrue(large.gridColumns > small.gridColumns, "$name gridColumns")
                assertTrue(large.title.value > small.title.value, "$name title")
                assertTrue(large.body.value > small.body.value, "$name body")
            }
        }
    }

    @Test
    fun spacingStaysOnTheFourDpGrid() {
        val all = listOf(
            CompactTokens, MediumTokens, ExpandedTokens,
            WatchTokens, TvTokens, TvMediumTokens, TvExpandedTokens,
        )
        all.forEach { t ->
            listOf(
                t.screenPadding, t.itemSpacing, t.sectionSpacing, t.toolbarHeight, t.overscanPadding,
            ).forEach {
                assertTrue(it.value.toInt() % 4 == 0, "$it is off the 4dp grid")
            }
        }
    }
}
