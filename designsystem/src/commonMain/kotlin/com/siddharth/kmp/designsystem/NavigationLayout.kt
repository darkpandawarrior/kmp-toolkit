package com.siddharth.kmp.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Which navigation affordance a surface should show. */
enum class NavigationLayout {
    /** Bottom bar. Thumb-reachable; the right answer on a phone and on a tabletop fold. */
    BottomBar,

    /** Left rail. Tablets, desktop, and every television — leanback never uses a bottom bar. */
    Rail,

    /** Permanently visible drawer. Only once there is genuinely room to spare. */
    Drawer,

    /** No persistent chrome. Wear navigates by swipe and scroll; a nav bar would eat the screen. */
    None,
}

/**
 * Picks the navigation affordance for a surface.
 *
 * Exists because the first-party `NavigationSuiteScaffold` decides this from **window width alone**,
 * which is right for phones and tablets and wrong for the two surfaces that are defined by something
 * other than width:
 *
 * - **Television** gets a bottom bar from a width-only rule, because a TV is "expanded". Leanback
 *   convention is a left rail driven by D-pad focus; a bottom bar is unreachable and off-convention.
 * - **Wear** gets a bottom bar too, on a 192dp screen, which is most of the display.
 *
 * The width thresholds themselves are calibrated against the official Reply sample rather than
 * invented: compact gets a bar, a drawer needs **1200dp** (not the 840dp Expanded boundary — Expanded
 * starts at a tablet in landscape, which still wants a rail), everything between gets a rail.
 *
 * [tabletop] is the folded-flat posture — a foldable open like a laptop, where the lower half is
 * effectively a keyboard deck. Reply demotes that to a bottom bar however wide the window is, because
 * the top half is the only part being looked at. Posture cannot be detected from `commonMain`, so it
 * is a parameter: pass `currentWindowAdaptiveInfo().windowPosture.isTabletop` on Android, leave it
 * `false` everywhere else.
 *
 * ```
 * val nav = navigationLayoutFor(LocalFormFactor.current, maxWidth)
 * when (nav) {
 *     NavigationLayout.BottomBar -> Scaffold(bottomBar = { ... })
 *     NavigationLayout.Rail -> Row { NavigationRail { ... }; content() }
 *     NavigationLayout.Drawer -> PermanentNavigationDrawer(drawerContent = { ... })
 *     NavigationLayout.None -> content()
 * }
 * ```
 *
 * ponytail: returns an enum rather than wrapping a scaffold. Deciding the layout needs no
 * dependency; *rendering* it would pull in `material3-adaptive-navigation-suite`, and new
 * dependencies are ask-first here. The decision is the part that carries the knowledge — the
 * rendering is three lines of first-party Material at the call site.
 */
fun navigationLayoutFor(
    formFactor: FormFactor,
    width: Dp,
    tabletop: Boolean = false,
): NavigationLayout = when {
    formFactor == FormFactor.Watch -> NavigationLayout.None
    formFactor == FormFactor.Tv -> NavigationLayout.Rail
    tabletop -> NavigationLayout.BottomBar
    windowTypeFor(width, formFactor) == WindowType.Compact -> NavigationLayout.BottomBar
    width >= DRAWER_MIN_WIDTH -> NavigationLayout.Drawer
    else -> NavigationLayout.Rail
}

/**
 * Below this a permanent drawer crowds the content it is supposed to be navigating. Sits well above
 * the 840dp Expanded boundary on purpose — a tablet in landscape is Expanded and still wants a rail.
 */
private val DRAWER_MIN_WIDTH = 1200.dp
