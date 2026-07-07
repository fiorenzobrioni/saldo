package com.callbackdev.saldo.core.domain.model

/**
 * Nature of an account. Purely cosmetic: drives the default icon and grouping,
 * never the balance math.
 */
enum class AccountType {
    /** Bank current account (conto corrente). */
    CHECKING,

    /** Debit or credit card (carta). */
    CARD,

    /** Physical cash (contanti). */
    CASH,

    /** Digital wallet such as PayPal or Revolut (wallet digitale). */
    DIGITAL_WALLET,

    /** Anything that does not fit the buckets above (altro). */
    OTHER,
}
