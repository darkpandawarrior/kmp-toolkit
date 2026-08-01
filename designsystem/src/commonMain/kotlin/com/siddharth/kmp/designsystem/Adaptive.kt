package com.siddharth.kmp.designsystem

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Adaptive layout on two orthogonal axes.
 *
 * Width alone is not enough. A 1920dp TV and a 1920dp desktop monitor are the same width bucket but
 * need completely different treatment — the TV is viewed from three metres with a D-pad, the monitor
 * from half a metre with a mouse. Forking the whole token system per form factor (one copy for
 * handheld, another for TV, a third for watch) is the usual answer and it triples every future edit.
 *
 * So: [FormFactor] answers *how far away the eye is and what the input is*, [WindowType] answers
 * *how much room there is*, and [AdaptiveTokens] resolves from both.
 */

/**
 * Viewing posture and input model. Not derivable from width — it comes from the platform, via
 * [rememberFormFactor].
 */
enum class FormFactor {
    /** Wear OS. ~192–227dp wide, viewed at ~30cm, rotary/touch input. */
    Watch,

    /** Phone, foldable, tablet. Arm's length, touch-first. */
    Handheld,

    /** Desktop JVM window, browser. ~50cm, mouse and keyboard. */
    Desktop,

    /** Android TV / leanback. ~3m ("10-foot"), D-pad focus, overscan-unsafe edges. */
    Tv,
}

/**
 * How much room there is, within a given [FormFactor]. The thresholds differ per form factor
 * (see [windowTypeFor]) — `Expanded` means "a big TV" on [FormFactor.Tv] and "a tablet in landscape"
 * on [FormFactor.Handheld].
 */
enum class WindowType {
    Compact,
    Medium,
    Expanded,
}

/**
 * Resolves the width bucket, using breakpoints appropriate to [formFactor].
 *
 * - **Handheld / Desktop** use the Material 3 width classes (600dp / 840dp) so a consumer that
 *   already resolves a `WindowSizeClass` upstream maps onto this 1:1.
 * - **Watch** is always [WindowType.Compact]. Every Wear OS device is 192–227dp; bucketing is noise.
 * - **Tv** uses 1280dp / 1920dp. Note that real Android TV normalises to roughly **960dp** wide
 *   regardless of panel resolution — a 720p set runs tvdpi and a 1080p set runs xhdpi, and both land
 *   near 960x540dp. So essentially every real television resolves to [WindowType.Compact], and the
 *   Compact TV token set is therefore the *standard* 10-foot set, not a cut-down one. The Medium and
 *   Expanded TV sets exist for desktop-as-TV windows and ultrawide panels.
 *
 *   (The repo this idea came from used 1400/2600dp for TV, which classifies every real Android TV as
 *   its smallest bucket while labelling it "720p" — those numbers describe a desktop window emulating
 *   a TV, not a TV.)
 */
fun windowTypeFor(width: Dp, formFactor: FormFactor = FormFactor.Handheld): WindowType =
    when (formFactor) {
        FormFactor.Watch -> WindowType.Compact
        FormFactor.Tv -> when {
            width < 1280.dp -> WindowType.Compact
            width < 1920.dp -> WindowType.Medium
            else -> WindowType.Expanded
        }
        FormFactor.Handheld, FormFactor.Desktop -> when {
            width < 600.dp -> WindowType.Compact
            width < 840.dp -> WindowType.Medium
            else -> WindowType.Expanded
        }
    }

/**
 * The subset of [DesignTokens] that should breathe with the surface. Everything else — shapes,
 * elevation, motion and the a11y-constant `Size.minTouch` — deliberately stays static in
 * [DesignTokens]: a 48dp touch target is 48dp on every device, and corner radius is brand identity,
 * not a function of viewport width.
 *
 * To extend this per component, follow the Material `FooDefaults` convention rather than growing this
 * class: `object PosterDefaults { fun width(f: FormFactor, w: WindowType): Dp = ... }`. This class
 * holds only values that more than one component reads.
 */
