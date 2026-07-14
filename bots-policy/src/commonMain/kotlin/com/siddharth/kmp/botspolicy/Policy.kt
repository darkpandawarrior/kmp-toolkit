package com.siddharth.kmp.botspolicy

/**
 * A generic bot/agent policy: given a redacted [View] of an opaque game state and the concrete legal
 * [Move]s, choose one. Domain-agnostic — knows nothing about any specific game's types.
 */
fun interface Policy<View, Move> {
    fun decide(
        view: View,
        legal: List<Move>,
    ): Move
}
