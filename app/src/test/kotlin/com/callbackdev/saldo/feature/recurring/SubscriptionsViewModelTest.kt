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
class SubscriptionsViewModelTest {

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
    private val ended = rule(
        5L, "Old", RecurrenceFrequency.MONTHLY, "5.00",
        LocalDate.of(2025, 1, 1), endDate = LocalDate.of(2026, 1, 1),
    )

    private fun viewModel(rules: List<RecurringRule>): SubscriptionsViewModel {
        every { recurringRuleRepository.observeRules() } returns flowOf(rules)
        every { accountRepository.observeAccountsWithBalance() } returns flowOf(emptyList<AccountWithBalance>())
        every { categoryRepository.observeCategories() } returns flowOf(emptyList<Category>())
        return SubscriptionsViewModel(recurringRuleRepository, accountRepository, categoryRepository, clock)
    }

    private suspend fun ReceiveTurbine<SubscriptionsUiState>.awaitLoaded(): SubscriptionsUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }

    @Test
    fun `monthly total sums equivalents, annual projects over twelve months, count is active`() = runTest {
        val viewModel = viewModel(listOf(netflix, spotify, insurance, salary, ended))

        viewModel.uiState.test {
            val state = awaitLoaded()
            // Income (salary) and the ended rule are excluded.
            assertEquals(3, state.activeCount)
            // 12.99 + 9.99 + (96.00 / 6) = 38.98
            assertEquals(BigDecimal("38.98"), state.monthlyTotal)
            assertEquals(BigDecimal("467.76"), state.annualProjection)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `default sort is by next charge`() = runTest {
        val viewModel = viewModel(listOf(netflix, spotify, insurance))

        viewModel.uiState.test {
            val state = awaitLoaded()
            // Spotify 12 Jul, Netflix 7 Aug, Assicurazione 15 Sep.
            assertEquals(listOf("Spotify", "Netflix", "Assicurazione"), state.items.map { it.rule.name })
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
            assertEquals(listOf("Assicurazione", "Netflix", "Spotify"), state.items.map { it.rule.name })
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
            assertEquals(listOf("Assicurazione", "Netflix", "Spotify"), state.items.map { it.rule.name })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
