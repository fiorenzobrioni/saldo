package com.callbackdev.saldo.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.BudgetProgress
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CounterpartyLedger
import com.callbackdev.saldo.core.domain.model.DailyBalance
import com.callbackdev.saldo.core.domain.model.DashboardTotals
import com.callbackdev.saldo.core.domain.model.DashboardWindows
import com.callbackdev.saldo.core.domain.model.ForeignDashboardDayFlows
import com.callbackdev.saldo.core.domain.model.PeriodTotals
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.SavingsGoalProgress
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import com.callbackdev.saldo.core.domain.model.hasEndedBy
import com.callbackdev.saldo.core.domain.model.primaryCurrency
import com.callbackdev.saldo.core.domain.model.runsInMonthOf
import com.callbackdev.saldo.core.domain.model.UpcomingMovement
import com.callbackdev.saldo.core.domain.rates.ConversionState
import com.callbackdev.saldo.core.domain.rates.ConvertedAggregates
import com.callbackdev.saldo.core.domain.rates.CurrencyConverter
import com.callbackdev.saldo.core.domain.rates.RateTable
import com.callbackdev.saldo.core.domain.recurrence.BalanceForecastCalculator
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceCalculator
import com.callbackdev.saldo.core.domain.recurrence.RuleOccurrence
import com.callbackdev.saldo.core.common.prefs.DashboardCardPreferences
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.common.time.midnightTicker
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.usecase.DueStatement
import com.callbackdev.saldo.core.domain.usecase.ObserveBudgetProgressUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveConversionStateUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveCounterpartyBalancesUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveDailyBalanceHistoryUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveDueStatementsUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveSafeToSpendUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveSavingsGoalsProgressUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveUpcomingMovementsUseCase
import com.callbackdev.saldo.core.domain.usecase.SafeToSpend
import com.callbackdev.saldo.feature.accounts.sortedByTypeThenName
import com.callbackdev.saldo.feature.transactions.TransactionListItem
import com.callbackdev.saldo.feature.transactions.countervalueIn
import com.callbackdev.saldo.feature.upcoming.UpcomingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import kotlin.random.Random

/** The soonest upcoming recurring charge or credit, for the dashboard card preview. */
data class NextRecurringEvent(
    val name: String,
    /** Signed amount in [currency]: negative for expenses, positive for incomes. */
    val amount: BigDecimal,
    val currency: Currency,
    val date: LocalDate,
)

/** The recurring-transactions summary shown on the dashboard card. */
data class RecurringSummary(
    /** Positive monthly-equivalent total of active recurring expenses, in the primary currency. */
    val monthlyExpenses: BigDecimal = BigDecimal.ZERO,
    /** Positive monthly-equivalent total of active recurring incomes, in the primary currency. */
    val monthlyIncomes: BigDecimal = BigDecimal.ZERO,
    /**
     * Positive monthly-equivalent total of active recurring transfers landing in
     * a savings account (planned savings), in the primary currency.
     */
    val monthlyTransfersToSavings: BigDecimal = BigDecimal.ZERO,
    val next: NextRecurringEvent? = null,
    /** Whether any recurring rule (either type, any currency) is active. */
    val hasRules: Boolean = false,
)

/**
 * The dashboard's preview of what is coming (ADR 36): the soonest few movements
 * and the size of the whole list, so the card can say "and 4 more" without
 * carrying them all.
 */
data class UpcomingPreview(
    val items: List<UpcomingItem> = emptyList(),
    val totalCount: Int = 0,
) {
    val isEmpty: Boolean get() = totalCount == 0

    /** How many the card is not showing; zero when it shows them all. */
    val hiddenCount: Int get() = (totalCount - items.size).coerceAtLeast(0)
}

/** Time-of-day band that selects the dashboard greeting. */
enum class GreetingBand {
    NIGHT,
    MORNING,
    AFTERNOON,
    EVENING,
    ;

    companion object {
        /** 00-05 night, 06-11 morning, 12-17 afternoon, 18-23 evening. */
        fun of(time: LocalTime): GreetingBand = when (time.hour) {
            in NIGHT_END downTo 0 -> NIGHT
            in MORNING_START..MORNING_END -> MORNING
            in AFTERNOON_START..AFTERNOON_END -> AFTERNOON
            else -> EVENING
        }

        private const val NIGHT_END = 5
        private const val MORNING_START = 6
        private const val MORNING_END = 11
        private const val AFTERNOON_START = 12
        private const val AFTERNOON_END = 17
    }
}

