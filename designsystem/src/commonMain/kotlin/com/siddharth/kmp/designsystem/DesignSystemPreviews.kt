package com.siddharth.kmp.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.collections.immutable.persistentListOf

// Previews for the design system's own components. These are also the input to the Roborazzi
// screenshot tests wired in this module's build file — `recordRoborazziJvm` generates one test per
// @Preview below, so a preview added here is covered by construction and there is no separate list
// of what is under test to forget to update.
//
// WHY THE ANNOTATION IS `androidx.compose.ui.tooling.preview.Preview` IN commonMain:
// since Compose Multiplatform 1.10 the AndroidX annotation IS the multiplatform one — JetBrains
// made it multiplatform and publishes it from `org.jetbrains.compose.ui:ui-tooling-preview`.
// Verified against the artifact rather than a blog post: the 1.13.0-alpha01 sources jar contains
// `commonMain/androidx/compose/ui/tooling/preview/Preview.kt`. The older
// `org.jetbrains.compose.ui.tooling.preview.Preview` is DEPRECATED and carries
// @Deprecated(ReplaceWith("Preview", "androidx.compose.ui.tooling.preview.Preview")).
// Do not "fix" these imports to the org.jetbrains one — that is 2024-era advice.
//
// WHY THESE COMPONENTS AND NOT ALL 74: a preview earns its place when it renders a branch a reader
// cannot check by eye — a state enum, an error path, a selected/unselected pair. The Modifier
// extensions in Adaptive.kt, the nav shells, CaptureController and PngEncoding have no such branch
// (or no renderable leaf at all) and get none. Coverage here is deliberate, not a percentage.
//
// WHY THERE IS NO LoadingState PREVIEW: it is a CircularProgressIndicator, which animates forever.
// Every recording would capture a different frame of the spinner and the golden would fail on the
// next run for no reason. A screenshot suite that cries wolf gets deleted; an indeterminate spinner
// is the one thing in this module that genuinely cannot have a stable golden.
//
// These are `private` on purpose: they stay out of the published API surface, so Dokka does not
// document them and the ABI of a Maven-published module does not move because a preview was added.
// The Roborazzi config sets `includePrivatePreviews = true` so the scanner still finds them.

/**
 * Renders [content] twice side by side, light on the left and dark on the right.
 *
 * This deliberately does NOT use `@PreviewLightDark` plus `isSystemInDarkTheme()`, which is the
 * obvious way to do it and silently does not work here. That pairing produced two goldens per
 * preview whose names differed (`.Light.png`, `.Dark_NIGHT.png`) and whose pixels did not: the
 * desktop renderer does not plumb the annotation's `uiMode` night bit through to Compose's
 * dark-theme detection, so both images came out light. Eight goldens asserted nothing while
 * reading as dark-mode coverage, which is worse than having none. Caught by opening a PNG; the
 * build was green either way.
 *
 * Rendering both schemes here instead depends on nothing but Compose: one golden per component,
 * both schemes provably different, and the comparison is side by side rather than across two files.
 */
@Composable
private fun PreviewShell(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxSize()) {
        // The weight goes on a Box that is the Row's DIRECT child, not on the Surface inside
        // [Scheme]. AdaptiveTheme wraps its content in a BoxWithConstraints, so that Box — not the
        // Surface — is what Row actually lays out; a RowScope weight applied below it is silently
        // in the wrong scope and does nothing. The first half then took the full width and the
        // second was never visible. Diagnosed with a two-coloured probe preview rather than by
        // reading the layout code, because the build was green and both halves were "rendering".
        Box(Modifier.weight(1f).fillMaxHeight()) { Scheme(dark = false, content = content) }
        Box(Modifier.weight(1f).fillMaxHeight()) { Scheme(dark = true, content = content) }
    }
}

/**
 * [FormFactor.Handheld] is passed explicitly rather than letting `rememberFormFactor()` detect it —
 * a preview has no real window, and Adaptive.kt's own KDoc names previews as the reason that
 * parameter exists.
 */
@Composable
private fun Scheme(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        AdaptiveTheme(formFactor = FormFactor.Handheld) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().padding(DesignTokens.Spacing.m)) { content() }
            }
        }
    }
}

/**
 * The scrolling variant, for components that size themselves against the height they are given.
 *
 * A vertical scroll measures its content with an unbounded height, which is what a real consumer
 * screen does. It matters for [StepTimeline]: its connector rail is a `weight(1f)` Box, so in a
 * bounded-height parent the first step expands to eat the whole container and the remaining steps
 * are never laid out. The first recording of that golden showed one step of four.
 */
