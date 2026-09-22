package com.siddharth.kmp.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import kotlinx.collections.immutable.persistentListOf

// Previews for the design system's own components.
//
// WHY THE ANNOTATION IS `androidx.compose.ui.tooling.preview.Preview` IN commonMain:
// since Compose Multiplatform 1.10 the AndroidX annotation IS the multiplatform one — JetBrains
// made it multiplatform and publishes it from `org.jetbrains.compose.ui:ui-tooling-preview`.
// Verified against the artifact rather than a blog post: the 1.13.0-alpha01 sources jar contains
// `commonMain/androidx/compose/ui/tooling/preview/Preview.kt`, alongside MultiPreviews.kt and
// PreviewParameter.kt. The older `org.jetbrains.compose.ui.tooling.preview.Preview` is DEPRECATED
// and carries @Deprecated(ReplaceWith("Preview", "androidx.compose.ui.tooling.preview.Preview")).
// Do not "fix" these imports to the org.jetbrains one — that is 2024-era advice.
//
// WHY THESE COMPONENTS AND NOT ALL 74: a preview earns its place when it renders a branch a reader
// cannot check by eye — a state enum, an error path, a selected/unselected pair. The Modifier
// extensions in Adaptive.kt, the nav shells, CaptureController and PngEncoding have no such branch
// (or no renderable leaf at all) and get none. Coverage here is deliberate, not a percentage.
//
// These are `private` on purpose: they stay out of the published API surface, so Dokka does not
// document them and the ABI of a Maven-published module does not move because a preview was added.

/**
 * The shell every preview below renders inside: Material colours, the adaptive tokens the
 * components read, and a Surface so dark mode has a background to be dark against.
 *
 * ponytail: a plain composable, not CMP 1.11's `@PreviewWrapper`. Same line count at the call site,
 * works in every IDE and every CMP version, and a reader can see which theme a preview renders
 * under instead of having to go and find a wrapper class.
 *
 * [FormFactor.Handheld] is passed explicitly rather than letting `rememberFormFactor()` detect it —
 * a preview has no real window, and Adaptive.kt's own KDoc names previews as the reason that
 * parameter exists.
 */
@Composable
private fun PreviewShell(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        AdaptiveTheme(formFactor = FormFactor.Handheld) {
            Surface {
                Column(Modifier.fillMaxWidth().padding(DesignTokens.Spacing.l)) { content() }
            }
        }
    }
}

// ── Primitives ────────────────────────────────────────────────────────────────

@PreviewLightDark
@Composable
private fun PageHeaderPreview() =
    PreviewShell {
        PageHeader(
            title = "Payments",
            eyebrow = "Wallet",
            subtitle = "Three providers connected",
        )
    }

@PreviewLightDark
@Composable
private fun SectionCardPreview() =
    PreviewShell {
        SectionCard(title = "Recent activity", subtitle = "Last 30 days") {
            Text("Two payouts settled.", style = MaterialTheme.typography.bodyMedium)
        }
    }

/** The empty/loading/error triad in one render — the three states every consuming screen has. */
@PreviewLightDark
@Composable
private fun StatePlaceholdersPreview() =
    PreviewShell {
        LoadingState(label = "Fetching balance")
        EmptyState(title = "No transactions yet", body = "Anything you send or receive shows up here.")
        ErrorState(message = "Could not reach the payments service.", onRetry = {})
    }

/**
 * Selected and unselected together. A chip whose selected state is only legible in one colour
 * scheme is the classic bug this pairing catches.
 */
@PreviewLightDark
@Composable
private fun TagChipPreview() =
    PreviewShell {
        TagChipRow(
            tags = listOf("UPI", "Card", "Netbanking", "Wallet"),
            selected = setOf("UPI", "Wallet"),
            onTagClick = {},
        )
    }

// ── Stateful components ───────────────────────────────────────────────────────

/** All four [StepState] values in one render — the highest-branching component in the module. */
@PreviewLightDark
@Composable
private fun StepTimelinePreview() =
    PreviewShell {
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

@PreviewLightDark
@Composable
private fun PayloadCardPreview() =
    PreviewShell {
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
    PreviewShell {
        OtpField(value = "123456", onValueChange = {})
        OtpField(value = "12", onValueChange = {})
        OtpField(value = "123456", onValueChange = {}, isError = true)
    }

@Preview
@Composable
private fun PageIndicatorPreview() =
    PreviewShell {
        PageIndicator(currentPage = 1, pageCount = 4)
    }

@Preview
@Composable
private fun AnimatedCounterPreview() =
    PreviewShell {
        AnimatedCounter(target = 1_24_500, suffix = " INR")
    }

/** Redacted is the default state, so this is what a consumer actually ships. */
@PreviewLightDark
@Composable
private fun RedactionRevealPreview() =
    PreviewShell {
        RedactionReveal(value = "4111 1111 1111 1111")
    }

@PreviewLightDark
@Composable
private fun MarkdownTextPreview() =
    PreviewShell {
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
