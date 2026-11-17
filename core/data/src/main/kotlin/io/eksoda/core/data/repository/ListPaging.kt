package io.eksoda.core.data.repository

import androidx.paging.PagingConfig

/**
 * Paging for the long lists (receipts, products). Without placeholders only loaded rows exist,
 * so a list never builds an entry for every row in the table, and a row never swaps a
 * placeholder key for its real one (which re-creates it and fades it in while scrolling). Pages
 * load a full page ahead, so a fling rarely reaches the end of what is loaded.
 */
internal fun listPagingConfig(pageSize: Int): PagingConfig = PagingConfig(
    pageSize = pageSize,
    prefetchDistance = pageSize,
    enablePlaceholders = false,
    initialLoadSize = pageSize * 2,
)
