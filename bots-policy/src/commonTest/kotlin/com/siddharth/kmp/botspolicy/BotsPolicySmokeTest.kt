package com.siddharth.kmp.botspolicy

import kotlin.test.Test
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
    fun ismcts_search_populatesRootChildren() {
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
}
