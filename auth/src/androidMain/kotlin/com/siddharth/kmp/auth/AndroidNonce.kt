package com.siddharth.kmp.auth

import java.security.SecureRandom

private const val NONCE_BYTES = 32

/**
 * A fresh, cryptographically random nonce/state value as lowercase hex.
 *
 * `SecureRandom`, not `Random`: both of the things this produces — the OIDC nonce and the OAuth
 * `state` — are anti-replay and anti-CSRF tokens, and a predictable one is the same as no token at
 * all. Deliberately *not* wired to the toolkit's `Rng`, which is a seeded SplitMix64 built for
 * replayable game state and is the exact wrong tool here.
 */
fun newRawNonce(bytes: Int = NONCE_BYTES): String {
    val buffer = ByteArray(bytes)
    SecureRandom().nextBytes(buffer)
    return buffer.toLowerHex()
}
