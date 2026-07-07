package com.callbackdev.saldo.core.domain.model

/** A free-form label that can be attached to zero or more movements. */
data class Tag(
    val name: String,
    val id: Long = 0L,
)
