package io.eksoda.ui

import kotlinx.serialization.Serializable

// Type-safe navigation destinations (docs/android-standalone.md §3). Onboarding is not one: it
// is shown instead of the graph until the household exists. Never put a receipt link in a
// destination: arguments are saved with the back stack, and the link is a credential (§8.1).

/** The tabs: Receipts and Products (Home and Insights join in L6). */
@Serializable
data object MainDestination

@Serializable
data class ReceiptDetailDestination(val receiptId: String)

@Serializable
data object ScanDestination

@Serializable
data object ImportsDestination

@Serializable
data class ProductDetailDestination(val productId: String)

@Serializable
data object ReviewDestination

@Serializable
data object CategoriesDestination

@Serializable
data object MerchantsDestination

@Serializable
data class MerchantDetailDestination(val merchantId: String)
