package com.siddharth.kmp.appshell

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The native-sheet review gate over [ReviewTracker.shouldPrompt] / [ReviewTracker.markPrompted], using
 * an in-memory store and a fixed clock.
 */
class ReviewTrackerTest {
    private val day = 24L * 60L * 60L * 1000L
    private val config = ReviewGateConfig(minAccountAgeDays = 7, minInteractions = 5, cooldownDays = 30)

    private fun trackerAt(
        nowMillis: Long,
        initial: ReviewState,
    ) = ReviewTracker(store = InMemoryReviewStateStore(initial), config = config, now = { nowMillis })

    @Test
    fun `not eligible before 7-day account age even with enough interactions`() =
        runTest {
            val now = 6 * day
            val tracker = trackerAt(now, ReviewState(firstOpenMillis = 1, interactionCount = 5))
            assertFalse(tracker.shouldPrompt())
        }

    @Test
    fun `eligible after 7 days with enough interactions`() =
        runTest {
            val now = 8 * day
            val tracker = trackerAt(now, ReviewState(firstOpenMillis = 1, interactionCount = 5))
            assertTrue(tracker.shouldPrompt())
        }

    @Test
    fun `not eligible without enough interactions`() =
        runTest {
            val now = 30 * day
            val tracker = trackerAt(now, ReviewState(firstOpenMillis = 1, interactionCount = 4))
            assertFalse(tracker.shouldPrompt())
        }

    @Test
    fun `markPrompted stamps the last-prompt time and starts the cooldown`() =
        runTest {
            val now = 10 * day
            val store = InMemoryReviewStateStore(ReviewState(firstOpenMillis = 1, interactionCount = 5))
            val tracker = ReviewTracker(store = store, config = config, now = { now })
            assertTrue(tracker.shouldPrompt())

            tracker.markPrompted()
            assertEquals(now, store.load().lastPromptMillis)
            // Within the 30-day cooldown it is no longer eligible.
            assertFalse(tracker.shouldPrompt())
        }

    @Test
    fun `recordFirstOpenIfNeeded sets first open once`() =
        runTest {
            val store = InMemoryReviewStateStore()
            var clock = 1000L
            val tracker = ReviewTracker(store, config) { clock }
            tracker.recordFirstOpenIfNeeded()
            val first = store.load().firstOpenMillis
            clock = 5000L
            tracker.recordFirstOpenIfNeeded()
            assertEquals(first, store.load().firstOpenMillis)
        }

    @Test
    fun `recordInteraction increments the counter`() =
        runTest {
            val store = InMemoryReviewStateStore()
            val tracker = ReviewTracker(store, config) { 0L }
            tracker.recordInteraction()
            tracker.recordInteraction()
            assertEquals(2, store.load().interactionCount)
        }

    private class CountingReviewManager : AppReviewManager {
        var prompts = 0

        override suspend fun promptForReview() {
            prompts++
        }
    }

    @Test
    fun `tryPrompt does nothing when not eligible`() =
        runTest {
            val store = InMemoryReviewStateStore(ReviewState(firstOpenMillis = 100 * day, interactionCount = 0))
            val tracker = ReviewTracker(store, config) { 100 * day }
            val manager = CountingReviewManager()
            assertFalse(tracker.tryPrompt(manager))
            assertEquals(0, manager.prompts)
        }

    @Test
    fun `tryPrompt prompts and stamps last prompt when eligible`() =
        runTest {
            val now = 100 * day
            val store =
                InMemoryReviewStateStore(ReviewState(firstOpenMillis = now - 10 * day, interactionCount = 5))
            val tracker = ReviewTracker(store, config) { now }
            val manager = CountingReviewManager()
            assertTrue(tracker.tryPrompt(manager))
            assertEquals(1, manager.prompts)
            assertEquals(now, store.load().lastPromptMillis)
        }

    @Test
    fun `tryPrompt respects cooldown after a prompt`() =
        runTest {
            var now = 100 * day
            val store =
                InMemoryReviewStateStore(ReviewState(firstOpenMillis = now - 10 * day, interactionCount = 5))
            val tracker = ReviewTracker(store, config) { now }
            val manager = CountingReviewManager()
            assertTrue(tracker.tryPrompt(manager))
            now += 5 * day
            assertFalse(tracker.tryPrompt(manager))
            assertEquals(1, manager.prompts)
        }
}
