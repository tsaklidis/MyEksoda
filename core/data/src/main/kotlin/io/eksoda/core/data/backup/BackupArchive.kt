package io.eksoda.core.data.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.eksoda.core.data.repository.BackupError
import io.eksoda.core.data.repository.BackupException
import io.eksoda.core.data.util.Fixed
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The plaintext inside a backup file: a ZIP with `manifest.json` and one
 * `<table>.ndjson` per table, one JSON object per row.
 *
 * The export is logical and does not depend on the Room schema: rows are read and written by
 * column name (`PRAGMA table_info`), columns missing from an older file get their defaults and
 * unknown keys are ignored. Decimal columns are written as decimal strings under the name
 * without the scale suffix (`gross_total_minor` 3210 → `"gross_total": "32.10"`), so the file
 * can be read without knowing the scales.
 */
internal object BackupArchive {
    const val FORMAT = 1
    private const val MANIFEST = "manifest.json"
    private const val EXTENSION = ".ndjson"

    /**
     * Every table of the schema, parents first: rows are inserted in this order and deleted in
     * reverse, which `RESTRICT` foreign keys need. `BackupArchiveTest` checks that it lists every
     * table in the database.
     */
    val TABLES = listOf(
        "household",
        "category",
        "merchant",
        "store",
        "product",
        "merchant_product_mapping",
        "receipt",
        "receipt_item",
        "import_job",
        "tombstone",
    )

    /** Fixed-point column suffixes and their scales (`Fixed`). */
    private val SCALES = listOf(
        "_minor" to Fixed.MINOR,
        "_e2" to Fixed.E2,
        "_e4" to Fixed.E4,
        "_e6" to Fixed.E6,
    )

    class Manifest(
        val format: Int,
        val schema: Int,
        val counts: Map<String, Int>,
        val householdName: String?,
        val firstIssued: LocalDate?,
        val lastIssued: LocalDate?,
    )

    class Written(val bytes: ByteArray, val manifest: Manifest)

