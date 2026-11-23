package io.eksoda.engine.sync

/**
 * One row of a table by column name, as SQLite stores it: every value is a [String], a [Long]
 * or null (booleans are 0 and 1, decimals fixed-point Longs).
 */
public typealias Row = Map<String, Any?>

/** What happens to a row when the row it points to goes (the database's foreign keys). */
public enum class OnDelete { CASCADE, SET_NULL, RESTRICT }

public class ForeignKeySpec(
    public val column: String,
    public val parent: String,
    public val onDelete: OnDelete,
)

/** How household sync treats one table (docs/design/household-sync.md, "Merge rules"). */
public class TableSpec internal constructor(
    public val name: String,
    /**
     * The table's unique keys. Two rows with the same values in every column of one key are the
     * same thing, imported on two phones under different ids. A key without columns matches
     * every row: the table holds one row (the household).
     */
    public val keys: List<List<String>> = emptyList(),
    public val foreignKeys: List<ForeignKeySpec> = emptyList(),
    /** Columns that never leave this phone: the receipt URL, a credential. */
    public val localColumns: Set<String> = emptySet(),
    /** An id that follows from the row (a budget's is its category's), after remapping. */
    internal val derivedId: ((Row) -> String)? = null,
)

/** The tables household sync merges, and how. */
public object SyncSchema {
    public const val ID: String = "id"

    /** Every synced row has it; the later one wins. */
    public const val VERSION: String = "updated_at"

    /** Deleted rows: `entity`, `id`, `deleted_at`, `natural_key`, `replaced_by`. */
    public const val TOMBSTONE: String = "tombstone"

    public const val RECEIPT: String = "receipt"
    public const val PRODUCT: String = "product"
    internal const val MERCHANT = "merchant"
    internal const val MAPPING = "merchant_product_mapping"

    /** The id of the budget for all spending; a category's budget has the category's id. */
    private const val TOTAL_BUDGET = "total"
    private const val KEY_SEPARATOR = "\u001f"

    /** Parents first, like the backup's table list; the tombstones are separate. */
    public val TABLES: List<TableSpec> = listOf(
        TableSpec("household", keys = listOf(emptyList())),
        TableSpec("category", keys = listOf(listOf("slug"))),
        TableSpec(
            "budget",
            foreignKeys = listOf(ForeignKeySpec("category_id", "category", OnDelete.CASCADE)),
            derivedId = { row -> row["category_id"] as? String ?: TOTAL_BUDGET },
        ),
        TableSpec(
            MERCHANT,
            keys = listOf(listOf("country_code", "vat_number")),
            foreignKeys = listOf(
                ForeignKeySpec("default_category_id", "category", OnDelete.SET_NULL),
            ),
        ),
        TableSpec(
            "store",
            keys = listOf(listOf("merchant_id", "store_key")),
            foreignKeys = listOf(ForeignKeySpec("merchant_id", MERCHANT, OnDelete.RESTRICT)),
        ),
        TableSpec(
            PRODUCT,
            foreignKeys = listOf(ForeignKeySpec("category_id", "category", OnDelete.SET_NULL)),
        ),
        TableSpec(
            MAPPING,
            keys = listOf(listOf("merchant_id", "mapping_key")),
            foreignKeys = listOf(
                ForeignKeySpec("merchant_id", MERCHANT, OnDelete.RESTRICT),
                ForeignKeySpec("product_id", PRODUCT, OnDelete.CASCADE),
            ),
        ),
        TableSpec(
            RECEIPT,
            keys = listOf(
                listOf("mark"),
                listOf("issuer_country", "issuer_vat", "uid"),
                listOf("source_key"),
            ),
            foreignKeys = listOf(
                ForeignKeySpec("merchant_id", MERCHANT, OnDelete.RESTRICT),
                ForeignKeySpec("store_id", "store", OnDelete.SET_NULL),
                ForeignKeySpec("category_id", "category", OnDelete.SET_NULL),
            ),
            localColumns = setOf("source_url"),
        ),
        TableSpec(
            "receipt_item",
            keys = listOf(listOf("receipt_id", "line_number")),
            foreignKeys = listOf(
                ForeignKeySpec("receipt_id", RECEIPT, OnDelete.CASCADE),
                ForeignKeySpec("product_id", PRODUCT, OnDelete.RESTRICT),
            ),
        ),
    )

    private val byName = TABLES.associateBy { it.name }

    /**
     * What a tombstone stores besides the id, so a deletion also reaches the copy another phone
     * imported under its own id: the row's first unique key without foreign keys, or null.
     */
    public fun naturalKey(table: String, row: Row): String? =
        byName[table]?.let { naturalKeys(it, row).firstOrNull() }

    /** Every unique key of [row] that does not depend on other rows' ids. */
    internal fun naturalKeys(spec: TableSpec, row: Row): List<String> {
        val foreign = spec.foreignKeys.map { it.column }.toSet()
        return spec.keys.mapIndexedNotNull { index, key ->
            if (key.isEmpty() || key.any { it in foreign }) null else keyString(index, key, row)
        }
    }

    /** "index:value␟value", or null when a column of the key is null (SQLite: never equal). */
    internal fun keyString(index: Int, key: List<String>, row: Row): String? {
        val values = key.map { row[it] ?: return null }
        return "$index:" + values.joinToString(KEY_SEPARATOR)
    }
}

/** A sync that cannot go on; [code] is shown through the UI's error strings. */
public class SyncException(public val code: String, cause: Throwable? = null) :
    Exception(code, cause) {
    public companion object {
        /** The two phones run app versions with different database schemas. */
        public const val SCHEMA_MISMATCH: String = "schema_mismatch"

        /** What arrived is not a snapshot this version can read. */
        public const val DAMAGED: String = "damaged"

        /** The merged rows could not be stored; the transaction rolled back. */
        public const val MERGE_FAILED: String = "merge_failed"

        /** The other phone does not hold this household's key. */
        public const val NOT_PAIRED: String = "not_paired"

        /** The connection broke off halfway. */
        public const val INTERRUPTED: String = "interrupted"

        /** The other phone was syncing with another one; nothing changed, try again. */
        public const val BUSY: String = "busy"

        /** Something this version did not expect; nothing changed. */
        public const val INTERNAL: String = "internal"
    }
}
