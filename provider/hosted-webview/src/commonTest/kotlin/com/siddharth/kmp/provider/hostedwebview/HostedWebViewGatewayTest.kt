package com.siddharth.kmp.provider.hostedwebview

import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.GatewayStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HostedWebViewGatewayTest {
    private val config =
        HostedGatewayConfig(
            gatewayId = GatewayId("mock_hosted"),
            displayName = "Mock Hosted",
            region = "Global",
            docsPath = "docs/providers/mock-hosted.md",
            blurb = "test",
            capabilities = setOf(Capability.CARDS),
            status = GatewayStatus.MOCK_MODE,
            buildCheckoutUrl = { "https://checkout.example/pay" },
            matchReturn = { null },
        )
    private val gateway = HostedWebViewGateway(config, HostedCheckoutRelay())

    @Test
    fun `success outcome maps to PaymentResult Success with payment id`() {
        val result = gateway.mapOutcome(HostedReturnOutcome.Success(paymentId = "pay_1"))

        assertEquals("pay_1", assertIs<com.siddharth.kmp.paymentsapi.PaymentResult.Success>(result).paymentId)
    }

    @Test
    fun `failure outcome maps to PaymentResult Failure with reason as message`() {
        val result = gateway.mapOutcome(HostedReturnOutcome.Failure(reason = "card_declined"))

        val failure = assertIs<com.siddharth.kmp.paymentsapi.PaymentResult.Failure>(result)
        assertEquals(com.siddharth.kmp.paymentsapi.FailureCode.GATEWAY_DECLINED, failure.code)
    }

    @Test
    fun `cancelled outcome maps to PaymentResult Cancelled`() {
        val result = gateway.mapOutcome(HostedReturnOutcome.Cancelled)

        assertIs<com.siddharth.kmp.paymentsapi.PaymentResult.Cancelled>(result)
    }
}
