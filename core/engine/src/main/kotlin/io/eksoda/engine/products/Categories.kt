package io.eksoda.engine.products

import io.eksoda.engine.text.pyRegex
import java.text.Normalizer
import java.util.Locale

// The category slug of the server's `products/api/serializers.py` (`category_slug`).

private const val SLUG_LENGTH = 80
private val NOT_SLUG = pyRegex("""[^\w\s-]""")
private val SEPARATORS = pyRegex("""[-\s]+""")

/** The slug a category name is unique by, among the system categories and the household's. */
public fun categorySlug(name: String): String =
    slugify(name).take(SLUG_LENGTH).ifEmpty { "category" }

/** Django's `slugify(value, allow_unicode=True)`. */
internal fun slugify(value: String): String {
    val folded = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
    return SEPARATORS.replace(NOT_SLUG.replace(folded, ""), "-").trim('-', '_')
}
