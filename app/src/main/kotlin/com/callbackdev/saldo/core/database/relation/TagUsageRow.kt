package com.callbackdev.saldo.core.database.relation

/** Movement count for one tag, from a DAO aggregate; unused tags produce no row. */
data class TagUsageRow(
    val tagId: Long,
    val count: Int,
)
