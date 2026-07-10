package com.callbackdev.saldo.feature.transactions

import app.cash.turbine.test
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
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
import com.callbackdev.saldo.navigation.TransactionEditorRoute
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Currency

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class TransactionEditorViewModelTest {

    private val eur = Currency.getInstance("EUR")
    private val usd = Currency.getInstance("USD")
    private val jpy = Currency.getInstance("JPY")

    // Fixed at 2026-07-08 12:15 in Rome (UTC+2 in July).
    private val clock = Clock.fixed(
        Instant.parse("2026-07-08T10:15:00Z"),
        ZoneId.of("Europe/Rome"),
    )

    private val transactionRepository = mockk<TransactionRepository>(relaxUnitFun = true)
    private val accountRepository = mockk<AccountRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val tagRepository = mockk<TagRepository>(relaxUnitFun = true)
    private val userPreferences = mockk<UserPreferencesRepository>(relaxUnitFun = true)

    private fun account(
        id: Long,
        currency: Currency = eur,
        archived: Boolean = false,
    ) = Account(
        id = id,
        name = "Account $id",
        type = AccountType.CHECKING,
        currency = currency,
        initialBalance = BigDecimal.ZERO,
        isArchived = archived,
    )

    private val checking = account(id = 1L)
    private val cash = account(id = 2L)
    private val dollars = account(id = 3L, currency = usd)
    private val yen = account(id = 4L, currency = jpy)

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
        route: TransactionEditorRoute = TransactionEditorRoute(),
        accounts: List<Account> = listOf(checking, cash, dollars, yen),
        categories: List<Category> = listOf(groceries, salary),
        tags: List<Tag> = emptyList(),
        lastUsedAccountId: Long? = null,
    ): TransactionEditorViewModel {
        every { accountRepository.observeAccountsWithBalance() } returns
            flowOf(accounts.map { AccountWithBalance(it, BigDecimal.ZERO) })
        every { categoryRepository.observeCategories() } returns flowOf(categories)
        every { tagRepository.observeTags() } returns flowOf(tags)
        every { userPreferences.lastUsedAccountId } returns flowOf(lastUsedAccountId)
        coEvery { transactionRepository.upsert(any()) } returns SAVED_ID
        coEvery { tagRepository.upsert(any()) } returns NEW_TAG_ID
        return TransactionEditorViewModel(
            route = route,
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            tagRepository = tagRepository,
            userPreferences = userPreferences,
            clock = clock,
        )
    }

    /** Keeps [TransactionEditorViewModel.uiState] hot for the whole test. */
    private fun TestScope.collectState(viewModel: TransactionEditorViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    @Test
    fun `new movement defaults to an expense today on the last used account`() = runTest {
        val viewModel = viewModel(lastUsedAccountId = 2L)
        collectState(viewModel)

        val state = viewModel.uiState.value
        assertEquals(TransactionType.EXPENSE, state.type)
        assertEquals(LocalDate.of(2026, 7, 8), state.date)
        assertEquals(cash, state.account)
        assertEquals(listOf(groceries), state.categories)
    }

    @Test
    fun `without a last used account the first active account is preselected`() = runTest {
        val viewModel = viewModel(
            accounts = listOf(account(id = 9L, archived = true), checking, cash),
            lastUsedAccountId = null,
        )
        collectState(viewModel)

        assertEquals(checking, viewModel.uiState.value.account)
        // Archived accounts never appear among the pickable ones.
        assertEquals(
            listOf(checking.id, cash.id),
            viewModel.uiState.value.accounts.map { it.account.id },
        )
    }

    @Test
    fun `amount input is sanitized to the currency rules`() = runTest {
        val viewModel = viewModel(lastUsedAccountId = 1L)
        collectState(viewModel)

        // Beyond EUR's two decimals: extra digits are dropped.
        viewModel.onAmountChanged("12.509")
        assertEquals("12.50", viewModel.uiState.value.amountInput)

        viewModel.onAmountChanged("12.5")
        assertEquals("12.5", viewModel.uiState.value.amountInput)
    }

    @Test
    fun `saving an expense records a negative amount with tags and default account`() = runTest {
        val saved = slot<Transaction>()
        val viewModel = viewModel(tags = listOf(Tag("work", id = 5L)), lastUsedAccountId = 1L)
        collectState(viewModel)
        coEvery { transactionRepository.upsert(capture(saved)) } returns SAVED_ID

        viewModel.onAmountChanged("12.5")
        viewModel.onCategorySelected(groceries.id)
        viewModel.onDescriptionChanged("  Weekly shop  ")
        viewModel.onTagToggled(Tag("work", id = 5L))
        viewModel.save()

        val transaction = saved.captured
        assertEquals(TransactionType.EXPENSE, transaction.type)
        assertEquals(BigDecimal("-12.50"), transaction.amount)
        assertEquals(eur, transaction.currency)
        assertEquals(checking.id, transaction.accountId)
        assertEquals(groceries.id, transaction.categoryId)
        assertEquals("Weekly shop", transaction.description)
        assertEquals(Instant.parse("2026-07-08T10:15:00Z"), transaction.timestamp)
        assertEquals(ZoneOffset.ofHours(2), transaction.zoneOffset)
        coVerify { tagRepository.setTagsForTransaction(SAVED_ID, listOf(5L)) }
        coVerify { userPreferences.setLastUsedAccountId(checking.id) }
        viewModel.events.test {
            assertEquals(TransactionEditorEvent.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saving an income records a positive amount on the picked date`() = runTest {
        val saved = slot<Transaction>()
        val viewModel = viewModel(lastUsedAccountId = 1L)
        collectState(viewModel)
        coEvery { transactionRepository.upsert(capture(saved)) } returns SAVED_ID

        viewModel.onTypeChanged(TransactionType.INCOME)
        viewModel.onAmountChanged("900")
        viewModel.onCategorySelected(salary.id)
        viewModel.onDateSelected(LocalDate.of(2026, 7, 5))
        viewModel.save()

        val transaction = saved.captured
        assertEquals(TransactionType.INCOME, transaction.type)
        assertEquals(BigDecimal("900.00"), transaction.amount)
        // Same wall-clock time (12:15) moved to the picked day, still UTC+2.
        assertEquals(Instant.parse("2026-07-05T10:15:00Z"), transaction.timestamp)
    }

    @Test
    fun `a transfer is a single record with both legs`() = runTest {
        val saved = slot<Transaction>()
        val viewModel = viewModel(lastUsedAccountId = 1L)
        collectState(viewModel)
        coEvery { transactionRepository.upsert(capture(saved)) } returns SAVED_ID

        viewModel.onCategorySelected(groceries.id) // must not leak into the transfer
        viewModel.onTypeChanged(TransactionType.TRANSFER)
        viewModel.onToAccountSelected(cash)
        viewModel.onAmountChanged("50")
        viewModel.save()

        val transaction = saved.captured
        assertEquals(TransactionType.TRANSFER, transaction.type)
        assertEquals(BigDecimal("-50.00"), transaction.amount)
        assertEquals(checking.id, transaction.accountId)
        assertEquals(cash.id, transaction.transferAccountId)
        assertEquals(BigDecimal("50.00"), transaction.transferAmount)
        assertEquals(eur, transaction.transferCurrency)
        assertNull(transaction.categoryId)
    }

    @Test
    fun `a cross-currency transfer requires the received amount`() = runTest {
        val saved = slot<Transaction>()
        val viewModel = viewModel(lastUsedAccountId = 1L)
        collectState(viewModel)

        viewModel.onTypeChanged(TransactionType.TRANSFER)
        viewModel.onToAccountSelected(dollars)
        viewModel.onAmountChanged("50")
        viewModel.save()

        assertTrue(viewModel.uiState.value.showValidation)
        coVerify(exactly = 0) { transactionRepository.upsert(any()) }

        coEvery { transactionRepository.upsert(capture(saved)) } returns SAVED_ID
        viewModel.onToAmountChanged("54.2")
        viewModel.save()

        assertEquals(BigDecimal("-50.00"), saved.captured.amount)
        assertEquals(BigDecimal("54.20"), saved.captured.transferAmount)
        assertEquals(usd, saved.captured.transferCurrency)
    }

    @Test
    fun `an expense without category is not saved`() = runTest {
        val viewModel = viewModel(lastUsedAccountId = 1L)
        collectState(viewModel)

        viewModel.onAmountChanged("5")
        viewModel.save()

        assertTrue(viewModel.uiState.value.showValidation)
        assertFalse(viewModel.uiState.value.isCategoryValid)
        coVerify(exactly = 0) { transactionRepository.upsert(any()) }
    }

    @Test
    fun `a refund income uses expense categories and persists the flag`() = runTest {
        val saved = slot<Transaction>()
        val viewModel = viewModel(lastUsedAccountId = 1L)
        collectState(viewModel)
        coEvery { transactionRepository.upsert(capture(saved)) } returns SAVED_ID

        viewModel.onTypeChanged(TransactionType.INCOME)
        assertEquals(listOf(salary), viewModel.uiState.value.categories)
        viewModel.onRefundChanged(true)
        assertEquals(listOf(groceries), viewModel.uiState.value.categories)

        viewModel.onAmountChanged("8")
        viewModel.onCategorySelected(groceries.id)
        viewModel.save()

        assertTrue(saved.captured.isRefund)
        assertEquals(BigDecimal("8.00"), saved.captured.amount)
        assertEquals(groceries.id, saved.captured.categoryId)
    }

    @Test
    fun `switching account rescales the typed amount to the new currency`() = runTest {
        val viewModel = viewModel(lastUsedAccountId = 1L)
        collectState(viewModel)

        viewModel.onAmountChanged("12.5")
        viewModel.onAccountSelected(yen)

        assertEquals("13", viewModel.uiState.value.amountInput)
        assertEquals(jpy, viewModel.uiState.value.currency)
    }

    @Test
    fun `editing loads the movement and locks the type of a transfer`() = runTest {
        val existing = Transaction(
            id = 7L,
            type = TransactionType.TRANSFER,
            amount = BigDecimal("-50.00"),
            currency = eur,
            accountId = checking.id,
            timestamp = Instant.parse("2026-07-01T10:00:00Z"),
            zoneOffset = ZoneOffset.ofHours(2),
            transferAccountId = cash.id,
            transferAmount = BigDecimal("50.00"),
            transferCurrency = eur,
            description = "Top up",
        )
        coEvery { transactionRepository.getTransaction(7L) } returns existing
        every { tagRepository.observeTagsForTransaction(7L) } returns
            flowOf(listOf(Tag("work", id = 5L)))
        val viewModel = viewModel(
            route = TransactionEditorRoute(7L),
            tags = listOf(Tag("work", id = 5L)),
        )
        collectState(viewModel)

        val state = viewModel.uiState.value
        assertFalse(state.isNew)
        assertTrue(state.isTypeLocked)
        assertEquals(TransactionType.TRANSFER, state.type)
        assertEquals("50", state.amountInput)
        assertEquals(checking, state.account)
        assertEquals(cash, state.toAccount)
        assertEquals(LocalDate.of(2026, 7, 1), state.date)
        assertEquals("Top up", state.description)
        assertEquals(listOf(5L), state.selectedTags.map { it.id })
    }

    @Test
    fun `saving an edit keeps the id and the fields the form does not touch`() = runTest {
        val existing = Transaction(
            id = 7L,
            type = TransactionType.EXPENSE,
            amount = BigDecimal("-12.00"),
            currency = eur,
            accountId = checking.id,
            timestamp = Instant.parse("2026-07-01T10:00:00Z"),
            zoneOffset = ZoneOffset.ofHours(2),
            categoryId = groceries.id,
            note = "keep me",
            recurringRuleId = 3L,
        )
        coEvery { transactionRepository.getTransaction(7L) } returns existing
        every { tagRepository.observeTagsForTransaction(7L) } returns flowOf(emptyList())
        val saved = slot<Transaction>()
        val viewModel = viewModel(route = TransactionEditorRoute(7L))
        collectState(viewModel)
        coEvery { transactionRepository.upsert(capture(saved)) } returns 7L

        viewModel.onAmountChanged("125") // was 12
        viewModel.save()

        assertEquals(7L, saved.captured.id)
        assertEquals(BigDecimal("-125.00"), saved.captured.amount)
        assertEquals("keep me", saved.captured.note)
        assertEquals(3L, saved.captured.recurringRuleId)
    }

    @Test
    fun `deleting an edited movement removes it and emits the event`() = runTest {
        val existing = Transaction(
            id = 7L,
            type = TransactionType.EXPENSE,
            amount = BigDecimal("-12.00"),
            currency = eur,
            accountId = checking.id,
            timestamp = Instant.parse("2026-07-01T10:00:00Z"),
            zoneOffset = ZoneOffset.ofHours(2),
            categoryId = groceries.id,
        )
        coEvery { transactionRepository.getTransaction(7L) } returns existing
        every { tagRepository.observeTagsForTransaction(7L) } returns flowOf(emptyList())
        val viewModel = viewModel(route = TransactionEditorRoute(7L))
        collectState(viewModel)

        viewModel.delete()

        coVerify { transactionRepository.delete(existing) }
        viewModel.events.test {
            assertEquals(TransactionEditorEvent.Deleted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `creating a tag reuses an existing name case-insensitively`() = runTest {
        val viewModel = viewModel(tags = listOf(Tag("Work", id = 5L)), lastUsedAccountId = 1L)
        collectState(viewModel)

        viewModel.onCreateTag("  work ")

        coVerify(exactly = 0) { tagRepository.upsert(any()) }
        assertEquals(listOf(5L), viewModel.uiState.value.selectedTags.map { it.id })
    }

    private companion object {
        const val SAVED_ID = 42L
        const val NEW_TAG_ID = 77L
    }
}
