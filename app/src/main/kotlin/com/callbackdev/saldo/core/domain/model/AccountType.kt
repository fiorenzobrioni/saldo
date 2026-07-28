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

    /**
     * Loan, financing plan or mortgage (prestito o finanziamento): a debt that
     * pre-exists the app, tracked as a negative balance shrinking toward zero
     * (PLANNING ADR 33). The initial balance is today's remaining debt as
     * stated by the bank (plan interest included), so it is mandatory and
     * negative; each installment is a transfer from the paying account, so it
     * stays out of statistics and lands the balance exactly at zero with the
     * last one. The app never computes interest or amortization plans.
     * Declared right after [CREDIT_CARD] on purpose: the accounts list groups
     * sections by ordinal and the two debt types belong side by side.
     */
    LOAN,

    /** Physical cash (contanti). */
    CASH,

    /** Digital wallet such as PayPal or Revolut (wallet digitale). */
    DIGITAL_WALLET,

    /** Anything that does not fit the buckets above (altro). */
    OTHER,
}
