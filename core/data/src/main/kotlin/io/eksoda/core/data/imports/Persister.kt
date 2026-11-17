package io.eksoda.core.data.imports

import android.database.sqlite.SQLiteConstraintException
import io.eksoda.core.data.util.Fixed
import io.eksoda.core.database.CategorySeed
import io.eksoda.core.database.dao.MerchantDao
import io.eksoda.core.database.dao.ReceiptDao
import io.eksoda.core.database.entity.MerchantEntity
import io.eksoda.core.database.entity.ReceiptEntity
import io.eksoda.core.database.entity.ReceiptItemEntity
import io.eksoda.core.database.entity.StoreEntity
import io.eksoda.engine.canonical.Address
import io.eksoda.engine.canonical.Branch
import io.eksoda.engine.canonical.CanonicalReceipt
import io.eksoda.engine.canonical.Line
import io.eksoda.engine.canonical.Party
import io.eksoda.engine.classification.LineType
import io.eksoda.engine.classification.classify
import io.eksoda.engine.merchants.FieldSize
import io.eksoda.engine.merchants.addressKey
import io.eksoda.engine.merchants.deriveDisplayName
import io.eksoda.engine.merchants.fit
import io.eksoda.engine.merchants.normalizeAddress
import io.eksoda.engine.merchants.refreshedDetails
import io.eksoda.engine.persist.documentNumber
import io.eksoda.engine.persist.isCancelled
import io.eksoda.engine.persist.paymentMethod
import io.eksoda.engine.persist.sourceData
import io.eksoda.engine.products.merchantCategorySlug
import io.eksoda.engine.text.fingerprint
import io.eksoda.engine.text.normalizeText
import io.eksoda.engine.validation.ValidationIssue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Clock
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** What persisting one receipt produced. */
data class Persisted(
    val receiptId: String,
    val outcome: String,
    val merchantName: String,
    val grossTotalMinor: Long,
)

/**
 * Canonical receipt → merchant, store, receipt and lines.
 * Must run inside the WriteCoordinator's transaction, so a failure leaves nothing behind.
 */
