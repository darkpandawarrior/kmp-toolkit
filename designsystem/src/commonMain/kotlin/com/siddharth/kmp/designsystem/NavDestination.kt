package com.siddharth.kmp.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One top-level destination in [AdaptiveNavigationShell].
 *
 * [key] is the caller's own route identity — this module deliberately knows nothing about the
 * navigation library in use, so the shell can sit above navigation-compose or navigation3 without
 * caring which.
 */
@Immutable
data class NavDestination(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onSelect: () -> Unit,
)
