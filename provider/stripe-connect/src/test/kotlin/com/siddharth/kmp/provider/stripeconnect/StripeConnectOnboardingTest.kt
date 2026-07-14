package com.siddharth.kmp.provider.stripeconnect

import com.siddharth.kmp.paymentsapi.ConnectAccount
import com.siddharth.kmp.paymentsapi.ConnectAccountStatus
import com.siddharth.kmp.paymentsapi.ConnectBackend
import com.siddharth.kmp.paymentsapi.ConnectOnboarding
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.Money
import com.siddharth.kmp.paymentsapi.PayoutSnapshot
import com.siddharth.kmp.paymentsapi.PayoutStatus
import com.siddharth.kmp.provider.hostedwebview.HostedCheckoutRelay
import com.siddharth.kmp.provider.hostedwebview.HostedReturnOutcome
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure-JVM tests over [StripeConnectOnboarding]'s onboard/status/payout flow against a fake [ConnectBackend]. */
class StripeConnectOnboardingTest {
    private class FakeConnectBackend : ConnectBackend {
        var completedAccountId: String? = null

        override suspend fun onboard() =
            ConnectOnboarding(
                onboardingId = "conn_1",
                hostedOAuthUrl = "/mock/connect/conn_1",
                accountId = "acct_conn_1",
            )

        override suspend fun completeOnboarding(onboardingId: String) =
            ConnectAccount(accountId = "acct_conn_1", status = ConnectAccountStatus.CONNECTED)

        override suspend fun status(accountId: String): ConnectAccount {
            completedAccountId = accountId
            return ConnectAccount(accountId = accountId, status = ConnectAccountStatus.CONNECTED)
        }

        override suspend fun payout(
            accountId: String,
            amount: Money,
            idempotencyKey: String,
        ) = PayoutSnapshot(
            payoutId = "payout_1",
            gatewayId = GatewayId("stripe_connect"),
            recipientRef = accountId,
            amount = amount,
            status = PayoutStatus.PENDING,
        )
    }

    @Test
    fun `onboard resolves to CONNECTED once the relay reports success`() =
        runTest {
            val backend = FakeConnectBackend()
            val relay = HostedCheckoutRelay()
            val onboarding = StripeConnectOnboarding(backend, relay)

            val result = async { onboarding.onboard() }
            yield() // let the onboard() coroutine run up to its suspendCancellableCoroutine registration
            relay.reportResult(STRIPE_CONNECT_GATEWAY_ID, HostedReturnOutcome.Success(paymentId = null))

            val account = result.await()
            assertEquals(ConnectAccountStatus.CONNECTED, account.status)
            assertEquals("acct_conn_1", backend.completedAccountId)
        }

    @Test
    fun `onboard resolves to ONBOARDING_PENDING when the relay reports cancelled`() =
        runTest {
            val backend = FakeConnectBackend()
            val relay = HostedCheckoutRelay()
            val onboarding = StripeConnectOnboarding(backend, relay)

            val result = async { onboarding.onboard() }
            yield()
            relay.reportResult(STRIPE_CONNECT_GATEWAY_ID, HostedReturnOutcome.Cancelled)

            val account = result.await()
            assertEquals(ConnectAccountStatus.ONBOARDING_PENDING, account.status)
            assertEquals("acct_conn_1", account.accountId)
        }

    @Test
    fun `payout delegates straight to the backend`() =
        runTest {
            val backend = FakeConnectBackend()
            val onboarding = StripeConnectOnboarding(backend, HostedCheckoutRelay())

            val snapshot = onboarding.payout("acct_conn_1", Money(1_000L, "USD"), "idem_1")

            assertEquals(PayoutStatus.PENDING, snapshot.status)
            assertEquals("acct_conn_1", snapshot.recipientRef)
        }
}