class Persister @Inject constructor(
    private val merchants: MerchantDao,
    private val matcher: ProductMatcher,
    private val receipts: ReceiptDao,
    private val clock: Clock,
) {
    suspend fun persist(
        canonical: CanonicalReceipt,
        issues: List<ValidationIssue>,
        zone: ZoneId,
    ): Persisted {
        val asOf = canonical.document.issuedAt.toInstant().toEpochMilli()
        val merchant = resolveMerchant(canonical.issuer, asOf)
        val store = resolveStore(merchant, canonical.branch, asOf)
        val grossMinor = Fixed.toLong(canonical.totals.gross, Fixed.MINOR)

        val receipt = receiptEntity(canonical, issues, merchant.id, store?.id, zone)
        val inserted = findExisting(canonical) == null && tryInsert(receipt, canonical)
        if (!inserted) {
            val existing = checkNotNull(findExisting(canonical))
            return Persisted(
                existing.id,
                OUTCOME_DUPLICATE,
                merchant.displayName,
                existing.grossTotalMinor,
            )
        }
        receipts.insertItems(canonical.lines.map { line -> item(receipt.id, merchant.id, line) })
        return Persisted(receipt.id, OUTCOME_CREATED, merchant.displayName, grossMinor)
    }

    /** Inserts the receipt; false when the unique keys (the final guard) find a copy. */
    private suspend fun tryInsert(receipt: ReceiptEntity, canonical: CanonicalReceipt): Boolean =
        try {
            receipts.insert(receipt)
            true
        } catch (exception: SQLiteConstraintException) {
            if (findExisting(canonical) == null) throw exception
            false
        }

    private suspend fun findExisting(canonical: CanonicalReceipt): ReceiptEntity? {
        val identity = canonical.identity
        val keys = listOf(identity.mark, identity.uid, identity.sourceKey)
        if (keys.all { it.isNullOrEmpty() }) return null
        return receipts.findExisting(
            mark = identity.mark?.ifEmpty { null },
            country = canonical.issuer.countryCode,
            vat = canonical.issuer.vatNumber,
            uid = identity.uid?.ifEmpty { null },
            sourceKey = identity.sourceKey?.ifEmpty { null },
        )
    }

    // --- merchants and stores (merchants/services.py) ---------------------------------------------

    private suspend fun resolveMerchant(issuer: Party, asOf: Long): MerchantEntity {
        val address = issuer.address ?: Address()
        val details = linkedMapOf(
            "legal_name" to fit(issuer.legalName.trim(), FieldSize.LEGAL_NAME),
            "activity_description" to issuer.activity.orEmpty().trim(),
            "tax_office" to fit(issuer.taxOffice.orEmpty().trim(), FieldSize.TAX_OFFICE),
            "gemi_number" to fit(issuer.gemiNumber.orEmpty().trim(), FieldSize.GEMI_NUMBER),
            "street" to fit(address.street.orEmpty().trim(), FieldSize.STREET),
            "street_number" to fit(address.number.orEmpty().trim(), FieldSize.STREET_NUMBER),
            "city" to fit(address.city.orEmpty().trim(), FieldSize.CITY),
            "postal_code" to fit(address.postalCode.orEmpty().trim(), FieldSize.POSTAL_CODE),
        )
        val now = clock.millis()
        val existing = merchants.find(issuer.countryCode, issuer.vatNumber)
        if (existing == null) {
            val created = MerchantEntity(
                id = UUID.randomUUID().toString(),
                countryCode = issuer.countryCode,
                vatNumber = issuer.vatNumber,
                legalName = details.getValue("legal_name"),
                displayName = deriveDisplayName(details.getValue("legal_name")),
                normalizedDisplayName = normalizeText(
                    deriveDisplayName(details.getValue("legal_name")),
                ),
                activityDescription = details.getValue("activity_description"),
                defaultCategoryId = defaultCategoryOf(details),
                taxOffice = details.getValue("tax_office"),
                gemiNumber = details.getValue("gemi_number"),
                street = details.getValue("street"),
                streetNumber = details.getValue("street_number"),
                city = details.getValue("city"),
                postalCode = details.getValue("postal_code"),
                detailsAsOf = asOf,
                createdAt = now,
                updatedAt = now,
            )
            merchants.insert(created)
            return created
        }
        val (changed, moveAsOf) =
            refreshedDetails(
                existing.details(),
                existing.detailsAsOf?.toInstant(),
                details,
                asOf.toInstant(),
            )
        if (changed.isEmpty() && !moveAsOf) return existing
        var updated = existing.withDetails(changed).copy(
            detailsAsOf = if (moveAsOf) asOf else existing.detailsAsOf,
            updatedAt = now,
        )
        // A new legal name renames the merchant only while its display name is still the derived
        // one: a name the user set is kept.
        val previousDefault = deriveDisplayName(existing.legalName)
        val derivedName = !existing.displayNameSetByUser && existing.displayName == previousDefault
        if ("legal_name" in changed && derivedName) {
            val name = deriveDisplayName(updated.legalName)
            updated = updated.copy(displayName = name, normalizedDisplayName = normalizeText(name))
        }
        merchants.update(updated)
        return updated
    }

    /**
     * A new merchant's default category: a fuel station, café, pharmacy... defaults to its kind
     * (AutoCategory.kt); general stores get none. The user can change or clear it.
     */
    private fun defaultCategoryOf(details: Map<String, String>): String? = merchantCategorySlug(
        details.getValue("activity_description"),
        details.getValue("legal_name"),
    )
        ?.let(CategorySeed::categoryId)

    /** By branch number; else by normalized address; without either, no store. */
    private suspend fun resolveStore(
        merchant: MerchantEntity,
        branch: Branch?,
        asOf: Long,
    ): StoreEntity? {
        if (branch == null) return null
        val address = branch.address ?: Address()
        val street = listOfNotNull(address.street, address.number)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        val details = linkedMapOf(
            "street" to fit(street.trim(), FieldSize.STREET),
            "city" to fit(address.city.orEmpty().trim(), FieldSize.CITY),
            "postal_code" to fit(address.postalCode.orEmpty().trim(), FieldSize.POSTAL_CODE),
        )
        val normalized = normalizeAddress(address)
        val number = branch.number?.ifEmpty { null }
        val key = when {
            number != null -> "branch:$number"
            normalized.isNotEmpty() -> "addr:$normalized"
            else -> return null
        }
        val existing = merchants.findStore(merchant.id, key)
        val now = clock.millis()
        if (existing == null) return createStore(merchant, number, details, normalized, asOf)
        if (number == null) return existing // address-keyed stores are not refreshed
        val current = mapOf(
            "street" to existing.street,
            "city" to existing.city,
            "postal_code" to existing.postalCode,
        )
        val (changed, moveAsOf) =
            refreshedDetails(current, existing.detailsAsOf?.toInstant(), details, asOf.toInstant())
        if (changed.isEmpty() && !moveAsOf) return existing
        var updated = existing.copy(
            street = changed["street"] ?: existing.street,
            city = changed["city"] ?: existing.city,
            postalCode = changed["postal_code"] ?: existing.postalCode,
            detailsAsOf = if (moveAsOf) asOf else existing.detailsAsOf,
            updatedAt = now,
        )
        // The address moved: keep its key in step.
        if (changed.isNotEmpty()) {
            val key = addressKey(updated.street, updated.postalCode, updated.city)
            updated = updated.copy(normalizedAddress = key)
        }
        merchants.updateStore(updated)
        return updated
    }

    private suspend fun createStore(
        merchant: MerchantEntity,
        number: String?,
        details: Map<String, String>,
        normalized: String,
        asOf: Long,
    ): StoreEntity {
        val now = clock.millis()
        val key = number?.let { "branch:$it" } ?: "addr:$normalized"
        val created = StoreEntity(
            id = UUID.randomUUID().toString(),
            merchantId = merchant.id,
            branchNumber = number,
            street = details.getValue("street"),
            city = details.getValue("city"),
            postalCode = details.getValue("postal_code"),
            countryCode = merchant.countryCode,
            normalizedAddress = normalized,
            storeKey = key,
            detailsAsOf = asOf,
            createdAt = now,
            updatedAt = now,
        )
        merchants.insertStore(created)
        return created
    }

    // --- receipt values and lines ----------------------------------------------------------------

    private fun receiptEntity(
        canonical: CanonicalReceipt,
        issues: List<ValidationIssue>,
        merchantId: String,
        storeId: String?,
        zone: ZoneId,
    ): ReceiptEntity {
        val identity = canonical.identity
        val document = canonical.document
        val totals = canonical.totals
        val issued = document.issuedAt.toInstant()
        val now = clock.millis()
        return ReceiptEntity(
            id = UUID.randomUUID().toString(),
            merchantId = merchantId,
            storeId = storeId,
            providerCode = canonical.providerCode,
            parserVersion = canonical.parserVersion,
            issuerCountry = canonical.issuer.countryCode,
            issuerVat = canonical.issuer.vatNumber,
            uid = identity.uid?.ifEmpty { null },
            mark = identity.mark?.ifEmpty { null },
            cancellationMark = identity.cancellationMark?.ifEmpty { null },
            sourceKey = identity.sourceKey?.ifEmpty { null },
            documentKind = document.kind.code,
            documentTypeCode = document.typeCode?.ifEmpty { null },
            documentNumber = documentNumber(document),
            issuedAt = issued.toEpochMilli(),
            issuedLocalDate = issued.atZone(zone).toLocalDate().toString(),
            currency = document.currency,
            grossTotalMinor = Fixed.toLong(totals.gross, Fixed.MINOR),
            netTotalMinor = Fixed.toLongOrNull(totals.net, Fixed.MINOR),
            vatTotalMinor = Fixed.toLongOrNull(totals.vat, Fixed.MINOR),
            discountTotalMinor = Fixed.toLongOrNull(totals.discount, Fixed.MINOR),
            paymentMethod = paymentMethod(canonical.payments),
            status = if (isCancelled(identity)) "cancelled" else "active",
            validationWarnings = JsonArray(issues.map(::issueJson)).toString(),
            sourceUrl = canonical.sourceUrl,
            sourceUrlFingerprint = fingerprint(canonical.sourceUrl),
            sourceData = sourceData(canonical).toString(),
            createdAt = now,
            updatedAt = now,
        )
    }

    private suspend fun item(receiptId: String, merchantId: String, line: Line): ReceiptItemEntity {
        val normalized = normalizeText(line.description)
        val lineType = classify(line, normalized)
        val code = line.merchantProductCode?.ifEmpty { null }
        val product = if (lineType == LineType.PRODUCT) {
            matcher.match(merchantId, line.description, normalized, code, line.unit)
        } else {
            null
        }
        val now = clock.millis()
        return ReceiptItemEntity(
            id = UUID.randomUUID().toString(),
            receiptId = receiptId,
            lineNumber = line.lineNumber,
            lineType = lineType.code,
            description = line.description,
            normalizedDescription = normalized,
            merchantProductCode = code,
            quantityE4 = Fixed.toLongOrNull(line.quantity, Fixed.E4),
            unit = line.unit?.code,
            unitPriceE4 = Fixed.toLongOrNull(line.unitPrice, Fixed.E4),
            grossAmountMinor = Fixed.toLong(line.gross, Fixed.MINOR),
            netAmountMinor = Fixed.toLongOrNull(line.net, Fixed.MINOR),
            vatRateE2 = Fixed.toLongOrNull(line.vatRate, Fixed.E2),
            vatAmountMinor = Fixed.toLongOrNull(line.vat, Fixed.MINOR),
            discountAmountMinor = Fixed.toLongOrNull(line.discount, Fixed.MINOR),
            productId = product?.id,
            matchStatus = when {
                lineType != LineType.PRODUCT -> "not_a_product"
                product != null -> "matched"
                else -> "needs_review"
            },
            normalizedQuantityE6 = null,
            normalizedUnit = null,
            normalizedUnitPriceE4 = null,
            createdAt = now,
            updatedAt = now,
        ).withPrices(product)
    }

    private fun issueJson(issue: ValidationIssue) = buildJsonObject {
        put("code", JsonPrimitive(issue.code))
        put("message", JsonPrimitive(issue.message))
        put("line_number", issue.lineNumber?.let(::JsonPrimitive) ?: JsonNull)
        put("expected", issue.expected?.let(::JsonPrimitive) ?: JsonNull)
        put("actual", issue.actual?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun Long.toInstant() = java.time.Instant.ofEpochMilli(this)

    private fun MerchantEntity.details() = mapOf(
        "legal_name" to legalName,
        "activity_description" to activityDescription,
        "tax_office" to taxOffice,
        "gemi_number" to gemiNumber,
        "street" to street,
        "street_number" to streetNumber,
        "city" to city,
        "postal_code" to postalCode,
    )

    private fun MerchantEntity.withDetails(changed: Map<String, String>) = copy(
        legalName = changed["legal_name"] ?: legalName,
        activityDescription = changed["activity_description"] ?: activityDescription,
        taxOffice = changed["tax_office"] ?: taxOffice,
        gemiNumber = changed["gemi_number"] ?: gemiNumber,
        street = changed["street"] ?: street,
        streetNumber = changed["street_number"] ?: streetNumber,
        city = changed["city"] ?: city,
        postalCode = changed["postal_code"] ?: postalCode,
    )

    companion object {
        const val OUTCOME_CREATED = "created"
        const val OUTCOME_DUPLICATE = "duplicate"
    }
}
