package com.siddharth.kmp.designsystem

import androidx.compose.runtime.Composable

/**
 * The platform's own answer for which surface this is. Android is the only target that genuinely
 * varies at runtime (the same APK runs on a phone, a watch and a television), so it reads the
 * system UI mode; the other targets are known at compile time.
 *
 * Composable rather than a plain function because the Android actual reads the configuration, which
 * changes on a fold/unfold or a display switch and must recompose.
 *
 * Override it at the call site — `AdaptiveTheme(formFactor = FormFactor.Tv)` — for previews,
 * screenshot tests, and desktop builds that render a leanback UI.
 */
@Composable
expect fun rememberFormFactor(): FormFactor
