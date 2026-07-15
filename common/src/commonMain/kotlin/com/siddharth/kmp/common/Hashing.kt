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
    /** SHA-1 digest of [bytes] (20 bytes). */
    fun sha1(bytes: ByteArray): ByteArray {
        var h0 = 0x67452301
        var h1 = 0xEFCDAB89L.toInt()
        var h2 = 0x98BADCFEL.toInt()
        var h3 = 0x10325476
        var h4 = 0xC3D2E1F0L.toInt()

        val bitLen = bytes.size.toLong() * 8
        val paddedLen = ((bytes.size + 9 + 63) / 64) * 64
        val msg = ByteArray(paddedLen)
        bytes.copyInto(msg)
        msg[bytes.size] = 0x80.toByte()
        for (i in 0 until 8) {
            msg[paddedLen - 1 - i] = ((bitLen ushr (8 * i)) and 0xFF).toByte()
        }

        val w = IntArray(80)
        var chunk = 0
        while (chunk < paddedLen) {
            for (i in 0 until 16) {
                val base = chunk + i * 4
                w[i] = ((msg[base].toInt() and 0xFF) shl 24) or
                    ((msg[base + 1].toInt() and 0xFF) shl 16) or
                    ((msg[base + 2].toInt() and 0xFF) shl 8) or
                    (msg[base + 3].toInt() and 0xFF)
            }
            for (i in 16 until 80) {
                val v = w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]
                w[i] = (v shl 1) or (v ushr 31)
            }

            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var e = h4

            for (i in 0 until 80) {
                val (f, k) =
                    when {
                        i < 20 -> ((b and c) or (b.inv() and d)) to 0x5A827999
                        i < 40 -> (b xor c xor d) to 0x6ED9EBA1
                        i < 60 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDCL.toInt()
                        else -> (b xor c xor d) to 0xCA62C1D6L.toInt()
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

            chunk += 64
        }

        val out = ByteArray(20)
        val parts = intArrayOf(h0, h1, h2, h3, h4)
        for (p in 0 until 5) {
            val v = parts[p]
            out[p * 4] = (v ushr 24).toByte()
            out[p * 4 + 1] = (v ushr 16).toByte()
            out[p * 4 + 2] = (v ushr 8).toByte()
            out[p * 4 + 3] = v.toByte()
        }
        return out
    }

    /** Lowercase hex of the SHA-1 digest of [text] (UTF-8). */
    fun sha1Hex(text: String): String = sha1(text.encodeToByteArray()).toHex()

    private val K =
        intArrayOf(
            0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b,
            0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
            -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3,
            0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
            -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc,
            0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039,
            -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
            0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
            -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d,
            -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
            0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8,
            -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
        )

    private fun ror(
        x: Int,
        n: Int,
    ): Int = (x ushr n) or (x shl (32 - n))

    /** SHA-256 digest of [bytes] (32 bytes). */
    fun sha256(bytes: ByteArray): ByteArray {
        var h0 = 0x6a09e667
        var h1 = -0x4498517b
        var h2 = 0x3c6ef372
        var h3 = -0x5ab00ac6
        var h4 = 0x510e527f
        var h5 = -0x64fa9774
        var h6 = 0x1f83d9ab
        var h7 = 0x5be0cd19

        val bitLen = bytes.size.toLong() * 8
        val paddedLen = ((bytes.size + 9 + 63) / 64) * 64
        val msg = ByteArray(paddedLen)
        bytes.copyInto(msg)
        msg[bytes.size] = 0x80.toByte()
        for (i in 0 until 8) {
            msg[paddedLen - 1 - i] = ((bitLen ushr (8 * i)) and 0xFF).toByte()
        }

        val w = IntArray(64)
        var chunk = 0
        while (chunk < paddedLen) {
            for (i in 0 until 16) {
                val base = chunk + i * 4
                w[i] = ((msg[base].toInt() and 0xFF) shl 24) or
                    ((msg[base + 1].toInt() and 0xFF) shl 16) or
                    ((msg[base + 2].toInt() and 0xFF) shl 8) or
                    (msg[base + 3].toInt() and 0xFF)
            }
            for (i in 16 until 64) {
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

            for (i in 0 until 64) {
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
            chunk += 64
        }

        val out = ByteArray(32)
        val parts = intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7)
        for (p in 0 until 8) {
            val v = parts[p]
            out[p * 4] = (v ushr 24).toByte()
            out[p * 4 + 1] = (v ushr 16).toByte()
            out[p * 4 + 2] = (v ushr 8).toByte()
            out[p * 4 + 3] = v.toByte()
        }
        return out
    }

    /** Lowercase hex of the SHA-256 digest of [text] (UTF-8). */
    fun sha256Hex(text: String): String = sha256(text.encodeToByteArray()).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { b -> (b.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
