package com.callbackdev.saldo.feature.transactions

import app.cash.turbine.test
import com.callbackdev.saldo.core.common.prefs.CsvSeparator
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TagRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.feature.transactions.export.TransactionsCsvExporter
import com.callbackdev.saldo.feature.transactions.filter.DatePreset
import com.callbackdev.saldo.feature.transactions.filter.TransactionFilters
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Currency

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class TransactionsViewModelTest {

    private val eur = Currency.getInstance("EUR")
    private val usd = Currency.getInstance("USD")

    private val clock = Clock.fixed(
        Instant.parse("2026-07-08T10:15:00Z"),
        ZoneId.of("Europe/Rome"),
    )

    private val transactionRepository = mockk<TransactionRepository>(relaxUnitFun = true)
    private val accountRepository = mockk<AccountRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val tagRepository = mockk<TagRepository>(relaxUnitFun = true)
    private val userPreferences = mockk<UserPreferencesRepository>(relaxUnitFun = true) {
        every { csvSeparator } returns flowOf(CsvSeparator.SEMICOLON)
        every { firstDayOfWeek } returns flowOf(DayOfWeek.MONDAY)
    }
    private val csvExporter = mockk<TransactionsCsvExporter>()

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

    private fun transaction(
        id: Long,
        type: TransactionType = TransactionType.EXPENSE,
        amount: String = "-10.00",
        currency: Currency = eur,
        timestamp: Instant = Instant.parse("2026-07-08T08:00:00Z"),
        offsetHours: Int = 2,
        categoryId: Long? = groceries.id,
    ) = Transaction(
        id = id,
        type = type,
        amount = BigDecimal(amount),
        currency = currency,
        accountId = checking.id,
        timestamp = timestamp,
        zoneOffset = ZoneOffset.ofHours(offsetHours),
        categoryId = categoryId,
        transferAccountId = if (type == TransactionType.TRANSFER) 2L else null,
        transferAmount = if (type == TransactionType.TRANSFER) BigDecimal(amount).abs() else null,
        transferCurrency = if (type == TransactionType.TRANSFER) currency else null,
    )

    private fun viewModel(
        transactions: List<Transaction> = emptyList(),
        accounts: List<Account> = listOf(checking),
        tags: List<Tag> = emptyList(),
        tagAssignments: Map<Long, Set<Long>> = emptyMap(),
    ): TransactionsViewModel {
        every { transactionRepository.observeTransactions() } returns flowOf(transactions)
        every { accountRepository.observeAccountsWithBalance() } returns
            flowOf(accounts.map { AccountWithBalance(it, BigDecimal.ZERO) })
        every { categoryRepository.observeCategories() } returns flowOf(listOf(groceries))
        every { tagRepository.observeTags() } returns flowOf(tags)
        every { tagRepository.observeTagAssignments() } returns flowOf(tagAssignments)
        return TransactionsViewModel(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            tagRepository = tagRepository,
            userPreferences = userPreferences,
            csvExporter = csvExporter,
            clock = clock,
            defaultDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @Test
    fun `movements are grouped by the day of their own offset`() = runTest {
        // Same instant: 22:30Z is 00:30 of July 9th at UTC+2 but 21:30 of
        // July 8th at UTC-1 (ADR 7: the saved offset decides the day).
        val instant = Instant.parse("2026-07-08T22:30:00Z")
        val late = transaction(id = 1L, timestamp = instant, offsetHours = 2)
        val early = transaction(id = 2L, timestamp = instant, offsetHours = -1)
        val viewModel = viewModel(transactions = listOf(late, early))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals(
                listOf(LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 8)),
                state.days.map { it.date },
            )
            assertEquals(LocalDate.of(2026, 7, 8), state.today)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `day totals net expenses and incomes per currency, ignoring transfers and adjustments`() =
        runTest {
            val viewModel = viewModel(
                transactions = listOf(
                    transaction(id = 1L, amount = "-10.00"),
                    transaction(id = 2L, type = TransactionType.INCOME, amount = "4.00"),
                    transaction(id = 3L, amount = "-5.00", currency = usd),
                    transaction(id = 4L, type = TransactionType.TRANSFER, amount = "-99.00"),
                    transaction(
                        id = 5L,
                        type = TransactionType.ADJUSTMENT,
                        amount = "7.00",
                        categoryId = null,
                    ),
                ),
            )

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.isLoading) state = awaitItem()
                val totals = state.days.single().totals
                assertEquals(
                    listOf(BigDecimal("-6.00") to eur, BigDecimal("-5.00") to usd),
                    totals.map { it.amount to it.currency },
                )
                assertEquals(5, state.days.single().items.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `hasAccounts requires at least one active account`() = runTest {
        val archived = checking.copy(isArchived = true)
        val viewModel = viewModel(accounts = listOf(archived))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertFalse(state.hasAccounts)
            assertTrue(state.isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting captures the tags and emits an undoable event`() = runTest {
        val target = transaction(id = 1L)
        every { tagRepository.observeTagsForTransaction(1L) } returns
            flowOf(listOf(Tag("work", id = 5L)))
        val viewModel = viewModel(transactions = listOf(target))

        viewModel.delete(
            TransactionListItem(target, checking, toAccount = null, category = groceries),
        )

        coVerify { transactionRepository.delete(target) }
        viewModel.events.test {
            assertEquals(
                TransactionsEvent.TransactionDeleted(target, listOf(5L)),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search narrows the list and the totals, and clearing restores it`() = runTest {
        val coffee = transaction(id = 1L, amount = "-3.00")
            .copy(description = "Caffè al bar")
        val groceriesRun = transaction(id = 2L, amount = "-20.00")
            .copy(description = "Spesa settimanale")
        val viewModel = viewModel(transactions = listOf(coffee, groceriesRun))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals(2, state.filteredCount)

            viewModel.setQuery("caffe")
            state = awaitItem()
            assertEquals(1, state.filteredCount)
            assertEquals(listOf(1L), state.days.single().items.map { it.id })
            assertEquals(BigDecimal("-3.00"), state.filteredTotals.single().net)
            assertTrue(state.filters.isActive)
            assertTrue(state.hasAnyTransactions)

            viewModel.clearFilters()
            state = awaitItem()
            assertEquals(2, state.filteredCount)
            // Clearing goes back to the default view (current month), not to "all".
            assertEquals(TransactionFilters.DEFAULT, state.filters)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the default view shows only the current month and the ALL preset lifts it`() = runTest {
        val thisMonth = transaction(id = 1L, timestamp = Instant.parse("2026-07-08T08:00:00Z"))
        val older = transaction(id = 2L, timestamp = Instant.parse("2026-05-02T08:00:00Z"))
        val viewModel = viewModel(transactions = listOf(thisMonth, older))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals(DatePreset.THIS_MONTH, state.filters.datePreset)
            assertEquals(listOf(1L), state.days.flatMap { day -> day.items.map { it.id } })

            viewModel.setDatePreset(DatePreset.ALL)
            state = awaitItem()
            assertEquals(2, state.filteredCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the filter badge ignores the default month preset and ALL`() {
        assertEquals(0, TransactionFilters.DEFAULT.activeCount)
        assertEquals(0, TransactionFilters.NONE.activeCount)
        assertEquals(1, TransactionFilters(datePreset = DatePreset.LAST_MONTH).activeCount)
    }

    @Test
    fun `a filter matching nothing flags no-results instead of empty`() = runTest {
        val viewModel = viewModel(transactions = listOf(transaction(id = 1L)))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.setQuery("nothing matches this")
            state = awaitItem()
            assertTrue(state.isNoResults)
            assertFalse(state.isEmpty)
            assertEquals(0, state.filteredCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tag filter uses the observed assignments`() = runTest {
        val tagged = transaction(id = 1L)
        val untagged = transaction(id = 2L)
        val viewModel = viewModel(
            transactions = listOf(tagged, untagged),
            tags = listOf(Tag("work", id = 5L)),
            tagAssignments = mapOf(1L to setOf(5L)),
        )

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.applyFilters(
                com.callbackdev.saldo.feature.transactions.filter.TransactionFilters(
                    tagIds = setOf(5L),
                ),
            )
            state = awaitItem()
            assertEquals(listOf(1L), state.days.single().items.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filtered totals split expenses and incomes per currency`() = runTest {
        val viewModel = viewModel(
            transactions = listOf(
                transaction(id = 1L, amount = "-10.00"),
                transaction(id = 2L, type = TransactionType.INCOME, amount = "4.00"),
                transaction(id = 3L, amount = "-5.00", currency = usd),
                transaction(id = 4L, type = TransactionType.TRANSFER, amount = "-99.00"),
            ),
        )

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            val byCurrency = state.filteredTotals.associateBy { it.currency }
            assertEquals(BigDecimal("-10.00"), byCurrency.getValue(eur).expenses)
            assertEquals(BigDecimal("4.00"), byCurrency.getValue(eur).incomes)
            assertEquals(BigDecimal("-6.00"), byCurrency.getValue(eur).net)
            assertEquals(BigDecimal("-5.00"), byCurrency.getValue(usd).net)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `undo re-inserts the movement and restores its tags`() = runTest {
        val target = transaction(id = 1L)
        coEvery { transactionRepository.upsert(any()) } returns 99L
        val viewModel = viewModel()

        viewModel.undoDelete(TransactionsEvent.TransactionDeleted(target, listOf(5L)))

        coVerify { transactionRepository.upsert(target.copy(id = 0L)) }
        coVerify { tagRepository.setTagsForTransaction(99L, listOf(5L)) }
    }
}
