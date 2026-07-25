package com.callbackdev.saldo.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.domain.model.AccountTotal
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryTotal
import com.callbackdev.saldo.core.domain.model.MonthlyBalance
import com.callbackdev.saldo.core.domain.model.MonthlyTotal
import com.callbackdev.saldo.core.domain.model.primaryCurrency
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.common.time.midnightTicker
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.usecase.ObserveBalanceHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    accountRepository: AccountRepository,
    userPreferences: UserPreferencesRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val observeBalanceHistory: ObserveBalanceHistoryUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val period = MutableStateFlow<StatsPeriod>(
        StatsPeriod.Month(YearMonth.now(clock)),
    )

    /** Everything the stats screen combines besides accounts and period. */
    private data class Sources(
        val categoryTotals: List<CategoryTotal>,
        val accountTotals: List<AccountTotal>,
        val monthlyTotals: List<MonthlyTotal>,
        val balances: List<MonthlyBalance>,
        val categories: List<Category>,
    )

    /** Everything upstream of the SQL windows: accounts, period, override, today. */
    private data class Inputs(
        val accounts: List<AccountWithBalance>,
        val period: StatsPeriod,
        val currencyOverride: Currency?,
        val today: LocalDate,
    )

    /**
     * The account list (plus the explicit Settings choice, when present)
     * drives the primary currency; the period drives the ring and per-account
     * windows. The trend charts always cover the last 12 months, re-anchored
     * by the midnight ticker if the day changes while the screen stays open.
     * All aggregation happens in SQL; this only resolves names and shapes the
     * rows for display.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StatsUiState> = combine(
        accountRepository.observeAccountsWithBalance(),
        period,
        userPreferences.primaryCurrencyOverride,
        midnightTicker(clock),
        ::Inputs,
    )
        .flatMapLatest { inputs ->
            val currency = primaryCurrency(inputs.accounts, inputs.currencyOverride)
            val zone = clock.zone
            val range = inputs.period.dateRange(inputs.today)
            val periodStart = range.start.atStartOfDay(zone).toInstant()
            val periodEnd = range.endInclusive.plusDays(1).atStartOfDay(zone).toInstant()
            val months = trailingMonths(YearMonth.from(inputs.today))
            val trendStart = months.first().atDay(1).atStartOfDay(zone).toInstant()
            val trendEnd = months.last().plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
            val sources = combine(
                transactionRepository.observeCategoryTotals(periodStart, periodEnd, currency),
                transactionRepository.observeAccountSpendTotals(periodStart, periodEnd, currency),
                transactionRepository.observeMonthlyTotals(trendStart, trendEnd, currency),
                observeBalanceHistory(currency, months),
                categoryRepository.observeCategories(),
            ) { categoryTotals, accountTotals, monthlyTotals, balances, categories ->
                Sources(
                    categoryTotals = categoryTotals,
                    accountTotals = accountTotals,
                    monthlyTotals = monthlyTotals,
                    balances = balances,
                    categories = categories,
                )
            }
            // Joined on top rather than inside: the typed combine overloads stop
            // at five flows.
            combine(
                sources,
                transactionRepository.observeOtherCurrencyCount(periodStart, periodEnd, currency),
            ) { collapsed, otherCurrencyCount ->
                buildState(
                    accounts = inputs.accounts,
                    activePeriod = inputs.period,
                    currency = currency,
                    today = inputs.today,
                    months = months,
                    sources = collapsed,
                    otherCurrencyCount = otherCurrencyCount,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = StatsUiState(),
        )

    /** Switches to the month view anchored on the current month. */
    fun selectMonthMode() {
        period.value = StatsPeriod.Month(YearMonth.now(clock))
    }

    /** Switches to the year view anchored on the current year. */
    fun selectYearMode() {
        period.value = StatsPeriod.Year(LocalDate.now(clock).year)
    }

    /**
     * Applies an explicit range picked by the user, possibly open-ended (a null
     * bound is open on that side). Both bounds null means no restriction at
     * all, which stats cannot represent: fall back to the current month.
     */
    fun selectCustomRange(start: LocalDate?, end: LocalDate?) {
        period.value = if (start == null && end == null) {
            StatsPeriod.Month(YearMonth.now(clock))
        } else {
            StatsPeriod.Custom(start, end)
        }
    }

    fun previousPeriod() {
        period.value = period.value.shifted(-1) ?: return
    }

    fun nextPeriod() {
        period.value = period.value.shifted(+1) ?: return
    }

    @Suppress("LongParameterList") // One value per figure family on the screen.
    private fun buildState(
        accounts: List<AccountWithBalance>,
        activePeriod: StatsPeriod,
        currency: Currency,
        today: LocalDate,
        months: List<YearMonth>,
        sources: Sources,
        otherCurrencyCount: Int,
    ): StatsUiState {
        val slices = categorySlices(sources.categoryTotals, sources.categories)
        return StatsUiState(
            isLoading = false,
            period = activePeriod,
            today = today,
            currency = currency,
            otherCurrencyCount = otherCurrencyCount,
            slices = slices,
            periodSpendTotal = slices.fold(BigDecimal.ZERO) { acc, slice -> acc.add(slice.amount) },
            accountSpends = accountSpends(sources.accountTotals, accounts),
            monthlyTotals = monthlyPoints(months, sources.monthlyTotals),
            balanceHistory = sources.balances,
            hasData = sources.monthlyTotals.isNotEmpty() ||
                sources.categoryTotals.isNotEmpty() ||
                sources.accountTotals.isNotEmpty(),
        )
    }

    /**
     * Categories with positive net spend, biggest first. Percentages are
     * BigDecimal math; the Float fraction only sizes arcs and bars. A NULL
     * category total (uncategorized movements) becomes its own slice, so the
     * ring and its center figure agree with the trend bars.
     */
    private fun categorySlices(
        totals: List<CategoryTotal>,
        categories: List<Category>,
    ): List<CategorySlice> {
        val categoryById = categories.associateBy { it.id }
        val spends = totals
            .filter { it.total.signum() < 0 }
            .mapNotNull { total ->
                val category = when (val id = total.categoryId) {
                    null -> null // Uncategorized bucket: kept as its own slice.
                    else -> categoryById[id] ?: return@mapNotNull null
                }
                Triple(category, total.total.negate(), total.count)
            }
            .sortedByDescending { it.second }
        val overall = spends.fold(BigDecimal.ZERO) { acc, (_, amount, _) -> acc.add(amount) }
        if (overall.signum() <= 0) return emptyList()
        return spends.map { (category, amount, count) ->
            val share = amount.divide(overall, FRACTION_SCALE, RoundingMode.HALF_UP)
            CategorySlice(
                category = category,
                amount = amount,
                fraction = share.toFloat(),
                percent = share.multiply(ONE_HUNDRED).setScale(0, RoundingMode.HALF_UP).toInt(),
                count = count,
            )
        }
    }

    /** Accounts with positive net spend, biggest first; bars relative to the top one. */
    private fun accountSpends(
        totals: List<AccountTotal>,
        accounts: List<AccountWithBalance>,
    ): List<AccountSpend> {
        val accountById = accounts.associate { it.account.id to it.account }
        val spends = totals
            .filter { it.total.signum() < 0 }
            .mapNotNull { total ->
                val account = accountById[total.accountId] ?: return@mapNotNull null
                Triple(account, total.total.negate(), total.count)
            }
            .sortedByDescending { it.second }
        val top = spends.firstOrNull()?.second ?: return emptyList()
        return spends.map { (account, amount, count) ->
            AccountSpend(
                account = account,
                amount = amount,
                fraction = amount.divide(top, FRACTION_SCALE, RoundingMode.HALF_UP).toFloat(),
                count = count,
            )
        }
    }

    /** The fixed 12-month axis, zero-filling months without data. */
    private fun monthlyPoints(
        months: List<YearMonth>,
        totals: List<MonthlyTotal>,
    ): List<MonthlyPoint> {
        val byMonth = totals.associateBy { it.month }
        return months.map { month ->
            val total = byMonth[month]
            MonthlyPoint(
                month = month,
                // A refund-heavy month can net above zero; the chart clamps it.
                expense = total?.expense?.negate()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO,
                income = total?.income ?: BigDecimal.ZERO,
            )
        }
    }

    private fun trailingMonths(current: YearMonth): List<YearMonth> =
        (TREND_MONTHS - 1 downTo 0).map { current.minusMonths(it.toLong()) }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TREND_MONTHS = 12
        const val FRACTION_SCALE = 4
        val ONE_HUNDRED: BigDecimal = BigDecimal(100)
    }
}
