package com.siddharth.kmp.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The "bucket this free text" case: intent routing without a model round trip — cheap, offline,
 * deterministic, so it replaces the three-regex-scrapes pattern the audit flagged for text a model
 * call would be overkill for.
 */
class KeywordClassifierTest {
    private enum class Intent { GREETING, COMPLAINT, BILLING }

    private val classifier =
        KeywordClassifier(
            mapOf(
                Intent.GREETING to listOf("hello", "hi", "good morning"),
                Intent.COMPLAINT to listOf("broken", "refund", "angry"),
                Intent.BILLING to listOf("invoice", "refund", "charge"),
            ),
        )

    @Test
    fun classifies_by_the_category_with_the_most_keyword_hits() {
        assertEquals(Intent.GREETING, classifier.classify("hi there, good morning!"))
    }

    @Test
    fun matches_are_case_insensitive() {
        assertEquals(Intent.GREETING, classifier.classify("HELLO there"))
    }

    @Test
    fun returns_null_when_no_keyword_matches() {
        assertNull(classifier.classify("what time does the shop close"))
    }

    @Test
    fun breaks_a_tie_by_declaration_order() {
        // "refund" alone scores COMPLAINT=1 and BILLING=1 — COMPLAINT is declared first.
        assertEquals(Intent.COMPLAINT, classifier.classify("I want a refund"))
    }

    @Test
    fun a_category_with_more_keyword_hits_wins_over_one_with_fewer() {
        assertEquals(Intent.BILLING, classifier.classify("please send the invoice, I need a refund on this charge"))
    }
}
