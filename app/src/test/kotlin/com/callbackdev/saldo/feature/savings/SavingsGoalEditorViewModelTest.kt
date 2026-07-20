package com.callbackdev.saldo.feature.savings

import app.cash.turbine.test
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.SavingsGoal
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.SavingsGoalRepository
import com.callbackdev.saldo.navigation.SavingsGoalEditorRoute
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

@ExtendWith(MainDispatcherExtension::class)
class SavingsGoalEditorViewModelTest {

    private val eur = Currency.getInstance("EUR")
    private val savingsGoalRepository = mockk<SavingsGoalRepository>()
    private val accountRepository = mockk<AccountRepository>()

    private val savingsAccount = Account(
        id = 9L,
        name = "Risparmi",
        type = AccountType.SAVINGS,
        currency = eur,
        initialBalance = BigDecimal.ZERO,
    )

    private fun viewModel(
        route: SavingsGoalEditorRoute = SavingsGoalEditorRoute(),
        accounts: List<AccountWithBalance> = listOf(AccountWithBalance(savingsAccount, BigDecimal("300.00"))),
        goals: List<SavingsGoal> = emptyList(),
    ): SavingsGoalEditorViewModel {
        every { accountRepository.observeAccountsWithBalance() } returns flowOf(accounts)
        every { savingsGoalRepository.observeGoals() } returns flowOf(goals)
        return SavingsGoalEditorViewModel(route, savingsGoalRepository, accountRepository)
    }

    @Test
    fun `create mode preselects the only free savings account`() = runTest {
        val viewModel = viewModel()

        val state = viewModel.uiState.value
        assertEquals(9L, state.accountId)
        assertEquals(eur, state.currency)
        assertEquals(BigDecimal("300.00"), state.savedBalance)
        assertFalse(state.noAvailableAccounts)
    }

    @Test
    fun `create mode with no savings account reports none available and none existing`() = runTest {
        val viewModel = viewModel(accounts = emptyList())

        val state = viewModel.uiState.value
        assertTrue(state.noAvailableAccounts)
        assertFalse(state.hasSavingsAccounts)
    }

    @Test
    fun `create mode with the only savings account already taken reports none available but existing`() = runTest {
        val existingGoal = SavingsGoal(
            id = 1L,
            name = "Vacanze",
            targetAmount = BigDecimal("500.00"),
            currency = eur,
            accountId = 9L,
        )
        val viewModel = viewModel(goals = listOf(existingGoal))

        val state = viewModel.uiState.value
        assertTrue(state.noAvailableAccounts)
        assertTrue(state.hasSavingsAccounts)
    }

    @Test
    fun `saving persists the parsed goal linked to the account`() = runTest {
        val saved = slot<SavingsGoal>()
        coEvery { savingsGoalRepository.upsert(capture(saved)) } returns 1L
        val viewModel = viewModel()

        viewModel.onNameChanged("  Vacanze  ")
        viewModel.onTargetChanged("2000,50")
        viewModel.onTargetDateSelected(LocalDate.of(2026, 12, 31))
        viewModel.save()

        viewModel.events.test {
            assertEquals(SavingsGoalEditorEvent.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        with(saved.captured) {
            assertEquals(0L, id)
            assertEquals("Vacanze", name)
            assertEquals(BigDecimal("2000.50"), targetAmount)
            assertEquals(eur, currency)
            assertEquals(9L, accountId)
            assertEquals(LocalDate.of(2026, 12, 31), targetDate)
        }
    }

    @Test
    fun `editing a field marks the form dirty and reverting clears it`() = runTest {
        val viewModel = viewModel()

        viewModel.hasUnsavedChanges.test {
            assertFalse(awaitItem())
            viewModel.onNameChanged("Vacanze")
            assertTrue(awaitItem())
            viewModel.onNameChanged("")
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `edit mode loads the goal and stays clean until changed`() = runTest {
        val goal = SavingsGoal(
            id = 5L,
            name = "Laptop",
            targetAmount = BigDecimal("1200.00"),
            currency = eur,
            accountId = 9L,
            targetDate = LocalDate.of(2027, 1, 1),
        )
        val viewModel = viewModel(
            route = SavingsGoalEditorRoute(goalId = 5L),
            goals = listOf(goal),
        )

        val state = viewModel.uiState.value
        assertFalse(state.isNew)
        assertEquals("Laptop", state.name)
        assertEquals("1200", state.targetInput)

        viewModel.hasUnsavedChanges.test {
            assertFalse(awaitItem())
            viewModel.onTargetChanged("1300")
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
