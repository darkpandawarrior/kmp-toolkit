package com.siddharth.kmp.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ExperimentalMediaQueryApi
import androidx.compose.ui.UiMediaScope
import androidx.compose.ui.mediaQuery

/**
 * What the user is *pointing and typing with*, and how the panel is folded — the axes
 * [FormFactor] deliberately cannot express.
 *
 * [FormFactor] answers "how far away is the eye" by asking the platform what kind of device this is,
 * which is the right question for a token scale but the wrong one for an affordance. A phone with a
 * paired Bluetooth mouse is still `Handheld`, yet it wants Fine-pointer hit targets; a foldable in
 * Tabletop posture is still `Handheld`, yet the bottom half is a keyboard tray. Compose 1.12's
 * `mediaQuery` reports those directly, so this reads them rather than guessing from width.
 *
 * The experimental opt-in is contained *here on purpose*. Consumers read these stable enums and never
 * import `androidx.compose.ui.UiMediaScope`, so when the API changes shape — and being `@Experimental`
 * it will — exactly one file in the family needs editing rather than four apps.
 */
@Immutable
data class InputProfile(
    val pointer: Pointer,
    val keyboard: Keyboard,
    val posture: Posture,
    val distance: Distance,
) {
    /** Precision of the pointing device. `Blunt` is a TV remote or a gaze cursor, not a finger. */
    enum class Pointer { Fine, Coarse, Blunt, None }

    enum class Keyboard { Physical, Virtual, None }

    /** Foldable hinge posture. `Flat` covers every non-folding device. */
    enum class Posture { Flat, Book, Tabletop }

    /** Eye-to-panel distance. Maps to the 10-foot / arm's-length / wrist distinction. */
    enum class Distance { Near, Medium, Far }

    companion object {
        /**
         * What a plain touch phone looks like, and what every surface reports when the media-query
         * integration is switched off. Deliberately the same defaults [LocalInputProfile] carries, so
         * a preview that never called [enableMediaQuery] behaves identically to one that did.
         */
        val Touch = InputProfile(
            pointer = Pointer.Coarse,
            keyboard = Keyboard.Virtual,
            posture = Posture.Flat,
            distance = Distance.Medium,
        )
    }
}

/**
 * Turns on Compose's media-query integration. Must run *before* any content composes.
 *
 * Google's own docs show this in an Android `Application.onCreate`, which is not a place a
 * multiplatform module has. Call it from each platform's entry point instead — `MainActivity`,
 * `UIViewController`, `main()`, the wasm `onWasmReady` — or from a shared `initDesignSystem()` if the
 * consumer already has one.
 *
 * ponytail: a plain function rather than an `expect`/`actual` pair. The flag is common API and the
 * assignment is identical on every target; the only platform-specific part is *where* it gets called,
 * which is the consumer's business and not something an `actual` can encode.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun enableMediaQuery() {
    // Guarded by ExperimentalComposeUiApi, not ExperimentalMediaQueryApi — ComposeUiFlags is the
    // general flag bag, so the opt-in here is the broader one.
    ComposeUiFlags.isMediaQueryIntegrationEnabled = true
}

/**
 * Defaults to [InputProfile.Touch] rather than erroring, for the same reason [LocalAdaptiveTokens]
 * defaults to [CompactTokens]: a leaf composable rendered in a `@Preview` or a Compose UI test has no
 * app shell above it, and touch-phone affordances are the safe wrong answer.
 */
val LocalInputProfile = staticCompositionLocalOf { InputProfile.Touch }

/**
 * Reads the live [InputProfile] from Compose's media-query integration.
 *
 * Recomposes when any axis changes — plugging in a mouse, unfolding the device, swapping to the
 * on-screen keyboard.
 *
 * The flag check is load-bearing, not defensive. `mediaQuery` **throws** `IllegalStateException`
 * when `isMediaQueryIntegrationEnabled` is false — it does not fall back to defaults. Without this
 * guard, every composable under an [AdaptiveTheme] dies on any surface that never called
 * [enableMediaQuery], which is every `@Preview`, every Compose UI test, and any consumer that has
 * not yet wired its entry point. Caught by `AdaptiveWidthUiTest` on iOS and wasm.
 *
 * Prefer reading [LocalInputProfile] inside the app shell; call this directly only where you are
 * providing that local.
 */
@Composable
@OptIn(ExperimentalMediaQueryApi::class, ExperimentalComposeUiApi::class)
fun rememberInputProfile(): InputProfile {
    if (!ComposeUiFlags.isMediaQueryIntegrationEnabled) return InputProfile.Touch
    return mediaQuery {
        InputProfile(
            pointer = when (pointerPrecision) {
                UiMediaScope.PointerPrecision.Fine -> InputProfile.Pointer.Fine
                UiMediaScope.PointerPrecision.Blunt -> InputProfile.Pointer.Blunt
                UiMediaScope.PointerPrecision.None -> InputProfile.Pointer.None
                else -> InputProfile.Pointer.Coarse
            },
            keyboard = when (keyboardKind) {
                UiMediaScope.KeyboardKind.Physical -> InputProfile.Keyboard.Physical
                UiMediaScope.KeyboardKind.None -> InputProfile.Keyboard.None
                else -> InputProfile.Keyboard.Virtual
            },
            posture = when (windowPosture) {
                UiMediaScope.Posture.Book -> InputProfile.Posture.Book
                UiMediaScope.Posture.Tabletop -> InputProfile.Posture.Tabletop
                else -> InputProfile.Posture.Flat
            },
            distance = when (viewingDistance) {
                UiMediaScope.ViewingDistance.Near -> InputProfile.Distance.Near
                UiMediaScope.ViewingDistance.Far -> InputProfile.Distance.Far
                else -> InputProfile.Distance.Medium
            },
        )
    }
}

/**
 * The minimum touch target for the *current* pointing device.
 *
 * [DesignTokens.Size.minTouch] is deliberately constant at 48dp because a finger is a finger on every
 * device. That constant is still correct — this is the separate question of what happens when the
 * input is not a finger: a TV remote driving a focus ring needs a bigger target than a mouse does.
 *
 * ponytail: three cases, no token set. If a second component ever needs pointer-scaled metrics, move
 * this to a `PointerDefaults` object following the Material `FooDefaults` convention that
 * [AdaptiveTokens] already points at — do not grow [AdaptiveTokens] for it.
 */
@Composable
fun minTargetForPointer(): androidx.compose.ui.unit.Dp = when (LocalInputProfile.current.pointer) {
    InputProfile.Pointer.Fine -> DesignTokens.Size.minTouch * 0.75f
    InputProfile.Pointer.Blunt -> DesignTokens.Size.minTouch * 1.5f
    else -> DesignTokens.Size.minTouch
}
