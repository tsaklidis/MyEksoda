package io.eksoda.core.model

public data class Category(
    val id: String,
    val name: String,
    /** Stable key for icon, color and the translated name of a system category, e.g. "groceries". */
    val slug: String,
    val isSystem: Boolean,
)
