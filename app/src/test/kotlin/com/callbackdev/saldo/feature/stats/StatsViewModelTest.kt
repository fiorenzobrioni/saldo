package com.callbackdev.saldo.feature.stats

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountTotal
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryTotal
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.MonthlyTotal
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.usecase.ObserveBalanceHistoryUseCase
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Currency

@ExtendWith(MainDispatcherExtension::class)
class StatsViewModelTest {

    private val eur = Currency.getInstance("EUR")

    private val clock = Clock.fixed(
        Instant.parse("2026-07-10T10:15:00Z"),
        ZoneId.of("Europe/Rome"),
    )

    private val accountRepository = mockk<AccountRepository>()
    private val userPreferences = mockk<UserPreferencesRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val balanceHistory = mockk<ObserveBalanceHistoryUseCase>()

    private val checking = Account(
        id = 1L,
        name = "Checking",
        type = AccountType.CHECKING,
        currency = eur,
        initialBalance = BigDecimal.ZERO,
    )

    private val groceries = Category(
        id = 10L,
        name = "Groceries",
        type = CategoryType.EXPENSE,
        color = 0x66BB6A,
        icon = "shopping_cart",
    )
    private val transport = Category(
        id = 11L,
        name = "Transport",
        type = CategoryType.EXPENSE,
        color = 0x42A5F5,
        icon = "commute",
    )

    private fun viewModel(
        categoryTotals: List<CategoryTotal> = emptyList(),
        accountTotals: List<AccountTotal> = emptyList(),
        monthlyTotals: List<MonthlyTotal> = emptyList(),
        accounts: List<Account> = listOf(checking),
        currencyOverride: Currency? = null,
        expectedCurrency: Currency = eur,
    ): StatsViewModel {
        every { accountRepository.observeAccountsWithBalance() } returns
            flowOf(accounts.map { AccountWithBalance(it, BigDecimal.ZERO) })
        every { userPreferences.primaryCurrencyOverride } returns flowOf(currencyOverride)
        every { transactionRepository.observeCategoryTotals(any(), any(), expectedCurrency) } returns
            flowOf(categoryTotals)
        every { transactionRepository.observeAccountSpendTotals(any(), any(), expectedCurrency) } returns
            flowOf(accountTotals)
        every { transactionRepository.observeMonthlyTotals(any(), any(), expectedCurrency) } returns
            flowOf(monthlyTotals)
        every { balanceHistory(expectedCurrency, any()) } returns flowOf(emptyList())
        every { categoryRepository.observeCategories() } returns
            flowOf(listOf(groceries, transport))
        return StatsViewModel(
            accountRepository = accountRepository,
            userPreferences = userPreferences,
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            observeBalanceHistory = balanceHistory,
            clock = clock,
        )
    }

    private suspend fun ReceiveTurbine<StatsUiState>.loaded(): StatsUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }

    @Test
    fun `explicit currency override drives every stats query`() = runTest {
        val usd = Currency.getInstance("USD")
        val viewModel = viewModel(currencyOverride = usd, expectedCurrency = usd)

        viewModel.uiState.test {
            val state = loaded()
            // The mocks above only answer for USD: reaching a loaded state
            // proves every query ran with the override, not the plurality.
            assertEquals(usd, state.currency)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `slices carry BigDecimal percentages of the period spend, biggest first`() = runTest {
        val viewModel = viewModel(
            categoryTotals = listOf(
                CategoryTotal(groceries.id, BigDecimal("-25.00"), count = 3),
                CategoryTotal(transport.id, BigDecimal("-75.00"), count = 1),
            ),
        )

        viewModel.uiState.test {
            val state = loaded()
            assertEquals(listOf("Transport", "Groceries"), state.slices.map { it.category.name })
            assertEquals(listOf(75, 25), state.slices.map { it.percent })
            assertEquals(BigDecimal("100.00"), state.periodSpendTotal)
            assertTrue(state.hasData)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uncategorized spend becomes its own slice and counts in the center total`() = runTest {
        val viewModel = viewModel(
            categoryTotals = listOf(
                CategoryTotal(groceries.id, BigDecimal("-60.00"), count = 2),
                CategoryTotal(null, BigDecimal("-40.00"), count = 1),
            ),
        )

        viewModel.uiState.test {
            val state = loaded()
            assertEquals(BigDecimal("100.00"), state.periodSpendTotal)
            val uncategorized = state.slices.single { it.category == null }
            assertEquals(BigDecimal("40.00"), uncategorized.amount)
            assertEquals(40, uncategorized.percent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a category net-refunded above zero is not a slice`() = runTest {
        val viewModel = viewModel(
            categoryTotals = listOf(
                CategoryTotal(groceries.id, BigDecimal("-40.00"), count = 2),
                // Refund larger than the period's expenses: positive net.
                CategoryTotal(transport.id, BigDecimal("15.00"), count = 2),
            ),
        )

        viewModel.uiState.test {
            val state = loaded()
            assertEquals(listOf("Groceries"), state.slices.map { it.category.name })
            assertEquals(listOf(100), state.slices.map { it.percent })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `monthly points cover a fixed 12-month axis, zero-filled and clamped`() = runTest {
        val viewModel = viewModel(
            monthlyTotals = listOf(
                MonthlyTotal(YearMonth.of(2026, 6), BigDecimal("-80.00"), BigDecimal("120.00")),
                // Refund-heavy month: positive expense sum, clamped to zero.
                MonthlyTotal(YearMonth.of(2026, 5), BigDecimal("12.00"), BigDecimal.ZERO),
            ),
        )

        viewModel.uiState.test {
            val state = loaded()
            assertEquals(12, state.monthlyTotals.size)
            assertEquals(YearMonth.of(2025, 8), state.monthlyTotals.first().month)
            assertEquals(YearMonth.of(2026, 7), state.monthlyTotals.last().month)

            val june = state.monthlyTotals.single { it.month == YearMonth.of(2026, 6) }
            assertEquals(BigDecimal("80.00"), june.expense)
            assertEquals(BigDecimal("120.00"), june.income)

            val may = state.monthlyTotals.single { it.month == YearMonth.of(2026, 5) }
            assertEquals(BigDecimal.ZERO, may.expense)

            val empty = state.monthlyTotals.single { it.month == YearMonth.of(2026, 1) }
            assertEquals(BigDecimal.ZERO, empty.expense)
            assertEquals(BigDecimal.ZERO, empty.income)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `account bars scale against the top spender`() = runTest {
        val savings = checking.copy(id = 2L, name = "Savings")
        val viewModel = viewModel(
            accountTotals = listOf(
                AccountTotal(checking.id, BigDecimal("-200.00"), count = 5),
                AccountTotal(savings.id, BigDecimal("-50.00"), count = 1),
            ),
            accounts = listOf(checking, savings),
        )

        viewModel.uiState.test {
            val state = loaded()
            assertEquals(listOf("Checking", "Savings"), state.accountSpends.map { it.account.name })
            assertEquals(1f, state.accountSpends[0].fraction)
            assertEquals(0.25f, state.accountSpends[1].fraction)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `period selection anchors month and year on today and steps back`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            var state = loaded()
            assertEquals(StatsPeriod.Month(YearMonth.of(2026, 7)), state.period)

            viewModel.previousPeriod()
            state = awaitItem()
            assertEquals(StatsPeriod.Month(YearMonth.of(2026, 6)), state.period)

            viewModel.selectYearMode()
            state = awaitItem()
            assertEquals(StatsPeriod.Year(2026), state.period)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no data anywhere flags the empty state`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            val state = loaded()
            assertTrue(state.isEmpty)
            assertFalse(state.hasData)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
