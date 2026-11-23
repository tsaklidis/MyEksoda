package io.eksoda.engine.sync

/** The schema version the sync tests use; any value works, as long as both sides have it. */
internal const val TEST_SCHEMA = 7

/** Rows with only the columns the merge reads: ids, keys, foreign keys, versions. */
internal object SyncRows {
    fun snapshot(vararg tables: Pair<String, List<Row>>) = Snapshot(TEST_SCHEMA, tables.toMap())

    fun household(id: String, name: String, updatedAt: Long = 1) =
        mapOf("id" to id, "name" to name, "updated_at" to updatedAt)

    fun category(id: String, slug: String, updatedAt: Long = 1) =
        mapOf("id" to id, "slug" to slug, "name" to slug, "updated_at" to updatedAt)

    fun budget(
        id: String,
        category: String?,
        amount: Long?,
        updatedAt: Long = 1,
        month: String? = null,
    ) = mapOf(
        "id" to id,
        "category_id" to category,
        "month" to month,
        "amount_minor" to amount,
        "updated_at" to updatedAt,
    )

    fun merchant(
        id: String,
        vat: String = "094014201",
        name: String = "Shop",
        category: String? = null,
        updatedAt: Long = 1,
    ) = mapOf(
        "id" to id,
        "country_code" to "GR",
        "vat_number" to vat,
        "display_name" to name,
        "default_category_id" to category,
        "updated_at" to updatedAt,
    )

    fun product(id: String, name: String, updatedAt: Long = 1) =
        mapOf("id" to id, "name" to name, "category_id" to null, "updated_at" to updatedAt)

    fun mapping(id: String, merchant: String, key: String, product: String, updatedAt: Long = 1) =
        mapOf(
            "id" to id,
            "merchant_id" to merchant,
            "mapping_key" to key,
            "product_id" to product,
            "updated_at" to updatedAt,
        )

    fun receipt(
        id: String,
        mark: String? = null,
        merchant: String = "m1",
        note: String = "",
        url: String? = null,
        updatedAt: Long = 1,
    ) = mapOf(
        "id" to id,
        "merchant_id" to merchant,
        "store_id" to null,
        "mark" to mark,
        "issuer_country" to "GR",
        "issuer_vat" to "094014201",
        "uid" to null,
        "source_key" to null,
        "user_note" to note,
        "source_url" to url,
        "updated_at" to updatedAt,
    )

    fun item(
        id: String,
        receipt: String,
        line: Long,
        product: String? = null,
        updatedAt: Long = 1,
    ) = mapOf(
        "id" to id,
        "receipt_id" to receipt,
        "line_number" to line,
        "product_id" to product,
        "updated_at" to updatedAt,
    )

    fun tombstone(
        entity: String,
        id: String,
        deletedAt: Long,
        naturalKey: String? = null,
        replacedBy: String? = null,
    ) = mapOf(
        "entity" to entity,
        "id" to id,
        "deleted_at" to deletedAt,
        "natural_key" to naturalKey,
        "replaced_by" to replacedBy,
    )
}

internal fun MergeResult.ids(table: String) = tables.getValue(table).map { it["id"] }

internal fun MergeResult.row(table: String, id: String) =
    tables.getValue(table).single { it["id"] == id }

internal fun MergeResult.counts() = Triple(inserted, updated, deleted)

/** Without the local columns, which only one phone has. */
internal fun MergeResult.synced() = tables.mapValues { (table, rows) ->
    val local = SyncSchema.TABLES.firstOrNull { it.name == table }?.localColumns.orEmpty()
    rows.map { it - local }
}
