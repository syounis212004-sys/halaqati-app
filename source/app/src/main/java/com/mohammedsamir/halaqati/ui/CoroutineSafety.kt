package com.mohammedsamir.halaqati.ui

import kotlinx.coroutines.CancellationException

/**
 * Like [runCatching] but never swallows a coroutine cancellation. A plain runCatching around a suspending load turns
 * "the screen left the composition" into a red error message and keeps a cancelled job running as if it had failed.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
