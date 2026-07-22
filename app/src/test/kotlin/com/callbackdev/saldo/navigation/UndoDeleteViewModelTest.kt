package com.callbackdev.saldo.navigation

import app.cash.turbine.test
import com.callbackdev.saldo.core.domain.model.Budget
import com.callbackdev.saldo.core.domain.model.SavingsGoal
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.BudgetRepository
import com.callbackdev.saldo.core.domain.repository.SavingsGoalRepository
import com.callbackdev.saldo.core.domain.repository.TagRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.undo.UndoDeleteCoordinator
import com.callbackdev.saldo.core.domain.undo.UndoableDelete
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.util.Currency

@ExtendWith(MainDispatcherExtension::class)
class UndoDeleteViewModelTest {

    private val eur = Currency.getInstance("EUR")
    private val transactionRepository = mockk<TransactionRepository>(relaxUnitFun = true)
    private val tagRepository = mockk<TagRepository>(relaxUnitFun = true)
    private val budgetRepository = mockk<BudgetRepository>(relaxUnitFun = true)
    private val savingsGoalRepository = mockk<SavingsGoalRepository>(relaxUnitFun = true)
    private val coordinator = UndoDeleteCoordinator()

    private val deletedMovement = Transaction(
        id = 7L,
        type = TransactionType.EXPENSE,
        amount = BigDecimal("-12.00"),
        currency = eur,
        accountId = 1L,
        timestamp = Instant.parse("2026-07-01T10:00:00Z"),
        zoneOffset = ZoneOffset.ofHours(2),
        categoryId = 10L,
    )

    private fun viewModel() = UndoDeleteViewModel(
        coordinator = coordinator,
        transactionRepository = transactionRepository,
        tagRepository = tagRepository,
        budgetRepository = budgetRepository,
        savingsGoalRepository = savingsGoalRepository,
    )

    @Test
    fun `undo of a movement re-inserts it with a fresh id and re-attaches its tags`() = runTest {
        val saved = slot<Transaction>()
        coEvery { transactionRepository.upsert(capture(saved)) } returns 42L
        val viewModel = viewModel()

        viewModel.undo(UndoableDelete.Movement(deletedMovement, listOf(5L)))

        assertEquals(0L, saved.captured.id)
        assertEquals(deletedMovement.amount, saved.captured.amount)
        coVerify { tagRepository.setTagsForTransaction(42L, listOf(5L)) }
    }

    @Test
    fun `undo of a movement without tags skips the tag write`() = runTest {
        coEvery { transactionRepository.upsert(any()) } returns 42L
        val viewModel = viewModel()

        viewModel.undo(UndoableDelete.Movement(deletedMovement, emptyList()))

        coVerify(exactly = 0) { tagRepository.setTagsForTransaction(any(), any()) }
    }

    @Test
    fun `undo of an overall budget goes through the transactional overall write path`() = runTest {
        val viewModel = viewModel()
        val budget = Budget(id = 3L, categoryId = null, amount = BigDecimal("800.00"), currency = eur)

        viewModel.undo(UndoableDelete.Budget(budget))

        coVerify { budgetRepository.setOverallBudget(BigDecimal("800.00"), eur) }
    }

    @Test
    fun `undo of a category budget recreates the cap on its category`() = runTest {
        val viewModel = viewModel()
        val budget = Budget(id = 4L, categoryId = 10L, amount = BigDecimal("150.00"), currency = eur)

        viewModel.undo(UndoableDelete.Budget(budget))

        coVerify { budgetRepository.upsertCategoryBudget(10L, BigDecimal("150.00"), eur) }
    }

    @Test
    fun `undo of a savings goal re-inserts it with a fresh id`() = runTest {
        val saved = slot<SavingsGoal>()
        coEvery { savingsGoalRepository.upsert(capture(saved)) } returns 42L
        val viewModel = viewModel()
        val goal = SavingsGoal(
            name = "Trip",
            targetAmount = BigDecimal("2000.00"),
            currency = eur,
            accountId = 6L,
            id = 9L,
        )

        viewModel.undo(UndoableDelete.Goal(goal))

        assertEquals(0L, saved.captured.id)
        assertEquals(goal.name, saved.captured.name)
        assertEquals(goal.accountId, saved.captured.accountId)
    }

    @Test
    fun `a failed undo surfaces the failure event`() = runTest {
        coEvery { transactionRepository.upsert(any()) } throws IllegalStateException("boom")
        val viewModel = viewModel()

        viewModel.undo(UndoableDelete.Movement(deletedMovement, emptyList()))

        viewModel.undoFailed.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
