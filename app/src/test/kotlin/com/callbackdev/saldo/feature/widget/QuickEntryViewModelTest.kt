package com.callbackdev.saldo.feature.widget

import app.cash.turbine.test
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.ZoneId
import java.util.Currency

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class QuickEntryViewModelTest {

    private val eur = Currency.getInstance("EUR")

    // Fixed at 2026-07-08 12:15 in Rome (UTC+2 in July).
    private val clock = Clock.fixed(Instant.parse("2026-07-08T10:15:00Z"), ZoneId.of("Europe/Rome"))

    private val transactionRepository = mockk<TransactionRepository>(relaxUnitFun = true)
    private val accountRepository = mockk<AccountRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val userPreferences = mockk<UserPreferencesRepository>(relaxUnitFun = true)

    private fun account(id: Long, archived: Boolean = false) = Account(
        id = id,
        name = "Account $id",
        type = AccountType.CHECKING,
        currency = eur,
        initialBalance = BigDecimal.ZERO,
        isArchived = archived,
    )

    private val checking = account(1L)
    private val cash = account(2L)

    private val groceries = Category(
        id = 10L,
        name = "Groceries",
        type = CategoryType.EXPENSE,
        color = 0x66BB6A,
        icon = "shopping_cart",
    )

    private fun viewModel(
        route: QuickEntryRoute = QuickEntryRoute(TransactionType.EXPENSE, groceries.id, checking.id),
        accounts: List<Account> = listOf(checking, cash),
        categories: List<Category> = listOf(groceries),
        defaultAccountId: Long? = null,
        lastUsedAccountId: Long? = null,
    ): QuickEntryViewModel {
        every { accountRepository.observeAccountsWithBalance() } returns
            flowOf(accounts.map { AccountWithBalance(it, BigDecimal.ZERO) })
        every { categoryRepository.observeCategories(any<CategoryType>()) } returns flowOf(categories)
        every { userPreferences.defaultAccountId } returns flowOf(defaultAccountId)
        every { userPreferences.lastUsedAccountId } returns flowOf(lastUsedAccountId)
        coEvery { transactionRepository.upsert(any()) } returns SAVED_ID
        return QuickEntryViewModel(
            route = route,
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            userPreferences = userPreferences,
            clock = clock,
        )
    }

    @Test
    fun `the widget's category and account arrive preselected`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(groceries, state.category)
            assertEquals(checking.id, state.account?.account?.id)
        }
    }

    @Test
    fun `saving writes the movement with the right sign and remembers the account`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test { expectMostRecentItem() }
        viewModel.onAmountChanged("12,50")
        viewModel.save()

        val saved = slot<Transaction>()
        coVerify { transactionRepository.upsert(capture(saved)) }
        assertEquals(BigDecimal("-12.50"), saved.captured.amount)
        assertEquals(checking.id, saved.captured.accountId)
        assertEquals(groceries.id, saved.captured.categoryId)
        coVerify { userPreferences.setLastUsedAccountId(checking.id) }
    }

    @Test
    fun `an empty or zero amount cannot be saved`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            assertFalse(expectMostRecentItem().canSave)
            viewModel.onAmountChanged("0")
            assertFalse(expectMostRecentItem().canSave)
            viewModel.onAmountChanged("1")
            assertTrue(expectMostRecentItem().canSave)
        }
    }

    @Test
    fun `a second tap during the confirmation does not write the movement twice`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test { expectMostRecentItem() }
        viewModel.onAmountChanged("5")
        viewModel.save()
        viewModel.save()
        coVerify(exactly = 1) { transactionRepository.upsert(any()) }
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `a failed write reports it instead of pretending to have saved`() = runTest {
        val viewModel = viewModel()
        coEvery { transactionRepository.upsert(any()) } throws IllegalStateException("disk full")
        viewModel.uiState.test { expectMostRecentItem() }
        viewModel.events.test {
            viewModel.onAmountChanged("5")
            viewModel.save()
            assertEquals(QuickEntryEvent.WriteFailed, awaitItem())
        }
        assertFalse(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `an account the widget no longer has falls back to the app default`() = runTest {
        val viewModel = viewModel(
            route = QuickEntryRoute(TransactionType.EXPENSE, groceries.id, accountId = null),
            defaultAccountId = cash.id,
        )
        viewModel.uiState.test {
            assertEquals(cash.id, expectMostRecentItem().account?.account?.id)
        }
    }

    @Test
    fun `the confirmation carries the saved amount formatted`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test { expectMostRecentItem() }
        viewModel.onAmountChanged("12,50")
        viewModel.save()
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state.isSaved)
            // Locale-dependent formatting, so assert the digits are there
            // rather than pinning a separator the test machine chooses.
            assertTrue(state.savedAmount.orEmpty().contains("12"))
        }
    }

    private companion object {
        const val SAVED_ID = 42L
    }
}
