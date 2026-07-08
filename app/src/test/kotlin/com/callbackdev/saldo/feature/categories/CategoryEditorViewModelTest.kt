package com.callbackdev.saldo.feature.categories

import app.cash.turbine.test
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.navigation.CategoryEditorRoute
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class CategoryEditorViewModelTest {

    private val categoryRepository = mockk<CategoryRepository>(relaxUnitFun = true)
    private val transactionRepository = mockk<TransactionRepository>()

    private fun category(
        id: Long,
        type: CategoryType,
        name: String = "cat-$id",
        icon: String = "home",
        isDefault: Boolean = false,
        sortOrder: Int = 0,
    ) = Category(
        name = name,
        type = type,
        color = 0x123456,
        icon = icon,
        id = id,
        sortOrder = sortOrder,
        isDefault = isDefault,
    )

    private fun viewModel(route: CategoryEditorRoute = CategoryEditorRoute()) =
        CategoryEditorViewModel(route, categoryRepository, transactionRepository)

    @Test
    fun `a new category adopts the initial type from the route`() = runTest {
        val viewModel = viewModel(CategoryEditorRoute(initialTypeName = "INCOME"))

        with(viewModel.uiState.value) {
            assertTrue(isNew)
            assertFalse(isLoading)
            assertEquals(CategoryType.INCOME, type)
        }
    }

    @Test
    fun `saving a new category appends it at the next sort order`() = runTest {
        coEvery { categoryRepository.nextSortOrder() } returns 7
        val saved = slot<Category>()
        coEvery { categoryRepository.upsert(capture(saved)) } returns 1L
        val viewModel = viewModel()

        viewModel.onNameChanged("  Coffee  ")
        viewModel.onTypeChanged(CategoryType.EXPENSE)
        viewModel.onColorSelected(0xEF5350)
        viewModel.onIconSelected("local_cafe")
        viewModel.save()

        viewModel.events.test {
            assertEquals(CategoryEditorEvent.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        with(saved.captured) {
            assertEquals(0L, id)
            assertEquals("Coffee", name)
            assertEquals(CategoryType.EXPENSE, type)
            assertEquals(0xEF5350, color)
            assertEquals("local_cafe", icon)
            assertEquals(7, sortOrder)
            assertFalse(isDefault)
        }
    }

    @Test
    fun `saving without a name surfaces validation and persists nothing`() = runTest {
        val viewModel = viewModel()

        viewModel.save()

        assertTrue(viewModel.uiState.value.showValidation)
        coVerify(exactly = 0) { categoryRepository.upsert(any()) }
    }

    @Test
    fun `editing keeps identity, sort order and default flag`() = runTest {
        val existing = category(5L, CategoryType.EXPENSE, name = "Health", isDefault = true, sortOrder = 4)
        coEvery { categoryRepository.getCategory(5L) } returns existing
        val saved = slot<Category>()
        coEvery { categoryRepository.upsert(capture(saved)) } returns 5L
        val viewModel = viewModel(CategoryEditorRoute(categoryId = 5L))

        assertFalse(viewModel.uiState.value.isNew)
        viewModel.onNameChanged("Health & care")
        viewModel.save()

        with(saved.captured) {
            assertEquals(5L, id)
            assertEquals("Health & care", name)
            assertEquals(4, sortOrder)
            assertTrue(isDefault)
        }
        coVerify(exactly = 0) { categoryRepository.nextSortOrder() }
    }

    @Test
    fun `a missing category leaves the screen`() = runTest {
        coEvery { categoryRepository.getCategory(9L) } returns null

        val viewModel = viewModel(CategoryEditorRoute(categoryId = 9L))

        viewModel.events.test {
            assertEquals(CategoryEditorEvent.CategoryMissing, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting a category without movements only confirms`() = runTest {
        val existing = category(5L, CategoryType.EXPENSE)
        coEvery { categoryRepository.getCategory(5L) } returns existing
        coEvery { transactionRepository.countForCategory(5L) } returns 0
        val viewModel = viewModel(CategoryEditorRoute(categoryId = 5L))

        viewModel.requestDelete()

        assertInstanceOf(CategoryDeleteDialog.Confirm::class.java, viewModel.uiState.value.deleteDialog)
    }

    @Test
    fun `deleting a used category offers reassignment defaulting to the Other bucket`() = runTest {
        val self = category(5L, CategoryType.EXPENSE)
        val other = category(10L, CategoryType.EXPENSE, name = "Other", icon = "category", isDefault = true)
        val groceries = category(11L, CategoryType.EXPENSE, name = "Groceries")
        val salary = category(12L, CategoryType.INCOME, name = "Salary")
        coEvery { categoryRepository.getCategory(5L) } returns self
        coEvery { transactionRepository.countForCategory(5L) } returns 3
        coEvery { categoryRepository.observeCategories() } returns
            flowOf(listOf(self, other, groceries, salary))
        val viewModel = viewModel(CategoryEditorRoute(categoryId = 5L))

        viewModel.requestDelete()

        val dialog = viewModel.uiState.value.deleteDialog as CategoryDeleteDialog.Reassign
        assertEquals(3, dialog.movementCount)
        assertEquals(listOf(10L, 11L), dialog.candidates.map { it.id })
        assertEquals(10L, dialog.selectedTargetId)
    }

    @Test
    fun `deleting a used category with no compatible target warns of orphaning`() = runTest {
        val self = category(5L, CategoryType.EXPENSE)
        val salary = category(12L, CategoryType.INCOME)
        coEvery { categoryRepository.getCategory(5L) } returns self
        coEvery { transactionRepository.countForCategory(5L) } returns 2
        coEvery { categoryRepository.observeCategories() } returns flowOf(listOf(self, salary))
        val viewModel = viewModel(CategoryEditorRoute(categoryId = 5L))

        viewModel.requestDelete()

        val dialog = viewModel.uiState.value.deleteDialog
        assertInstanceOf(CategoryDeleteDialog.ConfirmUncategorize::class.java, dialog)
        assertEquals(2, (dialog as CategoryDeleteDialog.ConfirmUncategorize).movementCount)
    }

    @Test
    fun `confirming a reassignment reassigns then deletes`() = runTest {
        val self = category(5L, CategoryType.EXPENSE)
        val other = category(10L, CategoryType.EXPENSE, name = "Other", icon = "category", isDefault = true)
        coEvery { categoryRepository.getCategory(5L) } returns self
        coEvery { transactionRepository.countForCategory(5L) } returns 4
        coEvery { categoryRepository.observeCategories() } returns flowOf(listOf(self, other))
        val viewModel = viewModel(CategoryEditorRoute(categoryId = 5L))

        viewModel.requestDelete()
        viewModel.confirmDelete()

        coVerify { categoryRepository.deleteWithReassignment(self, 10L) }
        viewModel.events.test {
            assertEquals(CategoryEditorEvent.Deleted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirming a plain delete removes the category`() = runTest {
        val existing = category(5L, CategoryType.EXPENSE)
        coEvery { categoryRepository.getCategory(5L) } returns existing
        coEvery { transactionRepository.countForCategory(5L) } returns 0
        val viewModel = viewModel(CategoryEditorRoute(categoryId = 5L))

        viewModel.requestDelete()
        viewModel.confirmDelete()

        coVerify { categoryRepository.delete(existing) }
    }
}