@Composable
private fun ScrollingPreviewShell(content: @Composable () -> Unit) {
    PreviewShell {
        Column(Modifier.verticalScroll(rememberScrollState())) { content() }
    }
}

// ── Primitives ────────────────────────────────────────────────────────────────

@Preview
@Composable
private fun PageHeaderPreview() =
    PreviewShell {
        PageHeader(
            title = "Payments",
            eyebrow = "Wallet",
            subtitle = "Three providers connected",
        )
    }

@Preview
@Composable
private fun SectionCardPreview() =
    PreviewShell {
        SectionCard(title = "Recent activity", subtitle = "Last 30 days") {
            Text("Two payouts settled.", style = MaterialTheme.typography.bodyMedium)
        }
    }

/** Full-screen placeholder, so it gets the plain bounded shell rather than the scrolling one. */
@Preview
@Composable
private fun EmptyStatePreview() =
    PreviewShell {
        EmptyState(title = "No transactions yet", body = "Anything you send or receive shows up here.")
    }

@Preview
@Composable
private fun ErrorStatePreview() =
    PreviewShell {
        ErrorState(message = "Could not reach the payments service.", onRetry = {})
    }

/**
 * Selected and unselected together. A chip whose selected state is only legible in one colour
 * scheme is the classic bug the light/dark pairing catches.
 */
@Preview
@Composable
private fun TagChipPreview() =
    ScrollingPreviewShell {
        TagChipRow(
            tags = listOf("UPI", "Card", "Netbanking", "Wallet"),
            selected = setOf("UPI", "Wallet"),
            onTagClick = {},
        )
    }

// ── Stateful components ───────────────────────────────────────────────────────

/** All four [StepState] values in one render — the highest-branching component in the module. */
@Preview
@Composable
private fun StepTimelinePreview() =
    ScrollingPreviewShell {
        StepTimeline(
            steps =
                persistentListOf(
                    TimelineStep("Authorised", "2 min ago", StepState.DONE, persistentListOf("Ref" to "AX-4417")),
                    TimelineStep("Captured", "just now", StepState.ACTIVE, persistentListOf()),
                    TimelineStep("Settled", "expected tomorrow", StepState.PENDING, persistentListOf()),
                    TimelineStep("Refunded", "declined by issuer", StepState.ERROR, persistentListOf("Code" to "51")),
                ),
        )
    }

@Preview
@Composable
private fun PayloadCardPreview() =
    ScrollingPreviewShell {
        PayloadCard(
            title = "Request payload",
            entries =
                persistentListOf(
                    "amount" to "1,24,500",
                    "currency" to "INR",
                    "method" to "upi",
                ),
        )
    }

/** Filled, partially filled and error — the states that differ only by border and colour. */
@Preview
@Composable
private fun OtpFieldPreview() =
    ScrollingPreviewShell {
        OtpField(value = "123456", onValueChange = {})
        OtpField(value = "12", onValueChange = {})
        OtpField(value = "123456", onValueChange = {}, isError = true)
    }

@Preview
@Composable
private fun PageIndicatorPreview() =
    ScrollingPreviewShell {
        PageIndicator(currentPage = 1, pageCount = 4)
    }

@Preview
@Composable
private fun AnimatedCounterPreview() =
    ScrollingPreviewShell {
        AnimatedCounter(target = 1_24_500, suffix = " INR")
    }

/**
 * `reducedMotion = true` is not decoration — it is what makes this golden possible at all.
 * RedactionReveal scrambles characters with `Random.nextInt()` on its way to the settled value, so
 * the default path renders differently on every single run and its golden failed `verify`
 * immediately after being recorded. The reduced-motion branch short-circuits to the settled string,
 * which is both the deterministic render and the one worth asserting: it is what a user with
 * animations disabled actually sees.
 */
@Preview
@Composable
private fun RedactionRevealPreview() =
    ScrollingPreviewShell {
        RedactionReveal(value = "4111 1111 1111 1111", reducedMotion = true)
    }

@Preview
@Composable
private fun MarkdownTextPreview() =
    ScrollingPreviewShell {
        MarkdownText(
            markdown =
                """
                # Settlement
                Funds land in **T+1** working days.
                - UPI settles same day
                - Cards settle next day
                """.trimIndent(),
        )
    }
