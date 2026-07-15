package com.siddharth.kmp.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Brand-agnostic reusable primitives — zero app/contract coupling. Extracted from HireSignal's
 * `core/designsystem/Primitives.kt` (backlog #5); HireSignal's `ScoreBadge`/`StatusChip`, which DO
 * depend on its career-data contract package, stayed behind.
 */

/** Page header with eyebrow, title and optional subtitle + trailing actions. */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    actions: @Composable (() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth().padding(bottom = DesignTokens.Spacing.l), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Eyebrow(eyebrow)
                Spacer(Modifier.height(DesignTokens.Spacing.xs))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) {
                Spacer(Modifier.height(DesignTokens.Spacing.xs))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (actions != null) Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.s)) { actions() }
    }
}

/**
 * Bordered surface card — the fundamental container: optional leading icon in a tinted rounded
 * container, title + subtitle, and a trailing action slot, with the body rendered below in a
 * Column.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    contentPadding: androidx.compose.ui.unit.Dp = DesignTokens.Spacing.l,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .clip(DesignTokens.Shape.card)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, DesignTokens.Shape.card)
                .padding(contentPadding),
    ) {
        if (title != null || subtitle != null || leadingIcon != null || trailingAction != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = DesignTokens.Spacing.m),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.m)) {
                    if (leadingIcon != null) {
                        Box(
                            Modifier.clip(DesignTokens.Shape.chip).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(9.dp),
                        ) {
                            Icon(
                                leadingIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(DesignTokens.Size.icon),
                            )
                        }
                    }
                    Column {
                        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
                if (trailingAction != null) Box(Modifier.padding(start = DesignTokens.Spacing.s)) { trailingAction() }
            }
        }
        content()
    }
}

/** Section label — the "// SECTION" mono caption idiom above grouped content. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier.padding(vertical = DesignTokens.Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.s),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(DesignTokens.Size.iconInline),
            )
        }
        Text(text.uppercase(), style = EyebrowStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Small pill / tag chip. */
@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    var m = modifier.clip(DesignTokens.Shape.chip).background(bg)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m.padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(DesignTokens.Spacing.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
        if (label != null) {
            Spacer(Modifier.height(DesignTokens.Spacing.m))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ponytail: PageHeader/SectionLabel's mono "eyebrow" caption was a same-package dependency on
// HireSignal's own Eyebrow()/EyebrowStyle (core/designsystem/Theme.kt + Components.kt), which stay
// app-side (Components.kt also holds contract-coupled ScoreBadge/StatusChip). Duplicated here as a
// private implementation detail instead of exporting a 6th public symbol nobody asked to extract.
private val EyebrowStyle: TextStyle =
    TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
    )

@Composable
private fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text("// $text", style = EyebrowStyle, color = color, modifier = modifier)
}
