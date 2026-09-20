package com.siddharth.kmp.provider.googlepay

import com.google.android.gms.wallet.WalletConstants

/**
 * [GooglePayEnvironment] → the Play Services int, at the Android edge and nowhere else.
 *
 * The whole reason the enum exists: `WalletConstants.ENVIRONMENT_TEST` is a Play Services constant,
 * and holding one in [GooglePayConfig] is what used to keep the config out of commonMain.
 */
internal fun GooglePayEnvironment.toWalletConstant(): Int =
    when (this) {
        GooglePayEnvironment.TEST -> WalletConstants.ENVIRONMENT_TEST
        GooglePayEnvironment.PRODUCTION -> WalletConstants.ENVIRONMENT_PRODUCTION
    }
