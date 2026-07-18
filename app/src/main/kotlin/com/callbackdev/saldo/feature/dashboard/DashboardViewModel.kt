package com.callbackdev.saldo.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.BudgetProgress
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.DashboardTotals
import com.callbackdev.saldo.core.domain.model.DashboardWindows
import com.callbackdev.saldo.core.domain.model.PeriodTotals
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.SavingsGoalProgress
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import com.callbackdev.saldo.core.domain.model.primaryCurrency
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceCalculator
import com.callbackdev.saldo.core.common.prefs.DashboardCardPreferences
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.common.time.midnightTicker
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.usecase.DueStatement
import com.callbackdev.saldo.core.domain.usecase.ObserveBudgetProgressUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveDueStatementsUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveSafeToSpendUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveSavingsGoalsProgressUseCase
import com.callbackdev.saldo.core.domain.usecase.SafeToSpend
import com.callbackdev.saldo.feature.transactions.TransactionListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
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
    /** Active (non-archived) accounts with balances, for the expandable detail. */
    val accounts: List<AccountWithBalance> = emptyList(),
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
    /** Confirm-mode credit card statements waiting to be paid (primary currency shown). */
    val dueStatements: List<DueStatement> = emptyList(),
    val recent: List<TransactionListItem> = emptyList(),
    /** Budget progress in the primary currency, overall first (empty: no budgets set). */
    val budgets: List<BudgetProgress> = emptyList(),
    /** Safe-to-spend figure; null without an overall budget in the primary currency. */
    val safeToSpend: SafeToSpend? = null,
    /** Savings goals with progress, ordered for display (empty: no goals set). */
    val savingsGoals: List<SavingsGoalProgress> = emptyList(),
    /** Which optional cards the user keeps visible (Settings > Dashboard). */
    val cardPrefs: DashboardCardPreferences = DashboardCardPreferences(),
    val date: LocalDate = LocalDate.ofEpochDay(0),
    /** Greeting band and a stable [0,1) roll, both fixed once per app-open. */
    val greetingBand: GreetingBand = GreetingBand.MORNING,
    val greetingRoll: Float = 0f,
)

