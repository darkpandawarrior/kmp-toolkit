package com.siddharth.kmp.paymentsapi

/**
 * An amount of money in the smallest indivisible unit of [currency] (paise for INR, cents for USD).
 *
 * Integer minor units avoid floating-point rounding errors — the cardinal rule of money handling.
 * Formatting to a human string (and to gateway-specific representations, e.g. UPI's two-decimal
 * `"10.00"` string) is a presentation concern handled at the edges, never in the value type.
 */
data class Money(
    val amountMinor: Long,
    val currency: String,
) {
    init {
        require(amountMinor >= 0) { "amountMinor must be non-negative, was $amountMinor" }
        require(currency.length == 3) { "currency must be an ISO-4217 code, was '$currency'" }
    }

    companion object {
        fun inr(rupees: Long): Money = Money(rupees * 100, "INR")

        fun usd(dollars: Long): Money = Money(dollars * 100, "USD")
    }
}
