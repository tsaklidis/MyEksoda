package io.eksoda.core.model

/** The one household in this database. */
public data class Household(
    val id: String,
    val name: String,
    /** ISO 4217 code, `EUR` by default. */
    val defaultCurrency: String,
    /** IANA zone, `Europe/Athens` by default. */
    val timezone: String,
)
