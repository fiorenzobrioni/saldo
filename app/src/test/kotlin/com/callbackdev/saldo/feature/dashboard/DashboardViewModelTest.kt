package com.callbackdev.saldo.feature.dashboard

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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

    // Fixed "now" = 8 July 2026, so today/month windows are deterministic.
    private val clock: Clock =
        Clock.fixed(Instant.parse("2026-07-08T10:00:00Z"), ZoneId.of("Europe/Rome"))

    private val accountRepository = mockk<AccountRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val recurringRuleRepository = mockk<RecurringRuleRepository>()

    private fun account(
        id: Long,
        currency: Currency = eur,
        includedInTotal: Boolean = true,
        archived: Boolean = false,
    ) = Account(
        id = id,
        name = "acc-$id",
        type = AccountType.CHECKING,
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
        transactions: List<Transaction> = emptyList(),
        categories: List<Category> = emptyList(),
        rules: List<RecurringRule> = emptyList(),
    ): DashboardViewModel {
        every { accountRepository.observeAccountsWithBalance() } returns flowOf(accounts)
        every { transactionRepository.observeTransactions() } returns flowOf(transactions)
        every { transactionRepository.observePendingTransactions() } returns flowOf(emptyList<Transaction>())
        every { categoryRepository.observeCategories() } returns flowOf(categories)
        every { recurringRuleRepository.observeRules() } returns flowOf(rules)
        return DashboardViewModel(
            accountRepository,
            transactionRepository,
            categoryRepository,
            recurringRuleRepository,
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
    fun `today and month flows and the month-over-month comparison are computed`() = runTest {
        val transactions = listOf(
            tx(1L, TransactionType.EXPENSE, "-18.90", LocalDate.of(2026, 7, 8)),
            tx(2L, TransactionType.INCOME, "5.00", LocalDate.of(2026, 7, 8)),
            tx(3L, TransactionType.EXPENSE, "-100.00", LocalDate.of(2026, 7, 1)),
            tx(4L, TransactionType.EXPENSE, "-50.00", LocalDate.of(2026, 6, 5)),
            tx(5L, TransactionType.EXPENSE, "-30.00", LocalDate.of(2026, 6, 20)),
            // Transfers never count as spend/income.
            tx(6L, TransactionType.TRANSFER, "-200.00", LocalDate.of(2026, 7, 8)),
        )
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal("0.00"))),
            transactions = transactions,
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
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recent movements are capped at seven and resolved against account and category`() = runTest {
        val category = Category(id = 9L, name = "Food", type = CategoryType.EXPENSE, color = 0x1, icon = "restaurant")
        val transactions = (1..8L).map { index ->
            tx(index, TransactionType.EXPENSE, "-1.00", LocalDate.of(2026, 7, 8), categoryId = 9L)
        }
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(1L, eur), BigDecimal("0.00"))),
            transactions = transactions,
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
    fun `subscriptions summary totals active recurring expenses and picks the next charge`() = runTest {
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
            assertEquals(2, state.subscriptions.activeCount)
            // 12.99 + 96.00/6 = 28.99
            assertEquals(BigDecimal("28.99"), state.subscriptions.monthlyTotal)
            assertEquals("Netflix", state.subscriptions.next?.name)
            assertEquals(LocalDate.of(2026, 7, 12), state.subscriptions.next?.date)
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
}
