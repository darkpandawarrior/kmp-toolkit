package com.siddharth.kmp.common

// Ported from HireSignal core/engine (Backlog #18), collapsing its two separate hash files into one
// :common seam:
//  - SHA-1:   core/engine/.../fingerprint/Sha1.kt   (was `internal fun sha1`)
//  - SHA-256: core/engine/.../interop/Sha256.kt      (was `internal fun sha256` + `sha256Hex`)
// Both were pure-Kotlin already (no java.*, no javax.crypto) — promoted to public API under one
// object so any consumer gets both algorithms from a single import. Algorithms unchanged.

/**
 * Pure-Kotlin SHA-1 / SHA-256 (no java.*, wasmJs-safe). Content-fingerprint / cache-key hashing —
 * **not a security primitive**: no HMAC, no constant-time comparison, no salting.
 */
object Hashing {
    // ── Structure shared by both algorithms (FIPS 180-4 §5). These have names in the standard,
    // so they get names here.
    private const val BLOCK_BYTES = 64
    private const val WORD_BYTES = 4
    private const val WORDS_PER_BLOCK = 16
    private const val BITS_PER_BYTE = 8
    private const val BYTE_MASK = 0xFF

    /** The single `1` bit that starts the padding, as a byte (FIPS 180-4 §5.1). */
    private const val PADDING_MARKER = 0x80

    /** The 64-bit big-endian message-length field that ends the padding. */
    private const val LENGTH_FIELD_BYTES = 8

    /** Padding is at least the marker byte plus the length field; the block is then rounded up. */
    private const val MIN_PADDING_BYTES = 1 + LENGTH_FIELD_BYTES

    // ── SHA-1 (FIPS 180-4 §6.1).
    private const val SHA1_ROUNDS = 80
    private const val SHA1_DIGEST_BYTES = 20
    private const val SHA1_WORDS = 5

    /** Round-constant boundaries: K changes every twenty rounds. */
    private const val SHA1_PHASE_1_END = 20
    private const val SHA1_PHASE_2_END = 40
    private const val SHA1_PHASE_3_END = 60

    // ── SHA-256 (FIPS 180-4 §6.2).
    private const val SHA256_ROUNDS = 64
    private const val SHA256_DIGEST_BYTES = 32
    private const val SHA256_WORDS = 8

    /**
     * SHA-1 digest of [bytes] (20 bytes).
     *
     * MagicNumber is suppressed for the body, and only for the body. What remains after the named
     * constants above are the rotation and shift distances of the SHA-1 compression function —
     * `a shl 5`, `b shl 30`, `v ushr 31`, the `ushr 24/16/8` big-endian byte splits. Those numbers
     * ARE the specification (FIPS 180-4 §6.1.2): naming one `ROTATE_LEFT_5` restates the digit
     * without adding a fact, and it makes the code harder to diff against the published algorithm,
     * which is the only review that matters for a hash. The constants that carry information —
     * block size, round count, digest length, the four round constants — are named above and used
     * below.
     */
    @Suppress("MagicNumber")
    fun sha1(bytes: ByteArray): ByteArray {
        var h0 = 0x67452301
        var h1 = 0xEFCDAB89L.toInt()
        var h2 = 0x98BADCFEL.toInt()
        var h3 = 0x10325476
        var h4 = 0xC3D2E1F0L.toInt()

        val bitLen = bytes.size.toLong() * BITS_PER_BYTE
        val paddedLen = ((bytes.size + MIN_PADDING_BYTES + BLOCK_BYTES - 1) / BLOCK_BYTES) * BLOCK_BYTES
        val msg = ByteArray(paddedLen)
        bytes.copyInto(msg)
        msg[bytes.size] = PADDING_MARKER.toByte()
        for (i in 0 until LENGTH_FIELD_BYTES) {
            msg[paddedLen - 1 - i] = ((bitLen ushr (BITS_PER_BYTE * i)) and BYTE_MASK.toLong()).toByte()
        }

        val w = IntArray(SHA1_ROUNDS)
        var chunk = 0
        while (chunk < paddedLen) {
            for (i in 0 until WORDS_PER_BLOCK) {
                val base = chunk + i * WORD_BYTES
                w[i] = ((msg[base].toInt() and 0xFF) shl 24) or
                    ((msg[base + 1].toInt() and 0xFF) shl 16) or
                    ((msg[base + 2].toInt() and 0xFF) shl 8) or
                    (msg[base + 3].toInt() and 0xFF)
            }
            for (i in WORDS_PER_BLOCK until SHA1_ROUNDS) {
                val v = w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]
                w[i] = (v shl 1) or (v ushr 31)
            }

            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var e = h4

            for (i in 0 until SHA1_ROUNDS) {
                val (f, k) =
                    when {
                        i < SHA1_PHASE_1_END -> ((b and c) or (b.inv() and d)) to SHA1_K1
                        i < SHA1_PHASE_2_END -> (b xor c xor d) to SHA1_K2
                        i < SHA1_PHASE_3_END -> ((b and c) or (b and d) or (c and d)) to SHA1_K3
                        else -> (b xor c xor d) to SHA1_K4
                    }
                val temp = ((a shl 5) or (a ushr 27)) + f + e + k + w[i]
                e = d
                d = c
                c = (b shl 30) or (b ushr 2)
                b = a
                a = temp
            }

            h0 += a
            h1 += b
            h2 += c
            h3 += d
            h4 += e

            chunk += BLOCK_BYTES
        }

