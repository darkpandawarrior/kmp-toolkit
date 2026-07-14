package com.siddharth.kmp.paymentsapi

/**
 * A server-created order the client is about to pay. The client never sets the amount — it sends a
 * catalog item id and the backend resolves the price (the trust boundary this whole app exists to
 * demonstrate). This is what the backend returns from `POST /orders`.
 */
data class OrderRef(
    val orderId: String,
    val catalogItemId: String,
    val amount: Money,
)

/**
 * Opaque, provider-specific data produced by [PaymentGateway.prepare] and handed to
 * [PaymentGateway.pay]. Each provider stuffs its own session material into [params]:
 * Razorpay → `order_id` + `key_id`; Cashfree → `payment_session_id`; Stripe → `client_secret`;
 * UPI intent → the constructed `upi://` reference fields.
 */
data class PreparedPayment(
    val gatewayId: GatewayId,
    val orderId: String,
    val amount: Money,
    val params: Map<String, String>,
)
