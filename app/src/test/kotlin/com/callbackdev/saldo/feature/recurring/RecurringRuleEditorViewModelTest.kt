package com.callbackdev.saldo.feature.recurring

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.navigation.RecurringRuleEditorRoute
import com.callbackdev.saldo.testing.MainDispatcherExtension
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency

@ExtendWith(MainDispatcherExtension::class)
class RecurringRuleEditorViewModelTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val jpy: Currency = Currency.getInstance("JPY")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-09T09:00:00Z"), ZoneId.of("Europe/Rome"))
    private val today: LocalDate = LocalDate.of(2026, 7, 9)

    private val recurringRuleRepository = mockk<RecurringRuleRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val categoryRepository = mockk<CategoryRepository>()

    private fun account(id: Long, currency: Currency) = Account(
        id = id,
        name = "acc-$id",
        type = AccountType.CARD,
        currency = currency,
        initialBalance = BigDecimal.ZERO,
    )

    private val subscriptionsCategory = Category(
        id = 10L,
        name = "Abbonamenti",
        type = CategoryType.EXPENSE,
        color = 0x8D6E63,
        icon = "subscriptions",
    )

    private fun viewModel(
        route: RecurringRuleEditorRoute = RecurringRuleEditorRoute(),
        accounts: List<Account> = listOf(account(1L, eur), account(2L, jpy)),
        categories: List<Category> = listOf(subscriptionsCategory),
    ): RecurringRuleEditorViewModel {
        every { accountRepository.observeAccountsWithBalance() } returns
            flowOf(accounts.map { AccountWithBalance(it, BigDecimal.ZERO) })
        every { categoryRepository.observeCategories() } returns flowOf(categories)
        return RecurringRuleEditorViewModel(
            route,
            recurringRuleRepository,
            accountRepository,
            categoryRepository,
            clock,
        )
    }

    @Test
    fun `a new subscription defaults to monthly, today, the first account and the subscriptions category`() = runTest {
        val viewModel = viewModel()

        with(viewModel.uiState.value) {
            assertTrue(isNew)
            assertEquals(RecurrenceFrequency.MONTHLY, frequency)
            assertEquals(today, startDate)
            assertEquals(1L, accountId)
            assertEquals(10L, categoryId)
            assertEquals("subscriptions", icon)
        }
    }

    @Test
    fun `saving a new subscription builds an expense rule with the day of reference and no back-fill`() = runTest {
        val saved = slot<RecurringRule>()
        coEvery { recurringRuleRepository.upsert(capture(saved)) } returns 1L
        val viewModel = viewModel()

        viewModel.onNameChanged("  Netflix  ")
        viewModel.onAmountChanged("12,99")
        viewModel.onStartDateSelected(LocalDate.of(2026, 5, 7))
        viewModel.save()

        viewModel.events.test {
            assertEquals(RecurringRuleEditorEvent.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        with(saved.captured) {
            assertEquals(0L, id)
            assertEquals("Netflix", name)
            assertEquals(TransactionType.EXPENSE, type)
            assertEquals(eur, currency)
            assertEquals(1L, accountId)
            assertEquals(BigDecimal("12.99"), amount)
            assertEquals(RecurrenceFrequency.MONTHLY, frequency)
            assertEquals(LocalDate.of(2026, 5, 7), startDate)
            assertEquals(7, dayOfReference)
            assertEquals(10L, categoryId)
            // Started in the past: seed lastGeneratedDate so history is not back-filled.
            assertEquals(LocalDate.of(2026, 7, 7), lastGeneratedDate)
        }
    }

    @Test
    fun `a subscription starting today is not pre-charged`() = runTest {
        val saved = slot<RecurringRule>()
        coEvery { recurringRuleRepository.upsert(capture(saved)) } returns 1L
        val viewModel = viewModel()

        viewModel.onNameChanged("Spotify")
        viewModel.onAmountChanged("9,99")
        viewModel.save()

        assertNull(saved.captured.lastGeneratedDate)
    }

    @Test
    fun `saving without a name or amount surfaces validation and persists nothing`() = runTest {
        val viewModel = viewModel()

        viewModel.onNameChanged("Netflix")
        viewModel.save() // no amount yet

        assertTrue(viewModel.uiState.value.showValidation)
        coVerify(exactly = 0) { recurringRuleRepository.upsert(any()) }
    }

    @Test
    fun `switching to a zero-decimal currency account drops typed decimals`() = runTest {
        val viewModel = viewModel()

        viewModel.onAmountChanged("12,34")
        viewModel.onAccountSelected(2L) // JPY account

        assertEquals("12", viewModel.uiState.value.amountInput)
    }

    @Test
    fun `editing loads the rule and preserves its progress and identity`() = runTest {
        val existing = RecurringRule(
            id = 7L,
            name = "Disney+",
            type = TransactionType.EXPENSE,
            currency = eur,
            accountId = 1L,
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = LocalDate.of(2026, 1, 18),
            amount = BigDecimal("8.99"),
            categoryId = 10L,
            dayOfReference = 18,
            lastGeneratedDate = LocalDate.of(2026, 6, 18),
            color = 0x42A5F5,
            icon = "movie",
        )
        coEvery { recurringRuleRepository.getRule(7L) } returns existing
        val saved = slot<RecurringRule>()
        coEvery { recurringRuleRepository.upsert(capture(saved)) } returns 7L

        val viewModel = viewModel(route = RecurringRuleEditorRoute(ruleId = 7L))
        with(viewModel.uiState.value) {
            assertEquals("Disney+", name)
            assertEquals("8.99", amountInput)
            assertEquals("movie", icon)
        }

        viewModel.onNameChanged("Disney Plus")
        viewModel.save()

        with(saved.captured) {
            assertEquals(7L, id)
            assertEquals("Disney Plus", name)
            // Untouched: generation progress is preserved so movements are not re-created.
            assertEquals(LocalDate.of(2026, 6, 18), lastGeneratedDate)
        }
    }
}
