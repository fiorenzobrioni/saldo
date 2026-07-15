package com.callbackdev.saldo.core.domain.model

/**
 * Nature of an account. Purely cosmetic: drives the default icon and grouping,
 * never the balance math.
 */
enum class AccountType {
    /** Bank current account (conto corrente). */
    CHECKING,

    /** Debit or prepaid card that holds its own balance (carta di debito/prepagata). */
    CARD,

    /**
     * Deferred credit card (carta di credito a saldo): spending accrues as a
     * negative balance over a billing cycle and is charged in one instalment to
     * a linked account. Carries a [com.callbackdev.saldo.core.domain.model.CreditCardConfig].
     */
    CREDIT_CARD,

    /** Physical cash (contanti). */
    CASH,

    /** Digital wallet such as PayPal or Revolut (wallet digitale). */
    DIGITAL_WALLET,

    /** Anything that does not fit the buckets above (altro). */
    OTHER,
}
