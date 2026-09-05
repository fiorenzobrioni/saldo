package com.callbackdev.saldo.feature.accounts

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.rates.ConversionState
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TagRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.undo.UndoDeleteCoordinator
import com.callbackdev.saldo.core.domain.undo.UndoableDelete
import com.callbackdev.saldo.core.domain.usecase.AdjustBalanceUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveAccountBalanceHistoryUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveConversionStateUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveDueStatementsUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveLoanProgressUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveSavingsGoalsProgressUseCase
import com.callbackdev.saldo.core.domain.usecase.SettleCreditCardStatementUseCase
import com.callbackdev.saldo.navigation.AccountDetailRoute
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Currency

@ExtendWith(MainDispatcherExtension::class)
class AccountDetailViewModelTest {

    private val eur = Currency.getInstance("EUR")
    private val zone: ZoneId = ZoneId.of("Europe/Rome")
    // Fixed "today": 21 July 2026.
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-21T10:00:00Z"), zone)

    private val accountRepository = mockk<AccountRepository>(relaxUnitFun = true)
    private val transactionRepository = mockk<TransactionRepository>(relaxUnitFun = true)
    private val recurringRuleRepository = mockk<RecurringRuleRepository>()
    private val tagRepository = mockk<TagRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val adjustBalance = mockk<AdjustBalanceUseCase>()
    private val settleStatement = mockk<SettleCreditCardStatementUseCase>()
    private val observeDueStatements = mockk<ObserveDueStatementsUseCase>()
    private val observeLoanProgress = mockk<ObserveLoanProgressUseCase>()
    private val observeSavingsGoalsProgress = mockk<ObserveSavingsGoalsProgressUseCase>()
    private val observeAccountBalanceHistory = mockk<ObserveAccountBalanceHistoryUseCase>()
    private val userPreferences = mockk<UserPreferencesRepository>()
    private val observeConversionState = mockk<ObserveConversionStateUseCase>()
    private val undoCoordinator = UndoDeleteCoordinator()

    private val checking = Account(
        id = 1L,
        name = "Checking",
        type = AccountType.CHECKING,
        currency = eur,
        initialBalance = BigDecimal("100.00"),
    )
    private val savings = Account(
        id = 2L,
        name = "Savings",
        type = AccountType.SAVINGS,
        currency = eur,
        initialBalance = BigDecimal.ZERO,
    )

    private fun movement(
        id: Long,
        day: LocalDate,
        amount: String,
        type: TransactionType = TransactionType.EXPENSE,
        accountId: Long = 1L,
    ) = Transaction(
        id = id,
        type = type,
        amount = BigDecimal(amount),
        currency = eur,
        accountId = accountId,
        timestamp = LocalDateTime.of(day, LocalTime.NOON).toInstant(ZoneOffset.UTC),
        zoneOffset = ZoneOffset.UTC,
    )

    private fun viewModel(
        accounts: List<AccountWithBalance> = listOf(AccountWithBalance(checking, BigDecimal("74.50"))),
        transactions: List<Transaction> = emptyList(),
        accountId: Long = 1L,
    ): AccountDetailViewModel {
        every { accountRepository.observeAccountsWithBalanceAsOfToday(any()) } returns flowOf(accounts)
        coEvery { accountRepository.upsert(any()) } returns accountId
        every { categoryRepository.observeCategories() } returns flowOf(emptyList())
        every { transactionRepository.observeTransactionsForAccount(accountId) } returns flowOf(transactions)
        every { userPreferences.primaryCurrencyOverride } returns flowOf(null)
        every { observeConversionState() } returns flowOf(ConversionState.INACTIVE)
        every { observeDueStatements() } returns flowOf(emptyList())
        every { observeLoanProgress() } returns flowOf(emptyMap())
        every { observeSavingsGoalsProgress() } returns flowOf(emptyList())
        every { observeAccountBalanceHistory(any(), any()) } returns flowOf(emptyList())
        coEvery { recurringRuleRepository.countForAccount(any()) } returns 0
        return AccountDetailViewModel(
            route = AccountDetailRoute(accountId),
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            recurringRuleRepository = recurringRuleRepository,
            tagRepository = tagRepository,
            categoryRepository = categoryRepository,
            adjustBalance = adjustBalance,
            settleStatement = settleStatement,
            observeDueStatements = observeDueStatements,
            observeLoanProgress = observeLoanProgress,
            observeSavingsGoalsProgress = observeSavingsGoalsProgress,
            observeAccountBalanceHistory = observeAccountBalanceHistory,
            userPreferences = userPreferences,
            observeConversionState = observeConversionState,
            undoCoordinator = undoCoordinator,
            clock = clock,
        )
    }

