package com.siddharth.kmp.designsystem

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationLayoutTest {
    @Test
    fun phoneGetsABottomBarAndTabletsGetARail() {
        assertEquals(NavigationLayout.BottomBar, navigationLayoutFor(FormFactor.Handheld, 360.dp))
        assertEquals(NavigationLayout.BottomBar, navigationLayoutFor(FormFactor.Handheld, 599.dp))
        assertEquals(NavigationLayout.Rail, navigationLayoutFor(FormFactor.Handheld, 600.dp))
        assertEquals(NavigationLayout.Rail, navigationLayoutFor(FormFactor.Handheld, 1199.dp))
    }

    @Test
    fun aDrawerNeeds1200dpNotMerelyExpanded() {
        // 840dp is Expanded — a tablet in landscape, which still wants a rail. Calibrated against
        // the official Reply sample, not against the WindowType boundary.
        assertEquals(WindowType.Expanded, windowTypeFor(840.dp, FormFactor.Desktop))
        assertEquals(NavigationLayout.Rail, navigationLayoutFor(FormFactor.Desktop, 840.dp))
        assertEquals(NavigationLayout.Drawer, navigationLayoutFor(FormFactor.Desktop, 1200.dp))
        assertEquals(NavigationLayout.Drawer, navigationLayoutFor(FormFactor.Desktop, 2560.dp))
    }

    @Test
    fun televisionNeverGetsABottomBarAtAnyWidth() {
        // The regression this exists for: a width-only rule calls a TV "expanded" and hands it a
        // bottom bar, which is off-convention and unreachable by D-pad.
        listOf(960.dp, 1280.dp, 1920.dp, 3840.dp).forEach { w ->
            assertEquals(NavigationLayout.Rail, navigationLayoutFor(FormFactor.Tv, w), "at $w")
        }
    }

    @Test
    fun wearShowsNoPersistentChrome() {
        listOf(192.dp, 227.dp).forEach { w ->
            assertEquals(NavigationLayout.None, navigationLayoutFor(FormFactor.Watch, w), "at $w")
        }
    }

    @Test
    fun tabletopPostureDemotesToABottomBarHoweverWideTheWindow() {
        // Folded flat like a laptop: the lower half is a keyboard deck, so the bar stays reachable.
        assertEquals(
            NavigationLayout.BottomBar,
            navigationLayoutFor(FormFactor.Handheld, 1400.dp, tabletop = true),
        )
        assertEquals(
            NavigationLayout.Drawer,
            navigationLayoutFor(FormFactor.Handheld, 1400.dp, tabletop = false),
        )
    }

    @Test
    fun postureNeverOverridesTheTwoFixedSurfaces() {
        assertEquals(NavigationLayout.None, navigationLayoutFor(FormFactor.Watch, 200.dp, tabletop = true))
        assertEquals(NavigationLayout.Rail, navigationLayoutFor(FormFactor.Tv, 960.dp, tabletop = true))
    }
}
