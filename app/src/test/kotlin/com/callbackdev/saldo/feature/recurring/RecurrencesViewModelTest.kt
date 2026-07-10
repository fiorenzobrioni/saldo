package com.callbackdev.saldo.feature.recurring

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
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

    private fun viewModel(rules: List<RecurringRule>): RecurrencesViewModel {
        every { recurringRuleRepository.observeRules() } returns flowOf(rules)
        every { accountRepository.observeAccountsWithBalance() } returns flowOf(emptyList<AccountWithBalance>())
        every { categoryRepository.observeCategories() } returns flowOf(emptyList<Category>())
        return RecurrencesViewModel(recurringRuleRepository, accountRepository, categoryRepository, clock)
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