/** Immutable UI state for the "Today" dashboard. */
data class DashboardUiState(
    val isLoading: Boolean = true,
    val hasAccounts: Boolean = false,
    val primaryCurrency: Currency = fallbackCurrency,
    val totalBalance: BigDecimal = BigDecimal.ZERO,
    /** Whether conversion is on with at least one usable rate (ADR 40). */
    val conversionActive: Boolean = false,
    /** True when [totalBalance] includes converted foreign balances: shown with "≈". */
    val totalBalanceEstimated: Boolean = false,
    /** Publication day of the stalest rate [totalBalance] leans on; null when exact. */
    val totalBalanceRateDay: LocalDate? = null,
    /** Estimated countervalue per foreign account id, for the breakdown rows. */
    val accountCountervalues: Map<Long, CurrencyConverter.Estimate> = emptyMap(),
    /** True when the Today/Month figures include converted foreign movements. */
    val periodTotalsEstimated: Boolean = false,
    /**
     * Balance as of today (movements dated up to today), i.e. the sparkline's
     * today point. Non-null only when it differs from [totalBalance], which
     * happens when confirmed movements dated in the future are already booked:
     * then [totalBalance] runs ahead of what is actually available today and
     * the card surfaces this figure as a secondary line. Null when the two
     * coincide (nothing to disambiguate).
     */
    val balanceAsOfToday: BigDecimal? = null,
    /** Active (non-archived) accounts with balances, for the expandable detail. */
    val accounts: List<AccountWithBalance> = emptyList(),
    /**
     * End-of-day total balance over the sparkline window (ascending, last
     * point = [balanceAsOfToday] when set, otherwise [totalBalance]); empty
     * while loading or without accounts.
     */
    val balanceHistory: List<DailyBalance> = emptyList(),
    /**
     * Estimated end-of-day balances from tomorrow to the last day of the
     * month, the sparkline's dashed forecast tail: fixed recurring flows on
     * their due dates plus the month's average daily spend
     * ([BalanceForecastCalculator]). Empty on the last day of the month or
     * when the sparkline itself is hidden.
     */
    val balanceForecast: List<DailyBalance> = emptyList(),
    val today: PeriodTotals = PeriodTotals(),
    val month: PeriodTotals = PeriodTotals(),
    /**
     * Signed difference between what has been spent so far this month and by the
     * same day last month (positive = more spent this month); null when there is
     * no baseline last month.
     */
    val monthVsPreviousToDate: BigDecimal? = null,
    /**
     * Positive magnitude of what had been spent by this same day last month, the
     * reference figure shown under the period cards; null when there is nothing
     * to compare against.
     */
    val previousMonthSpendToDate: BigDecimal? = null,
    /** Whether more has been spent so far this month than by this day last month. */
    val spentMoreThanLastMonth: Boolean = false,
    val recurring: RecurringSummary = RecurringSummary(),
    /** Number of recurring movements awaiting confirmation. */
    val pendingCount: Int = 0,
    /** What is coming, for the preview card: the soonest few and how many there are. */
    val upcoming: UpcomingPreview = UpcomingPreview(),
    /** Confirm-mode credit card statements waiting to be paid (primary currency shown). */
    val dueStatements: List<DueStatement> = emptyList(),
    val recent: List<TransactionListItem> = emptyList(),
    /** Budget progress in the primary currency, overall first (empty: no budgets set). */
    val budgets: List<BudgetProgress> = emptyList(),
    /** Safe-to-spend figure; null without an overall budget in the primary currency. */
    val safeToSpend: SafeToSpend? = null,
    /** Savings goals with progress, ordered for display (empty: no goals set). */
    val savingsGoals: List<SavingsGoalProgress> = emptyList(),
    /** Credits and debts toward people; empty when nobody was ever recorded. */
    val counterparties: CounterpartyLedger = CounterpartyLedger(),
    /** Which optional cards the user keeps visible (Settings > Dashboard). */
    val cardPrefs: DashboardCardPreferences = DashboardCardPreferences(),
    /**
     * The completed month whose recap teaser is shown; null when outside the
     * first week of the month, without data, or after a dismissal.
     */
    val recapTeaserMonth: YearMonth? = null,
    val date: LocalDate = LocalDate.ofEpochDay(0),
    /** Greeting band and a stable [0,1) roll, both fixed once per app-open. */
    val greetingBand: GreetingBand = GreetingBand.MORNING,
    val greetingRoll: Float = 0f,
)

