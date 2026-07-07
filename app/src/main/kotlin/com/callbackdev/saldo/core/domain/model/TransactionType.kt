package com.callbackdev.saldo.core.domain.model

/**
 * Kind of movement recorded in the ledger.
 *
 * [TRANSFER] and [ADJUSTMENT] are always excluded from statistics at query level
 * (domain rule, see PLANNING ADR 8).
 */
enum class TransactionType {
    /** Reduces an account balance, has a category, appears in statistics. */
    EXPENSE,

    /** Increases an account balance, may have a category, appears in statistics. */
    INCOME,

    /** Moves funds between two accounts. Single record with a source and a destination. */
    TRANSFER,

    /** Aligns an account to its real balance. Never pollutes statistics. */
    ADJUSTMENT,
}
