package com.siddharth.kmp.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Design tokens — the single source of spacing / shape / size values. Consume these instead of
 * hard-coded dp so the whole fleet shares one visual rhythm (4dp scale).
 */
object DesignTokens {
    object Spacing {
        val xxs = 2.dp
        val xs = 4.dp
        val s = 8.dp
        val m = 12.dp
        val l = 16.dp
        val xl = 20.dp
        val xxl = 24.dp
        val huge = 32.dp
        val screen = 16.dp
        val section = 24.dp

        /** Max readable content width for the centered dashboard columns. */
        val contentMaxWidth = 1080.dp
    }

    object Shape {
        val chip = RoundedCornerShape(10.dp)
        val card = RoundedCornerShape(16.dp)
        val cardLg = RoundedCornerShape(20.dp)
        val hero = RoundedCornerShape(24.dp)
        val badge = RoundedCornerShape(8.dp)
        val monogram = RoundedCornerShape(10.dp)
    }

    object Size {
        val monogram = 36.dp
        val monogramLg = 44.dp
        val iconInline = 16.dp
        val icon = 20.dp
        val iconLg = 24.dp
        val iconXl = 28.dp
        val minTouch = 48.dp
        val railWidth = 88.dp
        val barTrackHeight = 8.dp

        /** Minimum height for a KPI/stat card so a row of them stays visually even. */
        val statCardMin = 96.dp

        /** Square edge for a tappable action tile (Ops maintenance actions, quick-action grid). */
        val actionTile = 72.dp

        /** Avatar/thumbnail sizes used in list rows and report headers. */
        val avatar = 40.dp
        val avatarLg = 56.dp
    }

    /**
     * Elevation scale. The app is a flat, bordered, dark-first surface system (cards use a 1dp
     * outline, not a drop shadow) — so most surfaces sit at [flat]. Raised values are reserved for
     * genuinely floating layers (menus, bottom sheets, snackbars) where a shadow reads correctly.
     */
    object Elevation {
        val flat = 0.dp
        val raised = 1.dp
        val floating = 3.dp
        val overlay = 8.dp
    }

    /**
     * Motion tokens — one duration/easing scale so transitions feel like one app. Values follow the
     * Material motion spec's short/medium buckets; the dashboard favours quick, low-drama motion.
     */
    object Motion {
        const val INSTANT_MS = 90
        const val FAST_MS = 160
        const val MEDIUM_MS = 240
        const val SLOW_MS = 360
    }
}
