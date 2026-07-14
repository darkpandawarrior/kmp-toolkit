package com.siddharth.kmp.paymentsapi

/**
 * The app's view of the server's Stripe Connect payout onboarding rail (roadmap #11) — mirrors
 * [PayoutBackend]'s shape: [onboard]/[completeOnboarding] do the server round-trips, [status] polls
 * server-authoritative state. Implemented in `core:network` against `core:protocol` DTOs.
 *
 * Real Connect onboarding is a KYC/OAuth-gated flow (see [GatewayStatus.MOCK_MODE]'s own doc
 * comment) — this app models only the honest onboard → pending → CONNECTED (via mock hosted-OAuth
 * callback) lifecycle, never a fake instant CONNECTED.
 */
interface ConnectBackend {
    /** `POST /connect/onboard`. Returns the mock hosted OAuth URL to render + the onboarding id. */
    suspend fun onboard(): ConnectOnboarding

    /** The mock OAuth callback the hosted WebView return-URL triggers once onboarding is authorized. */
    suspend fun completeOnboarding(onboardingId: String): ConnectAccount

    /** `GET /connect/{accountId}` — poll the server-authoritative state. */
    suspend fun status(accountId: String): ConnectAccount

    /** `POST /connect/{accountId}/payouts` — pay out to a connected account via the existing payout rail. */
    suspend fun payout(
        accountId: String,
        amount: Money,
        idempotencyKey: String,
    ): PayoutSnapshot
}

enum class ConnectAccountStatus {
    ONBOARDING_PENDING,
    CONNECTED,
}

data class ConnectOnboarding(
    val onboardingId: String,
    val hostedOAuthUrl: String,
    val accountId: String,
)

data class ConnectAccount(
    val accountId: String,
    val status: ConnectAccountStatus,
)
