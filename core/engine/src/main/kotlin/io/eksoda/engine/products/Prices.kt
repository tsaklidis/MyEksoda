// Port of normalize() in backend/apps/products/prices.py. The PRICE_HISTORY filter and the
// refresh functions query the database, so they live in core:data (docs/android-standalone.md §6.6).
package io.eksoda.engine.products

import io.eksoda.engine.classification.LineType
import io.eksoda.engine.mydata.Unit
import io.eksoda.engine.text.pyDivide
import java.math.BigDecimal
import java.math.RoundingMode

// Normalized unit prices on receipt lines (requirements §11.1, TD §13.4).
//
// Price history is a query over receipt lines: each product line with a product carries its
// quantity in a base unit and its price per base unit. A price is null when it cannot be
// computed, never a misleading value.

/** `ReceiptItem.NormalizedUnit`. */
public enum class NormalizedUnit(public val code: String) {
    KG("kg"),
    L("l"),
    PIECE("piece"),
    OTHER("other"),
    ;

    public companion object {
        public fun fromCode(code: String): NormalizedUnit = entries.first { it.code == code }
    }
}

/** The receipt line fields `normalize` reads. */
public data class PricedLine(
    val lineType: LineType,
    val quantity: BigDecimal?,
    val unit: Unit?,
    val grossAmount: BigDecimal,
)

/** The product fields `normalize` reads: its package size, both set or both null. */
public data class ProductPackage(val quantity: BigDecimal?, val unit: String?)

/** (normalized quantity, normalized unit, price per normalized unit). */
public data class NormalizedPrice(
    val quantity: BigDecimal,
    val unit: NormalizedUnit,
    /** Null when the quantity is zero. */
    val unitPrice: BigDecimal?,
)

private const val QUANTITY_SCALE = 6 // NormalizedQuantityField
private const val PRICE_SCALE = 4 // UnitPriceField

/**
 * The normalized quantity, unit and unit price of [line] linked to [product], or null where
 * Python returns `(None, None, None)`: not a product line, no product, or no quantity.
 */
public fun normalize(line: PricedLine, product: ProductPackage?): NormalizedPrice? {
    val itemQuantity = line.quantity
    if (line.lineType != LineType.PRODUCT || product == null || itemQuantity == null) return null
    val packageQuantity = product.quantity
    val (quantity, unit) = when {
        // Weighed or measured: the quantity itself.
        line.unit == Unit.KG -> toBase(itemQuantity, Unit.KG.code)

        line.unit == Unit.L -> toBase(itemQuantity, Unit.L.code)

        line.unit == Unit.PIECE && packageQuantity != null && packageQuantity.signum() != 0 -> {
            // Python raises ValueError for a None unit; toBase does too.
            val (packageInBase, base) = toBase(packageQuantity, product.unit ?: "None")
            itemQuantity * packageInBase to base
        }

        line.unit == Unit.PIECE -> itemQuantity to NormalizedUnit.PIECE.code

        else -> itemQuantity to NormalizedUnit.OTHER.code
    }
    val price = if (quantity.signum() != 0) {
        line.grossAmount.pyDivide(quantity).setScale(PRICE_SCALE, RoundingMode.HALF_UP)
    } else {
        null
    }
    return NormalizedPrice(
        quantity = quantity.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP),
        unit = NormalizedUnit.fromCode(unit),
        unitPrice = price,
    )
}
