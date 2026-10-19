package com.siddharth.kmp.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * Grows the layout box to at least [DesignTokens.Size.minTouch] in both axes (DESIGN.md's touch
 * target rule). The visible content should be centred by the caller (`contentAlignment = Center`),
 * so a small pill keeps its look while its tappable area reaches 48dp.
 */
fun Modifier.minTouchTarget(): Modifier = sizeIn(minWidth = DesignTokens.Size.minTouch, minHeight = DesignTokens.Size.minTouch)

/** [clickable] with an explicit [role] and [onClickLabel], on a [minTouchTarget]-sized box. */
fun Modifier.accessibleClickable(
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = minTouchTarget().clickable(onClickLabel = onClickLabel, role = role, onClick = onClick)
