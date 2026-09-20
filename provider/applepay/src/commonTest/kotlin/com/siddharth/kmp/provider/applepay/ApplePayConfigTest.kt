package com.siddharth.kmp.provider.applepay

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The placeholder contract's one load-bearing branch: an unprovisioned (or wrongly provisioned)
 * merchant id must read as not-provisioned, so the gateway reports NOT_CONFIGURED and no button is
 * drawn. If this test ever goes green on a sentinel, the app ships a working-looking Apple Pay
 * button that fails with money on the line.
 */
class ApplePayConfigTest {
    @Test
    fun defaultConfigIsNotProvisioned() {
        assertFalse(ApplePayConfig().isProvisioned)
        assertFalse(ApplePayConfig().merchantConfig.isProvisioned)
    }

    @Test
    fun aWrongButNonSentinelMerchantIdIsStillNotProvisioned() {
        // The realistic mistake: pasting the bundle id instead of the merchant id.
        assertFalse(ApplePayConfig(merchantId = "com.siddharth.kmp.sample").isProvisioned)
    }

    @Test
    fun aRealMerchantIdIsProvisioned() {
        assertTrue(ApplePayConfig(merchantId = "merchant.com.siddharth.kmp").isProvisioned)
    }
}