        val out = ByteArray(SHA1_DIGEST_BYTES)
        val parts = intArrayOf(h0, h1, h2, h3, h4)
        for (p in 0 until SHA1_WORDS) {
            val v = parts[p]
            out[p * WORD_BYTES] = (v ushr 24).toByte()
            out[p * WORD_BYTES + 1] = (v ushr 16).toByte()
            out[p * WORD_BYTES + 2] = (v ushr 8).toByte()
            out[p * WORD_BYTES + 3] = v.toByte()
        }
        return out
    }

    /** Lowercase hex of the SHA-1 digest of [text] (UTF-8). */
    fun sha1Hex(text: String): String = sha1(text.encodeToByteArray()).toHex()

    // The four SHA-1 round constants (FIPS 180-4 §4.2.1), one per twenty rounds.
    private const val SHA1_K1 = 0x5A827999
    private const val SHA1_K2 = 0x6ED9EBA1
    private const val SHA1_K3 = -0x70e44324 // 0x8F1BBCDC
    private const val SHA1_K4 = -0x359d3e2a // 0xCA62C1D6

    /** The 64 SHA-256 round constants (FIPS 180-4 §4.2.2). */
    private val K =
        intArrayOf(
            0x428a2f98,
            0x71374491,
            -0x4a3f0431,
            -0x164a245b,
            0x3956c25b,
            0x59f111f1,
            -0x6dc07d5c,
            -0x54e3a12b,
            -0x27f85568,
            0x12835b01,
            0x243185be,
            0x550c7dc3,
            0x72be5d74,
            -0x7f214e02,
            -0x6423f959,
            -0x3e640e8c,
            -0x1b64963f,
            -0x1041b87a,
            0x0fc19dc6,
            0x240ca1cc,
            0x2de92c6f,
            0x4a7484aa,
            0x5cb0a9dc,
            0x76f988da,
            -0x67c1aeae,
            -0x57ce3993,
            -0x4ffcd838,
            -0x40a68039,
            -0x391ff40d,
            -0x2a586eb9,
            0x06ca6351,
            0x14292967,
            0x27b70a85,
            0x2e1b2138,
            0x4d2c6dfc,
            0x53380d13,
            0x650a7354,
            0x766a0abb,
            -0x7e3d36d2,
            -0x6d8dd37b,
            -0x5d40175f,
            -0x57e599b5,
            -0x3db47490,
            -0x3893ae5d,
            -0x2e6d17e7,
            -0x2966f9dc,
            -0xbf1ca7b,
            0x106aa070,
            0x19a4c116,
            0x1e376c08,
            0x2748774c,
            0x34b0bcb5,
            0x391c0cb3,
            0x4ed8aa4a,
            0x5b9cca4f,
            0x682e6ff3,
            0x748f82ee,
            0x78a5636f,
            -0x7b3787ec,
            -0x7338fdf8,
            -0x6f410006,
            -0x5baf9315,
            -0x41065c09,
            -0x398e870e,
        )

    private const val INT_BITS = 32

    private fun ror(
        x: Int,
        n: Int,
    ): Int = (x ushr n) or (x shl (INT_BITS - n))

    /** SHA-256 digest of [bytes] (32 bytes). See [sha1] for why MagicNumber is suppressed here. */
    @Suppress("MagicNumber")
    fun sha256(bytes: ByteArray): ByteArray {
        var h0 = 0x6a09e667
        var h1 = -0x4498517b
        var h2 = 0x3c6ef372
        var h3 = -0x5ab00ac6
        var h4 = 0x510e527f
        var h5 = -0x64fa9774
        var h6 = 0x1f83d9ab
        var h7 = 0x5be0cd19

        val bitLen = bytes.size.toLong() * BITS_PER_BYTE
        val paddedLen = ((bytes.size + MIN_PADDING_BYTES + BLOCK_BYTES - 1) / BLOCK_BYTES) * BLOCK_BYTES
        val msg = ByteArray(paddedLen)
        bytes.copyInto(msg)
        msg[bytes.size] = PADDING_MARKER.toByte()
        for (i in 0 until LENGTH_FIELD_BYTES) {
            msg[paddedLen - 1 - i] = ((bitLen ushr (BITS_PER_BYTE * i)) and BYTE_MASK.toLong()).toByte()
        }

        val w = IntArray(SHA256_ROUNDS)
        var chunk = 0
        while (chunk < paddedLen) {
            for (i in 0 until WORDS_PER_BLOCK) {
                val base = chunk + i * WORD_BYTES
                w[i] = ((msg[base].toInt() and 0xFF) shl 24) or
                    ((msg[base + 1].toInt() and 0xFF) shl 16) or
                    ((msg[base + 2].toInt() and 0xFF) shl 8) or
                    (msg[base + 3].toInt() and 0xFF)
            }
            for (i in WORDS_PER_BLOCK until SHA256_ROUNDS) {
                val s0 = ror(w[i - 15], 7) xor ror(w[i - 15], 18) xor (w[i - 15] ushr 3)
                val s1 = ror(w[i - 2], 17) xor ror(w[i - 2], 19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }

            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var e = h4
            var f = h5
            var g = h6
            var h = h7

            for (i in 0 until SHA256_ROUNDS) {
                val s1 = ror(e, 6) xor ror(e, 11) xor ror(e, 25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = h + s1 + ch + K[i] + w[i]
                val s0 = ror(a, 2) xor ror(a, 13) xor ror(a, 22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                h = g
                g = f
                f = e
                e = d + t1
                d = c
                c = b
                b = a
                a = t1 + t2
            }

            h0 += a
            h1 += b
            h2 += c
            h3 += d
            h4 += e
            h5 += f
            h6 += g
            h7 += h
            chunk += BLOCK_BYTES
        }

        val out = ByteArray(SHA256_DIGEST_BYTES)
        val parts = intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7)
        for (p in 0 until SHA256_WORDS) {
            val v = parts[p]
            out[p * WORD_BYTES] = (v ushr 24).toByte()
            out[p * WORD_BYTES + 1] = (v ushr 16).toByte()
            out[p * WORD_BYTES + 2] = (v ushr 8).toByte()
            out[p * WORD_BYTES + 3] = v.toByte()
        }
        return out
    }

    /** Lowercase hex of the SHA-256 digest of [text] (UTF-8). */
    fun sha256Hex(text: String): String = sha256(text.encodeToByteArray()).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { b -> (b.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0') }

    private const val HEX_RADIX = 16
}
