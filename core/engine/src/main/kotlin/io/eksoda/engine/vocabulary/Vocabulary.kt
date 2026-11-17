package io.eksoda.engine.vocabulary

import io.eksoda.engine.mydata.DocumentKind
import io.eksoda.engine.mydata.PaymentMethod
import io.eksoda.engine.mydata.Unit
import io.eksoda.engine.mydata.VAT_RATES
import io.eksoda.engine.mydata.documentKind
import io.eksoda.engine.text.normalizeText
import io.eksoda.engine.text.pyIsAlpha
import io.eksoda.engine.text.pyIsAscii
import java.math.BigDecimal

// Label vocabularies shared by every provider.
//
// Sources that print labels instead of myDATA codes ("Απόδειξη Λιανικής Πώλησης", "TEMAXIO",
// "POS / e-POS", …) are mapped here. Keys are compared after `normalizeText` (case, accents,
// Latin/Greek look-alike letters, punctuation), so "ΤΕΜΑΧΙΟ" and "TEMAXIO" are the same key.
// Extend these tables when a new label appears: unknown labels never crash a parser, they become
// null/OTHER and are reported as format drift.

private fun <T> index(vararg table: Pair<String, T>): Map<String, T> =
    table.associate { (label, value) -> normalizeText(label) to value }

public val DOCUMENT_TYPE_LABELS: Map<String, String> = index(
    "Απόδειξη Λιανικής Πώλησης" to "11.1",
    "ΑΛΠ" to "11.1",
    "ΑΛΠ (Απόδειξη Λιανικής Πώλησης)" to "11.1",
    "Απόδειξη Παροχής Υπηρεσιών" to "11.2",
    "ΑΠΥ" to "11.2",
    "Απλοποιημένο Τιμολόγιο" to "11.3",
    "Πιστωτικό Στοιχείο Λιανικής" to "11.4",
    "Πιστωτικό Στοιχ. Λιανικής" to "11.4",
    "Απόδειξη Λιανικής Πώλησης για Λογαριασμό Τρίτων" to "11.5",
    "Τιμολόγιο Πώλησης" to "1.1",
    "Τιμολόγιο Παροχής Υπηρεσιών" to "2.1",
)

public val UNIT_LABELS: Map<String, Unit> = index(
    "ΤΕΜΑΧΙΟ" to Unit.PIECE,
    "ΤΕΜΑΧΙΑ" to Unit.PIECE,
    "ΤΕΜΑΧ" to Unit.PIECE,
    "ΤΕΜ" to Unit.PIECE,
    "ΤΜΧ" to Unit.PIECE,
    "PCS" to Unit.PIECE,
    "ΚΙΛΑ" to Unit.KG,
    "ΚΙΛΟ" to Unit.KG,
    "ΚΙΛ" to Unit.KG,
    "KG" to Unit.KG,
    "ΛΙΤΡΑ" to Unit.L,
    "ΛΙΤΡΟ" to Unit.L,
    "LT" to Unit.L,
    "L" to Unit.L,
)

public val PAYMENT_LABELS: Map<String, PaymentMethod> = index(
    "POS / e-POS" to PaymentMethod.CARD,
    "POS" to PaymentMethod.CARD,
    "e-POS" to PaymentMethod.CARD,
    "ΚΑΡΤΑ" to PaymentMethod.CARD,
    "ΠΙΣΤΩΤΙΚΗ ΚΑΡΤΑ" to PaymentMethod.CARD,
    "ΧΡΕΩΣΤΙΚΗ ΚΑΡΤΑ" to PaymentMethod.CARD,
    "VISA" to PaymentMethod.CARD,
    "MASTERCARD" to PaymentMethod.CARD,
    "MAESTRO" to PaymentMethod.CARD,
    "AMEX" to PaymentMethod.CARD,
    "AMERICAN EXPRESS" to PaymentMethod.CARD,
    "ΜΕΤΡΗΤΑ" to PaymentMethod.CASH,
    "CASH" to PaymentMethod.CASH,
    "WEB BANKING" to PaymentMethod.BANK,
    "IRIS" to PaymentMethod.BANK,
    "ΚΑΤΑΘΕΣΗ" to PaymentMethod.BANK,
    "ΕΠΙ ΠΙΣΤΩΣΕΙ" to PaymentMethod.OTHER,
    "ΕΠΙΤΑΓΗ" to PaymentMethod.OTHER,
)

public val CURRENCY_LABELS: Map<String, String> = index(
    "EUR" to "EUR",
    "ΕΥΡΩ" to "EUR",
    "0# Euro Member Countries" to "EUR",
)

private const val CURRENCY_CODE_LENGTH = 3

/** (myDATA code or null, kind) for a printed document type label. */
public fun documentType(label: String?): Pair<String?, DocumentKind> {
    val key = normalizeText(label.orEmpty())
    val code = DOCUMENT_TYPE_LABELS[key]
    if (code != null) return code to documentKind(code)
    // Combined or unknown labels: decide the kind from keywords. Refunds and returns
    // ("ΑΠΟΔΕΙΞΗ ΕΠΙΣΤΡΟΦΗΣ") reduce spending; cancellations are left for review.
    return when {
        "ΠΙΣΤΩΤΙΚ" in key || "ΕΠΙΣΤΡΟΦ" in key -> null to DocumentKind.CREDIT_NOTE
        "ΑΚΥΡΩΤ" in key -> null to DocumentKind.OTHER
        "ΑΠΟΔΕΙΞΗ" in key || "ΤΙΜΟΛΟΓΙΟ" in key -> null to DocumentKind.SALE
        else -> null to DocumentKind.OTHER
    }
}

/** The unit for a printed label; null when the label is unknown or blank. */
public fun unit(label: String?): Unit? = UNIT_LABELS[normalizeText(label.orEmpty())]

public fun paymentMethod(label: String?): PaymentMethod? =
    PAYMENT_LABELS[normalizeText(label.orEmpty())]

public fun currency(label: String?): String? {
    if ("€" in label.orEmpty()) return "EUR"
    val key = normalizeText(label.orEmpty())
    if (key.isEmpty()) return null
    CURRENCY_LABELS[key]?.let { return it }
    return key.takeIf { it.length == CURRENCY_CODE_LENGTH && it.pyIsAlpha() && it.pyIsAscii() }
}

/** The myDATA VAT category for a rate, when the rate identifies exactly one category. */
public fun vatCategoryForRate(rate: BigDecimal?): String? {
    if (rate == null) return null
    val matches = VAT_RATES.filterValues { it != null && it.compareTo(rate) == 0 }.keys
    return matches.singleOrNull()
}
