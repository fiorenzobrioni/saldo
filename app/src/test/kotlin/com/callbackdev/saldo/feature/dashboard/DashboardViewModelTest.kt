package com.callbackdev.saldo.feature.dashboard

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.DashboardTotals
import com.callbackdev.saldo.core.domain.model.DashboardWindows
import com.callbackdev.saldo.core.domain.model.PeriodTotals
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.common.prefs.DashboardCardPreferences
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.model.BudgetProgress
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.usecase.ObserveBudgetProgressUseCase
import com.callbackdev.saldo.core.domain.model.CounterpartyLedger
import com.callbackdev.saldo.core.domain.model.DailyBalance
import com.callbackdev.saldo.core.domain.model.SavingsGoalProgress
import com.callbackdev.saldo.core.domain.rates.ConversionState
import com.callbackdev.saldo.core.domain.usecase.ObserveConversionStateUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveDailyBalanceHistoryUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveDueStatementsUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveSafeToSpendUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveUpcomingMovementsUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveCounterpartyBalancesUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveSavingsGoalsProgressUseCase
import com.callbackdev.saldo.core.domain.usecase.SafeToSpend
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Currency

@ExtendWith(MainDispatcherExtension::class)
class DashboardViewModelTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val usd: Currency = Currency.getInstance("USD")
    private val offset: ZoneOffset = ZoneOffset.ofHours(2)
    private val zone: ZoneId = ZoneId.of("Europe/Rome")

    // Fixed "now" = 8 July 2026, so today/month windows are deterministic.
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-08T10:00:00Z"), zone)

    private val accountRepository = mockk<AccountRepository>()
    private val userPreferences = mockk<UserPreferencesRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val recurringRuleRepository = mockk<RecurringRuleRepository>()
    private val observeBudgetProgress = mockk<ObserveBudgetProgressUseCase>()
    private val observeSafeToSpend = mockk<ObserveSafeToSpendUseCase>()
    private val observeDueStatements = mockk<ObserveDueStatementsUseCase>()
    private val observeSavingsGoalsProgress = mockk<ObserveSavingsGoalsProgressUseCase>()
    private val observeCounterpartyBalances = mockk<ObserveCounterpartyBalancesUseCase>()
    private val observeDailyBalanceHistory = mockk<ObserveDailyBalanceHistoryUseCase>()
    private val observeConversionState = mockk<ObserveConversionStateUseCase>()

    private fun account(
        id: Long,
        currency: Currency = eur,
        includedInTotal: Boolean = true,
        archived: Boolean = false,
        name: String = "acc-$id",
        type: AccountType = AccountType.CHECKING,
    ) = Account(
        id = id,
        name = name,
        type = type,
        currency = currency,
        initialBalance = BigDecimal.ZERO,
        isIncludedInTotal = includedInTotal,
        isArchived = archived,
    )

    private fun tx(
        id: Long,
        type: TransactionType,
        amount: String,
        date: LocalDate,
        currency: Currency = eur,
        categoryId: Long? = null,
    ) = Transaction(
        id = id,
        type = type,
        amount = BigDecimal(amount),
        currency = currency,
        accountId = 1L,
        timestamp = date.atTime(12, 0).toInstant(offset),
        zoneOffset = offset,
        categoryId = categoryId,
    )

    private fun viewModel(
        accounts: List<AccountWithBalance> = emptyList(),
        totals: DashboardTotals = DashboardTotals(),
        recent: List<Transaction> = emptyList(),
        categories: List<Category> = emptyList(),
        rules: List<RecurringRule> = emptyList(),
        currencyOverride: Currency? = null,
        budgets: List<BudgetProgress> = emptyList(),
        safeToSpend: SafeToSpend? = null,
        cardPrefs: DashboardCardPreferences = DashboardCardPreferences(),
        savingsGoals: List<SavingsGoalProgress> = emptyList(),
        counterparties: CounterpartyLedger = CounterpartyLedger(),
        balanceHistory: List<DailyBalance> = emptyList(),
        /** Confirmed movements dated in the future (ADR 36). */
        upcoming: List<Transaction> = emptyList(),
        clock: Clock = this.clock,
        dismissedRecapMonth: java.time.YearMonth? = null,
        previousMonthTotals: List<com.callbackdev.saldo.core.domain.model.MonthlyTotal> = emptyList(),
        balanceAccountsExpandedByDefault: Boolean = true,
    ): DashboardViewModel {
        every { accountRepository.observeAccountsWithBalance() } returns flowOf(accounts)
        every { accountRepository.observeAccountsWithBalanceAsOfToday(any()) } returns flowOf(accounts)
        every { userPreferences.primaryCurrencyOverride } returns flowOf(currencyOverride)
        every { userPreferences.dashboardCardPreferences } returns flowOf(cardPrefs)
        every {
            userPreferences.balanceAccountsExpandedByDefault
        } returns flowOf(balanceAccountsExpandedByDefault)
        every { userPreferences.dismissedRecapMonth } returns flowOf(dismissedRecapMonth)
        every {
            transactionRepository.observeMonthlyTotals(any(), any(), any())
        } returns flowOf(previousMonthTotals)
        every { transactionRepository.observeDashboardTotals(any(), any()) } returns flowOf(totals)
        every { transactionRepository.observeRecentTransactions(any()) } returns flowOf(recent)
        every { transactionRepository.observePendingTransactions() } returns flowOf(emptyList<Transaction>())
        every { categoryRepository.observeCategories() } returns flowOf(categories)
        every { recurringRuleRepository.observeRules() } returns flowOf(rules)
        every { observeBudgetProgress(any(), any()) } returns flowOf(budgets)
        every { observeSafeToSpend(any(), any()) } returns flowOf(safeToSpend)
        every { observeDueStatements() } returns flowOf(emptyList())
        every { observeSavingsGoalsProgress() } returns flowOf(savingsGoals)
        every { observeCounterpartyBalances() } returns flowOf(counterparties)
        every {
            observeDailyBalanceHistory(any(), any(), any(), any())
        } returns flowOf(balanceHistory)
        every { observeConversionState() } returns flowOf(ConversionState.INACTIVE)
        every { transactionRepository.observeTransactionsFrom(any()) } returns flowOf(upcoming)
        val observeUpcomingMovements = ObserveUpcomingMovementsUseCase(
            transactionRepository,
            accountRepository,
            userPreferences,
            observeConversionState,
            clock,
        )
        return DashboardViewModel(
            accountRepository,
            userPreferences,
            transactionRepository,
            categoryRepository,
            recurringRuleRepository,
            observeBudgetProgress,
            observeSafeToSpend,
            observeDueStatements,
            observeSavingsGoalsProgress,
            observeCounterpartyBalances,
            observeDailyBalanceHistory,
            observeUpcomingMovements,
            observeConversionState,
            clock,
        )
    }

    private suspend fun ReceiveTurbine<DashboardUiState>.awaitLoaded(): DashboardUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }

    @Test
    fun `total balance sums only included non-archived accounts in the primary currency`() = runTest {
        val accounts = listOf(
            AccountWithBalance(account(1L, eur), BigDecimal("100.00")),
            AccountWithBalance(account(2L, eur), BigDecimal("20.00")),
            AccountWithBalance(account(3L, usd), BigDecimal("50.00")),
            AccountWithBalance(account(4L, eur, includedInTotal = false), BigDecimal("999.00")),
            AccountWithBalance(account(5L, eur, archived = true), BigDecimal("888.00")),
        )
        val viewModel = viewModel(accounts = accounts)

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertTrue(state.hasAccounts)
            assertEquals(eur, state.primaryCurrency)
            assertEquals(BigDecimal("120.00"), state.totalBalance)
            // Active accounts (archived excluded) are exposed for the breakdown.
            assertEquals(4, state.accounts.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `total-balance breakdown follows the accounts screen order, type then name`() = runTest {
        // Fed in a deliberately scrambled order; the card breakdown must present
        // the accounts grouped by type (enum declaration order) then by name,
        // case-insensitive, mirroring the Accounts list screen.
        val accounts = listOf(
            AccountWithBalance(account(1L, name = "Zeta", type = AccountType.CASH), BigDecimal.ZERO),
            AccountWithBalance(account(2L, name = "banca", type = AccountType.CHECKING), BigDecimal.ZERO),
            AccountWithBalance(account(3L, name = "Alfa", type = AccountType.CHECKING), BigDecimal.ZERO),
            AccountWithBalance(account(4L, name = "PayPal", type = AccountType.DIGITAL_WALLET), BigDecimal.ZERO),
            AccountWithBalance(account(5L, name = "Libretto", type = AccountType.SAVINGS), BigDecimal.ZERO),
        )
        val viewModel = viewModel(accounts = accounts)

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(
                listOf("Alfa", "banca", "Libretto", "Zeta", "PayPal"),
                state.accounts.map { it.account.name },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sparkline window covers the last thirty days anchored to today`() = runTest {
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal.ZERO)),
        )
        // Stubbed after the helper so this capture wins over its any() stub.
        val days = slot<List<LocalDate>>()
        every {
            observeDailyBalanceHistory(eur, capture(days), any(), any())
        } returns flowOf(emptyList())

        viewModel.uiState.test {
            awaitLoaded()
            cancelAndIgnoreRemainingEvents()
        }

        val today = LocalDate.of(2026, 7, 8)
        assertEquals(30, days.captured.size)
        assertEquals(today.minusDays(29), days.captured.first())
        assertEquals(today, days.captured.last())
    }

    @Test
    fun `balance history flows into the ui state`() = runTest {
        val history = listOf(
            DailyBalance(LocalDate.of(2026, 7, 6), BigDecimal("100.00")),
            DailyBalance(LocalDate.of(2026, 7, 7), BigDecimal("80.00")),
            DailyBalance(LocalDate.of(2026, 7, 8), BigDecimal("120.00")),
        )
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal.ZERO)),
            balanceHistory = history,
        )

        viewModel.uiState.test {
            assertEquals(history, awaitLoaded().balanceHistory)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `balance as of today is surfaced when future-dated movements run the headline ahead`() = runTest {
        // Headline (every booking) 120.00, today's point (dated up to today)
        // 100.00: 20.00 sits in the future, so the card names the today figure.
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal("120.00"))),
            balanceHistory = listOf(DailyBalance(LocalDate.of(2026, 7, 8), BigDecimal("100.00"))),
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(BigDecimal("120.00"), state.totalBalance)
            assertEquals(BigDecimal("100.00"), state.balanceAsOfToday)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `balance as of today is null when the headline already is the today figure`() = runTest {
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal("100.00"))),
            balanceHistory = listOf(DailyBalance(LocalDate.of(2026, 7, 8), BigDecimal("100.00"))),
        )

        viewModel.uiState.test {
            assertNull(awaitLoaded().balanceAsOfToday)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recap teaser shows the previous month during the first week with data`() = runTest {
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal.ZERO)),
            clock = Clock.fixed(Instant.parse("2026-07-03T10:00:00Z"), zone),
            previousMonthTotals = listOf(
                com.callbackdev.saldo.core.domain.model.MonthlyTotal(
                    month = java.time.YearMonth.of(2026, 6),
                    expense = BigDecimal("-10.00"),
                    income = BigDecimal.ZERO,
                ),
            ),
        )

        viewModel.uiState.test {
            assertEquals(java.time.YearMonth.of(2026, 6), awaitLoaded().recapTeaserMonth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recap teaser is hidden after the first week`() = runTest {
        // Fixed clock is 8 July: past the teaser window.
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal.ZERO)),
            previousMonthTotals = listOf(
                com.callbackdev.saldo.core.domain.model.MonthlyTotal(
                    month = java.time.YearMonth.of(2026, 6),
                    expense = BigDecimal("-10.00"),
                    income = BigDecimal.ZERO,
                ),
            ),
        )

        viewModel.uiState.test {
            assertNull(awaitLoaded().recapTeaserMonth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recap teaser is hidden without previous month data`() = runTest {
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal.ZERO)),
            clock = Clock.fixed(Instant.parse("2026-07-03T10:00:00Z"), zone),
            previousMonthTotals = emptyList(),
        )

        viewModel.uiState.test {
            assertNull(awaitLoaded().recapTeaserMonth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recap teaser is hidden when the settings switch is off`() = runTest {
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal.ZERO)),
            clock = Clock.fixed(Instant.parse("2026-07-03T10:00:00Z"), zone),
            cardPrefs = DashboardCardPreferences(showRecapTeaser = false),
            previousMonthTotals = listOf(
                com.callbackdev.saldo.core.domain.model.MonthlyTotal(
                    month = java.time.YearMonth.of(2026, 6),
                    expense = BigDecimal("-10.00"),
                    income = BigDecimal.ZERO,
                ),
            ),
        )

        viewModel.uiState.test {
            assertNull(awaitLoaded().recapTeaserMonth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recap teaser is hidden after dismissal`() = runTest {
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal.ZERO)),
            clock = Clock.fixed(Instant.parse("2026-07-03T10:00:00Z"), zone),
            dismissedRecapMonth = java.time.YearMonth.of(2026, 6),
            previousMonthTotals = listOf(
                com.callbackdev.saldo.core.domain.model.MonthlyTotal(
                    month = java.time.YearMonth.of(2026, 6),
                    expense = BigDecimal("-10.00"),
                    income = BigDecimal.ZERO,
                ),
            ),
        )

        viewModel.uiState.test {
            assertNull(awaitLoaded().recapTeaserMonth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `balance forecast walks from the today balance with spend average and recurring charges`() = runTest {
        val history = listOf(
            DailyBalance(LocalDate.of(2026, 7, 7), BigDecimal("110.00")),
            DailyBalance(LocalDate.of(2026, 7, 8), BigDecimal("100.00")),
        )
        val rules = listOf(
            RecurringRule(
                id = 1L, name = "Netflix", type = TransactionType.EXPENSE, currency = eur, accountId = 1L,
                frequency = com.callbackdev.saldo.core.domain.model.RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.of(2026, 1, 20), amount = BigDecimal("10.00"), dayOfReference = 20,
            ),
        )
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal("100.00"))),
            // The daily average is built from non-recurring spend only: 80.00 in
            // 8 days = 10.00/day for the 23 remaining days.
            totals = DashboardTotals(
                monthToDateSpend = BigDecimal("100.00"),
                monthToDateNonRecurringSpend = BigDecimal("80.00"),
            ),
            rules = rules,
            balanceHistory = history,
        )

        viewModel.uiState.test {
            val forecast = awaitLoaded().balanceForecast
            assertEquals(LocalDate.of(2026, 7, 9), forecast.first().date)
            assertEquals(LocalDate.of(2026, 7, 31), forecast.last().date)
            // The walk starts from the last history point (100.00), the point
            // the drawn line ends on, so the dashed tail attaches to it.
            assertEquals(BigDecimal("90.00"), forecast.first().balance)
            // 100.00 - 23 x 10.00 - 10.00 (Netflix on the 20th).
            assertEquals(BigDecimal("-140.00"), forecast.last().balance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `balance forecast excludes recurring spend from the daily average`() = runTest {
        // A monthly rule already charged this month sits in monthToDateSpend but
        // not in the non-recurring base, so it must not bend the tail: no other
        // spend and no future occurrence this month leaves the forecast flat.
        val rules = listOf(
            RecurringRule(
                id = 1L, name = "Rent", type = TransactionType.EXPENSE, currency = eur, accountId = 1L,
                frequency = com.callbackdev.saldo.core.domain.model.RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.of(2026, 1, 1), amount = BigDecimal("1.00"), dayOfReference = 1,
                lastGeneratedDate = LocalDate.of(2026, 7, 1),
            ),
        )
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal("100.00"))),
            totals = DashboardTotals(
                monthToDateSpend = BigDecimal("1.00"),
                monthToDateNonRecurringSpend = BigDecimal.ZERO,
            ),
            rules = rules,
            balanceHistory = listOf(
                DailyBalance(LocalDate.of(2026, 7, 7), BigDecimal("100.00")),
                DailyBalance(LocalDate.of(2026, 7, 8), BigDecimal("100.00")),
            ),
        )

        viewModel.uiState.test {
            val forecast = awaitLoaded().balanceForecast
            assertEquals(BigDecimal("100.00"), forecast.first().balance)
            assertEquals(BigDecimal("100.00"), forecast.last().balance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `balance forecast is empty when the sparkline is hidden`() = runTest {
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal.TEN)),
            totals = DashboardTotals(monthToDateSpend = BigDecimal("80.00")),
            balanceHistory = listOf(DailyBalance(LocalDate.of(2026, 7, 8), BigDecimal.TEN)),
        )

        viewModel.uiState.test {
            assertTrue(awaitLoaded().balanceForecast.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `budget progress flows into the ui state`() = runTest {
        val progress = BudgetProgress(
            budget = com.callbackdev.saldo.core.domain.model.Budget(
                id = 1L,
                categoryId = null,
                amount = BigDecimal("500.00"),
                currency = eur,
            ),
            category = null,
            spent = BigDecimal("120.00"),
            fraction = 0.24f,
            level = com.callbackdev.saldo.core.domain.model.BudgetLevel.UNDER,
        )
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal.ZERO)),
            budgets = listOf(progress),
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(listOf(progress), state.budgets)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `card visibility preferences flow into the ui state`() = runTest {
        val prefs = DashboardCardPreferences(
            showBudget = false,
            showSafeToSpend = false,
            showRecentTransactions = false,
        )
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal.ZERO)),
            cardPrefs = prefs,
        )

        viewModel.uiState.test {
            assertEquals(prefs, awaitLoaded().cardPrefs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `explicit currency override wins over the account plurality`() = runTest {
        val accounts = listOf(
            AccountWithBalance(account(1L, eur), BigDecimal("100.00")),
            AccountWithBalance(account(2L, eur), BigDecimal("20.00")),
            AccountWithBalance(account(3L, usd), BigDecimal("50.00")),
        )
        val viewModel = viewModel(accounts = accounts, currencyOverride = usd)

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(usd, state.primaryCurrency)
            // The total is scoped to the chosen currency, not the majority one.
            assertEquals(BigDecimal("50.00"), state.totalBalance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `aggregate windows are derived from the clock and passed to the query`() = runTest {
        val windows = slot<DashboardWindows>()
        every { accountRepository.observeAccountsWithBalance() } returns flowOf(
            listOf(AccountWithBalance(account(1L, eur), BigDecimal.ZERO)),
        )
        every { accountRepository.observeAccountsWithBalanceAsOfToday(any()) } returns flowOf(
            listOf(AccountWithBalance(account(1L, eur), BigDecimal.ZERO)),
        )
        every { userPreferences.primaryCurrencyOverride } returns flowOf(null)
        every { userPreferences.dashboardCardPreferences } returns flowOf(DashboardCardPreferences())
        every { userPreferences.balanceAccountsExpandedByDefault } returns flowOf(true)
        every {
            transactionRepository.observeDashboardTotals(capture(windows), eur)
        } returns flowOf(DashboardTotals())
        every { transactionRepository.observeRecentTransactions(any()) } returns flowOf(emptyList())
        every { transactionRepository.observePendingTransactions() } returns flowOf(emptyList())
        every { categoryRepository.observeCategories() } returns flowOf(emptyList())
        every { recurringRuleRepository.observeRules() } returns flowOf(emptyList())
        every { observeBudgetProgress(any(), any()) } returns flowOf(emptyList())
        every { observeSafeToSpend(any(), any()) } returns flowOf(null)
        every { observeDueStatements() } returns flowOf(emptyList())
        every { observeSavingsGoalsProgress() } returns flowOf(emptyList())
        every { observeCounterpartyBalances() } returns flowOf(CounterpartyLedger())
        every {
            observeDailyBalanceHistory(any(), any(), any(), any())
        } returns flowOf(emptyList())
        every { observeConversionState() } returns flowOf(ConversionState.INACTIVE)
        every { transactionRepository.observeTransactionsFrom(any()) } returns flowOf(emptyList())
        val observeUpcomingMovements = ObserveUpcomingMovementsUseCase(
            transactionRepository,
            accountRepository,
            userPreferences,
            observeConversionState,
            clock,
        )
        val viewModel = DashboardViewModel(
            accountRepository,
            userPreferences,
            transactionRepository,
            categoryRepository,
            recurringRuleRepository,
            observeBudgetProgress,
            observeSafeToSpend,
            observeDueStatements,
            observeSavingsGoalsProgress,
            observeCounterpartyBalances,
            observeDailyBalanceHistory,
            observeUpcomingMovements,
            observeConversionState,
            clock,
        )

        viewModel.uiState.test {
            awaitLoaded()
            cancelAndIgnoreRemainingEvents()
        }

        val today = LocalDate.of(2026, 7, 8)
        assertEquals(today.atStartOfDay(zone).toInstant(), windows.captured.todayStart)
        assertEquals(today.plusDays(1).atStartOfDay(zone).toInstant(), windows.captured.todayEnd)
        assertEquals(
            LocalDate.of(2026, 7, 1).atStartOfDay(zone).toInstant(),
            windows.captured.monthStart,
        )
        assertEquals(
            LocalDate.of(2026, 8, 1).atStartOfDay(zone).toInstant(),
            windows.captured.monthEnd,
        )
        assertEquals(
            LocalDate.of(2026, 6, 1).atStartOfDay(zone).toInstant(),
            windows.captured.previousStart,
        )
        assertEquals(
            LocalDate.of(2026, 6, 9).atStartOfDay(zone).toInstant(),
            windows.captured.previousToDateEnd,
        )
    }

    @Test
    fun `today and month totals and the month-over-month comparison come from the aggregates`() = runTest {
        val totals = DashboardTotals(
            today = PeriodTotals(spend = BigDecimal("-18.90"), income = BigDecimal("5.00")),
            month = PeriodTotals(spend = BigDecimal("-118.90"), income = BigDecimal("5.00")),
            monthToDateSpend = BigDecimal("118.90"),
            previousMonthToDateSpend = BigDecimal("50.00"),
        )
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal("0.00"))),
            totals = totals,
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(BigDecimal("-18.90"), state.today.spend)
            assertEquals(BigDecimal("5.00"), state.today.income)
            assertEquals(BigDecimal("-13.90"), state.today.net)
            assertEquals(BigDecimal("-118.90"), state.month.spend)
            assertEquals(BigDecimal("5.00"), state.month.income)
            // 118.90 spent so far this month vs 50.00 by the 8th last month.
            assertEquals(BigDecimal("68.90"), state.monthVsPreviousToDate)
            assertEquals(BigDecimal("50.00"), state.previousMonthSpendToDate)
            assertTrue(state.spentMoreThanLastMonth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no last-month baseline yields no comparison and no spent-more flag`() = runTest {
        val totals = DashboardTotals(
            month = PeriodTotals(spend = BigDecimal("-118.90"), income = BigDecimal.ZERO),
            monthToDateSpend = BigDecimal("118.90"),
            previousMonthToDateSpend = BigDecimal.ZERO,
        )
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal("0.00"))),
            totals = totals,
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertNull(state.monthVsPreviousToDate)
            assertNull(state.previousMonthSpendToDate)
            assertFalse(state.spentMoreThanLastMonth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recent movements are resolved against account and category`() = runTest {
        val category = Category(id = 9L, name = "Food", type = CategoryType.EXPENSE, color = 0x1, icon = "restaurant")
        val recent = (1..7L).map { index ->
            tx(index, TransactionType.EXPENSE, "-1.00", LocalDate.of(2026, 7, 8), categoryId = 9L)
        }
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal("0.00"))),
            recent = recent,
            categories = listOf(category),
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(7, state.recent.size)
            assertNotNull(state.recent.first().account)
            assertEquals(category, state.recent.first().category)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recurring summary totals active expenses and picks the next charge with a negative sign`() = runTest {
        val rules = listOf(
            RecurringRule(
                id = 1L, name = "Netflix", type = TransactionType.EXPENSE, currency = eur, accountId = 1L,
                frequency = com.callbackdev.saldo.core.domain.model.RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.of(2026, 7, 12), amount = BigDecimal("12.99"), dayOfReference = 12,
            ),
            RecurringRule(
                id = 2L, name = "Insurance", type = TransactionType.EXPENSE, currency = eur, accountId = 1L,
                frequency = com.callbackdev.saldo.core.domain.model.RecurrenceFrequency.SEMIANNUAL,
                startDate = LocalDate.of(2026, 9, 15), amount = BigDecimal("96.00"), dayOfReference = 15,
            ),
        )
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal("0.00"))),
            rules = rules,
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertTrue(state.recurring.hasRules)
            // Only Netflix: it starts on 12 Jul, inside the current month, so it
            // is a real monthly cost. The insurance starts on 15 Sep and carries
            // no cost into July, so its 96.00/6 must not show up here.
            assertEquals(BigDecimal("12.99"), state.recurring.monthlyExpenses)
            assertEquals(BigDecimal.ZERO, state.recurring.monthlyIncomes)
            // It still feeds the "next charge" line, which is about dates, not cost.
            assertEquals("Netflix", state.recurring.next?.name)
            assertEquals(BigDecimal("-12.99"), state.recurring.next?.amount)
            assertEquals(LocalDate.of(2026, 7, 12), state.recurring.next?.date)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recurring summary totals both types and picks the earliest event across them`() = runTest {
        val rules = listOf(
            RecurringRule(
                id = 1L, name = "Netflix", type = TransactionType.EXPENSE, currency = eur, accountId = 1L,
                frequency = com.callbackdev.saldo.core.domain.model.RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.of(2026, 7, 12), amount = BigDecimal("12.99"), dayOfReference = 12,
            ),
            RecurringRule(
                id = 2L, name = "Salary", type = TransactionType.INCOME, currency = eur, accountId = 1L,
                frequency = com.callbackdev.saldo.core.domain.model.RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.of(2026, 7, 10), amount = BigDecimal("2000.00"), dayOfReference = 10,
            ),
        )
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal("0.00"))),
            rules = rules,
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(BigDecimal("12.99"), state.recurring.monthlyExpenses)
            assertEquals(BigDecimal("2000.00"), state.recurring.monthlyIncomes)
            // The salary on the 10th comes before the Netflix charge on the 12th.
            assertEquals("Salary", state.recurring.next?.name)
            assertEquals(BigDecimal("2000.00"), state.recurring.next?.amount)
            assertEquals(LocalDate.of(2026, 7, 10), state.recurring.next?.date)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recurring rules past their end date are excluded from the summary`() = runTest {
        val rules = listOf(
            RecurringRule(
                id = 1L, name = "Old gym", type = TransactionType.EXPENSE, currency = eur, accountId = 1L,
                frequency = com.callbackdev.saldo.core.domain.model.RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.of(2025, 1, 5), amount = BigDecimal("30.00"), dayOfReference = 5,
                endDate = LocalDate.of(2026, 6, 30),
            ),
        )
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal("0.00"))),
            rules = rules,
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertFalse(state.recurring.hasRules)
            assertEquals(BigDecimal.ZERO, state.recurring.monthlyExpenses)
            assertNull(state.recurring.next)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no accounts yields an empty dashboard`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertFalse(state.hasAccounts)
            assertTrue(state.recent.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `balance accounts breakdown starts from the settings default when expanded`() = runTest {
        val viewModel = viewModel(balanceAccountsExpandedByDefault = true)

        viewModel.balanceAccountsExpanded.test {
            // The stateIn initial (false) may or may not be observed before the
            // combine settles on the persisted default, depending on conflation;
            // either way the flow settles on the default (expanded).
            var value = awaitItem()
            while (!value) value = awaitItem()
            assertTrue(value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `balance accounts breakdown starts from the settings default when collapsed`() = runTest {
        val viewModel = viewModel(balanceAccountsExpandedByDefault = false)

        viewModel.balanceAccountsExpanded.test {
            // The default is off, matching the initial value: a single emission.
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling the balance accounts breakdown flips it regardless of the default`() = runTest {
        val viewModel = viewModel(balanceAccountsExpandedByDefault = false)

        viewModel.balanceAccountsExpanded.test {
            assertFalse(awaitItem())
            viewModel.toggleBalanceAccountsExpanded()
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `safe to spend breakdown starts collapsed and toggles`() = runTest {
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal.ZERO)),
        )

        viewModel.safeToSpendExpanded.test {
            assertFalse(awaitItem())
            viewModel.toggleSafeToSpendExpanded()
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the forecast starts from the today balance, not from the headline it runs ahead of`() = runTest {
        // A confirmed movement dated later this month is already in the account
        // balance (100.00) but not in the balance as of today (130.00). The tail
        // must attach to the today figure and apply the movement on its own day,
        // or it would book it twice: once at the start, once when it arrives.
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal("100.00"))),
            balanceHistory = listOf(
                DailyBalance(LocalDate.of(2026, 7, 7), BigDecimal("130.00")),
                DailyBalance(LocalDate.of(2026, 7, 8), BigDecimal("130.00")),
            ),
            upcoming = listOf(
                futureMovement(id = 1L, accountId = 1L, amount = "-30.00", day = 20),
            ),
        )

        viewModel.uiState.test {
            val state = awaitLoaded()

            assertEquals(BigDecimal("130.00"), state.balanceAsOfToday)
            val byDate = state.balanceForecast.associate { it.date to it.balance }
            assertEquals(BigDecimal("130.00"), byDate[LocalDate.of(2026, 7, 19)])
            assertEquals(BigDecimal("100.00"), byDate[LocalDate.of(2026, 7, 20)])
            assertEquals(BigDecimal("100.00"), state.balanceForecast.last().balance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a future movement on an account outside the total does not bend the tail`() = runTest {
        val viewModel = viewModel(
            accounts = listOf(
                AccountWithBalance(account(1L, eur), BigDecimal("100.00")),
                AccountWithBalance(
                    account(9L, eur, includedInTotal = false),
                    BigDecimal("500.00"),
                ),
            ),
            balanceHistory = listOf(
                DailyBalance(LocalDate.of(2026, 7, 7), BigDecimal("100.00")),
                DailyBalance(LocalDate.of(2026, 7, 8), BigDecimal("100.00")),
            ),
            upcoming = listOf(
                futureMovement(id = 1L, accountId = 9L, amount = "-400.00", day = 20),
            ),
        )

        viewModel.uiState.test {
            assertEquals(BigDecimal("100.00"), awaitLoaded().balanceForecast.last().balance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the upcoming card previews the soonest movements and counts the rest`() = runTest {
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal("100.00"))),
            upcoming = (1..5).map { index ->
                futureMovement(id = index.toLong(), accountId = 1L, amount = "-5.00", day = 9 + index)
            },
        )

        viewModel.uiState.test {
            val preview = awaitLoaded().upcoming

            assertEquals(5, preview.totalCount)
            assertEquals(listOf(1L, 2L, 3L), preview.items.map { it.id })
            assertEquals(2, preview.hiddenCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A confirmed movement dated on [day] of July 2026, after today (the 8th). */
    private fun futureMovement(
        id: Long,
        accountId: Long,
        amount: String,
        day: Int,
    ) = Transaction(
        id = id,
        type = TransactionType.EXPENSE,
        amount = BigDecimal(amount),
        currency = eur,
        accountId = accountId,
        timestamp = LocalDate.of(2026, 7, day).atTime(12, 0).atZone(zone).toInstant(),
        zoneOffset = java.time.ZoneOffset.ofHours(2),
        description = "future-$id",
    )
}
