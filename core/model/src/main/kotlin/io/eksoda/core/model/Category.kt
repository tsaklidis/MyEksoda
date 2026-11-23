package io.eksoda.core.model

public data class Category(
    val id: String,
    val name: String,
    /** Stable key for the icon, color and translated name of a system category: "groceries". */
    val slug: String,
    val isSystem: Boolean,
    /** The household's own name for a system category, shown instead of the translated one. */
    val customName: String? = null,
)
