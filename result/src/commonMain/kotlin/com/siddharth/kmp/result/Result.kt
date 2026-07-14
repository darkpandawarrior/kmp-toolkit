package com.siddharth.kmp.result

/**
 * A typed success-or-failure. Unlike `kotlin.Result`, the error arm is a typed `E` (usually a
 * [DataError]) rather than a `Throwable`, so callers exhaustively handle known failure modes
 * instead of catching. Import this `Result` explicitly where `kotlin.Result` is also in scope.
 */
sealed interface Result<out D, out E> {
    data class Success<out D>(val data: D) : Result<D, Nothing>
    data class Failure<out E>(val error: E) : Result<Nothing, E>
}

/** A [Result] carrying no success payload — for operations whose success is just "it worked". */
typealias EmptyResult<E> = Result<Unit, E>

/** Map the success value; failures pass through untouched. */
inline fun <D, E, R> Result<D, E>.map(transform: (D) -> R): Result<R, E> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Failure -> this
}

/** Chain a success into another [Result]; failures short-circuit. */
inline fun <D, E, R> Result<D, E>.flatMap(transform: (D) -> Result<R, E>): Result<R, E> = when (this) {
    is Result.Success -> transform(data)
    is Result.Failure -> this
}

/** Collapse both arms into one value. */
inline fun <D, E, R> Result<D, E>.fold(onSuccess: (D) -> R, onFailure: (E) -> R): R = when (this) {
    is Result.Success -> onSuccess(data)
    is Result.Failure -> onFailure(error)
}

/** Map the error arm; successes pass through untouched. */
inline fun <D, E, F> Result<D, E>.mapError(transform: (E) -> F): Result<D, F> = when (this) {
    is Result.Success -> this
    is Result.Failure -> Result.Failure(transform(error))
}

/** Run [action] on success, returning the receiver for chaining. */
inline fun <D, E> Result<D, E>.onSuccess(action: (D) -> Unit): Result<D, E> {
    if (this is Result.Success) action(data)
    return this
}

/** Run [action] on failure, returning the receiver for chaining. */
inline fun <D, E> Result<D, E>.onFailure(action: (E) -> Unit): Result<D, E> {
    if (this is Result.Failure) action(error)
    return this
}

/** Discard the success payload, keeping only success-vs-failure. */
fun <D, E> Result<D, E>.asEmpty(): EmptyResult<E> = map {}

fun <D, E> Result<D, E>.getOrNull(): D? = (this as? Result.Success)?.data

fun <D, E> Result<D, E>.errorOrNull(): E? = (this as? Result.Failure)?.error
