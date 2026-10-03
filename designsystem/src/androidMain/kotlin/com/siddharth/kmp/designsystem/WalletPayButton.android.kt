package com.siddharth.kmp.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.koin.core.context.GlobalContext

/** Optional official Google Pay asset, supplied by :designsystem-wallet-gms. */
interface GooglePayButtonRenderer {
    @Composable
    fun Render(
        allowedPaymentMethodsJson: String,
        onClick: () -> Unit,
        modifier: Modifier,
    )
}

@Composable
internal actual fun PlatformWalletPayButton(
    allowedPaymentMethodsJson: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val renderer = GlobalContext.getOrNull()?.getOrNull<GooglePayButtonRenderer>()
    if (renderer != null) {
        renderer.Render(allowedPaymentMethodsJson, onClick, modifier)
    } else {
        Text("Google Pay is not available in this build.", modifier = modifier)
    }
}
