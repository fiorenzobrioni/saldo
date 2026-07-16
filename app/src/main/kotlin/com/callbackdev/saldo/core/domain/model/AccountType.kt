package com.callbackdev.saldo.core.domain.model

/**
 * Nature of an account. Purely cosmetic (icon, label, grouping) for every type
 * except [CREDIT_CARD], which carries a [CreditCardConfig] and the deferred
 * settlement behaviour; the balance math is identical for all types. The
 * editor shows a contextual description for the selected type, so each entry
 * documents its own usage to the user.
 *
 * Debit cards are deliberately not a type: they spend straight from the bank
 * account and have no balance of their own, so their movements are recorded on
 * the [CHECKING] account (its description says so).
 */
enum class AccountType {
    /** Bank current account (conto corrente); debit card spending is recorded here. */
    CHECKING,

    /**
     * Savings account (conto di risparmio): money fenced off from daily
     * spending, moved in and out via transfers. The editor defaults it to
     * excluded from the budget, so dipping into savings never consumes the
     * month's budget. Also covers cash parked for investments: the app tracks
     * the amount, never quotes (investments are out of scope by VISION).
     */
    SAVINGS,

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
