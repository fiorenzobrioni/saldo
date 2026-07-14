package com.callbackdev.saldo.core.database.relation

/** Aggregated total (minor units) and movement count for a category, from a DAO query. */
data class CategoryTotalRow(
    /** Null for the uncategorized bucket (statistics ring); spend queries never produce it. */
    val categoryId: Long?,
    val totalMinor: Long,
    val count: Int,
)
