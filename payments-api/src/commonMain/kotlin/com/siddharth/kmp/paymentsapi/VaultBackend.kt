package com.siddharth.kmp.paymentsapi

/**
 * The app's view of the server's Stripe Customer + vault rail (roadmap #7) — mirrors
 * [PayoutBackend]'s shape: [save]/[list]/[charge] each do a server round-trip against
 * `core:protocol` DTOs. Implemented in `core:network`.
 *
 * A modern retelling of the five-gateway `card_id` vault pattern: save a card token once against a
 * customer, charge it later without re-entering. The raw token never comes back from the server —
 * only [SavedInstrument]'s masked brand/last4.
 */
interface VaultBackend {
    /**
     * `POST /vault/{customerId}/instruments`. [idempotencyKey] must be stable across retries of the
     * SAME logical save attempt, same contract as [PaymentBackend.createOrder]'s.
     */
    suspend fun save(
        customerId: String,
        cardToken: String,
        brand: String,
        last4: String,
        idempotencyKey: String,
    ): SavedInstrument

    /** `GET /vault/{customerId}/instruments` — list saved instruments for a customer. */
    suspend fun list(customerId: String): List<SavedInstrument>

    /**
     * `POST /vault/{customerId}/instruments/{instrumentId}/charge`. [idempotencyKey] must be stable
     * across retries of the SAME logical charge attempt.
     */
    suspend fun charge(
        customerId: String,
        instrumentId: String,
        catalogItemId: String,
        idempotencyKey: String,
    ): InstrumentCharge
}

/** A saved instrument as the app ever sees it — masked, never the raw card token. */
data class SavedInstrument(
    val instrumentId: String,
    val customerId: String,
    val brand: String,
    val last4: String,
)

data class InstrumentCharge(
    val chargeId: String,
    val customerId: String,
    val instrumentId: String,
    val amount: Money,
    val status: PaymentStatus,
)
