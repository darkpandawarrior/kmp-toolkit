package com.siddharth.kmp.designsystem

import androidx.compose.runtime.Composable

/**
 * Desktop, for the same reason the browser is: this is a posture, not a screen size. A Compose
 * Desktop window dragged narrow is still a ~360dp viewport and resolves to [WindowType.Compact],
 * which is what actually drives the layout. Desktop and Handheld share a token ladder precisely so
 * this call only has to be right about viewing distance.
 *
 * A leanback desktop build (a TV emulator harness, a kiosk) overrides at the call site with
 * `AdaptiveTheme(formFactor = FormFactor.Tv)`, exactly as the wasm target does.
 */
@Composable
actual fun rememberFormFactor(): FormFactor = FormFactor.Desktop
