package com.siddharth.kmp.paymentsapi

import com.siddharth.kmp.common.UiText
import kotlinx.coroutines.delay

/** The scripted outcome a [SimulatedPayment] run should settle to. */
enum class SimulatedOutcome {
    SUCCESS,
    FAILURE,
    PENDING,
}

/**
 * Stands in for a real SDK round-trip when a gateway has no live sandbox credentials
 * ([GatewayStatus.MOCK_MODE]). A short suspend delay mimics the SDK hop; the outcome is scripted
 * (not random) so the Lab timeline is deterministic and every mock-mode gateway can be demoed
 * end-to-end with zero credentials.
 *
 * Lives in `core:payments-api` rather than `core:common` (its natural home per the plan) because it
 * builds [PaymentResult]/[RedactedPayload] values, and `core:common` cannot depend back on
 * `core:payments-api` without a module cycle — `payments-api` already depends on `common`.
 */
object SimulatedPayment {
    suspend fun run(
        gatewayId: GatewayId,
        prepared: PreparedPayment,
        outcome: SimulatedOutcome = SimulatedOutcome.SUCCESS,
        delayMs: Long = 900L,
    ): PaymentResult {
        delay(delayMs)
        val payload =
            Redactor.redact(
                "${gatewayId.value}_mock",
                prepared.params + mapOf("order_id" to prepared.orderId, "mode" to "MOCK_MODE"),
            )
        return when (outcome) {
            SimulatedOutcome.SUCCESS ->
                PaymentResult.Success(
                    paymentId = "mock_pay_${prepared.orderId}",
                    verification = mapOf("marker" to "succeeded"),
                    raw = payload,
                )
            SimulatedOutcome.FAILURE ->
                PaymentResult.Failure(
                    code = FailureCode.GATEWAY_DECLINED,
                    message = UiText.of("Simulated decline (mock mode — no live sandbox for this gateway)"),
                    raw = payload,
                )
            SimulatedOutcome.PENDING ->
                PaymentResult.Pending(
                    reason = PendingReason.AWAITING_WEBHOOK,
                    verification = mapOf("marker" to "pending"),
                    raw = payload,
                )
        }
    }
}
