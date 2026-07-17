package com.callbackdev.saldo.feature.recurring

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency

@ExtendWith(MainDispatcherExtension::class)
class RecurrencesViewModelTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-09T09:00:00Z"), ZoneId.of("Europe/Rome"))

    private val recurringRuleRepository = mockk<RecurringRuleRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val userPreferences = mockk<UserPreferencesRepository>()

    private fun rule(
        id: Long,
        name: String,
        frequency: RecurrenceFrequency,
        amount: String,
        startDate: LocalDate,
        type: TransactionType = TransactionType.EXPENSE,
        endDate: LocalDate? = null,
    ) = RecurringRule(
        id = id,
        name = name,
        type = type,
        currency = eur,
        accountId = 1L,
        frequency = frequency,
        startDate = startDate,
        amount = BigDecimal(amount),
        dayOfReference = startDate.dayOfMonth,
        endDate = endDate,
    )

    private val netflix =
        rule(1L, "Netflix", RecurrenceFrequency.MONTHLY, "12.99", LocalDate.of(2026, 7, 7))
    private val spotify =
        rule(2L, "Spotify", RecurrenceFrequency.MONTHLY, "9.99", LocalDate.of(2026, 7, 12))
    private val insurance =
        rule(3L, "Assicurazione", RecurrenceFrequency.SEMIANNUAL, "96.00", LocalDate.of(2026, 9, 15))
    private val salary = rule(
        4L, "Stipendio", RecurrenceFrequency.MONTHLY, "2000.00",
        LocalDate.of(2026, 7, 27), TransactionType.INCOME,
    )
    private val rent = rule(
        5L, "Affitto attivo", RecurrenceFrequency.MONTHLY, "650.00",
        LocalDate.of(2026, 7, 3), TransactionType.INCOME,
    )
    private val ended = rule(
        6L, "Old", RecurrenceFrequency.MONTHLY, "5.00",
        LocalDate.of(2025, 1, 1), endDate = LocalDate.of(2026, 1, 1),
    )
    private val endedIncome = rule(
        7L, "Old bonus", RecurrenceFrequency.MONTHLY, "100.00",
        LocalDate.of(2025, 1, 1), TransactionType.INCOME, endDate = LocalDate.of(2026, 1, 1),
    )

    private fun account(id: Long, type: AccountType) = Account(
        id = id,
        name = "acc-$id",
        type = type,
        currency = eur,
        initialBalance = BigDecimal.ZERO,
    )

    private fun transfer(
        id: Long,
        amount: String,
        toAccountId: Long,
        startDate: LocalDate = LocalDate.of(2026, 7, 5),
    ) = RecurringRule(
        id = id,
        name = "transfer-$id",
        type = TransactionType.TRANSFER,
        currency = eur,
        accountId = 1L,
        frequency = RecurrenceFrequency.MONTHLY,
        startDate = startDate,
        amount = BigDecimal(amount),
        dayOfReference = startDate.dayOfMonth,
        transferAccountId = toAccountId,
        transferAmount = BigDecimal(amount),
        transferCurrency = eur,
    )

    private fun viewModel(
        rules: List<RecurringRule>,
        currencyOverride: Currency? = null,
        accounts: List<Account> = emptyList(),
    ): RecurrencesViewModel {
        every { recurringRuleRepository.observeRules() } returns flowOf(rules)
        every { accountRepository.observeAccountsWithBalance() } returns
            flowOf(accounts.map { AccountWithBalance(it, BigDecimal.ZERO) })
        every { categoryRepository.observeCategories() } returns flowOf(emptyList<Category>())
        every { userPreferences.primaryCurrencyOverride } returns flowOf(currencyOverride)
        return RecurrencesViewModel(
            recurringRuleRepository,
            accountRepository,
            categoryRepository,
            userPreferences,
            clock,
        )
    }

    private suspend fun ReceiveTurbine<RecurrencesUiState>.awaitLoaded(): RecurrencesUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }

    @Test
    fun `monthly total sums equivalents, annual projects over twelve months, count is active`() = runTest {
        val viewModel = viewModel(listOf(netflix, spotify, insurance, salary, ended))

        viewModel.uiState.test {
            val state = awaitLoaded()
            // Income (salary) and the ended rule are excluded from the expenses tab.
            assertEquals(3, state.expenses.activeCount)
            // 12.99 + 9.99 + (96.00 / 6) = 38.98
            assertEquals(BigDecimal("38.98"), state.expenses.monthlyTotal)
            assertEquals(BigDecimal("467.76"), state.expenses.annualProjection)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `explicit currency override scopes the section totals over the item majority`() = runTest {
        val usd = Currency.getInstance("USD")
        val viewModel = viewModel(listOf(netflix, spotify), currencyOverride = usd)

        viewModel.uiState.test {
            val state = awaitLoaded()
            // All rules are EUR: with a USD override the section stays in USD
            // (consistent with dashboard and stats) and totals nothing.
            assertEquals(usd, state.expenses.currency)
            assertEquals(BigDecimal.ZERO, state.expenses.monthlyTotal)
            assertEquals(0, state.expenses.activeCount)
            // The rules themselves are still listed, each in its own currency.
            assertEquals(2, state.expenses.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `income section carries recurring incomes only, with totals and next credit`() = runTest {
        val viewModel = viewModel(listOf(netflix, salary, rent, endedIncome))

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(2, state.incomes.activeCount)
            // 2000.00 + 650.00; the ended income and the expense are excluded.
            assertEquals(BigDecimal("2650.00"), state.incomes.monthlyTotal)
            assertEquals(BigDecimal("31800.00"), state.incomes.annualProjection)
            // Default sort by next credit: salary 27 Jul before rent 3 Aug
            // (rent's July credit on the 3rd is already past today, 9 Jul).
            assertEquals(listOf("Stipendio", "Affitto attivo"), state.incomes.items.map { it.rule.name })
            assertEquals(LocalDate.of(2026, 7, 27), state.incomes.items[0].nextCharge)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expense and income sections do not leak into each other`() = runTest {
        val viewModel = viewModel(listOf(netflix, salary))

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(listOf("Netflix"), state.expenses.items.map { it.rule.name })
            assertEquals(listOf("Stipendio"), state.incomes.items.map { it.rule.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `transfers section carries transfers only and planned savings sums savings destinations`() = runTest {
        // Account 2 is a savings account; account 3 is checking.
        val toSavings = transfer(10L, "150.00", toAccountId = 2L)
        val toChecking = transfer(11L, "80.00", toAccountId = 3L)
        val viewModel = viewModel(
            rules = listOf(netflix, salary, toSavings, toChecking),
            accounts = listOf(
                account(1L, AccountType.CHECKING),
                account(2L, AccountType.SAVINGS),
                account(3L, AccountType.CHECKING),
            ),
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(
                listOf("transfer-10", "transfer-11"),
                state.transfers.items.map { it.rule.name }.sorted(),
            )
            // Only the transfer landing in the savings account counts.
            assertEquals(0, BigDecimal("150.00").compareTo(state.plannedMonthlySavings))
            // Transfers never surface in the expense or income tabs.
            assertEquals(listOf("Netflix"), state.expenses.items.map { it.rule.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `planned savings is zero when no transfer targets a savings account`() = runTest {
        val toChecking = transfer(11L, "80.00", toAccountId = 3L)
        val viewModel = viewModel(
            rules = listOf(toChecking),
            accounts = listOf(account(1L, AccountType.CHECKING), account(3L, AccountType.CHECKING)),
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(0, BigDecimal.ZERO.compareTo(state.plannedMonthlySavings))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `default sort is by next charge`() = runTest {
        val viewModel = viewModel(listOf(netflix, spotify, insurance))

        viewModel.uiState.test {
            val state = awaitLoaded()
            // Spotify 12 Jul, Netflix 7 Aug, Assicurazione 15 Sep.
            assertEquals(
                listOf("Spotify", "Netflix", "Assicurazione"),
                state.expenses.items.map { it.rule.name },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sort by cost orders by monthly equivalent descending`() = runTest {
        val viewModel = viewModel(listOf(netflix, spotify, insurance))

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.onSortSelected(SubscriptionSort.COST)
            var state = awaitItem()
            while (state.sort != SubscriptionSort.COST) state = awaitItem()
            // 16.00, 12.99, 9.99
            assertEquals(
                listOf("Assicurazione", "Netflix", "Spotify"),
                state.expenses.items.map { it.rule.name },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sort by name orders alphabetically`() = runTest {
        val viewModel = viewModel(listOf(netflix, spotify, insurance))

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.onSortSelected(SubscriptionSort.NAME)
            var state = awaitItem()
            while (state.sort != SubscriptionSort.NAME) state = awaitItem()
            assertEquals(
                listOf("Assicurazione", "Netflix", "Spotify"),
                state.expenses.items.map { it.rule.name },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
