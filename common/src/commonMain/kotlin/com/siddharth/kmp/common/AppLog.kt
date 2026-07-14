package com.siddharth.kmp.common

import io.github.aakira.napier.Napier

/**
 * Thin KMP logging facade over Napier so call sites never import Napier directly (swap the backend
 * in one place). Napier's antilog is installed per-platform at app startup by each entrypoint.
 */
object AppLog {
    fun d(
        message: String,
        tag: String? = null,
    ) = Napier.d(message, tag = tag)

    fun i(
        message: String,
        tag: String? = null,
    ) = Napier.i(message, tag = tag)

    fun w(
        message: String,
        throwable: Throwable? = null,
        tag: String? = null,
    ) = Napier.w(message, throwable, tag)

    fun e(
        message: String,
        throwable: Throwable? = null,
        tag: String? = null,
    ) = Napier.e(message, throwable, tag)
}
