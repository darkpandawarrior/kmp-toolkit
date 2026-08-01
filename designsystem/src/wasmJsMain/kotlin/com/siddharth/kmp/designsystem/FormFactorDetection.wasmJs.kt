package com.siddharth.kmp.designsystem

import androidx.compose.runtime.Composable

/**
 * The browser is treated as Desktop. That is the posture, not the screen size — a phone browser is
 * still a ~360dp viewport and resolves to [WindowType.Compact], which is what actually drives the
 * layout. Desktop and Handheld share a token ladder precisely so this call doesn't have to be right
 * about the device, only about the viewing distance.
 */
@Composable
actual fun rememberFormFactor(): FormFactor = FormFactor.Desktop
