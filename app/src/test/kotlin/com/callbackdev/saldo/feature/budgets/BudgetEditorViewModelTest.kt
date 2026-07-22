package com.callbackdev.saldo.feature.budgets

import app.cash.turbine.test
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Budget
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.BudgetRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.undo.UndoDeleteCoordinator
import com.callbackdev.saldo.core.domain.undo.UndoableDelete
import com.callbackdev.saldo.navigation.BudgetEditorRoute
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.util.Currency

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class BudgetEditorViewModelTest {

    private val eur = Currency.getInstance("EUR")

    private val budgetRepository = mockk<BudgetRepository>(relaxUnitFun = true)
    private val categoryRepository = mockk<CategoryRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val userPreferences = mockk<UserPreferencesRepository>(relaxUnitFun = true)
    private val undoCoordinator = UndoDeleteCoordinator()

    private val groceries = Category(
        id = 10L,
        name = "Groceries",
        type = CategoryType.EXPENSE,
        color = 0x66BB6A,
        icon = "shopping_cart",
    )
    private val salary = Category(
        id = 20L,
        name = "Salary",
        type = CategoryType.INCOME,
        color = 0x43A047,
        icon = "payments",
    )

    private fun viewModel(
        route: BudgetEditorRoute = BudgetEditorRoute(),
        budgets: List<Budget> = emptyList(),
        categories: List<Category> = listOf(groceries, salary),
    ): BudgetEditorViewModel {
        every { accountRepository.observeAccountsWithBalance() } returns flowOf(emptyList())
        every { userPreferences.primaryCurrencyOverride } returns flowOf(eur)
        coEvery { budgetRepository.getBudgets() } returns budgets
        every { categoryRepository.observeCategories() } returns flowOf(categories)
        return BudgetEditorViewModel(
            route = route,
            budgetRepository = budgetRepository,
            categoryRepository = categoryRepository,
            accountRepository = accountRepository,
            userPreferences = userPreferences,
            undoCoordinator = undoCoordinator,
        )
    }

    /** Keeps [BudgetEditorViewModel.uiState] hot for the whole test. */
    private fun TestScope.collectState(viewModel: BudgetEditorViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    @Test
    fun `create mode offers the overall scope plus every uncapped expense category`() = runTest {
        val viewModel = viewModel()
        collectState(viewModel)

        val state = viewModel.uiState.value
        assertTrue(state.isNew)
        assertEquals(
            listOf<BudgetScope>(BudgetScope.Overall, BudgetScope.ForCategory(groceries)),
            state.scopeOptions,
        )
        assertEquals(BudgetScope.Overall, state.scope)
    }

    @Test
    fun `saving a category budget goes through the category write path`() = runTest {
        val viewModel = viewModel()
        collectState(viewModel)

        viewModel.onScopeSelected(BudgetScope.ForCategory(groceries))
        viewModel.onAmountChanged("150")
        viewModel.save()

        coVerify { budgetRepository.upsertCategoryBudget(groceries.id, BigDecimal("150"), eur) }
        viewModel.events.test {
            assertEquals(BudgetEditorEvent.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saving without a valid amount surfaces validation and persists nothing`() = runTest {
        val viewModel = viewModel()
        collectState(viewModel)

        viewModel.save()

        assertTrue(viewModel.uiState.value.showValidation)
        assertFalse(viewModel.uiState.value.isAmountValid)
        coVerify(exactly = 0) { budgetRepository.setOverallBudget(any(), any()) }
        coVerify(exactly = 0) { budgetRepository.upsertCategoryBudget(any(), any(), any()) }
    }

    @Test
    fun `edit mode loads the budget and keeps its own currency`() = runTest {
        val usd = Currency.getInstance("USD")
        val budget = Budget(id = 3L, categoryId = groceries.id, amount = BigDecimal("99.00"), currency = usd)
        coEvery { categoryRepository.getCategory(groceries.id) } returns groceries
        val viewModel = viewModel(route = BudgetEditorRoute(3L), budgets = listOf(budget))
        collectState(viewModel)

        val state = viewModel.uiState.value
        assertFalse(state.isNew)
        assertEquals(usd, state.currency)
        assertEquals(BudgetScope.ForCategory(groceries), state.scope)
        assertEquals("99", state.amountInput)
    }

    @Test
    fun `deleting hands the budget to the undo coordinator and emits the event`() = runTest {
        val budget = Budget(id = 3L, categoryId = null, amount = BigDecimal("800.00"), currency = eur)
        val viewModel = viewModel(route = BudgetEditorRoute(3L), budgets = listOf(budget))
        collectState(viewModel)

        viewModel.delete()

        coVerify { budgetRepository.deleteBudget(3L) }
        undoCoordinator.events.test {
            assertEquals(UndoableDelete.Budget(budget), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.events.test {
            assertEquals(BudgetEditorEvent.Deleted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
