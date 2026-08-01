package com.siddharth.kmp.designsystem

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Android is the one target where the surface genuinely varies at runtime — the same codebase runs
 * on a phone, a Wear OS watch and an Android TV. `uiMode`'s type bits are the platform's own answer,
 * and reading them through [LocalConfiguration] means a fold, a display switch or a move to an
 * external monitor recomposes with the right tokens.
 *
 * ponytail: `UI_MODE_TYPE_DESK` and `_CAR` fall through to Handheld. Desk mode is a docked phone at
 * arm's length (Handheld metrics are right), and nothing in this family ships to Auto — which needs
 * driver-distraction rules, not a token scale. Add them when a consumer actually targets them.
 */
@Composable
actual fun rememberFormFactor(): FormFactor =
    when (LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK) {
        Configuration.UI_MODE_TYPE_TELEVISION -> FormFactor.Tv
        Configuration.UI_MODE_TYPE_WATCH -> FormFactor.Watch
        else -> FormFactor.Handheld
    }
