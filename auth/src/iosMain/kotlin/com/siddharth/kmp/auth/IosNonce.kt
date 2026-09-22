package com.siddharth.kmp.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

private const val NONCE_BYTES = 32

/**
 * A fresh, cryptographically random nonce/state value as lowercase hex.
 *
 * `SecRandomCopyBytes`, not `NSUUID` and not `Random`: the nonce is anti-replay, and a predictable
 * one is the same as no nonce at all. A non-zero `OSStatus` means the system CSPRNG refused, which
 * is not a condition to paper over with a weaker fallback — it throws.
 */
@OptIn(ExperimentalForeignApi::class)
fun newRawNonce(bytes: Int = NONCE_BYTES): String {
    require(bytes > 0) { "nonce length must be positive" }
    val buffer = ByteArray(bytes)
    val status =
        buffer.usePinned { pinned ->
            SecRandomCopyBytes(kSecRandomDefault, bytes.convert(), pinned.addressOf(0))
        }
    check(status == 0) { "SecRandomCopyBytes failed with OSStatus $status" }
    return buffer.toLowerHex()
}
