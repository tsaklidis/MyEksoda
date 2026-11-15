// Port of backend/apps/receipts/mydata.py.
package io.eksoda.engine.mydata

import io.eksoda.engine.text.pyIsDigit
import io.eksoda.engine.text.pyStrip
import java.math.BigDecimal

// AADE myDATA code lists (TD §9.2).
//
// Every Greek e-receipt provider reports the same myDATA codes, so these tables are
// provider-neutral. Source: AADE "myDATA REST API Documentation" v1.0.9 (official, English,
// 2024-10), appendix 8.1, 8.2, 8.12 and 8.13 (checked 2026-11-14).
//
// Unknown codes never raise: they map to OTHER / null and the raw code is kept.

public enum class DocumentKind(public val code: String) {
    SALE("sale"),
    CREDIT_NOTE("credit_note"),
    OTHER("other"),
}

public enum class Unit(public val code: String) {
    PIECE("piece"),
    KG("kg"),
    L("l"),
    OTHER("other"),
}

public enum class PaymentMethod(public val code: String) {
    CARD("card"),
    CASH("cash"),
    BANK("bank"),
    OTHER("other"),
    UNKNOWN("unknown"),
}

/** 8.1 Invoice type (subset relevant to household purchases). */
public val DOCUMENT_TYPES: Map<String, String> = linkedMapOf(
    "1.1" to "Sales Invoice",
    "1.2" to "Sales Invoice/Intra-community Supplies",
    "1.3" to "Sales Invoice/Third Country Supplies",
    "1.4" to "Sales Invoice/Sale on Behalf of Third Parties",
    "1.5" to "Sales Invoice/Clearance of Sales on Behalf of Third Parties",
    "1.6" to "Sales Invoice/Supplemental Accounting Source Document",
    "2.1" to "Service Rendered Invoice",
    "2.2" to "Intra-community Service Rendered Invoice",
    "2.3" to "Third Country Service Rendered Invoice",
    "2.4" to "Service Rendered Invoice/Supplemental Accounting Source Document",
    "5.1" to "Credit Invoice/Associated",
    "5.2" to "Credit Invoice/Non-Associated",
    "11.1" to "Retail Sales Receipt",
    "11.2" to "Service Rendered Receipt",
    "11.3" to "Simplified Invoice",
    "11.4" to "Retail Sales Credit Note",
    "11.5" to "Retail Sales Receipt on Behalf of Third Parties",
)
public val CREDIT_NOTE_TYPES: Set<String> = setOf("5.1", "5.2", "11.4")
public val SALE_TYPES: Set<String> = DOCUMENT_TYPES.keys - CREDIT_NOTE_TYPES

/**
 * 8.2 VAT category → rate in percent (null: records without VAT, e.g. payroll). Ordered like
 * the Python dict, which `vatCategoryForRate` depends on.
 */
public val VAT_RATES: Map<String, BigDecimal?> = linkedMapOf(
    "1" to BigDecimal("24"),
    "2" to BigDecimal("13"),
    "3" to BigDecimal("6"),
    "4" to BigDecimal("17"),
    "5" to BigDecimal("9"),
    "6" to BigDecimal("4"),
    "7" to BigDecimal("0"),
    "8" to null,
    "9" to BigDecimal("3"), // Article 31, Law 5057/2023
    "10" to BigDecimal("4"), // Article 31, Law 5057/2023
)

/** 8.13 Unit of measurement. */
public val MEASUREMENT_UNITS: Map<String, Unit> = mapOf(
    "1" to Unit.PIECE,
    "2" to Unit.KG,
    "3" to Unit.L,
)

/** 8.12 Payment methods. */
public val PAYMENT_METHODS: Map<String, PaymentMethod> = mapOf(
    "1" to PaymentMethod.BANK, // domestic payments account number
    "2" to PaymentMethod.BANK, // foreign payments account number
    "3" to PaymentMethod.CASH,
    "4" to PaymentMethod.OTHER, // check
    "5" to PaymentMethod.OTHER, // on credit
    "6" to PaymentMethod.BANK, // web banking
    "7" to PaymentMethod.CARD, // POS / e-POS
    "8" to PaymentMethod.BANK, // direct payments IRIS
)

// Line type (8.16) and remark (8.15) codes are only stored as printed (Line.lineTypeCode,
// Line.annotationCode); nothing interprets them.

/**
 * Trims a code; blank means absent. Integer codes lose leading zeros ("07" → "7").
 *
 * Python's `str.isdigit()` also accepts digits such as "²", for which `int()` then raises; only
 * decimal digits (Nd) count as digits here, so such codes are kept as printed.
 */
public fun normalizeCode(code: String?): String? {
    if (code == null) return null
    val value = code.pyStrip()
    if (value.isEmpty()) return null
    return if (value.pyIsDigit()) value.toBigInteger().toString() else value
}

/** Optional myDATA integer codes are rendered as "0" when not set. */
public fun isAbsent(code: String?): Boolean = normalizeCode(code).let { it == null || it == "0" }

public fun documentKind(typeCode: String?): DocumentKind {
    val code = typeCode.orEmpty().pyStrip()
    return when (code) {
        in CREDIT_NOTE_TYPES -> DocumentKind.CREDIT_NOTE
        in SALE_TYPES -> DocumentKind.SALE
        else -> DocumentKind.OTHER
    }
}

public fun isKnownVatCategory(code: String?): Boolean = normalizeCode(code) in VAT_RATES

public fun vatRate(categoryCode: String?): BigDecimal? =
    VAT_RATES[normalizeCode(categoryCode).orEmpty()]

public fun measurementUnit(code: String?): Unit? {
    val normalized = normalizeCode(code) ?: return null
    return MEASUREMENT_UNITS[normalized] ?: Unit.OTHER
}

public fun paymentMethod(code: String?): PaymentMethod {
    val normalized = normalizeCode(code) ?: return PaymentMethod.UNKNOWN
    return PAYMENT_METHODS[normalized] ?: PaymentMethod.OTHER
}
