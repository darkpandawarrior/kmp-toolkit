package com.siddharth.kmp.botspolicy

import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Toy 2-player Nim: subtract 1..3 from [pile]; whoever takes the last counter wins. No hidden info. */
private data class NimState(
    val pile: Int,
    val turn: Boolean,
)

private object NimRules : GameRules<NimState, Int, Boolean, NimState> {
    override fun whoActsNext(state: NimState): Boolean? = if (state.pile <= 0) null else state.turn

    override fun legalMoves(
        state: NimState,
        actor: Boolean,
    ): List<Int> = (1..3).filter { it <= state.pile }

    override fun apply(
        state: NimState,
        move: Int,
    ): Outcome<NimState> =
        if (move < 1 || move > 3 || move > state.pile) {
            Outcome.Rejected("illegal move")
        } else {
            Outcome.Accepted(NimState(state.pile - move, !state.turn))
        }

    override fun isTerminal(state: NimState): Boolean = state.pile <= 0

    // Terminal state's `turn` already flipped to whoever moves next (nobody) — the winner is
    // whoever just moved, i.e. the other side.
    override fun winner(state: NimState): Boolean? = if (state.pile <= 0) !state.turn else null

    override fun redact(
        state: NimState,
        viewer: Boolean,
    ): NimState = state
}

private val firstLegalPolicy = Policy<NimState, Int> { _, legal -> legal.first() }

class BotsPolicySmokeTest {
    @Test
    fun ismcts_search_populatesRootChildren() =
        runTest {
            val ismcts =
                Ismcts(
                    rules = NimRules,
                    rolloutPolicy = { firstLegalPolicy },
                    staticEval = { _, _ -> 0.5 },
                    budget = SearchBudget(maxMillis = 200L, maxIterations = 200, rolloutHorizon = 10),
                )
            val start = NimState(pile = 10, turn = true)

            val root =
                ismcts.search(
                    determinize = { start },
                    legal = NimRules.legalMoves(start, true),
                    viewer = true,
                    rolloutHorizon = 10,
                    elapsedMillis = { 0L },
                )

            assertTrue(root.children.size == 3)
            assertTrue(root.children.values.any { it.visits > 0 })
        }

    /** A player who moves on cancels the search immediately instead of it burning the full budget. */
    @Test
    fun search_cancelledMidLoop_stopsBeforeExhaustingTheBudget() =
        runTest {
            var determinizeCalls = 0
            val ismcts =
                Ismcts(
                    rules = NimRules,
                    rolloutPolicy = { firstLegalPolicy },
                    staticEval = { _, _ -> 0.5 },
                    budget = SearchBudget(maxMillis = 10_000L, maxIterations = 1_000, rolloutHorizon = 10),
                )
            val start = NimState(pile = 10, turn = true)

            val job =
                launch {
                    ismcts.search(
                        determinize = {
                            determinizeCalls++
                            if (determinizeCalls == 3) cancel()
                            start
                        },
                        legal = NimRules.legalMoves(start, true),
                        viewer = true,
                        rolloutHorizon = 10,
                        elapsedMillis = { 0L },
                    )
                }
            job.join()

            assertTrue(job.isCancelled)
            assertTrue(
                determinizeCalls < 1_000,
                "ensureActive() must stop the loop at the next iteration, not exhaust maxIterations",
            )
        }

    /** A rules bug that always throws must surface through [Ismcts.search]'s onSearchError, not vanish. */
    @Test
    fun search_reportsDeterminizeFailures_insteadOfSwallowingThem() =
        runTest {
            val ismcts =
                Ismcts(
                    rules = NimRules,
                    rolloutPolicy = { firstLegalPolicy },
                    staticEval = { _, _ -> 0.5 },
                    budget = SearchBudget(maxMillis = 200L, maxIterations = 3, rolloutHorizon = 10),
                )
            val start = NimState(pile = 10, turn = true)
            val errors = mutableListOf<Throwable>()
            var calls = 0

            val root =
                ismcts.search(
                    determinize = {
                        calls++
                        if (calls == 1) throw IllegalStateException("bad determinization")
                        start
                    },
                    legal = NimRules.legalMoves(start, true),
                    viewer = true,
                    rolloutHorizon = 10,
                    elapsedMillis = { 0L },
                    onSearchError = { errors += it },
                )

            assertEquals(1, errors.size)
            assertTrue(errors.single() is IllegalStateException)
            assertTrue(root.children.values.any { it.visits > 0 }, "must still make progress after a reported failure")
        }

    /** Same hook, the other swallow site: a mid-tree rules exception, not a determinization one. */
    @Test
    fun search_reportsMidTreeFailures_insteadOfSwallowingThem() =
        runTest {
            var applyCalls = 0
            val flakyRules =
                object : GameRules<NimState, Int, Boolean, NimState> by NimRules {
                    override fun apply(
                        state: NimState,
                        move: Int,
                    ): Outcome<NimState> {
                        applyCalls++
                        if (applyCalls == 1) error("rules bug mid-tree")
                        return NimRules.apply(state, move)
                    }
                }
            val ismcts =
                Ismcts(
                    rules = flakyRules,
                    rolloutPolicy = { firstLegalPolicy },
                    staticEval = { _, _ -> 0.5 },
                    budget = SearchBudget(maxMillis = 200L, maxIterations = 3, rolloutHorizon = 10),
                )
            val start = NimState(pile = 10, turn = true)
            val errors = mutableListOf<Throwable>()

            ismcts.search(
                determinize = { start },
                legal = NimRules.legalMoves(start, true),
                viewer = true,
                rolloutHorizon = 10,
                elapsedMillis = { 0L },
                onSearchError = { errors += it },
            )

            assertEquals(1, errors.size)
            assertTrue(errors.single() is IllegalStateException)
        }
}
