package io.eksoda.feature.settings.more

/** Where the More tab leads; each does nothing unless the app wires it. */
interface MoreActions {
    fun openReceipts() {}

    fun openBudget() {}

    fun openBills() {}

    fun openProducts() {}

    fun openCategories() {}

    fun openMerchants() {}

    fun openImports() {}

    fun openSync() {}

    fun openSettings() {}
}
