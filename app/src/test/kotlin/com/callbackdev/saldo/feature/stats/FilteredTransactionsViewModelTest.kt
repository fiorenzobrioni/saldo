package com.callbackdev.saldo.feature.stats

import app.cash.turbine.test
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TagRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.navigation.FilteredTransactionsRoute
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class FilteredTransactionsViewModelTest {

    private val eur = Currency.getInstance("EUR")

    private val clock = Clock.fixed(
        Instant.parse("2026-07-10T10:15:00Z"),
        ZoneId.of("Europe/Rome"),
    )

    private val transactionRepository = mockk<TransactionRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val tagRepository = mockk<TagRepository>()

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
        day: LocalDate,
        amount: String = "-10.00",
        categoryId: Long? = groceries.id,
        accountId: Long = checking.id,
    ) = Transaction(
        id = id,
        type = TransactionType.EXPENSE,
        amount = BigDecimal(amount),
        currency = eur,
        accountId = accountId,
        timestamp = day.atTime(12, 0).toInstant(ZoneOffset.UTC),
        zoneOffset = ZoneOffset.UTC,
        categoryId = categoryId,
    )

    private fun viewModel(
        route: FilteredTransactionsRoute,
        transactions: List<Transaction>,
    ): FilteredTransactionsViewModel {
        every { transactionRepository.observeTransactions() } returns flowOf(transactions)
        every { accountRepository.observeAccountsWithBalance() } returns
            flowOf(listOf(AccountWithBalance(checking, BigDecimal.ZERO)))
        every { categoryRepository.observeCategories() } returns flowOf(listOf(groceries))
        every { tagRepository.observeTagAssignments() } returns flowOf(emptyMap())
        return FilteredTransactionsViewModel(
            route = route,
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            tagRepository = tagRepository,
            clock = clock,
            defaultDispatcher = UnconfinedTestDispatcher(),
        )
    }

    private fun julyRoute(categoryId: Long? = null, accountId: Long? = null) =
        FilteredTransactionsRoute(
            startEpochDay = LocalDate.of(2026, 7, 1).toEpochDay(),
            endEpochDayExclusive = LocalDate.of(2026, 8, 1).toEpochDay(),
            categoryId = categoryId,
            accountId = accountId,
        )

    @Test
    fun `route window and category seed the filters and resolve the title`() = runTest {
        val inside = transaction(id = 1L, day = LocalDate.of(2026, 7, 5))
        val outsideWindow = transaction(id = 2L, day = LocalDate.of(2026, 6, 30))
        val otherCategory = transaction(id = 3L, day = LocalDate.of(2026, 7, 6), categoryId = 99L)
        val viewModel = viewModel(
            route = julyRoute(categoryId = groceries.id),
            transactions = listOf(inside, outsideWindow, otherCategory),
        )

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals("Groceries", state.title)
            assertEquals(listOf(1L), state.days.single().items.map { it.id })
            assertEquals(1, state.count)
            assertEquals(BigDecimal("-10.00"), state.totals.single().net)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a window matching nothing is flagged empty`() = runTest {
        val viewModel = viewModel(
            route = julyRoute(accountId = 42L),
            transactions = listOf(transaction(id = 1L, day = LocalDate.of(2026, 7, 5))),
        )

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertTrue(state.isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
