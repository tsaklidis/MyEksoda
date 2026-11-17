package io.eksoda.feature.settings

import io.eksoda.core.data.repository.BackupRepository

/** A rough strength hint for a backup passphrase, shown in words. */
internal enum class PassphraseStrength { WEAK, FAIR, STRONG }

internal fun passphraseStrength(text: String): PassphraseStrength {
    val kinds = listOf(
        text.any(Char::isLowerCase),
        text.any(Char::isUpperCase),
        text.any(Char::isDigit),
        text.any { !it.isLetterOrDigit() },
    ).count { it }
    val varied = text.toSet().size >= MIN_DISTINCT
    val minLen = BackupRepository.MIN_PASSPHRASE_LENGTH
    val strongLen = text.length >= STRONG_LENGTH
    val mixedStrong = text.length >= MIXED_STRONG_LENGTH && kinds >= MIXED_KINDS
    val fairLen = text.length >= MIXED_STRONG_LENGTH
    return when {
        text.length < minLen || !varied -> PassphraseStrength.WEAK
        strongLen || mixedStrong -> PassphraseStrength.STRONG
        kinds >= 2 || fairLen -> PassphraseStrength.FAIR
        else -> PassphraseStrength.WEAK
    }
}

/** An export passphrase is long enough and typed the same twice. */
internal fun passphraseAccepted(passphrase: String, repeated: String): Boolean =
    passphrase.length >= BackupRepository.MIN_PASSPHRASE_LENGTH && passphrase == repeated

private const val MIN_DISTINCT = 6
private const val MIXED_KINDS = 3
private const val MIXED_STRONG_LENGTH = 16
private const val STRONG_LENGTH = 24
