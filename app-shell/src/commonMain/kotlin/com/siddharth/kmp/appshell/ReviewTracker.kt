package com.siddharth.kmp.appshell

import kotlin.time.Clock

/** Persistence for the review counters. Default in-memory; the app can swap a durable-store impl. */
interface ReviewStateStore {
    suspend fun load(): ReviewState

    suspend fun save(state: ReviewState)
}

/** Process-lifetime in-memory store (default / test double; resets on cold start). */
class InMemoryReviewStateStore(initial: ReviewState = ReviewState()) : ReviewStateStore {
    private var current = initial

    override suspend fun load(): ReviewState = current

    override suspend fun save(state: ReviewState) {
        current = state
    }
}

/**
 * Drives the in-app review prompt from engagement signals. Records first-open + interaction counts,
 * and prompts (via the platform [AppReviewManager]) only when [ReviewEligibility] is satisfied, then
 * stamps the prompt time to enforce the cooldown.
 */
class ReviewTracker(
    private val store: ReviewStateStore = InMemoryReviewStateStore(),
    private val config: ReviewGateConfig = ReviewGateConfig(),
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    suspend fun recordFirstOpenIfNeeded() {
        val state = store.load()
        if (state.firstOpenMillis <= 0L) store.save(state.copy(firstOpenMillis = now()))
    }

    suspend fun recordInteraction() {
        val state = store.load()
        store.save(state.copy(interactionCount = state.interactionCount + 1))
    }

    /** Prompts for review if eligible; returns true iff a prompt was launched. */
    suspend fun tryPrompt(manager: AppReviewManager): Boolean {
        val state = store.load()
        if (!ReviewEligibility.isEligible(state, now(), config)) return false
        manager.promptForReview()
        store.save(state.copy(lastPromptMillis = now()))
        return true
    }

    /**
     * Pure eligibility read for a caller-drawn native review sheet (no store SDK) — the caller shows
     * its own UI when this is true, then calls [markPrompted]. Separate from [tryPrompt] (which drives
     * the platform [AppReviewManager]) so an offline/demo build can present a native sheet instead.
     */
    suspend fun shouldPrompt(): Boolean = ReviewEligibility.isEligible(store.load(), now(), config)

    /** Stamps the last-prompt time so the cooldown starts, after a native prompt was shown. */
    suspend fun markPrompted() {
        store.save(store.load().copy(lastPromptMillis = now()))
    }
}