    private suspend fun ReceiveTurbine<AccountDetailUiState>.awaitLoaded(): AccountDetailUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }

    @Test
    fun `the state carries the account and only the current month's movements`() = runTest {
        val viewModel = viewModel(
            transactions = listOf(
                movement(1L, LocalDate.of(2026, 7, 3), "-12.00"),
                movement(2L, LocalDate.of(2026, 7, 10), "-8.00"),
                movement(3L, LocalDate.of(2026, 7, 10), "500.00", type = TransactionType.INCOME),
                movement(4L, LocalDate.of(2026, 6, 28), "-40.00"),
            ),
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(checking, state.item?.account)
            assertEquals(YearMonth.of(2026, 7), state.month)
            assertEquals(3, state.monthMovementCount)
            // Newest day first, like the ledger.
            assertEquals(listOf(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 3)), state.days.map { it.date })
            val totals = state.monthTotals.single()
            assertEquals(BigDecimal("-20.00"), totals.expenses)
            assertEquals(BigDecimal("500.00"), totals.incomes)
            // June holds a movement, nothing is dated after July.
            assertTrue(state.canGoToPreviousMonth)
            assertFalse(state.canGoToNextMonth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stepping back stops at the earliest month with movements`() = runTest {
        val viewModel = viewModel(
            transactions = listOf(movement(4L, LocalDate.of(2026, 6, 28), "-40.00")),
        )

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.previousMonth()
            var state = awaitItem()
            assertEquals(YearMonth.of(2026, 6), state.month)
            assertEquals(1, state.monthMovementCount)
            assertFalse(state.canGoToPreviousMonth)
            assertTrue(state.canGoToNextMonth)
            // A further step back is refused: nothing older exists.
            viewModel.previousMonth()
            expectNoEvents()
            viewModel.nextMonth()
            state = awaitItem()
            assertEquals(YearMonth.of(2026, 7), state.month)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a future-dated movement opens the way to its month`() = runTest {
        val viewModel = viewModel(
            transactions = listOf(movement(5L, LocalDate.of(2026, 9, 2), "-15.00")),
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertTrue(state.canGoToNextMonth)
            assertEquals(0, state.monthMovementCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a missing account flags the state so the screen leaves`() = runTest {
        val viewModel = viewModel(accounts = listOf(AccountWithBalance(savings, BigDecimal.ZERO)), accountId = 1L)

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertTrue(state.isMissing)
            assertNull(state.item)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `archiving persists the flag and emits an undoable event`() = runTest {
        val viewModel = viewModel()

        viewModel.archive(checking)

        coVerify { accountRepository.upsert(checking.copy(isArchived = true)) }
        viewModel.events.test {
            assertEquals(AccountsEvent.AccountArchived(checking), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete request with movements proposes archiving instead`() = runTest {
        coEvery { transactionRepository.countForAccount(1L) } returns 3
        val viewModel = viewModel()

        viewModel.requestDelete(checking)

        viewModel.uiState.test {
            assertEquals(AccountsDialog.ArchiveInstead(checking, 3), awaitLoaded().dialog)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { accountRepository.delete(any()) }
    }

    @Test
    fun `delete request without movements asks for confirmation then deletes`() = runTest {
        coEvery { transactionRepository.countForAccount(1L) } returns 0
        val viewModel = viewModel()

        viewModel.requestDelete(checking)
        viewModel.uiState.test {
            assertEquals(AccountsDialog.ConfirmDelete(checking), awaitLoaded().dialog)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.confirmDelete()

        coVerify { accountRepository.delete(checking) }
        viewModel.events.test {
            assertEquals(AccountsEvent.AccountDeleted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `adjust flow computes the delta from the shown balance and records it`() = runTest {
        coEvery { adjustBalance(1L, BigDecimal("80.00")) } returns
            AdjustBalanceUseCase.Result.Adjusted(BigDecimal("5.50"))
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.openAdjustBalance()
            viewModel.onAdjustInputChanged("80,00")
            var state = awaitItem()
            while ((state.dialog as? AccountsDialog.AdjustBalance)?.input != "80,00") state = awaitItem()
            val dialog = state.dialog as AccountsDialog.AdjustBalance
            assertEquals(BigDecimal("5.50"), dialog.delta)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.confirmAdjustBalance()

        viewModel.events.test {
            assertEquals(AccountsEvent.BalanceAdjusted(BigDecimal("5.50"), eur), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting a movement hands it to the undo coordinator with its tags`() = runTest {
        val transaction = movement(9L, LocalDate.of(2026, 7, 3), "-12.00")
        every { tagRepository.observeTagsForTransaction(9L) } returns flowOf(listOf(Tag(id = 4L, name = "x")))
        val viewModel = viewModel(transactions = listOf(transaction))

        viewModel.uiState.test {
            val state = awaitLoaded()
            viewModel.deleteMovement(state.days.single().items.single())
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { transactionRepository.delete(transaction) }
        undoCoordinator.events.test {
            assertEquals(UndoableDelete.Movement(transaction, listOf(4L)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