    /** Reads every table. Run it inside a transaction, so the copy is consistent. */
    fun write(db: SupportSQLiteDatabase): Written {
        val counts = TABLES.associateWith { table ->
            db.query("SELECT COUNT(*) FROM `$table`").use {
                it.moveToFirst()
                it.getInt(0)
            }
        }
        val household = db.query("SELECT name FROM household LIMIT 1").use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        val (first, last) = db.query(
            "SELECT MIN(issued_local_date), MAX(issued_local_date) FROM receipt",
        ).use {
            it.moveToFirst()
            it.getStringOrNull(0) to it.getStringOrNull(1)
        }
        val manifest = buildJsonObject {
            put("format", FORMAT)
            put("schema", db.version)
            putJsonObject("counts") { counts.forEach { (table, count) -> put(table, count) } }
            putJsonObject("household") { put("name", household) }
            put("first_issued", first)
            put("last_issued", last)
        }
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            val writer = zip.bufferedWriter(Charsets.UTF_8)
            zip.putNextEntry(ZipEntry(MANIFEST))
            writer.write(manifest.toString())
            writer.flush()
            zip.closeEntry()
            TABLES.forEach { table ->
                val columns = columns(db, table)
                zip.putNextEntry(ZipEntry(table + EXTENSION))
                db.query("SELECT * FROM `$table`").use { cursor ->
                    while (cursor.moveToNext()) {
                        writer.write(cursor.row(columns).toString())
                        writer.write("\n")
                    }
                }
                writer.flush()
                zip.closeEntry()
            }
        }
        return Written(bytes.toByteArray(), parseManifest(manifest))
    }

    /**
     * Reads the manifest and checks that every table has the number of rows it lists. Throws
     * [BackupException] with [BackupError.DAMAGED] otherwise.
     */
    fun inspect(archive: ByteArray): Manifest {
        var manifest: Manifest? = null
        val lines = mutableMapOf<String, Int>()
        entries(archive) { name, zip ->
            val reader = zip.bufferedReader(Charsets.UTF_8)
            when {
                name == MANIFEST -> manifest = parseManifest(readJson(reader.readText()))

                name.endsWith(EXTENSION) ->
                    lines[name.removeSuffix(EXTENSION)] =
                        reader.lineSequence().count { it.isNotBlank() }
            }
        }
        val found = manifest ?: fail()
        if (found.format > FORMAT) throw BackupException(BackupError.NEWER_VERSION)
        val consistent = TABLES.all { (found.counts[it] ?: 0) == (lines[it] ?: 0) }
        if (!consistent) fail()
        return found
    }

    /**
     * Replaces every row with the archive's. Run it inside one transaction: any error (a
     * constraint, a bad value, a foreign key without its row) throws and rolls everything back.
     */
    fun restore(db: SupportSQLiteDatabase, archive: ByteArray) {
        // Rows are checked against their parents at commit, whatever the order in the file.
        db.execSQL("PRAGMA defer_foreign_keys = ON")
        TABLES.asReversed().forEach { db.execSQL("DELETE FROM `$it`") }
        val columns = TABLES.associateWith { columns(db, it) }
        entries(archive) { name, zip ->
            val table = name.removeSuffix(EXTENSION)
            val tableColumns = columns[table]
            if (!name.endsWith(EXTENSION) || tableColumns == null) return@entries
            zip.bufferedReader(Charsets.UTF_8).lineSequence()
                .filter { it.isNotBlank() }
                .forEach { line ->
                    db.insert(table, SQLiteDatabase.CONFLICT_ABORT, values(tableColumns, line))
                }
        }
        db.query("PRAGMA foreign_key_check").use { if (it.moveToFirst()) fail() }
    }

    private class Column(val name: String, val integer: Boolean, val scale: Int?) {
        /** The JSON key: decimal columns lose their scale suffix. */
        val key: String = SCALES.firstOrNull { name.endsWith(it.first) && scale != null }
            ?.let { name.removeSuffix(it.first) } ?: name
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): List<Column> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    val integer = cursor.getString(typeIndex).equals("INTEGER", ignoreCase = true)
                    val scale = if (integer) {
                        SCALES.firstOrNull {
                            name.endsWith(it.first)
                        }
                    } else {
                        null
                    }
                    add(Column(name, integer, scale?.second))
                }
            }
        }

    private fun Cursor.row(columns: List<Column>): JsonObject = buildJsonObject {
        columns.forEach { column ->
            val index = getColumnIndexOrThrow(column.name)
            when {
                isNull(index) -> put(column.key, JsonNull)

                column.scale != null ->
                    put(column.key, Fixed.toDecimal(getLong(index), column.scale).toPlainString())

                column.integer -> put(column.key, getLong(index))

                else -> put(column.key, getString(index))
            }
        }
    }

    private fun values(columns: List<Column>, line: String): ContentValues {
        val row = readJson(line)
        return ContentValues().apply {
            columns.forEach { column ->
                val element = row[column.key] ?: return@forEach
                if (element is JsonNull) {
                    putNull(column.name)
                    return@forEach
                }
                val value = element as? JsonPrimitive ?: fail()
                when {
                    column.scale != null -> put(column.name, fixed(value.content, column.scale))
                    column.integer -> put(column.name, integer(value))
                    else -> put(column.name, value.content)
                }
            }
        }
    }

    /** Exact: a value with more decimals than the column holds means the file is wrong. */
    private fun fixed(text: String, scale: Int): Long = try {
        BigDecimal(text).setScale(scale).unscaledValue().longValueExact()
    } catch (e: ArithmeticException) {
        throw BackupException(BackupError.DAMAGED, e)
    } catch (e: NumberFormatException) {
        throw BackupException(BackupError.DAMAGED, e)
    }

    private fun integer(value: JsonPrimitive): Long =
        value.longOrNull ?: value.booleanOrNull?.let { if (it) 1L else 0L } ?: fail()

    private fun parseManifest(json: JsonObject): Manifest = try {
        Manifest(
            format = json.getValue("format").jsonPrimitive.int,
            schema = json.getValue("schema").jsonPrimitive.int,
            counts = json.getValue("counts").jsonObject.mapValues { it.value.jsonPrimitive.int },
            householdName = json["household"]?.jsonObject?.get("name")?.jsonPrimitive
                ?.contentOrNull,
            firstIssued = json.date("first_issued"),
            lastIssued = json.date("last_issued"),
        )
    } catch (e: IllegalArgumentException) {
        throw BackupException(BackupError.DAMAGED, e)
    } catch (e: NoSuchElementException) {
        throw BackupException(BackupError.DAMAGED, e)
    } catch (e: DateTimeParseException) {
        throw BackupException(BackupError.DAMAGED, e)
    }

    private fun JsonObject.date(key: String): LocalDate? =
        this[key]?.jsonPrimitive?.contentOrNull?.let(LocalDate::parse)

    private fun readJson(text: String): JsonObject = try {
        Json.parseToJsonElement(text).jsonObject
    } catch (e: IllegalArgumentException) {
        throw BackupException(BackupError.DAMAGED, e)
    }

    private fun entries(archive: ByteArray, block: (String, ZipInputStream) -> Unit) {
        ZipInputStream(archive.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry -> block(entry.name, zip) }
        }
    }

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun fail(): Nothing = throw BackupException(BackupError.DAMAGED)
}
