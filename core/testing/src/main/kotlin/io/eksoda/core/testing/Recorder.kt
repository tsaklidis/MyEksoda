package io.eksoda.core.testing

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData

/**
 * What every fake repository shares: the writes it was asked for ([calls], as
 * "name(arguments)", in order) and a [failure] that, while set, every write fails with.
 */
open class Recorder {
    /** When set, every write fails with it. */
    var failure: Throwable? = null

    /** Every write, as "name(arguments)", in order. */
    val calls = mutableListOf<String>()

    /** Records [call]; [failure] if one is set, else [value]'s result. */
    protected fun <T> answer(call: String, value: () -> T): Result<T> {
        calls += call
        return failure?.let { Result.failure(it) } ?: Result.success(value())
    }
}

/** A read the test did not set up: it fails, so a test never passes on data it forgot. */
class NotFaked(what: String) : IllegalStateException("$what is not set up in this test")

/**
 * [items] as one fully loaded page. The load states matter: `PagingData.from(list)` alone
 * reports none, and `asSnapshot` then waits for a load that never ends.
 */
fun <T : Any> staticPage(items: List<T>): PagingData<T> {
    val done = LoadState.NotLoading(endOfPaginationReached = true)
    return PagingData.from(items, LoadStates(refresh = done, prepend = done, append = done))
}