@HiltViewModel
@Suppress("LongParameterList") // The dashboard aggregates one source per card, all Hilt-injected.
class DashboardViewModel @Inject constructor(
    accountRepository: AccountRepository,
    userPreferences: UserPreferencesRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val observeBudgetProgress: ObserveBudgetProgressUseCase,
    private val observeSafeToSpend: ObserveSafeToSpendUseCase,
    private val observeDueStatements: ObserveDueStatementsUseCase,
    private val observeSavingsGoalsProgress: ObserveSavingsGoalsProgressUseCase,
    private val clock: Clock,
) : ViewModel() {

    // Fixed once when the ViewModel is created (once per app-open): the greeting
    // stays put across recomposition and rotation, and only re-rolls on a fresh
    // open. The roll indexes the band's message array in the composable.
    private val greetingBand: GreetingBand = GreetingBand.of(LocalTime.now(clock))
    private val greetingRoll: Float = Random.nextFloat()

    /** Everything the dashboard combines besides the accounts themselves. */
    private data class Sources(
        val totals: DashboardTotals,
        val recent: List<Transaction>,
        val categories: List<Category>,
        val rules: List<RecurringRule>,
        val pendingCount: Int,
    )

    /** The budget/goal figures that join on top of the core [Sources]. */
    private data class Extras(
        val budgets: List<BudgetProgress>,
        val safeToSpend: SafeToSpend?,
        val cardPrefs: DashboardCardPreferences,
        val dueStatements: List<DueStatement>,
        val savingsGoals: List<SavingsGoalProgress>,
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
        ::Triple,
    )
        .flatMapLatest { (accounts, currencyOverride, today) ->
            val primary = primaryCurrency(accounts, currencyOverride)
            // The typed combine overloads stop at five flows, so the core
            // sources collapse first and the budget figures join on top.
            val sources = combine(
                transactionRepository.observeDashboardTotals(
                    windows = DashboardWindows.around(today, clock.zone),
                    currency = primary,
                ),
                transactionRepository.observeRecentTransactions(RECENT_COUNT),
                categoryRepository.observeCategories(),
                recurringRuleRepository.observeRules(),
                transactionRepository.observePendingTransactions(),
            ) { totals, recent, categories, rules, pending ->
                Sources(totals, recent, categories, rules, pending.size)
            }
            // Budget/goal figures collapse into one bundle so the whole dashboard
            // stays within the typed combine arity.
            val extras = combine(
                observeBudgetProgress(primary),
                observeSafeToSpend(primary),
                userPreferences.dashboardCardPreferences,
                observeDueStatements(),
                observeSavingsGoalsProgress(),
            ) { budgets, safeToSpend, cardPrefs, dueStatements, savingsGoals ->
                Extras(budgets, safeToSpend, cardPrefs, dueStatements, savingsGoals)
            }
            combine(sources, extras) { collapsed, bundle ->
                buildState(
                    accounts = accounts,
                    primary = primary,
                    today = today,
                    sources = collapsed,
                    budgets = bundle.budgets,
                    safeToSpend = bundle.safeToSpend,
                    cardPrefs = bundle.cardPrefs,
                    dueStatements = bundle.dueStatements.filter { it.currency == primary },
                    savingsGoals = bundle.savingsGoals.filter { it.goal.currency == primary },
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

    private fun buildState(
        accounts: List<AccountWithBalance>,
        primary: Currency,
        today: LocalDate,
        sources: Sources,
        budgets: List<BudgetProgress>,
        safeToSpend: SafeToSpend?,
        cardPrefs: DashboardCardPreferences,
        dueStatements: List<DueStatement>,
        savingsGoals: List<SavingsGoalProgress>,
    ): DashboardUiState {
        val active = accounts.filter { !it.account.isArchived }
        val totalBalance = active
            .filter { it.account.isIncludedInTotal && it.account.currency == primary }
            .fold(BigDecimal.ZERO) { acc, item -> acc.add(item.balance) }

        val totals = sources.totals
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
            )
        }

        return DashboardUiState(
            isLoading = false,
            hasAccounts = active.isNotEmpty(),
            primaryCurrency = primary,
            totalBalance = totalBalance,
            accounts = active,
            today = totals.today,
            month = totals.month,
            monthVsPreviousToDate = comparison,
            previousMonthSpendToDate = previousReference,
            spentMoreThanLastMonth = spentMore,
            recurring = recurringSummary(sources.rules, primary, today, savingsAccountIds(accounts)),
            pendingCount = sources.pendingCount,
            dueStatements = dueStatements,
            recent = recent,
            budgets = budgets,
            safeToSpend = safeToSpend,
            savingsGoals = savingsGoals,
            cardPrefs = cardPrefs,
            date = today,
            greetingBand = greetingBand,
            greetingRoll = greetingRoll,
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
        val flows = rules.filter { it.isFlow() && it.isActiveOn(today) }
        val plannedSavings = rules.filter { it.isPlannedSavingsInto(savingsAccountIds, primary, today) }
        if (flows.isEmpty() && plannedSavings.isEmpty()) return RecurringSummary()

        // Totals are scoped to the primary currency, so the figures stay coherent.
        val primaryFlows = flows.filter { it.currency == primary }
        return RecurringSummary(
            monthlyExpenses = monthlyEquivalentOf(primaryFlows, TransactionType.EXPENSE),
            monthlyIncomes = monthlyEquivalentOf(primaryFlows, TransactionType.INCOME),
            monthlyTransfersToSavings = plannedSavings.sumMonthlyEquivalent(),
            next = nextRecurringEvent(flows, today),
            hasRules = true,
        )
    }

    private fun RecurringRule.isActiveOn(today: LocalDate): Boolean = endDate == null || endDate >= today

    private fun RecurringRule.isFlow(): Boolean =
        type == TransactionType.EXPENSE || type == TransactionType.INCOME

    /** A same-currency recurring transfer landing in a savings account (planned savings). */
    private fun RecurringRule.isPlannedSavingsInto(
        savingsAccountIds: Set<Long>,
        primary: Currency,
        today: LocalDate,
    ): Boolean = type == TransactionType.TRANSFER && amount != null && currency == primary &&
        transferAccountId in savingsAccountIds && isActiveOn(today)

    private fun monthlyEquivalentOf(rules: List<RecurringRule>, type: TransactionType): BigDecimal =
        rules.filter { it.type == type }.sumMonthlyEquivalent()

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
    }
}
