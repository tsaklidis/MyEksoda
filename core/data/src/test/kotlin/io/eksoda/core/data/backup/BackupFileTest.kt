package io.eksoda.core.data.backup

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.eksoda.core.data.repository.BackupError
import io.eksoda.core.data.repository.BackupException
import org.junit.Test
import java.time.Instant

class BackupFileTest {
    private val plaintext = "zip bytes, any content".toByteArray()
    private val createdAt = Instant.parse("2026-11-17T10:00:00.123Z")

    private fun encrypt(passphrase: String = PASSPHRASE) =
        BackupFile.encrypt(plaintext, passphrase, "1.0.0 (1)", createdAt, iterations = ITERATIONS)

    private fun assertError(error: BackupError, block: () -> Unit) {
        assertFailure(block)
            .isInstanceOf(BackupException::class)
            .prop(BackupException::error)
            .isEqualTo(error)
    }

    @Test
    fun `round trip with the same passphrase`() {
        val decrypted = BackupFile.decrypt(encrypt(), PASSPHRASE)
        assertThat(decrypted.plaintext.toList()).isEqualTo(plaintext.toList())
        assertThat(decrypted.header.createdAt).isEqualTo(Instant.parse("2026-11-17T10:00:00Z"))
        assertThat(decrypted.header.appVersion).isEqualTo("1.0.0 (1)")
    }

    @Test
    fun `composed and decomposed accents give the same key`() {
        val composed = "καλημέρα κόσμε 2026"
        val decomposed = java.text.Normalizer.normalize(composed, java.text.Normalizer.Form.NFD)
        val decrypted = BackupFile.decrypt(encrypt(composed), decomposed)
        assertThat(decrypted.plaintext.toList()).isEqualTo(plaintext.toList())
    }

    @Test
    fun `a wrong passphrase is refused`() {
        assertError(BackupError.WRONG_PASSPHRASE) {
            BackupFile.decrypt(encrypt(), "wrong passphrase!")
        }
    }

    @Test
    fun `a changed ciphertext byte is refused`() {
        val file = encrypt()
        file[file.lastIndex] = (file.last().toInt() xor 1).toByte()
        assertError(BackupError.WRONG_PASSPHRASE) { BackupFile.decrypt(file, PASSPHRASE) }
    }

    @Test
    fun `a changed header byte is refused, the header is authenticated`() {
        val file = encrypt()
        val text = file.toString(Charsets.ISO_8859_1)
        val at = text.indexOf("1.0.0")
        file[at] = '2'.code.toByte()
        assertError(BackupError.WRONG_PASSPHRASE) { BackupFile.decrypt(file, PASSPHRASE) }
    }

    @Test
    fun `other files are not backups`() {
        assertError(BackupError.NOT_A_BACKUP) {
            BackupFile.decrypt("PK\u0003\u0004 a zip file".toByteArray(), PASSPHRASE)
        }
        assertError(BackupError.NOT_A_BACKUP) { BackupFile.decrypt(ByteArray(3), PASSPHRASE) }
    }

    @Test
    fun `a newer format is reported as such`() {
        val file = encrypt()
        val text = file.toString(Charsets.ISO_8859_1)
        val at = text.indexOf("\"format\":1") + "\"format\":".length
        file[at] = '9'.code.toByte()
        assertError(BackupError.NEWER_VERSION) { BackupFile.decrypt(file, PASSPHRASE) }
    }

    @Test
    fun `a truncated header is damaged`() {
        // The magic, the length and a few header bytes: the length points past the end.
        val file = encrypt().copyOf(21)
        assertError(BackupError.DAMAGED) { BackupFile.decrypt(file, PASSPHRASE) }
    }

    private companion object {
        const val PASSPHRASE = "correct horse battery"

        /** Fast for tests; the file records the count, so decrypt follows it. */
        const val ITERATIONS = 1_000
    }
}
