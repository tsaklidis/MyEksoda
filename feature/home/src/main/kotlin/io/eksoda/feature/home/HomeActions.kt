package io.eksoda.feature.home

import io.eksoda.core.model.Period

/** Where the Overview leads. */
class HomeActions(
    val onBudgetClick: () -> Unit,
    val onBillsClick: () -> Unit,
    /** A category's screen over the month shown ([categoryId] null: the uncategorized lines). */
    val onCategoryClick: (categoryId: String?, period: Period) -> Unit,
    val onSpendingClick: () -> Unit,
    val onReviewClick: () -> Unit,
    val onImportsClick: () -> Unit,
)
