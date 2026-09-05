package com.siddharth.kmp.ai

/**
 * Buckets free text into one of a fixed set of categories by keyword hits — no model call, no
 * network, no on-device inference. The "bucket this free text" case doesn't always need a full
 * [StructuredOutput] round trip; when the categories are known in advance (intent routing, ticket
 * triage), this is a shared, tested classifier instead of every caller hand-rolling its own
 * `.contains()` chain that breaks on a synonym.
 *
 * Matching is case-insensitive substring containment. Each category's score is how many of its
 * keywords appear anywhere in the text; the highest-scoring category wins, ties broken by
 * [categories]' declaration order (the same tiebreak a `when` chain would give). Returns null when
 * no category's keywords appear at all.
 */
class KeywordClassifier<T>(private val categories: Map<T, List<String>>) {
    fun classify(text: String): T? {
        val lowerText = text.lowercase()
        return categories
            .mapValues { (_, keywords) -> keywords.count { lowerText.contains(it.lowercase()) } }
            .filterValues { it > 0 }
            .maxByOrNull { it.value }
            ?.key
    }
}
