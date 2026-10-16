package com.callbackdev.saldo.feature.widget

import com.callbackdev.saldo.core.common.applock.AppLockRepository
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.DashboardWindows
import com.callbackdev.saldo.core.domain.model.primaryCurrency
import com.callbackdev.saldo.core.domain.rates.CurrencyConverter
import com.callbackdev.saldo.core.domain.rates.RateTable
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.usecase.ObserveBudgetProgressUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveConversionStateUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveSafeToSpendUseCase
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads what the month widgets draw, once per refresh (ADR 54). The figures
 * are the dashboard's own, through the same use cases, so the card and the
 * app can never disagree: the overall budget's progress (the budget card), what
 * is still safely spendable (the safe-to-spend card), the statistics spend of
 * the month, and the next recurring charge (the hub's).
 *
 * One-shot reads (`first()`), never a held subscription: what keeps the card
 * current is [WidgetRefreshWatcher], which only listens while a month widget is
 * placed and asks for a fresh pass when the data moves.
 */
@Singleton
class MonthWidgetDataLoader @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val userPreferences: UserPreferencesRepository,
    private val appLockRepository: AppLockRepository,
    private val observeConversionState: ObserveConversionStateUseCase,
    private val observeBudgetProgress: ObserveBudgetProgressUseCase,
    private val observeSafeToSpend: ObserveSafeToSpendUseCase,
    private val clock: Clock,
) {

    suspend fun load(): MonthWidgetData {
        val today = LocalDate.now(clock)
        val accounts = accountRepository.observeAccountsWithBalance().first()
        val currency = primaryCurrency(accounts, userPreferences.primaryCurrencyOverride.first())
        val conversion = observeConversionState().first()
        val progress = observeBudgetProgress(currency, conversion).first().firstOrNull { it.budget.isOverall }
        val safe = progress?.let { observeSafeToSpend(currency, conversion).first() }
        val budget = if (progress != null && safe != null) {
            MonthBudget(
                limit = progress.budget.amount,
                spent = progress.spent,
                fraction = progress.fraction,
                level = progress.level,
                safeToSpend = safe.remaining,
                perDay = safe.perDay,
            )
        } else {
            null
        }
        val (spent, spentEstimated) = progress?.let { it.spent to it.includesConvertedSpend }
            ?: monthSpend(today, currency, conversion.active, conversion.rates)
        return MonthWidgetData(
            today = today,
            currency = currency,
            hasAccounts = accounts.any { !it.account.isArchived },
            budget = budget,
            spent = spent,
            next = nextCharge(recurringRuleRepository.observeRules().first(), today),
            amountsAllowed = !appLockRepository.lockEnabled.first(),
            includesEstimates = spentEstimated || safe?.includesEstimates == true,
        )
    }

    /**
     * The month's statistics spend when there is no budget to carry it: the
     * same fold the budget card uses, exact in [currency] and converted at
     * each day's rate for the rest when conversion is on (ADR 40).
     */
    private suspend fun monthSpend(
        today: LocalDate,
        currency: Currency,
        convert: Boolean,
        rates: RateTable,
    ): Pair<BigDecimal, Boolean> {
        val windows = DashboardWindows.around(today, clock.zone)
        if (!convert) {
            val total = transactionRepository
                .observeStatsSpendTotal(windows.monthStart, windows.todayEnd, currency)
                .first()
            return total.negate().max(BigDecimal.ZERO) to false
        }
        var total = BigDecimal.ZERO
        var converted = false
        transactionRepository.observeSpendByCurrencyDay(windows.monthStart, windows.todayEnd).first().forEach { row ->
            if (row.currency == currency) {
                total = total.add(row.total)
            } else {
                CurrencyConverter.convertOn(row.total, row.currency, currency, row.day, rates)?.let {
                    total = total.add(it.amount)
                    converted = true
                }
            }
        }
        return total.negate().max(BigDecimal.ZERO) to converted
    }
}
