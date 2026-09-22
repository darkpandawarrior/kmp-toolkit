package com.siddharth.kmp.botspolicy

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.ln
import kotlin.math.sqrt

/** A move-keyed UCB1 tree node. Exposed so callers can read visit/reward stats after a [Ismcts.search]. */
class SearchNode<Move> internal constructor(
    val move: Move?,
) {
    var visits: Int = 0
    var totalReward: Double = 0.0
    var availability: Int = 0
    val children: MutableMap<Move, SearchNode<Move>> = linkedMapOf()

    fun meanReward(): Double = if (visits == 0) 0.0 else totalReward / visits

    fun ucb(c: Double): Double {
        if (visits == 0 || availability == 0) return Double.MAX_VALUE
        return meanReward() + c * sqrt(ln(availability.toDouble()) / visits)
    }
}

/**
 * Generic Information-Set Monte Carlo Tree Search shell. Knows nothing about any specific game —
 * every domain concept ([rules], leaf evaluation, rollout policy) is injected by the caller.
 *
 * Determinization (sampling a consistent full [State] from a hidden-information [View]) is
 * deliberately OUT of scope here: [search]'s [determinize] parameter is called once per iteration to
 * supply that fresh determinized root, since HOW hidden information is sampled (belief models,
 * priors, ...) is domain-specific and stays out of this shell.
 *
 * [rolloutPolicy] is a FACTORY, not a single instance, and is invoked once per rollout ply. This is
 * load-bearing for callers that reseed a fresh policy per ply (e.g. from a dedicated RNG stream) —
 * a single shared, continuously-advancing rollout policy instance can measurably weaken deeper
 * searches versus per-ply reseeding. Keeping it a factory lets a caller reproduce exact seeding while
 * this shell stays domain-agnostic (the factory closes over the caller's own RNG; the shell never
 * sees a seed).
 */
