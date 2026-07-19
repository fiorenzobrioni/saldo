package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.CategoryTotal
import com.callbackdev.saldo.core.domain.model.DailyActivity
import com.callbackdev.saldo.core.domain.model.MonthlyRecap
import com.callbackdev.saldo.core.domain.model.RecapBiggestExpense
import com.callbackdev.saldo.core.domain.model.RecapBusiestDay
import com.callbackdev.saldo.core.domain.model.RecapCategoryShare
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.util.Currency
import javax.inject.Inject

/**
 * Assembles the [MonthlyRecap] of one completed month in the primary currency.
 * Every figure comes from the statistics queries (ADR 8 exclusions, refunds
 * netted), so the recap always agrees with the Statistics screen. Month
 * windows are calendar months in the device zone, the same approximation the
 * dashboard uses; selection logic (top categories, busiest day, percentages)
 * lives here so it is unit-testable (ADR 12).
 */
class GetMonthlyRecapUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(month: YearMonth, currency: Currency): MonthlyRecap {
        val start = month.startInstant()
        val end = month.plusMonths(1).startInstant()
        val previousStart = month.minusMonths(1).startInstant()

        val totals = transactionRepository.getStatsPeriodTotals(start, end, currency)
        val categoryTotals = transactionRepository.getCategoryTotals(start, end, currency)
        val activity = transactionRepository.getDailyActivity(start, end, currency)
        val biggestExpense = transactionRepository.getBiggestExpense(start, end, currency)
        val recurringSpend = transactionRepository.getRecurringSpendTotal(start, end, currency)
        // The baseline exists only when the previous month has statistics rows
        // at all: a spend of zero in a tracked month is a real comparison, an
        // untracked month is not (same guard as the dashboard comparison).
        val previousExpense = if (transactionRepository.getCategoryTotals(previousStart, start, currency).isEmpty()) {
            null
        } else {
            transactionRepository.getStatsPeriodTotals(previousStart, start, currency)
                .expense.negate().max(BigDecimal.ZERO)
        }

        val expenseTotal = totals.expense.negate().max(BigDecimal.ZERO)
        val incomeTotal = totals.income.max(BigDecimal.ZERO)
        return MonthlyRecap(
            month = month,
            currency = currency,
            expenseTotal = expenseTotal,
            incomeTotal = incomeTotal,
            previousExpenseTotal = previousExpense,
            topCategories = topCategories(categoryTotals),
            biggestExpense = biggestExpense?.let { expense ->
                RecapBiggestExpense(
                    amount = expense.amount.negate(),
                    description = expense.description,
                    categoryId = expense.categoryId,
                    date = expense.timestamp.atOffset(expense.zoneOffset).toLocalDate(),
                )
            },
            busiestDay = busiestDay(activity),
            recurringSpend = recurringSpend.negate().max(BigDecimal.ZERO),
            dailyAverageSpend = expenseTotal.divide(
                BigDecimal(month.lengthOfMonth()),
                MoneyMapper.fractionDigits(currency),
                RoundingMode.HALF_UP,
            ),
            movementCount = activity.sumOf { it.count },
            savingsRatePercent = savingsRatePercent(expenseTotal, incomeTotal),
        )
    }

    private fun YearMonth.startInstant(): Instant =
        atDay(1).atStartOfDay(clock.zone).toInstant()

    /**
     * Categories with positive net spend, biggest first, capped at
     * [MonthlyRecap.TOP_CATEGORIES]. Percentages are BigDecimal math over the
     * whole month's spend (not only the top slice), mirroring the ring.
     */
    private fun topCategories(totals: List<CategoryTotal>): List<RecapCategoryShare> {
        val spends = totals
            .filter { it.total.signum() < 0 }
            .map { Triple(it.categoryId, it.total.negate(), it.count) }
            .sortedByDescending { it.second }
        val overall = spends.fold(BigDecimal.ZERO) { acc, (_, amount, _) -> acc.add(amount) }
        if (overall.signum() <= 0) return emptyList()
        return spends.take(MonthlyRecap.TOP_CATEGORIES).map { (categoryId, amount, count) ->
            val share = amount.divide(overall, FRACTION_SCALE, RoundingMode.HALF_UP)
            RecapCategoryShare(
                categoryId = categoryId,
                amount = amount,
                fraction = share.toFloat(),
                percent = share.multiply(ONE_HUNDRED).setScale(0, RoundingMode.HALF_UP).toInt(),
                count = count,
            )
        }
    }

    /**
     * The day with the most movements; ties break on the higher spend (the
     * more negative signed figure), then on the earlier date, for determinism.
     */
    private fun busiestDay(activity: List<DailyActivity>): RecapBusiestDay? =
        activity
            .sortedWith(
                compareByDescending<DailyActivity> { it.count }
                    .thenBy { it.spend }
                    .thenBy { it.date },
            )
            .firstOrNull()
            ?.let { day ->
                RecapBusiestDay(
                    date = day.date,
                    count = day.count,
                    spend = day.spend.negate().max(BigDecimal.ZERO),
                )
            }

    /** Floor percent of the income kept as savings; null unless income and net are positive. */
    private fun savingsRatePercent(expenseTotal: BigDecimal, incomeTotal: BigDecimal): Int? {
        val net = incomeTotal.subtract(expenseTotal)
        return if (incomeTotal.signum() > 0 && net.signum() > 0) {
            net.multiply(ONE_HUNDRED).divide(incomeTotal, 0, RoundingMode.FLOOR).toInt()
        } else {
            null
        }
    }

    private companion object {
        const val FRACTION_SCALE = 4
        val ONE_HUNDRED: BigDecimal = BigDecimal(100)
    }
}
