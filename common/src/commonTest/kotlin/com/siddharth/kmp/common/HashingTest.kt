package com.siddharth.kmp.common

import kotlin.test.Test
import kotlin.test.assertEquals

class HashingTest {
    @Test
    fun sha256OfEmptyStringMatchesKnownVector() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Hashing.sha256Hex(""),
        )
    }

    @Test
    fun sha256OfAbcMatchesKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Hashing.sha256Hex("abc"),
        )
    }

    @Test
    fun sha1OfEmptyStringMatchesKnownVector() {
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", Hashing.sha1Hex(""))
    }

    @Test
    fun sha1OfAbcMatchesKnownVector() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", Hashing.sha1Hex("abc"))
    }
}
