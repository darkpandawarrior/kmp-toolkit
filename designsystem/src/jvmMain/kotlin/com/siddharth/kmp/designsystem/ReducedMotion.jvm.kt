package com.siddharth.kmp.designsystem

import androidx.compose.runtime.Composable

/** Desktop has no portable reduce-motion setting; wire a per-OS probe when a consumer asks. */
@Composable
actual fun systemReducedMotion(): Boolean = false
