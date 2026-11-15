// Port of the pure parts of backend/apps/receipts/services/persistence.py.
package io.eksoda.engine.persist

import io.eksoda.engine.canonical.CanonicalReceipt
import io.eksoda.engine.canonical.Document
import io.eksoda.engine.canonical.Identity
import io.eksoda.engine.canonical.Payment
import io.eksoda.engine.text.pyRegex
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

public fun documentNumber(document: Document): String {
    document.formattedNumber?.takeIf { it.isNotEmpty() }?.let { return it }
    return listOfNotNull(document.series, document.number).filter {
        it.isNotEmpty()
    }.joinToString("-")
}

/** The one method used; `mixed` for several different ones; `unknown` for none. */
public fun paymentMethod(payments: List<Payment>): String {
    val methods = payments.map { it.method?.code ?: "unknown" }.toSet()
    return when {
        methods.isEmpty() -> "unknown"
        methods.size > 1 -> "mixed"
        else -> methods.single()
    }
}

/** A non-zero cancellation MARK means the receipt was cancelled (requirements §8.6). */
public fun isCancelled(identity: Identity): Boolean =
    identity.cancellationMark?.trim('0')?.isNotEmpty() == true

/**
 * The canonical receipt as stored (requirements §15.3):
 * - without its URL (a credential, stored only in `source_url`)
 * - with the names of unknown fields but not their values: an unknown field was not
 *   allowlisted, so it may hold personal data (a loyalty code, a terminal id)
 * - without anything shaped like a card number in payment labels
 */
public fun sourceData(canonical: CanonicalReceipt): JsonObject {
    val data = canonical.toJson().toMutableMap()
    data.remove("source_url")
    data["unknown_fields"] =
        JsonObject(canonical.unknownFields.keys.associateWith { JsonPrimitive("") })
    val payments = data["payments"] as? JsonArray
    if (payments != null) {
        data["payments"] = JsonArray(
            payments.map { payment ->
                val fields = payment.jsonObject.toMutableMap()
                val label = (fields["method_label"] as? JsonPrimitive)?.takeIf {
                    it.isString
                }?.content
                if (!label.isNullOrEmpty()) {
                    fields["method_label"] =
                        JsonPrimitive(dropCardNumbers(label))
                }
                JsonObject(fields)
            },
        )
    }
    return JsonObject(data)
}

private val CARD_NUMBER_RE = pyRegex("""(?<!\d)\d(?:[ -]?\d){12,18}(?!\d)""")
private const val LUHN_MODULUS = 10
private const val LUHN_DOUBLE_LIMIT = 4
private const val LUHN_CARRY = 9

/** Replaces digit runs of card-number length that pass the Luhn check. */
public fun dropCardNumbers(text: String): String = CARD_NUMBER_RE.replace(text) { match ->
    val digits = match.value.filter(Char::isDigit).map { Character.digit(it, LUHN_MODULUS) }
    val total = digits.reversed().withIndex().sumOf { (index, digit) ->
        when {
            index % 2 == 0 -> digit
            digit > LUHN_DOUBLE_LIMIT -> digit * 2 - LUHN_CARRY
            else -> digit * 2
        }
    }
    if (total % LUHN_MODULUS == 0) "<card>" else match.value
}