@HiltViewModel
@Suppress("LongParameterList") // The dashboard aggregates one source per card, all Hilt-injected.
class DashboardViewModel @Inject constructor(
    accountRepository: AccountRepository,
    private val userPreferences: UserPreferencesRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val observeBudgetProgress: ObserveBudgetProgressUseCase,
    private val observeSafeToSpend: ObserveSafeToSpendUseCase,
    private val observeDueStatements: ObserveDueStatementsUseCase,
    private val observeSavingsGoalsProgress: ObserveSavingsGoalsProgressUseCase,
    private val observeCounterpartyBalances: ObserveCounterpartyBalancesUseCase,
    private val observeDailyBalanceHistory: ObserveDailyBalanceHistoryUseCase,
    private val observeUpcomingMovements: ObserveUpcomingMovementsUseCase,
    private val observeConversionState: ObserveConversionStateUseCase,
    private val clock: Clock,
) : ViewModel() {

    // Fixed once when the ViewModel is created (once per app-open): the greeting
    // stays put across recomposition and rotation, and only re-rolls on a fresh
    // open. The roll indexes the band's message array in the composable.
    private val greetingBand: GreetingBand = GreetingBand.of(LocalTime.now(clock))
    private val greetingRoll: Float = Random.nextFloat()

    /** Everything the dashboard combines besides the accounts themselves. */
    private data class Sources(
        val totals: ConvertedAggregates.Merged<DashboardTotals>,
        val recent: List<Transaction>,
        val categories: List<Category>,
        val rules: List<RecurringRule>,
        val upcoming: List<UpcomingMovement>,
    )

    /** Everything upstream of the SQL windows: accounts, override, today, conversion. */
    private data class Inputs(
        val accounts: List<AccountWithBalance>,
        val currencyOverride: Currency?,
        val today: LocalDate,
        val conversion: ConversionState,
    )

    /** The budget/goal figures that join on top of the core [Sources]. */
    private data class Extras(
        val budgets: List<BudgetProgress>,
        val safeToSpend: SafeToSpend?,
        val cardPrefs: DashboardCardPreferences,
        val dueStatements: List<DueStatement>,
        val savingsGoals: List<SavingsGoalProgress>,
        val counterparties: CounterpartyLedger,
    )

    /**
     * The account list (plus the explicit Settings choice, when present)
     * drives the primary currency and the aggregate windows; every figure is
     * then computed by the database ([DashboardWindows],
     * [TransactionRepository.observeDashboardTotals]) instead of loading the
     * ledger in memory. The midnight ticker re-anchors "today" when the day
     * changes while the screen stays open.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> = combine(
        accountRepository.observeAccountsWithBalance(),
        userPreferences.primaryCurrencyOverride,
        midnightTicker(clock),
        observeConversionState(),
        ::Inputs,
    )
        .flatMapLatest { inputs ->
            val accounts = inputs.accounts
            val today = inputs.today
            val conversion = inputs.conversion
            val primary = primaryCurrency(accounts, inputs.currencyOverride)
            val rates = if (conversion.active) conversion.rates else RateTable.EMPTY
            val windows = DashboardWindows.around(today, clock.zone)
            // The base totals and their foreign residue collapse into one
            // merged figure right away (ADR 40): downstream nobody needs to
            // know there were two queries. With conversion off the residue
            // flow is a constant and the merge is the identity.
            val totals = combine(
                transactionRepository.observeDashboardTotals(windows, primary),
                if (conversion.active) {
                    transactionRepository.observeForeignDashboardFlows(windows, primary)
                } else {
                    flowOf(emptyList<ForeignDashboardDayFlows>())
                },
            ) { base, foreign ->
                ConvertedAggregates.mergeDashboardTotals(base, foreign, primary, rates)
            }
            // The typed combine overloads stop at five flows, so the core
            // sources collapse first and the budget figures join on top.
            val sources = combine(
                totals,
                transactionRepository.observeRecentTransactions(RECENT_COUNT),
                categoryRepository.observeCategories(),
                recurringRuleRepository.observeRules(),
                // Future-dated movements and pending occurrences in one flow:
                // the forecast walks them and the card previews them.
                observeUpcomingMovements.movements(today),
            ) { mergedTotals, recent, categories, rules, upcoming ->
                Sources(mergedTotals, recent, categories, rules, upcoming)
            }
            // Budget/goal figures collapse into one bundle so the whole dashboard
            // stays within the typed combine arity.
            val extras = combine(
                observeBudgetProgress(primary, conversion),
                observeSafeToSpend(primary, conversion),
                userPreferences.dashboardCardPreferences,
                observeDueStatements(),
                // The typed combine stops at five flows, so the two "who holds
                // what" sources travel together in the last slot.
                combine(observeSavingsGoalsProgress(), observeCounterpartyBalances(), ::Pair),
            ) { budgets, safeToSpend, cardPrefs, dueStatements, (savingsGoals, counterparties) ->
                Extras(budgets, safeToSpend, cardPrefs, dueStatements, savingsGoals, counterparties)
            }
            val sparklineDays = List(SPARKLINE_DAYS) { today.minusDays(SPARKLINE_DAYS - 1L - it) }
            // Foreign currencies whose included accounts should enter the
            // sparkline as converted stocks; empty when conversion is off.
            val sparklineForeign = if (conversion.active) {
                accounts
                    .filter {
                        !it.account.isArchived && it.account.isIncludedInTotal &&
                            it.account.currency != primary
                    }
                    .map { it.account.currency }
                    .distinct()
            } else {
                emptyList()
            }
            combine(
                sources,
                extras,
                observeDailyBalanceHistory(primary, sparklineDays, sparklineForeign, rates),
                recapTeaserMonth(today, primary),
                // Accounts enriched with their per-account "as of today" balance,
                // so a diverging account can show it in the breakdown.
                accountRepository.observeAccountsWithBalanceAsOfToday(today.plusDays(1).toEpochDay()),
            ) { collapsed, bundle, balanceHistory, recapTeaserMonth, accountsToday ->
                buildState(
                    accounts = accountsToday,
                    primary = primary,
                    conversion = conversion,
                    today = today,
                    sources = collapsed,
                    balanceHistory = balanceHistory,
                    recapTeaserMonth = recapTeaserMonth,
                    budgets = bundle.budgets,
                    safeToSpend = bundle.safeToSpend,
                    cardPrefs = bundle.cardPrefs,
                    dueStatements = bundle.dueStatements.filter { it.currency == primary },
                    // With conversion on, a goal in another currency is no
                    // longer hidden from the card (ADR 40): its figures stay
                    // in its own currency, which the row already shows.
                    savingsGoals = bundle.savingsGoals.filter {
                        conversion.active || it.goal.currency == primary
                    },
                    counterparties = bundle.counterparties,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = DashboardUiState(
                date = LocalDate.now(clock),
                greetingBand = greetingBand,
                greetingRoll = greetingRoll,
            ),
        )

    /**
     * In-session expansion of the Total-balance card's account breakdown. Held
     * in the ViewModel (not the composable) so it outlives the card scrolling
     * out of the list and navigating between screens/tabs, and resets to the
     * Settings default only when the app is reopened (a fresh ViewModel). A null
     * override means "still following the persisted default", so changing the
     * default in Settings updates the card live until the user toggles it by
     * hand this session.
     */
    private val balanceAccountsExpandedOverride = MutableStateFlow<Boolean?>(null)

    val balanceAccountsExpanded: StateFlow<Boolean> = combine(
        balanceAccountsExpandedOverride,
        userPreferences.balanceAccountsExpandedByDefault,
    ) { override, default -> override ?: default }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = false,
        )

    fun toggleBalanceAccountsExpanded() {
        balanceAccountsExpandedOverride.value = !balanceAccountsExpanded.value
    }

    /**
     * In-session expansion of the Spendable-today breakdown, with the same
     * lifetime as [balanceAccountsExpanded]: it survives scrolling and
     * navigation and resets on a fresh app open. Unlike the accounts breakdown
     * it has no persisted default, so it always starts collapsed.
     */
    private val safeToSpendExpandedState = MutableStateFlow(false)
    val safeToSpendExpanded: StateFlow<Boolean> = safeToSpendExpandedState.asStateFlow()

    fun toggleSafeToSpendExpanded() {
        safeToSpendExpandedState.value = !safeToSpendExpandedState.value
    }

    /**
     * The recap teaser month: the just-completed month, only during the first
     * [RECAP_TEASER_MAX_DAY] days of the new one, only when that month has
     * statistics movements, and only until the user dismisses it.
     */
    private fun recapTeaserMonth(
        today: LocalDate,
        primary: Currency,
    ): Flow<YearMonth?> {
        if (today.dayOfMonth > RECAP_TEASER_MAX_DAY) return flowOf(null)
        val previousMonth = YearMonth.from(today).minusMonths(1)
        return combine(
            userPreferences.dismissedRecapMonth,
            transactionRepository.observeMonthlyTotals(
                start = previousMonth.atDay(1).atStartOfDay(clock.zone).toInstant(),
                end = YearMonth.from(today).atDay(1).atStartOfDay(clock.zone).toInstant(),
                currency = primary,
            ),
        ) { dismissed, previousTotals ->
            previousMonth.takeIf { previousTotals.isNotEmpty() && dismissed != previousMonth }
        }
    }

    /** Persists the dismissal of the current teaser month. */
    fun dismissRecapTeaser() {
        val month = uiState.value.recapTeaserMonth ?: return
        viewModelScope.launch { userPreferences.setDismissedRecapMonth(month) }
    }

    @Suppress("LongParameterList") // One argument per collapsed bundle field.
    private fun buildState(
        accounts: List<AccountWithBalance>,
        primary: Currency,
        conversion: ConversionState,
        today: LocalDate,
        sources: Sources,
        balanceHistory: List<DailyBalance>,
        recapTeaserMonth: YearMonth?,
        budgets: List<BudgetProgress>,
        safeToSpend: SafeToSpend?,
        cardPrefs: DashboardCardPreferences,
        dueStatements: List<DueStatement>,
        savingsGoals: List<SavingsGoalProgress>,
        counterparties: CounterpartyLedger,
    ): DashboardUiState {
        val active = accounts.filter { !it.account.isArchived }
        val rates = if (conversion.active) conversion.rates else RateTable.EMPTY
        // With conversion off (or nothing cached) the empty table converts
        // nothing and this is exactly the old primary-only sum (ADR 40).
        val balance = ConvertedAggregates.convertTotalBalance(accounts, primary, rates)
        val totalBalance = balance.total

        // The sparkline's today point is the balance dated up to today; it lags
        // [totalBalance] when future-dated confirmed movements are already
        // booked. Surface it only on that divergence, so the card stays quiet
        // when the headline already is the today figure.
        val todayBalance = balanceHistory.lastOrNull()?.balance ?: totalBalance
        val balanceAsOfToday = todayBalance.takeIf { it.compareTo(totalBalance) != 0 }

        val totals = sources.totals.value
        val previousReference = totals.previousMonthToDateSpend.takeIf { it.signum() > 0 }
        val comparison = previousReference?.let { totals.monthToDateSpend.subtract(it) }
        // Meaningful only when a baseline exists, like the two figures above.
        val spentMore = previousReference != null && totals.monthToDateSpend > previousReference

        val accountById = accounts.associate { it.account.id to it.account }
        val categoryById = sources.categories.associateBy { it.id }
        val recent = sources.recent.map { transaction ->
            TransactionListItem(
                transaction = transaction,
                account = accountById[transaction.accountId],
                toAccount = transaction.transferAccountId?.let { accountById[it] },
                category = transaction.categoryId?.let { categoryById[it] },
                countervalue = transaction.countervalueIn(primary, rates),
                countervalueCurrency = primary,
            )
        }

        return DashboardUiState(
            isLoading = false,
            hasAccounts = active.isNotEmpty(),
            primaryCurrency = primary,
            totalBalance = totalBalance,
            conversionActive = conversion.active,
            totalBalanceEstimated = balance.convertedCount > 0,
            totalBalanceRateDay = balance.rateDay,
            accountCountervalues = balance.countervalues,
            periodTotalsEstimated = sources.totals.includesEstimates,
            balanceAsOfToday = balanceAsOfToday,
            // Same order as the Accounts screen (type declaration order, then
            // name) so the total-balance breakdown and the full list agree.
            accounts = active.sortedByTypeThenName(),
            balanceHistory = balanceHistory,
            // Anchored to the today figure, the point the drawn line ends on,
            // so the dashed tail attaches seamlessly and every future movement
            // is applied once, on its own day, instead of all at once here.
            balanceForecast = if (balanceHistory.size > 1) {
                BalanceForecastCalculator.projectToEndOfMonth(
                    balanceAsOfToday = todayBalance,
                    today = today,
                    nonRecurringMonthToDateSpend = totals.monthToDateNonRecurringSpend,
                    rules = sources.rules,
                    upcoming = BalanceForecastCalculator.upcomingNetByDay(
                        movements = sources.upcoming,
                        includedAccountIds = totalAccountIds(active, primary, rates),
                        firstForecastDay = today.plusDays(1),
                        lastForecastDay = today.withDayOfMonth(today.lengthOfMonth()),
                        currencyByAccountId = active.associate {
                            it.account.id to it.account.currency
                        },
                        target = primary,
                        rates = rates,
                    ),
                    materializedOccurrences = materializedOccurrences(sources.upcoming),
                    currency = primary,
                    rates = rates,
                )
            } else {
                emptyList()
            },
            today = totals.today,
            month = totals.month,
            monthVsPreviousToDate = comparison,
            previousMonthSpendToDate = previousReference,
            spentMoreThanLastMonth = spentMore,
            recurring = recurringSummary(sources.rules, primary, today, savingsAccountIds(accounts)),
            pendingCount = sources.upcoming.count { it.isPending },
            upcoming = upcomingPreview(sources.upcoming, accountById, categoryById, sources.rules),
            dueStatements = dueStatements,
            recent = recent,
            budgets = budgets,
            safeToSpend = safeToSpend,
            savingsGoals = savingsGoals,
            counterparties = counterparties,
            cardPrefs = cardPrefs,
            // The Settings switch silences the teaser without touching the
            // per-month dismissal flow.
            recapTeaserMonth = recapTeaserMonth.takeIf { cardPrefs.showRecapTeaser },
            date = today,
            greetingBand = greetingBand,
            greetingRoll = greetingRoll,
        )
    }

    /**
     * Ids of the accounts that count toward the total balance in [primary]:
     * the same set the balance sum covers, so the forecast applies a future
     * movement exactly when the balance eventually will. With conversion on
     * that includes foreign accounts whose currency has rates (ADR 40).
     */
    private fun totalAccountIds(
        accounts: List<AccountWithBalance>,
        primary: Currency,
        rates: RateTable,
    ): Set<Long> = accounts
        .filter {
            it.account.isIncludedInTotal &&
                (it.account.currency == primary || rates.covers(it.account.currency.currencyCode))
        }
        .map { it.account.id }
        .toSet()

    /**
     * The rule occurrences already filled by an upcoming movement. Those slots
     * are counted through the movement itself, so the schedule walk must skip
     * them or the same charge would land twice on the same day.
     */
    private fun materializedOccurrences(upcoming: List<UpcomingMovement>): Set<RuleOccurrence> =
        upcoming.mapNotNullTo(mutableSetOf()) { movement ->
            val ruleId = movement.transaction.recurringRuleId ?: return@mapNotNullTo null
            val occurrence = movement.transaction.recurringOccurrenceDate ?: return@mapNotNullTo null
            RuleOccurrence(ruleId, occurrence)
        }

    /** The soonest [UPCOMING_PREVIEW_COUNT] movements, resolved for display. */
    private fun upcomingPreview(
        upcoming: List<UpcomingMovement>,
        accountById: Map<Long, Account>,
        categoryById: Map<Long, Category>,
        rules: List<RecurringRule>,
    ): UpcomingPreview {
        if (upcoming.isEmpty()) return UpcomingPreview()
        val ruleById = rules.associateBy { it.id }
        return UpcomingPreview(
            items = upcoming.take(UPCOMING_PREVIEW_COUNT).map { movement ->
                val transaction = movement.transaction
                UpcomingItem(
                    movement = movement,
                    account = accountById[transaction.accountId],
                    transferAccount = transaction.transferAccountId?.let { accountById[it] },
                    category = transaction.categoryId?.let { categoryById[it] },
                    rule = transaction.recurringRuleId?.let { ruleById[it] },
                )
            },
            totalCount = upcoming.size,
        )
    }

    /** Ids of the active (non-archived) savings accounts, the planned-savings destinations. */
    private fun savingsAccountIds(accounts: List<AccountWithBalance>): Set<Long> =
        accounts
            .filter { it.account.type == AccountType.SAVINGS && !it.account.isArchived }
            .map { it.account.id }
            .toSet()

    /**
     * Active recurring rules: normalized monthly totals for expenses, incomes and
     * transfers into savings (planned savings), plus the next upcoming charge or
     * credit. Transfers have no meaningful "next charge" line, so they feed only
     * the planned-savings figure.
     */
    private fun recurringSummary(
        rules: List<RecurringRule>,
        primary: Currency,
        today: LocalDate,
        savingsAccountIds: Set<Long>,
    ): RecurringSummary {
        // Everything not yet over feeds the "next charge" line: a rule starting
        // next quarter has a real, useful upcoming date.
        val flows = rules.filter { it.isFlow() && !it.hasEndedBy(today) }
        val plannedSavings = rules.filter { it.isPlannedSavingsInto(savingsAccountIds, primary, today) }
        if (flows.isEmpty() && plannedSavings.isEmpty()) return RecurringSummary()

        // The monthly figures instead price only the rules that carry a cost
        // into this month, and are scoped to the primary currency: a schedule
        // that starts next quarter costs nothing now.
        val primaryFlows = flows.filter { it.currency == primary && it.runsInMonthOf(today) }
        return RecurringSummary(
            monthlyExpenses = primaryFlows
                .filter { it.type == TransactionType.EXPENSE }
                .sumMonthlyEquivalent(),
            monthlyIncomes = primaryFlows
                .filter { it.type == TransactionType.INCOME }
                .sumMonthlyEquivalent(),
            monthlyTransfersToSavings = plannedSavings.sumMonthlyEquivalent(),
            next = nextRecurringEvent(flows, today),
            hasRules = true,
        )
    }

    private fun RecurringRule.isFlow(): Boolean =
        type == TransactionType.EXPENSE || type == TransactionType.INCOME

    /** A same-currency recurring transfer landing in a savings account (planned savings). */
    private fun RecurringRule.isPlannedSavingsInto(
        savingsAccountIds: Set<Long>,
        primary: Currency,
        today: LocalDate,
    ): Boolean = type == TransactionType.TRANSFER && amount != null && currency == primary &&
        transferAccountId in savingsAccountIds && runsInMonthOf(today)

    private fun List<RecurringRule>.sumMonthlyEquivalent(): BigDecimal = fold(BigDecimal.ZERO) { acc, rule ->
        acc.add(RecurrenceCalculator.monthlyEquivalent(rule) ?: BigDecimal.ZERO)
    }

    /** The soonest upcoming charge or credit across the expense/income rules. */
    private fun nextRecurringEvent(flows: List<RecurringRule>, today: LocalDate): NextRecurringEvent? =
        flows.mapNotNull { rule ->
            val amount = rule.amount ?: return@mapNotNull null
            val signed = if (rule.type == TransactionType.EXPENSE) amount.negate() else amount
            val floor = rule.lastGeneratedDate?.plusDays(1)?.takeIf { it > today } ?: today
            RecurrenceCalculator.nextOccurrence(rule, floor)?.let { date ->
                NextRecurringEvent(rule.name, signed, rule.currency, date)
            }
        }.minByOrNull { it.date }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val RECENT_COUNT = 7

        /** Window of the balance sparkline on the hero card, today included. */
        const val SPARKLINE_DAYS = 30

        /** How many upcoming movements the dashboard card previews before summarizing. */
        const val UPCOMING_PREVIEW_COUNT = 3

        /** Last day of the month on which the recap teaser is offered. */
        const val RECAP_TEASER_MAX_DAY = 7
    }
}
