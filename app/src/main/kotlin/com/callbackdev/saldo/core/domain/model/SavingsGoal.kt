package com.callbackdev.saldo.core.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/**
 * A savings target laid over a dedicated savings account. The amount saved is
 * never stored here: it is the linked account's computed balance (PLANNING ADR
 * 3), fed by transfers into that account (manual or recurring). Exactly one goal
 * exists per account (the pot/vault model), so an account's balance maps to one
 * goal.
 *
 * [targetAmount] is a positive magnitude in [currency], which always matches the
 * linked account's currency. [targetDate] is optional; when set it drives the
 * suggested monthly contribution. [color] and [icon] drive the goal avatar,
 * mirroring accounts and categories.
 */
data class SavingsGoal(
    val name: String,
    val targetAmount: BigDecimal,
    val currency: Currency,
    val accountId: Long,
    val id: Long = 0L,
    val targetDate: LocalDate? = null,
    val color: Int? = null,
    val icon: String? = null,
    val sortOrder: Int = 0,
)
