package com.callbackdev.saldo.feature.transactions

import app.cash.turbine.test
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
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Currency

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
    ): TransactionsViewModel {
        every { transactionRepository.observeTransactions() } returns flowOf(transactions)
        every { accountRepository.observeAccountsWithBalance() } returns
            flowOf(accounts.map { AccountWithBalance(it, BigDecimal.ZERO) })
        every { categoryRepository.observeCategories() } returns flowOf(listOf(groceries))
        return TransactionsViewModel(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            tagRepository = tagRepository,
            clock = clock,
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
    fun `undo re-inserts the movement and restores its tags`() = runTest {
        val target = transaction(id = 1L)
        coEvery { transactionRepository.upsert(any()) } returns 99L
        val viewModel = viewModel()

        viewModel.undoDelete(TransactionsEvent.TransactionDeleted(target, listOf(5L)))

        coVerify { transactionRepository.upsert(target.copy(id = 0L)) }
        coVerify { tagRepository.setTagsForTransaction(99L, listOf(5L)) }
    }
}
