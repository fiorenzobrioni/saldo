package com.callbackdev.saldo.feature.accounts

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.creditcard.BillingCycle
import com.callbackdev.saldo.core.domain.usecase.AdjustBalanceUseCase
import com.callbackdev.saldo.core.domain.usecase.DueStatement
import com.callbackdev.saldo.core.domain.usecase.ObserveDueStatementsUseCase
import com.callbackdev.saldo.core.domain.usecase.SettleCreditCardStatementUseCase
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

@ExtendWith(MainDispatcherExtension::class)
class AccountsViewModelTest {

    private val eur = Currency.getInstance("EUR")

    private val accountRepository = mockk<AccountRepository>(relaxUnitFun = true)
    private val transactionRepository = mockk<TransactionRepository>()
    private val recurringRuleRepository = mockk<RecurringRuleRepository>()
    private val adjustBalance = mockk<AdjustBalanceUseCase>()
    private val observeDueStatements = mockk<ObserveDueStatementsUseCase>()
    private val settleStatement = mockk<SettleCreditCardStatementUseCase>()

    private fun account(
        id: Long = 1L,
        archived: Boolean = false,
    ) = Account(
        id = id,
        name = "Checking",
        type = AccountType.CHECKING,
        currency = eur,
        initialBalance = BigDecimal("100.00"),
        isArchived = archived,
    )

    private fun viewModel(
        accounts: List<AccountWithBalance> = emptyList(),
        dueStatements: List<DueStatement> = emptyList(),
    ): AccountsViewModel {
        every { accountRepository.observeAccountsWithBalance() } returns flowOf(accounts)
        coEvery { accountRepository.upsert(any()) } returns 1L
        coEvery { recurringRuleRepository.countForAccount(any()) } returns 0
        every { observeDueStatements() } returns flowOf(dueStatements)
        return AccountsViewModel(
            accountRepository,
            transactionRepository,
            recurringRuleRepository,
            adjustBalance,
            observeDueStatements,
            settleStatement,
        )
    }

    private suspend fun ReceiveTurbine<AccountsUiState>.awaitLoaded(): AccountsUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }

    @Test
    fun `ui state splits active and archived accounts`() = runTest {
        val active = AccountWithBalance(account(id = 1L), BigDecimal("74.50"))
        val archived = AccountWithBalance(account(id = 2L, archived = true), BigDecimal.ZERO)
        val viewModel = viewModel(accounts = listOf(active, archived))

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(listOf(active), state.activeGroups.flatMap { it.accounts })
            assertEquals(listOf(archived), state.archived)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the due statement shown per card is the oldest cycle`() = runTest {
        fun statement(closing: LocalDate, amount: String) = DueStatement(
            accountId = 1L,
            cardName = "Credit card",
            amount = BigDecimal(amount),
            currency = eur,
            cycle = BillingCycle(
                start = closing.minusMonths(1).plusDays(1),
                closing = closing,
                paymentDue = closing.plusMonths(1).withDayOfMonth(5),
            ),
            autoPosted = false,
        )
        // Oldest first, as the observe use case emits them: the CTA must show
        // the oldest, because settlement always pays the oldest cycle first.
        val older = statement(LocalDate.of(2026, 5, 20), "90.00")
        val newer = statement(LocalDate.of(2026, 6, 20), "40.00")
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(id = 1L), BigDecimal("-130.00"))),
            dueStatements = listOf(older, newer),
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(older, state.dueStatement(1L))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `archiving persists the flag and emits an undoable event`() = runTest {
        val target = account()
        val viewModel = viewModel()

        viewModel.archive(target)

        coVerify { accountRepository.upsert(target.copy(isArchived = true)) }
        viewModel.events.test {
            assertEquals(AccountsEvent.AccountArchived(target), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unarchiving clears the flag`() = runTest {
        val target = account(archived = true)
        val viewModel = viewModel()

        viewModel.unarchive(target)

        coVerify { accountRepository.upsert(target.copy(isArchived = false)) }
    }

    @Test
    fun `delete request with movements proposes archiving instead`() = runTest {
        val target = account()
        coEvery { transactionRepository.countForAccount(target.id) } returns 3
        val viewModel = viewModel()

        viewModel.requestDelete(target)

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(AccountsDialog.ArchiveInstead(target, 3), state.dialog)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { accountRepository.delete(any()) }
    }

    @Test
    fun `delete request with recurring rules proposes archiving instead`() = runTest {
        val target = account()
        val viewModel = viewModel()
        // After viewModel(): its catch-all countForAccount(any()) stub would
        // otherwise shadow these (MockK: the last matching stub wins).
        coEvery { transactionRepository.countForAccount(target.id) } returns 0
        coEvery { recurringRuleRepository.countForAccount(target.id) } returns 2

        viewModel.requestDelete(target)

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(
                AccountsDialog.ArchiveInstead(target, movementCount = 0, ruleCount = 2),
                state.dialog,
            )
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { accountRepository.delete(any()) }
    }

    @Test
    fun `delete request without movements asks for confirmation then deletes`() = runTest {
        val target = account()
        coEvery { transactionRepository.countForAccount(target.id) } returns 0
        val viewModel = viewModel()

        viewModel.requestDelete(target)
        viewModel.uiState.test {
            assertEquals(AccountsDialog.ConfirmDelete(target), awaitLoaded().dialog)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.confirmDelete()

        coVerify { accountRepository.delete(target) }
        viewModel.events.test {
            assertEquals(AccountsEvent.AccountDeleted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.uiState.test {
            assertNull(awaitLoaded().dialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `adjust flow computes the delta and records the adjustment`() = runTest {
        val item = AccountWithBalance(account(), BigDecimal("74.50"))
        coEvery { adjustBalance(item.account.id, BigDecimal("80.00")) } returns
            AdjustBalanceUseCase.Result.Adjusted(BigDecimal("5.50"))
        val viewModel = viewModel()

        viewModel.openAdjustBalance(item)
        viewModel.onAdjustInputChanged("80,00")

        viewModel.uiState.test {
            val dialog = awaitLoaded().dialog as AccountsDialog.AdjustBalance
            assertEquals("80,00", dialog.input)
            assertEquals(BigDecimal("5.50"), dialog.delta)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.confirmAdjustBalance()

        viewModel.events.test {
            assertEquals(
                AccountsEvent.BalanceAdjusted(BigDecimal("5.50"), eur),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `matching balance input disables the adjustment`() = runTest {
        val item = AccountWithBalance(account(), BigDecimal("74.50"))
        val viewModel = viewModel()

        viewModel.openAdjustBalance(item)
        viewModel.onAdjustInputChanged("74,50")

        viewModel.uiState.test {
            val dialog = awaitLoaded().dialog as AccountsDialog.AdjustBalance
            assertEquals(0, dialog.delta?.signum())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
