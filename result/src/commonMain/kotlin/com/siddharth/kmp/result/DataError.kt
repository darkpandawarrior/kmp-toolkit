package com.siddharth.kmp.result

/**
 * Root of the typed error hierarchy carried in [Result.Failure]. Split by source so callers can
 * react differently to a network failure vs a local one. Apps extend this with their own arms
 * (e.g. a `Validation` error) by implementing the interface.
 */
sealed interface DataError {
    enum class Network : DataError {
        REQUEST_TIMEOUT,
        TOO_MANY_REQUESTS,
        NO_INTERNET,
        SERVER_ERROR,
        SERIALIZATION,
        UNAUTHORIZED,
        FORBIDDEN,
        NOT_FOUND,
        CONFLICT,
        UNKNOWN,
    }

    enum class Local : DataError {
        DISK_FULL,
        NOT_FOUND,
        UNKNOWN,
    }
}