@Immutable
data class AdaptiveTokens(
    val screenPadding: Dp,
    val itemSpacing: Dp,
    val sectionSpacing: Dp,
    val toolbarHeight: Dp,
    /** Sensible column count for a `LazyVerticalGrid` of cards on this surface. */
    val gridColumns: Int,
    /**
     * Outer margin a television may physically clip. Zero on every non-TV surface. Content inset by
     * [screenPadding] is already safe; this is for full-bleed surfaces (hero images, edge-to-edge
     * video) that need to know where the unsafe band ends before placing text or controls in it.
     */
    val overscanPadding: Dp,
    /**
     * Scale applied to a focused item so D-pad focus is visible from across a room. `1f` wherever
     * focus is not the primary input model — on touch surfaces a growing card is just noise.
     */
    val focusScale: Float,
    val title: TextUnit,
    val sectionTitle: TextUnit,
    val body: TextUnit,
    val caption: TextUnit,
)

// ---------------------------------------------------------------------------------------------
// Handheld / Desktop ladder — arm's length, touch or mouse.
// ---------------------------------------------------------------------------------------------

val CompactTokens = AdaptiveTokens(
    screenPadding = 16.dp,
    itemSpacing = 8.dp,
    sectionSpacing = 24.dp,
    toolbarHeight = 64.dp,
    gridColumns = 2,
    overscanPadding = 0.dp,
    focusScale = 1f,
    title = 20.sp,
    sectionTitle = 18.sp,
    body = 14.sp,
    caption = 12.sp,
)

val MediumTokens = AdaptiveTokens(
    screenPadding = 24.dp,
    itemSpacing = 12.dp,
    sectionSpacing = 32.dp,
    toolbarHeight = 68.dp,
    gridColumns = 3,
    overscanPadding = 0.dp,
    focusScale = 1f,
    title = 24.sp,
    sectionTitle = 20.sp,
    body = 15.sp,
    caption = 13.sp,
)

val ExpandedTokens = AdaptiveTokens(
    screenPadding = 32.dp,
    itemSpacing = 16.dp,
    sectionSpacing = 40.dp,
    toolbarHeight = 72.dp,
    gridColumns = 4,
    overscanPadding = 0.dp,
    focusScale = 1f,
    title = 28.sp,
    sectionTitle = 22.sp,
    body = 16.sp,
    caption = 14.sp,
)

// ---------------------------------------------------------------------------------------------
// Watch — a single set. Every Wear OS device is 192–227dp wide; a ladder would be theatre.
// ---------------------------------------------------------------------------------------------

val WatchTokens = AdaptiveTokens(
    screenPadding = 8.dp,
    itemSpacing = 4.dp,
    sectionSpacing = 12.dp,
    toolbarHeight = 32.dp,
    gridColumns = 1,
    overscanPadding = 0.dp,
    focusScale = 1f,
    title = 16.sp,
    sectionTitle = 14.sp,
    body = 13.sp,
    caption = 11.sp,
)

// ---------------------------------------------------------------------------------------------
// TV ladder — 10-foot viewing, D-pad focus, overscan-unsafe edges. [TvTokens] is the standard set:
// real Android TV lands near 960dp wide, which resolves to Compact. Type sizes follow the leanback
// floor (18sp body minimum at 960dp) — a 14sp body is unreadable from a sofa.
// ---------------------------------------------------------------------------------------------

val TvTokens = AdaptiveTokens(
    screenPadding = 48.dp,
    itemSpacing = 16.dp,
    sectionSpacing = 32.dp,
    toolbarHeight = 80.dp,
    gridColumns = 5,
    overscanPadding = 28.dp,
    focusScale = 1.08f,
    title = 34.sp,
    sectionTitle = 24.sp,
    body = 18.sp,
    caption = 14.sp,
)

