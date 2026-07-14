package com.siddharth.kmp.provider.googlepay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * No device/emulator was available to exercise [GooglePayGateway] against a real Google Pay sheet —
 * this test covers the one part of the integration that's pure logic and genuinely verifiable on the
 * JVM: the request JSON this app sends actually matches Google's documented shape. Robolectric only
 * because `org.json.JSONObject` is a stub on plain JVM unit tests otherwise.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GooglePayRequestBuilderTest {
    private val config =
        GooglePayConfig(
            gateway = "example",
            gatewayMerchantId = "exampleGatewayMerchantId",
            merchantName = "kmp-toolkit Demo",
            countryCode = "IN",
            currencyCode = "INR",
        )
    private val builder = GooglePayRequestBuilder(config)

    @Test
    fun `isReadyToPayRequest declares CARD as the only payment method`() {
        val json = builder.isReadyToPayRequest()

        assertEquals(2, json.getInt("apiVersion"))
        val method = json.getJSONArray("allowedPaymentMethods").getJSONObject(0)
        assertEquals("CARD", method.getString("type"))
    }

    @Test
    fun `paymentDataRequest carries the tokenization gateway and merchant id`() {
        val json = builder.paymentDataRequest(amountMinor = 14_900L)

        val method = json.getJSONArray("allowedPaymentMethods").getJSONObject(0)
        val tokenization = method.getJSONObject("tokenizationSpecification")
        assertEquals("PAYMENT_GATEWAY", tokenization.getString("type"))
        assertEquals("example", tokenization.getJSONObject("parameters").getString("gateway"))
        assertEquals(
            "exampleGatewayMerchantId",
            tokenization.getJSONObject("parameters").getString("gatewayMerchantId"),
        )
    }

    @Test
    fun `paymentDataRequest converts minor units to a major-unit decimal string`() {
        val json = builder.paymentDataRequest(amountMinor = 14_900L)

        val transactionInfo = json.getJSONObject("transactionInfo")
        assertEquals("149.00", transactionInfo.getString("totalPrice"))
        assertEquals("INR", transactionInfo.getString("currencyCode"))
        assertEquals("FINAL", transactionInfo.getString("totalPriceStatus"))
    }

    @Test
    fun `merchant name flows through to merchantInfo`() {
        val json = builder.paymentDataRequest(amountMinor = 100L)

        assertEquals("kmp-toolkit Demo", json.getJSONObject("merchantInfo").getString("merchantName"))
    }

    @Test
    fun `allowed card networks and auth methods are configurable`() {
        val customConfig =
            config.copy(allowedCardNetworks = listOf("VISA"), allowedAuthMethods = listOf("PAN_ONLY"))
        val json = GooglePayRequestBuilder(customConfig).isReadyToPayRequest()

        val parameters =
            json.getJSONArray("allowedPaymentMethods").getJSONObject(0).getJSONObject("parameters")
        assertEquals(1, parameters.getJSONArray("allowedCardNetworks").length())
        assertEquals("VISA", parameters.getJSONArray("allowedCardNetworks").getString(0))
        assertTrue(parameters.getJSONArray("allowedAuthMethods").getString(0) == "PAN_ONLY")
    }
}