class Ismcts<State, Move, View, Actor>(
    private val rules: GameRules<State, Move, Actor, View>,
    private val rolloutPolicy: () -> Policy<View, Move>,
    /** Leaf evaluation for positions the rollout reaches without terminating (see [rollout]). */
    private val staticEval: (State, Actor) -> Double,
    private val budget: SearchBudget,
) {
    private val ucbExplorationConstant = 0.7

    /**
     * Runs the search and returns the populated root node (one child per element of [legal], plus
     * whatever the tree expanded into). [determinize] supplies one fresh determinized [State] per
     * iteration; a thrown exception from it is treated as a free retry (does not consume an
     * iteration) — the caller is expected to advance its own RNG before rethrowing, mirroring a
     * failed-sample retry. [elapsedMillis] is polled against [SearchBudget.maxMillis].
     *
     * A `suspend` function: cancelling the calling coroutine (a player moves on, a screen leaves
     * composition) stops the loop at the next iteration boundary via [ensureActive] instead of
     * burning the full budget. [onSearchError] receives every determinization/iteration failure
     * that isn't a cancellation — a rules bug that always throws is now visible instead of
     * silently producing a near-empty root; the default is a no-op so existing callers that don't
     * care still compile.
     *
     * `determinize`, `iterate` and the rules callbacks are all caller-supplied, which is why the
     * catches below are broad: a search shell whose job is to survive a bad sample cannot narrow to
     * types it does not own. Nothing is swallowed — every non-cancellation failure goes to
     * [onSearchError], and CancellationException is rethrown first so cancellation still works.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun search(
        determinize: () -> State,
        legal: List<Move>,
        viewer: Actor,
        rolloutHorizon: Int,
        elapsedMillis: () -> Long,
        onSearchError: (Throwable) -> Unit = {},
    ): SearchNode<Move> {
        val root = SearchNode<Move>(null)
        for (m in legal) root.children.getOrPut(m) { SearchNode(m) }

        var iterations = 0
        while (iterations < budget.maxIterations && elapsedMillis() < budget.maxMillis) {
            coroutineContext.ensureActive()
            val detState =
                try {
                    determinize()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onSearchError(e)
                    continue // failed determinization — free retry, doesn't consume an iteration
                }
            try {
                iterate(root, detState, viewer, rolloutHorizon)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onSearchError(e) // bad determinization mid-tree; continue
            }
            iterations++
        }
        return root
    }

    private fun iterate(
        root: SearchNode<Move>,
        initState: State,
        viewer: Actor,
        rolloutHorizon: Int,
    ) {
        val path = mutableListOf<SearchNode<Move>>()
        var currentNode = root
        var currentState = initState

        // Selection + Expansion. Three of the loop's four exits — no actor, no legal move, and a
        // move the rules rejected — are the same event: this determinization cannot be walked any
        // further. [advance] returns null for all three, so the loop states that once.
        while (!rules.isTerminal(currentState)) {
            val step = advance(currentNode, currentState, path) ?: break
            currentNode = step.node
            currentState = step.state
            // A freshly expanded node has nothing to select from yet — go to rollout.
            if (currentNode.visits == 0) break
        }
        path.add(currentNode)

        // Rollout
        val reward = rollout(currentState, viewer, rolloutHorizon)

        // Backpropagate
        for (node in path) {
            node.visits++
            node.totalReward += reward
        }
    }

    /** Where one selection step landed. */
    private inner class Step(
        val node: SearchNode<Move>,
        val state: State,
    )

    /**
     * One selection/expansion step from [node] in [state].
     *
     * Appends [node] to [path] exactly where the original inline loop did — BEFORE applying the
     * chosen move — so a rules rejection still leaves the node on the path, and the caller's
     * trailing `path.add(currentNode)` still double-counts it in that one case. That is existing
     * behaviour, preserved deliberately: changing which nodes get backpropagated changes search
     * results, and this refactor is about the four `break`s, not the statistics.
     *
     * Returns null when the walk cannot continue: nobody acts, nothing is legal, or the rules
     * rejected the move.
     */
    private fun advance(
        node: SearchNode<Move>,
        state: State,
        path: MutableList<SearchNode<Move>>,
    ): Step? {
        val who = rules.whoActsNext(state) ?: return null
        val legalNow = rules.legalMoves(state, who)
        if (legalNow.isEmpty()) return null

        // Update availability for all legal children in this determinization
        for (move in legalNow) {
            node.children.getOrPut(move) { SearchNode(move) }.availability++
        }

        // Find unvisited legal children
        val unvisited =
            legalNow.filter {
                val child = node.children[it]
                child == null || child.visits == 0
            }

        val chosenMove =
            if (unvisited.isNotEmpty()) {
                // Expand: pick one unvisited (first for determinism)
                unvisited.first()
            } else {
                // Select via UCB1
                legalNow.maxByOrNull { move ->
                    node.children[move]?.ucb(ucbExplorationConstant) ?: Double.MAX_VALUE
                } ?: legalNow.first()
            }

        path.add(node)
        val childNode = node.children.getOrPut(chosenMove) { SearchNode(chosenMove) }
        val accepted = rules.apply(state, chosenMove) as? Outcome.Accepted ?: return null
        return Step(childNode, accepted.state)
    }

    private fun rollout(
        state: State,
        viewer: Actor,
        rolloutHorizon: Int,
    ): Double {
        var currentState = state
        var plies = 0
        while (plies < rolloutHorizon && !rules.isTerminal(currentState)) {
            currentState = advanceOnePly(currentState) ?: break
            plies++
        }
        if (rules.isTerminal(currentState)) {
            return if (rules.winner(currentState) == viewer) 1.0 else 0.0
        }
        return staticEval(currentState, viewer)
    }

    /**
     * One rollout ply: pick a move for whoever acts next and apply it. Returns the next state, or
     * `null` when the rollout cannot continue — nobody to act, no legal move, or the rules rejected
     * the chosen move. Split out of [rollout] so the loop has one exit instead of three; the three
     * "stop here" conditions are the same value (`null`) and now say so.
     */
    private fun advanceOnePly(currentState: State): State? {
        val who = rules.whoActsNext(currentState) ?: return null
        val legalNow = rules.legalMoves(currentState, who)
        if (legalNow.isEmpty()) return null
        val view = rules.redact(currentState, who)
        val move =
            try {
                rolloutPolicy().decide(view, legalNow)
            } catch (ignored: Exception) {
                // A rollout is a cheap random playout, not a decision the user sees. A policy that
                // throws on one view must not abort the search, so fall back to the first legal
                // move and carry on — the breadth of the catch is the fallback's whole point.
                legalNow.first()
            }
        return when (val outcome = rules.apply(currentState, move)) {
            is Outcome.Accepted -> outcome.state
            is Outcome.Rejected -> null
        }
    }
}
