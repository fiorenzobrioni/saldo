package com.callbackdev.saldo.core.common.coroutines

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but rethrows [CancellationException] so structured
 * cancellation is never swallowed. Wraps repository writes launched from
 * ViewModels: the [Result] drives a saved/failed one-shot event instead of an
 * unhandled coroutine exception crashing the app and leaving the screen stuck.
 */
@Suppress("TooGenericExceptionCaught")
suspend fun <T> suspendRunCatching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(error)
    }
