package com.callbackdev.saldo.core.domain.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * The repayment state of a [AccountType.LOAN] account, joined with the
 * recurring transfers paying its installments. Every figure is computed, never
 * stored: the residual debt is the account's calculated balance (PLANNING
 * ADR 3) and the installment link is not a column - a rule is "linked" when it
 * is a fixed-amount recurring transfer into the loan account in its currency
 * (ADR 33), the same predicate the savings goals projection uses.
 *
 * [residual] is the debt still owed as a positive magnitude, floored at zero:
 * a balance at (or, after an overpayment, above) zero reads as [isPaidOff].
 * [fraction] is the repaid share `1 - residual/initial`, clamped to 0..1 (an
 * adjustment can push the debt above the initial one; the bar has nothing
 * meaningful to show below zero). [plannedMonthly] is the monthly-equivalent
 * of the linked rules; [nextInstallmentAmount]/[nextInstallmentDate] describe
 * the earliest upcoming charge among them. [remainingInstallments] is the
 * estimate `ceil(residual / plannedMonthly)` and [projectedPayoffDate] follows
 * from it; all four are null when no rule is linked (nothing to estimate: the
 * app never builds amortization plans).
 */
data class LoanProgress(
    val account: Account,
    val residual: BigDecimal,
    val fraction: Float,
    val isPaidOff: Boolean,
    val plannedMonthly: BigDecimal? = null,
    val nextInstallmentAmount: BigDecimal? = null,
    val nextInstallmentDate: LocalDate? = null,
    val remainingInstallments: Long? = null,
    val projectedPayoffDate: LocalDate? = null,
) {
    val hasLinkedRule: Boolean get() = plannedMonthly != null
}
