package com.callbackdev.saldo.core.domain.quickentry

/** A past description and the category it was filed under. */
data class DescriptionUsage(
    val description: String,
    val categoryId: Long,
)
