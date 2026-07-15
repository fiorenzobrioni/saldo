package com.callbackdev.saldo.feature.categories

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class CategoriesViewModelTest {

    private val categoryRepository = mockk<CategoryRepository>(relaxUnitFun = true)

    private fun category(
        id: Long,
        type: CategoryType,
        sortOrder: Int,
        sortOrderIncome: Int = sortOrder,
    ) = Category(
        name = "cat-$id",
        type = type,
        color = 0x123456,
        icon = "category",
        id = id,
        sortOrder = sortOrder,
        sortOrderIncome = sortOrderIncome,
    )

    private fun viewModel(categories: List<Category>): CategoriesViewModel {
        every { categoryRepository.observeCategories() } returns flowOf(categories)
        return CategoriesViewModel(categoryRepository)
    }

    private suspend fun ReceiveTurbine<CategoriesUiState>.awaitLoaded(): CategoriesUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }

    @Test
    fun `tabs split by type and include BOTH in both`() = runTest {
        val expense = category(1L, CategoryType.EXPENSE, 0)
        val income = category(2L, CategoryType.INCOME, 1)
        val both = category(3L, CategoryType.BOTH, 2)
        val viewModel = viewModel(listOf(expense, income, both))

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(listOf(expense, both), state.expenses)
            assertEquals(listOf(income, both), state.incomes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reordering a tab persists only that tab's categories under its type`() = runTest {
        val a = category(1L, CategoryType.EXPENSE, 0)
        val b = category(2L, CategoryType.EXPENSE, 1)
        val c = category(3L, CategoryType.INCOME, 2)
        val viewModel = viewModel(listOf(a, b, c))
        val persisted = slot<List<Category>>()

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.persistOrder(CategoryType.EXPENSE, listOf(2L, 1L))
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { categoryRepository.reorder(CategoryType.EXPENSE, capture(persisted)) }
        // Only the expense tab's categories are handed to the repository, in the
        // new order; the income category is untouched.
        assertEquals(listOf(2L, 1L), persisted.captured.map { it.id })
    }

    @Test
    fun `reordering one tab leaves the other tab's order untouched`() = runTest {
        // A BOTH category sits in both tabs; reordering incomes must not move it
        // in the expense tab.
        val expense = category(1L, CategoryType.EXPENSE, sortOrder = 0)
        val both = category(2L, CategoryType.BOTH, sortOrder = 1, sortOrderIncome = 1)
        val income = category(3L, CategoryType.INCOME, sortOrder = 2, sortOrderIncome = 0)
        val viewModel = viewModel(listOf(expense, both, income))
        val persisted = slot<List<Category>>()

        viewModel.uiState.test {
            val state = awaitLoaded()
            // Income tab is ordered by sortOrderIncome: income(0) then both(1).
            assertEquals(listOf(3L, 2L), state.incomes.map { it.id })
            viewModel.persistOrder(CategoryType.INCOME, listOf(2L, 3L))
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { categoryRepository.reorder(CategoryType.INCOME, capture(persisted)) }
        assertEquals(listOf(2L, 3L), persisted.captured.map { it.id })
    }

    @Test
    fun `an unchanged order does not persist`() = runTest {
        val a = category(1L, CategoryType.EXPENSE, 0)
        val b = category(2L, CategoryType.EXPENSE, 1)
        val viewModel = viewModel(listOf(a, b))

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.persistOrder(CategoryType.EXPENSE, listOf(1L, 2L))
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { categoryRepository.reorder(any(), any()) }
    }

    @Test
    fun `a stale drop with mismatched ids is ignored`() = runTest {
        val a = category(1L, CategoryType.EXPENSE, 0)
        val b = category(2L, CategoryType.EXPENSE, 1)
        val viewModel = viewModel(listOf(a, b))

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.persistOrder(CategoryType.EXPENSE, listOf(2L, 99L))
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { categoryRepository.reorder(any(), any()) }
    }
}
