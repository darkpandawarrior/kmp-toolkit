package com.siddharth.kmp.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@JsFun("() => window.matchMedia('(prefers-reduced-motion: reduce)').matches")
private external fun prefersReducedMotion(): Boolean

/** `prefers-reduced-motion: reduce`, read once per composition entry (not observed live). */
@Composable
actual fun systemReducedMotion(): Boolean = remember { runCatching { prefersReducedMotion() }.getOrDefault(false) }
