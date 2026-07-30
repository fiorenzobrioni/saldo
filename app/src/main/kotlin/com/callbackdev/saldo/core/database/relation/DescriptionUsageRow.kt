package com.callbackdev.saldo.core.database.relation

/** A past description and the category it was filed under, from a DAO query. */
data class DescriptionUsageRow(
    val description: String,
    val categoryId: Long,
)
