package com.siddharth.kmp.designsystem

import androidx.compose.runtime.Composable

/**
 * iOS and iPadOS are both arm's-length touch surfaces, so the width axis alone separates iPhone from
 * iPad — no runtime probe needed.
 *
 * tvOS and watchOS are deliberately absent: Compose Multiplatform renders neither, so this module has
 * no such target. Apple's 10-foot and wrist UIs are SwiftUI, consuming the non-UI toolkit modules
 * (`offline-outbox` already targets `watchos*`, as does Mileway's `sharedWatch`). The [FormFactor.Tv]
 * and [FormFactor.Watch] sets still matter here — they drive Android TV and Wear OS, and a desktop
 * build can opt into either explicitly via `AdaptiveTheme(formFactor = ...)`.
 */
@Composable
actual fun rememberFormFactor(): FormFactor = FormFactor.Handheld
