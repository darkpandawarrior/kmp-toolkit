package com.siddharth.kmp.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.pay.button.ButtonTheme
import com.google.pay.button.ButtonType
import com.google.pay.button.PayButton

/**
 * Google's `PayButton` from `com.google.pay.button:compose-pay-button` — the officially supplied
 * asset, which also renders the accepted card networks inside the mark from the same JSON the
 * `isReadyToPay` probe uses.
 */
@Composable
internal actual fun PlatformWalletPayButton(
    allowedPaymentMethodsJson: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    PayButton(
        onClick = onClick,
        allowedPaymentMethods = allowedPaymentMethodsJson,
        modifier = modifier.fillMaxWidth(),
        type = ButtonType.Pay,
        // Google's guidance is contrast, not brand-matching: the dark button goes on light
        // surfaces. Inverted from what "dark theme -> dark button" would suggest.
        theme = if (isSystemInDarkTheme()) ButtonTheme.Light else ButtonTheme.Dark,
        radius = 8.dp,
    )
}
