package io.eksoda.core.ui.text

import java.text.Normalizer
import java.util.Locale

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/**
 * A key for matching typed text in pickers: no accents, lower case, final sigma as sigma, so
 * "μασουτης" finds "Μασούτης" and "ΜΑΣΟΥΤΗΣ". The receipt list's own search uses the engine's
 * `normalizeText` in SQL; this is for lists already in memory (filter sheet suggestions).
 */
fun searchKey(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(COMBINING_MARKS, "")
    .lowercase(Locale.ROOT)
    .replace('ς', 'σ')
    .trim()
