package com.siddharth.kmp.designsystem

import androidx.compose.foundation.layout.ExperimentalFlexBoxApi
import androidx.compose.foundation.layout.FlexBox
import androidx.compose.foundation.layout.FlexBoxConfig
import androidx.compose.foundation.layout.FlexWrap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * A row of [TagChip]s that wraps onto as many lines as it needs.
 *
 * Backed by Compose 1.12's `FlexBox` rather than `FlowRow`, for the gap and wrap control `FlowRow`
 * cannot express in one config object.
 *
 * The experimental opt-in is contained here, exactly as [InputProfile] contains `mediaQuery`'s.
 * Consumers call this and never import `androidx.compose.foundation.layout.FlexBox`.
 *
 * Worth knowing: the 1.12.0 release notes say FlexBox was promoted to stable, but that landed in
 * AndroidX 1.12.0 *final*. Compose Multiplatform 1.12.0-rc01 — what this family pins — is built on
 * AndroidX 1.12.0-rc01, where it is still `@ExperimentalFlexBoxApi`. Drop the opt-in once CMP ships
 * on the final artifact; nothing else here changes.
 */
@Composable
@OptIn(ExperimentalFlexBoxApi::class)
fun TagChipRow(
    tags: List<String>,
    modifier: Modifier = Modifier,
    gap: Dp = DesignTokens.Spacing.s,
    selected: Set<String> = emptySet(),
    onTagClick: ((String) -> Unit)? = null,
) {
    val config = remember(gap) {
        FlexBoxConfig {
            wrap(FlexWrap.Wrap)
            gap(gap)
        }
    }
    FlexBox(modifier = modifier, config = config) {
        tags.forEach { tag ->
            TagChip(
                text = tag,
                selected = tag in selected,
                onClick = onTagClick?.let { click -> { click(tag) } },
            )
        }
    }
}

// Styles API spike deliberately NOT landed here. Two reasons, in order:
//
// 1. Google's docs state Material support for Styles is still to come, and every surface in this
//    family sits on org.jetbrains.compose.material3 — so adopting it now means styling *around* the
//    component library rather than through it.
// 2. On CMP 1.12.0-rc01 the DSL does not resolve the way the API listing implies: StyleScope does
//    not expose isPressed/isEnabled directly (they live behind StyleStateScope.state(), keyed), and
//    MutableStyleState's setters do not bind from a rememberUpdatedStyleState receiver lambda.
//    Worth a second attempt against real sources, not bytecode archaeology.
//
// TagChip stays the house default until M3 accepts a Style.
