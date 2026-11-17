package io.eksoda.core.data.repository

import io.eksoda.core.model.ReceiptFilter

/**
 * The receipt list's last filters, without the search text. The period
 * moves to this month when a new calendar month has started since it was saved.
 */
interface ReceiptFilterStore {
    /** The saved filters, or the defaults. A merchant, store or product that is gone is dropped. */
    suspend fun load(): ReceiptFilter

    suspend fun save(filter: ReceiptFilter)
}
