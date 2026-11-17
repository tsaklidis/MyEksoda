package io.eksoda.feature.settings

import io.eksoda.core.data.repository.BackupRepository

/** A rough strength hint for a backup passphrase (§12.1), shown in words. */
internal enum class PassphraseStrength { WEAK, FAIR, STRONG }

internal fun passphraseStrength(text: String): PassphraseStrength {
    val kinds = listOf(
        text.any(Char::isLowerCase),
        text.any(Char::isUpperCase),
        text.any(Char::isDigit),
        text.any { !it.isLetterOrDigit() },
    ).count { it }
    val varied = text.toSet().size >= MIN_DISTINCT
    return when {
        text.length < BackupRepository.MIN_PASSPHRASE_LENGTH || !varied -> PassphraseStrength.WEAK
        text.length >= STRONG_LENGTH || (text.length >= MIXED_STRONG_LENGTH && kinds >= MIXED_KINDS) ->
            PassphraseStrength.STRONG
        kinds >= 2 || text.length >= MIXED_STRONG_LENGTH -> PassphraseStrength.FAIR
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
