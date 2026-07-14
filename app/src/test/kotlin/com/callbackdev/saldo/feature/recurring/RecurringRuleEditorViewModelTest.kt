package com.callbackdev.saldo.feature.recurring

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurrenceMode
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.usecase.GenerateRecurringMovementsUseCase
import com.callbackdev.saldo.navigation.RecurringRuleEditorRoute
import com.callbackdev.saldo.testing.MainDispatcherExtension
import app.cash.turbine.test
import io.mockk.coEvery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    private val generateRecurringMovements = mockk<GenerateRecurringMovementsUseCase>(relaxed = true)
    private val applicationScope = CoroutineScope(Dispatchers.Unconfined)

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

    private val salaryCategory = Category(
        id = 20L,
        name = "Stipendio",
        type = CategoryType.INCOME,
        color = 0x66BB6A,
        icon = "payments",
    )

    private val bothCategory = Category(
        id = 30L,
        name = "Varie",
        type = CategoryType.BOTH,
        color = 0x26A69A,
        icon = "category",
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
            generateRecurringMovements,
            applicationScope,
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
    fun `a new income rule filters categories by type and defaults to the salary category`() = runTest {
        val viewModel = viewModel(
            route = RecurringRuleEditorRoute(initialTypeName = TransactionType.INCOME.name),
            categories = listOf(subscriptionsCategory, salaryCategory, bothCategory),
        )

        with(viewModel.uiState.value) {
            assertEquals(TransactionType.INCOME, type)
            assertEquals(listOf(20L, 30L), categories.map { it.id })
            assertEquals(20L, categoryId)
            assertEquals("payments", icon)
        }
    }

    @Test
    fun `saving a new income rule builds an income-typed rule`() = runTest {
        val saved = slot<RecurringRule>()
        coEvery { recurringRuleRepository.upsert(capture(saved)) } returns 1L
        val viewModel = viewModel(
            route = RecurringRuleEditorRoute(initialTypeName = TransactionType.INCOME.name),
            categories = listOf(subscriptionsCategory, salaryCategory),
        )

        viewModel.onNameChanged("Stipendio")
        viewModel.onAmountChanged("2000")
        viewModel.save()

        with(saved.captured) {
            assertEquals(TransactionType.INCOME, type)
            assertEquals(BigDecimal("2000"), amount)
            assertEquals(20L, categoryId)
        }
    }

    @Test
    fun `editing an income rule keeps its type and filters categories accordingly`() = runTest {
        val existing = RecurringRule(
            id = 9L,
            name = "Stipendio",
            type = TransactionType.INCOME,
            currency = eur,
            accountId = 1L,
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = LocalDate.of(2026, 1, 27),
            amount = BigDecimal("2000.00"),
            categoryId = 20L,
            dayOfReference = 27,
            lastGeneratedDate = LocalDate.of(2026, 6, 27),
        )
        coEvery { recurringRuleRepository.getRule(9L) } returns existing
        val saved = slot<RecurringRule>()
        coEvery { recurringRuleRepository.upsert(capture(saved)) } returns 9L

        val viewModel = viewModel(
            route = RecurringRuleEditorRoute(ruleId = 9L),
            categories = listOf(subscriptionsCategory, salaryCategory, bothCategory),
        )
        with(viewModel.uiState.value) {
            assertEquals(TransactionType.INCOME, type)
            assertEquals(listOf(20L, 30L), categories.map { it.id })
        }

        viewModel.onAmountChanged("2100")
        viewModel.save()

        assertEquals(TransactionType.INCOME, saved.captured.type)
        assertEquals(BigDecimal("2100"), saved.captured.amount)
    }

    @Test
    fun `an unknown or missing initial type falls back to expense`() = runTest {
        val viewModel = viewModel(
            route = RecurringRuleEditorRoute(initialTypeName = "TRANSFER"),
            categories = listOf(subscriptionsCategory, salaryCategory),
        )

        with(viewModel.uiState.value) {
            assertEquals(TransactionType.EXPENSE, type)
            assertEquals(listOf(10L), categories.map { it.id })
        }
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
    fun `enabling variable amount forces confirm mode and drops the amount requirement`() = runTest {
        val saved = slot<RecurringRule>()
        coEvery { recurringRuleRepository.upsert(capture(saved)) } returns 1L
        val viewModel = viewModel()

        viewModel.onNameChanged("Bolletta luce")
        viewModel.onVariableAmountToggled(true)

        with(viewModel.uiState.value) {
            assertTrue(isVariableAmount)
            assertEquals(RecurrenceMode.CONFIRM, mode)
        }

        viewModel.save() // no amount typed, but variable rules do not need one
        with(saved.captured) {
            assertNull(amount)
            assertTrue(isVariableAmount)
            assertEquals(RecurrenceMode.CONFIRM, mode)
        }
    }

    @Test
    fun `a fixed rule can be set to confirm mode`() = runTest {
        val saved = slot<RecurringRule>()
        coEvery { recurringRuleRepository.upsert(capture(saved)) } returns 1L
        val viewModel = viewModel()

        viewModel.onNameChanged("Netflix")
        viewModel.onAmountChanged("12,99")
        viewModel.onModeChanged(RecurrenceMode.CONFIRM)
        viewModel.save()

        with(saved.captured) {
            assertEquals(BigDecimal("12.99"), amount)
            assertFalse(isVariableAmount)
            assertEquals(RecurrenceMode.CONFIRM, mode)
        }
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

    @Test
    fun `editing preserves the reminder watermark so an edit does not re-notify`() = runTest {
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
            lastReminderDate = LocalDate.of(2026, 7, 18),
        )
        coEvery { recurringRuleRepository.getRule(7L) } returns existing
        val saved = slot<RecurringRule>()
        coEvery { recurringRuleRepository.upsert(capture(saved)) } returns 7L

        val viewModel = viewModel(route = RecurringRuleEditorRoute(ruleId = 7L))
        viewModel.onNameChanged("Disney Plus")
        viewModel.save()

        assertEquals(LocalDate.of(2026, 7, 18), saved.captured.lastReminderDate)
    }

    @Test
    fun `editing a rule tied to an archived account keeps that account resolvable and saves`() = runTest {
        val archived = account(3L, eur).copy(isArchived = true)
        val existing = RecurringRule(
            id = 8L,
            name = "Gym",
            type = TransactionType.EXPENSE,
            currency = eur,
            accountId = 3L,
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = LocalDate.of(2026, 1, 10),
            amount = BigDecimal("30.00"),
            categoryId = 10L,
            dayOfReference = 10,
            lastGeneratedDate = LocalDate.of(2026, 7, 10),
        )
        coEvery { recurringRuleRepository.getRule(8L) } returns existing
        val saved = slot<RecurringRule>()
        coEvery { recurringRuleRepository.upsert(capture(saved)) } returns 8L

        val viewModel = viewModel(
            route = RecurringRuleEditorRoute(ruleId = 8L),
            accounts = listOf(account(1L, eur), archived),
        )

        // The archived account the rule points to is still in the pickable list.
        assertEquals(3L, viewModel.uiState.value.accountId)
        assertEquals(listOf(1L, 3L), viewModel.uiState.value.accounts.map { it.id })

        viewModel.onAmountChanged("35,00")
        viewModel.save()

        viewModel.events.test {
            assertEquals(RecurringRuleEditorEvent.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(3L, saved.captured.accountId)
        assertEquals(BigDecimal("35.00"), saved.captured.amount)
    }
}
