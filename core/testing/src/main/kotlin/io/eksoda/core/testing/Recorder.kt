package io.eksoda.core.testing

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData

/**
 * What every fake repository shares: the writes it was asked for ([calls], as
 * "name(arguments)", in order) and the failures a test sets up. A call fails with, first, the
 * next failure queued for its operation ([failNext]: one request fails, the retry succeeds),
 * then its operation's lasting failure ([failOperation]), then [failure] (every operation).
 */
open class Recorder {
    /** When set, every write fails with it. */
    var failure: Throwable? = null

    /** Every write, as "name(arguments)", in order. */
    val calls = mutableListOf<String>()

    private val lasting = mutableMapOf<String, Throwable>()
    private val queued = mutableMapOf<String, ArrayDeque<Throwable>>()

    /** The next [times] calls of [operation] (a method name: "importReceipt") fail. */
    fun failNext(
        operation: String,
        times: Int = 1,
        error: Throwable = IllegalStateException("$operation failed"),
    ) {
        repeat(times) { queued.getOrPut(operation) { ArrayDeque() }.addLast(error) }
    }

    /** Every call of [operation] fails with [error] until it is set to null. */
    fun failOperation(operation: String, error: Throwable?) {
        if (error == null) lasting -= operation else lasting[operation] = error
    }

    /** Records [call] ("operation(arguments)"); its failure if one is due, else [value]'s. */
    protected fun <T> answer(call: String, value: () -> T): Result<T> {
        calls += call
        val operation = call.substringBefore('(')
        val error = queued[operation]?.removeFirstOrNull() ?: lasting[operation] ?: failure
        return error?.let { Result.failure(it) } ?: Result.success(value())
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
