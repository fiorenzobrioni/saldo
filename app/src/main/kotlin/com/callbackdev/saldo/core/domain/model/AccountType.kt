package com.callbackdev.saldo.core.domain.model

/**
 * Nature of an account. Purely cosmetic (icon, label, grouping) for every type
 * except [CREDIT_CARD], which carries a [CreditCardConfig] and the deferred
 * settlement behaviour; the balance math is identical for all types.
 */
enum class AccountType {
    /** Bank current account (conto corrente). */
    CHECKING,

    /**
     * Debit card (bancomat). Spends straight from the bank account it is tied
     * to: worth a separate account only when that bank account is not tracked
     * in the app (the editor explains this).
     */
    DEBIT_CARD,

    /** Prepaid card holding its own balance, topped up via transfers (Postepay, prepaid Revolut). */
    PREPAID_CARD,

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
