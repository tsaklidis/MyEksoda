package io.eksoda.core.data.backup

import io.eksoda.core.data.repository.BackupError
import io.eksoda.core.data.repository.BackupException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.text.Normalizer
import java.time.Instant
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The encrypted backup file (docs/android-standalone.md §12.1):
 *
 * ```
 * MAGIC "EKSODABK" | header length (uint32, big-endian) | header (UTF-8 JSON) | ciphertext
 * ```
 *
 * The key is derived from the passphrase with PBKDF2-HMAC-SHA256 and a random salt; the
 * ciphertext is AES-256-GCM with a 128-bit tag over the archive, and the header bytes are the
 * additional authenticated data, so changing any byte of the file makes decryption fail. The
 * passphrase is never stored: without it the file cannot be read.
 */
internal object BackupFile {
    const val FORMAT = 1
    const val ITERATIONS = 600_000

    private val MAGIC = "EKSODABK".toByteArray(Charsets.US_ASCII)
    private const val LENGTH_BYTES = 4
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val MAX_HEADER_BYTES = 64 * 1024

    /** Refuses files that would keep the phone busy for minutes before failing. */
    private const val MAX_ITERATIONS = 10_000_000
    private const val KDF = "PBKDF2-HMAC-SHA256"
    private const val CIPHER = "AES-256-GCM"

    class Header(val format: Int, val appVersion: String, val createdAt: Instant)

    class Decrypted(val header: Header, val plaintext: ByteArray)

    fun encrypt(
        plaintext: ByteArray,
        passphrase: String,
        appVersion: String,
        createdAt: Instant,
        iterations: Int = ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = buildJsonObject {
            put("format", FORMAT)
            put("app_version", appVersion)
            put("created_at", createdAt.truncatedTo(ChronoUnit.SECONDS).toString())
            putJsonObject("kdf") {
                put("name", KDF)
                put("iterations", iterations)
                put("salt", Base64.getEncoder().encodeToString(salt))
            }
            putJsonObject("cipher") {
                put("name", CIPHER)
                put("nonce", Base64.getEncoder().encodeToString(nonce))
            }
        }.toString().toByteArray(Charsets.UTF_8)
        val cipher = cipher(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt, iterations), nonce)
        cipher.updateAAD(header)
        val ciphertext = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(MAGIC.size + LENGTH_BYTES + header.size + ciphertext.size)
            .put(MAGIC)
            .putInt(header.size)
            .put(header)
            .put(ciphertext)
            .array()
    }

    /**
     * Throws [BackupException]: [BackupError.NOT_A_BACKUP] when the file does not start like a
     * backup, [BackupError.NEWER_VERSION] for a format this version cannot read,
     * [BackupError.DAMAGED] for an unreadable header and [BackupError.WRONG_PASSPHRASE] when the
     * tag does not verify (a wrong passphrase and a changed file look the same).
     */
    fun decrypt(file: ByteArray, passphrase: String): Decrypted {
        val buffer = ByteBuffer.wrap(file)
        if (file.size < MAGIC.size + LENGTH_BYTES) fail(BackupError.NOT_A_BACKUP)
        val magic = ByteArray(MAGIC.size).also { buffer.get(it) }
        if (!magic.contentEquals(MAGIC)) fail(BackupError.NOT_A_BACKUP)
        val headerLength = buffer.int
        if (headerLength !in 1..minOf(MAX_HEADER_BYTES, buffer.remaining())) {
            fail(BackupError.DAMAGED)
        }
        val headerBytes = ByteArray(headerLength).also { buffer.get(it) }
        val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val parsed = parseHeader(headerBytes)
        val cipher = cipher(
            Cipher.DECRYPT_MODE,
            deriveKey(passphrase, parsed.salt, parsed.iterations),
            parsed.nonce,
        )
        cipher.updateAAD(headerBytes)
        val plaintext = try {
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            throw BackupException(BackupError.WRONG_PASSPHRASE, e)
        }
        return Decrypted(parsed.header, plaintext)
    }

    private class ParsedHeader(
        val header: Header,
        val iterations: Int,
        val salt: ByteArray,
        val nonce: ByteArray,
    )

    private fun parseHeader(bytes: ByteArray): ParsedHeader = try {
        val json = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        // A newer format may have other fields: check the version before reading them.
        val format = json.getValue("format").jsonPrimitive.int
        if (format > FORMAT) fail(BackupError.NEWER_VERSION)
        readHeader(json, format)
    } catch (e: IllegalArgumentException) {
        // Also JSON syntax errors, wrong types and bad Base64.
        throw BackupException(BackupError.DAMAGED, e)
    } catch (e: NoSuchElementException) {
        throw BackupException(BackupError.DAMAGED, e)
    } catch (e: DateTimeParseException) {
        throw BackupException(BackupError.DAMAGED, e)
    }

    private fun readHeader(json: JsonObject, format: Int): ParsedHeader {
        val kdf = json.getValue("kdf").jsonObject
        val cipher = json.getValue("cipher").jsonObject
        val parsed = ParsedHeader(
            header = Header(
                format = format,
                appVersion = json["app_version"]?.jsonPrimitive?.content.orEmpty(),
                createdAt = Instant.parse(json.getValue("created_at").jsonPrimitive.content),
            ),
            iterations = kdf.getValue("iterations").jsonPrimitive.int,
            salt = Base64.getDecoder().decode(kdf.getValue("salt").jsonPrimitive.content),
            nonce = Base64.getDecoder().decode(cipher.getValue("nonce").jsonPrimitive.content),
        )
        val supported = format >= 1 &&
            kdf.getValue("name").jsonPrimitive.content == KDF &&
            cipher.getValue("name").jsonPrimitive.content == CIPHER &&
            parsed.iterations in 1..MAX_ITERATIONS &&
            parsed.salt.size == SALT_BYTES &&
            parsed.nonce.size == NONCE_BYTES
        if (!supported) fail(BackupError.DAMAGED)
        return parsed
    }

    /**
     * The passphrase is NFC-normalized first, so the same Greek text typed with composed or
     * decomposed accents gives the same key. PBKDF2 encodes it as UTF-8.
     */
    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(
            Normalizer.normalize(passphrase, Normalizer.Form.NFC).toCharArray(),
            salt,
            iterations,
            KEY_BITS,
        )
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    private fun cipher(mode: Int, key: SecretKeySpec, nonce: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
        }

    private fun fail(error: BackupError): Nothing = throw BackupException(error)
}
