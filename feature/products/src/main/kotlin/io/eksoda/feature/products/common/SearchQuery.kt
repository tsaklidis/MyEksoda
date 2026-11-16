package io.eksoda.feature.products.common

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val SEARCH_DEBOUNCE_MS = 300L

/** Typed text after a pause, so each key press is not a query; clearing it applies at once. */
@OptIn(FlowPreview::class)
internal fun Flow<String>.debouncedQuery(): Flow<String> =
    debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .map { it.trim() }
        .distinctUntilChanged()
