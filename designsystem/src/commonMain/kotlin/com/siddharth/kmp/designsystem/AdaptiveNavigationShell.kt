package com.siddharth.kmp.designsystem

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.collections.immutable.ImmutableList

/**
 * Adaptive top-level chrome: a bottom bar on a phone, a nav rail on a tablet or desktop, and a
 * drawer where the window is wide enough to justify one. The layout decision belongs to the window
 * size class, not to the platform, which is why this replaces per-platform branching rather than
 * moving it somewhere tidier.
 *
 * **This is chrome, not routing.** navigation-compose and navigation3 remain the router. The shell
 * never navigates; it renders [destinations] and calls [NavDestination.onSelect]. Keeping that line
 * sharp is what lets an app adopt the shell without touching its graph.
 *
 * Lives in `:designsystem` rather than `:app-shell` for a hard reason: `app-shell` applies no
 * Compose plugin and declares no wasmJs target, so a Compose UI dependency cannot go there without
 * changing what that module is. `app-shell` is headless platform services.
 *
 * @param content the destination's own screen. The shell adds no padding of its own — the scaffold
 *   already insets the content for whichever navigation affordance it chose.
 */
@Composable
fun AdaptiveNavigationShell(
    destinations: ImmutableList<NavDestination>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            destinations.forEach { destination ->
                item(
                    selected = destination.selected,
                    onClick = destination.onSelect,
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = { Text(destination.label) },
                )
            }
        },
        modifier = modifier,
        content = content,
    )
}