val TvMediumTokens = AdaptiveTokens(
    screenPadding = 56.dp,
    itemSpacing = 20.dp,
    sectionSpacing = 40.dp,
    toolbarHeight = 88.dp,
    gridColumns = 6,
    overscanPadding = 32.dp,
    focusScale = 1.08f,
    title = 40.sp,
    sectionTitle = 28.sp,
    body = 20.sp,
    caption = 16.sp,
)

val TvExpandedTokens = AdaptiveTokens(
    screenPadding = 64.dp,
    itemSpacing = 24.dp,
    sectionSpacing = 48.dp,
    toolbarHeight = 96.dp,
    gridColumns = 7,
    overscanPadding = 36.dp,
    focusScale = 1.08f,
    title = 48.sp,
    sectionTitle = 32.sp,
    body = 22.sp,
    caption = 18.sp,
)

/** Resolves the token set for a surface. Total coverage: every [FormFactor] x [WindowType] pair. */
fun tokensFor(formFactor: FormFactor, windowType: WindowType): AdaptiveTokens =
    when (formFactor) {
        FormFactor.Watch -> WatchTokens
        FormFactor.Tv -> when (windowType) {
            WindowType.Compact -> TvTokens
            WindowType.Medium -> TvMediumTokens
            WindowType.Expanded -> TvExpandedTokens
        }
        FormFactor.Handheld, FormFactor.Desktop -> when (windowType) {
            WindowType.Compact -> CompactTokens
            WindowType.Medium -> MediumTokens
            WindowType.Expanded -> ExpandedTokens
        }
    }

/**
 * Picks a per-bucket value. This is the whole `object FooDefaults { fun width(w: WindowType) }`
 * idiom compressed to one call — the source this came from wrote that `when` block out longhand
 * **thirty-one times** across ten files (`MovieCardDefaults`, `BannerDefaults`, `ButtonDefaults`,
 * `CastCardDefaults`, …), which is why none of those files were worth copying individually and this
 * function is.
 *
 * ```
 * object PosterDefaults {
 *     fun width(w: WindowType) = byWindow(w, 100.dp, 140.dp, 160.dp)
 *     fun height(w: WindowType) = byWindow(w, 150.dp, 200.dp, 230.dp)
 * }
 * ```
 *
 * Generic in [T], so it carries `Dp`, `TextUnit`, `Int`, `Alignment`, a lambda — anything that
 * differs by width.
 */
fun <T> byWindow(windowType: WindowType, compact: T, medium: T, expanded: T): T =
    when (windowType) {
        WindowType.Compact -> compact
        WindowType.Medium -> medium
        WindowType.Expanded -> expanded
    }

/** [byWindow] against the ambient [LocalWindowType]. */
@Composable
fun <T> byWindow(compact: T, medium: T, expanded: T): T =
    byWindow(LocalWindowType.current, compact, medium, expanded)

/**
 * Picks a per-surface value, overriding only the surfaces that actually differ.
 *
 * Most components need one value everywhere and one exception — a poster that must be bigger on a
 * television, a control that must be smaller on a wrist. Spelling out four branches to change one of
 * them is how the source ended up with a second, fully duplicated token system for TV.
 *
 * ```
 * val posterWidth = byFormFactor(default = 140.dp, watch = 64.dp, tv = 220.dp)
 * ```
 */
fun <T> byFormFactor(
    formFactor: FormFactor,
    default: T,
    watch: T = default,
    handheld: T = default,
    desktop: T = default,
    tv: T = default,
): T = when (formFactor) {
    FormFactor.Watch -> watch
    FormFactor.Handheld -> handheld
    FormFactor.Desktop -> desktop
    FormFactor.Tv -> tv
}

/** [byFormFactor] against the ambient [LocalFormFactor]. */
@Composable
fun <T> byFormFactor(
    default: T,
    watch: T = default,
    handheld: T = default,
    desktop: T = default,
    tv: T = default,
): T = byFormFactor(LocalFormFactor.current, default, watch, handheld, desktop, tv)

