package io.eksoda.core.model

import java.math.BigDecimal
import java.time.OffsetDateTime

/** A receipt as the lists show it. Times are in the household timezone. */
public data class Receipt(
    val id: String,
    val merchant: Merchant,
    val store: Store?,
    val issuedAt: OffsetDateTime,
    /** `issued_local_date`, the day the receipt counts for. */
    val issuedDate: java.time.LocalDate,
    val totalAmount: Money,
    val itemCount: Int,
    val paymentMethod: PaymentMethod,
    val documentKind: DocumentKind,
    val cancelled: Boolean,
    val excludedFromAnalytics: Boolean,
    val needsReview: Boolean,
    val hasWarnings: Boolean,
)

public data class Merchant(
    val id: String,
    val name: String,
)

public data class Store(
    val id: String,
    val name: String,
    val address: String?,
    val city: String?,
) {
    /** What to call the store: its name, else its address. */
    val label: String get() = name.ifEmpty { listOfNotNull(address, city).joinToString(", ") }
}

public enum class PaymentMethod(public val code: String) {
    CARD("card"),
    CASH("cash"),
    BANK("bank"),
    MIXED("mixed"),
    OTHER("other"),
    UNKNOWN("unknown"),
    ;

    public companion object {
        public fun fromCode(code: String?): PaymentMethod = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

public enum class DocumentKind(public val code: String) {
    SALE("sale"),
    CREDIT_NOTE("credit_note"),
    OTHER("other"),
    ;

    public companion object {
        public fun fromCode(code: String?): DocumentKind = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

/** How exact `issuedAt` is: some sources print only the day, or a time close to it. */
public enum class IssueTime { EXACT, APPROXIMATE, DATE_ONLY }

public enum class LineType { PRODUCT, DISCOUNT, FEE, OTHER }

public enum class MatchStatus { MATCHED, NEEDS_REVIEW, NOT_A_PRODUCT }

public data class ReceiptItem(
    val id: String,
    val lineNumber: Int,
    val lineType: LineType,
    val description: String,
    val quantity: BigDecimal?,
    /** piece | kg | l | other */
    val unit: String?,
    val unitPrice: Money?,
    val totalPrice: Money,
    val matchStatus: MatchStatus,
    val productId: String?,
    val productName: String?,
    val categorySlug: String?,
    val normalizedUnitPrice: Money?,
    /** kg | l | piece | other */
    val normalizedUnit: String?,
)

public data class ReceiptPayment(val amount: Money, val method: PaymentMethod, val label: String?, val estimated: Boolean)

public data class VatAmount(val rate: BigDecimal?, val net: Money, val vat: Money)

public data class ReceiptWarning(val code: String, val lineNumber: Int?)

/** Everything Receipt detail shows (android-app.md §6.4.3). */
public data class ReceiptDetail(
    val receipt: Receipt,
    val issueTime: IssueTime,
    val hasLineItems: Boolean,
    val items: List<ReceiptItem>,
    val payments: List<ReceiptPayment>,
    val vatBreakdown: List<VatAmount>,
    val warnings: List<ReceiptWarning>,
    val userNote: String,
    val documentNumber: String,
    val documentTypeCode: String?,
    val mark: String?,
    val uid: String?,
    val cancellationMark: String?,
    val providerCode: String,
    val parserVersion: String,
)

/** A choice in a filter or picker. */
public data class Option(val id: String, val name: String)
