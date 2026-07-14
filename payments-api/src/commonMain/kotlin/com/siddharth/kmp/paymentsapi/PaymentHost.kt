package com.siddharth.kmp.paymentsapi

/**
 * Platform handle a [PaymentGateway] needs to launch its SDK/UI. Deliberately opaque in commonMain —
 * it carries no platform types, so the contract stays multiplatform. On Android the concrete host
 * (see `AndroidPaymentHost` in androidMain) owns the `ComponentActivity` + ActivityResult plumbing
 * and bridges the SDK's callback back into the suspending [PaymentGateway.pay] call.
 *
 * This indirection is the heart of the architecture: it lets Compose-era, coroutine-based feature
 * code drive Activity-callback-era gateway SDKs without leaking Activity references upward.
 */
interface PaymentHost