/**
 * ponytail: defaults to Compact handheld rather than `error("no AdaptiveTheme")`. A hard error is
 * louder, but it also breaks every `@Preview` and Compose UI test that renders a leaf composable
 * without the app shell around it. Compact is the safe wrong answer — phone metrics on a television
 * look cramped, not broken. Switch to `error()` if a real screen ever ships un-wrapped by accident.
 */
val LocalAdaptiveTokens = staticCompositionLocalOf { CompactTokens }

val LocalWindowType = staticCompositionLocalOf { WindowType.Compact }

val LocalFormFactor = staticCompositionLocalOf { FormFactor.Handheld }

/**
 * Provides [AdaptiveTokens] for the current surface. Wrap the app shell once, then read
 * `LocalAdaptiveTokens.current.screenPadding` anywhere below.
 *
 * [formFactor] is detected from the platform by default ([rememberFormFactor]); pass it explicitly to
 * force a surface — useful for previews, screenshot tests, and a desktop build that renders a TV UI.
 *
 * Width comes from the *container*, not the window, so nesting works: a detail pane inside a two-pane
 * layout resolves to Compact even on an Expanded window, which is what you want.
 */
@Composable
fun AdaptiveTheme(
    formFactor: FormFactor = rememberFormFactor(),
    content: @Composable () -> Unit,
) {
    BoxWithConstraints {
        AdaptiveTheme(formFactor, windowTypeFor(maxWidth, formFactor), content)
    }
}

/**
 * Insets the content by the surface's *unsafe* regions and then by its adaptive [screenPadding], in
 * one call. Use this instead of `padding(tokens.screenPadding)`.
 *
 * A raw `screenPadding` is not enough on real hardware: on a landscape phone the display cutout and
 * the gesture bar eat into the same edge, on iOS the notch and home indicator do, and on a television
 * the panel physically clips the border. Applying only the token clips content on all three. This
 * applies `WindowInsets.safeDrawing` first — which covers cutouts, system bars and the IME on every
 * platform that has them, and is empty on desktop and web — then the token on top.
 *
 * ```
 * Column(Modifier.screenPadding()) { /* never under a notch, never off the edge of a TV */ }
 * LazyColumn(Modifier.screenPadding(vertical = false))   // let the list scroll under the status bar
 * ```
 *
 * ponytail: the TV case rides on `screenPadding` alone, which is already overscan-safe (48dp+), not
 * on [AdaptiveTokens.overscanPadding]. Adding both would double-inset. `overscanPadding` is for
 * full-bleed surfaces that opt out of this modifier entirely — a hero image that must reach the edge
 * but keep its caption out of the clipped band.
 */
@Composable
fun Modifier.screenPadding(horizontal: Boolean = true, vertical: Boolean = true): Modifier {
    val pad = LocalAdaptiveTokens.current.screenPadding
    val sides = when {
        horizontal && vertical -> WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical
        horizontal -> WindowInsetsSides.Horizontal
        vertical -> WindowInsetsSides.Vertical
        else -> return this
    }
    return this
        .windowInsetsPadding(WindowInsets.safeDrawing.only(sides))
        .padding(
            horizontal = if (horizontal) pad else 0.dp,
            vertical = if (vertical) pad else 0.dp,
        )
}

/**
 * Escape hatch for consumers that already resolve a window size class upstream (Android
 * `currentWindowAdaptiveInfo()`, a desktop window listener) and don't want a second
 * `BoxWithConstraints` subcomposition at the root.
 */
@Composable
fun AdaptiveTheme(
    formFactor: FormFactor,
    windowType: WindowType,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAdaptiveTokens provides tokensFor(formFactor, windowType),
        LocalWindowType provides windowType,
        LocalFormFactor provides formFactor,
        content = content,
    )
}
