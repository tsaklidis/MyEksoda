package io.eksoda.core.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The SQLCipher raw-key form `x'<64 hex digits>'` of the 32-byte database key.
 *
 * SQLCipher uses a raw key as is. Given as a passphrase, the same bytes would go through
 * PBKDF2 with 256,000 iterations on every connection it opens (several at app start), which
 * costs hundreds of milliseconds on a mid-range phone. The key is already 32 random bytes, so
 * stretching it adds nothing.
 */
fun sqlCipherRawKey(key: ByteArray): ByteArray {
    require(key.size == RAW_KEY_BYTES) { "the database key must be $RAW_KEY_BYTES bytes" }
    return "x'${key.toHexString()}'".toByteArray(Charsets.US_ASCII)
}

private const val RAW_KEY_BYTES = 32

/** The database key exists but cannot be unwrapped (e.g. the Keystore was reset by a device restore). */
class DatabaseKeyUnavailable(cause: Throwable) :
    Exception("the database key cannot be unwrapped", cause)

/**
 * The only code that touches the database key.
 *
 * On first use it creates 32 random bytes as the SQLCipher passphrase and stores them wrapped
 * with an AES-256-GCM key in the Android Keystore (non-exportable, no user authentication, so
 * background imports keep working). The wrapped key and its nonce live in `noBackupFilesDir`.
 * If unwrapping fails, [passphrase] throws [DatabaseKeyUnavailable]; it never creates a new key
 * over an existing database (the UI offers restore or start over).
 */
@Singleton
class KeyManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val wrappedFile: File get() = File(context.noBackupFilesDir, WRAPPED_KEY_FILE)

    @Synchronized
    fun passphrase(databaseExists: Boolean): ByteArray {
        val file = wrappedFile
        if (!file.exists()) {
            check(!databaseExists) { "the database exists but its key file is missing" }
            return createAndStore(file)
        }
        return try {
            val bytes = file.readBytes()
            val nonce = bytes.copyOfRange(0, NONCE_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                existingWrappingKey(),
                GCMParameterSpec(TAG_BITS, nonce),
            )
            cipher.doFinal(bytes, NONCE_BYTES, bytes.size - NONCE_BYTES)
        } catch (exception: java.security.GeneralSecurityException) {
            throw DatabaseKeyUnavailable(exception)
        } catch (exception: IllegalStateException) {
            throw DatabaseKeyUnavailable(exception)
        }
    }

    /** "Delete all data" removes the wrapped key with the database file. */
    @Synchronized
    fun deleteKey() {
        wrappedFile.delete()
        keyStore().deleteEntry(KEY_ALIAS)
    }

    private fun createAndStore(file: File): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, newWrappingKey())
        val wrapped = cipher.iv + cipher.doFinal(passphrase)
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeBytes(wrapped)
        check(temporary.renameTo(file)) { "could not store the database key" }
        return passphrase
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingWrappingKey(): SecretKey =
        (keyStore().getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: error("the wrapping key is missing from the Keystore")

    private fun newWrappingKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_BITS)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "eksoda.database.wrapping"
        const val WRAPPED_KEY_FILE = "database.key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PASSPHRASE_BYTES = 32
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val AES_KEY_BITS = 256
    }
}
