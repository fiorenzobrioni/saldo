package com.callbackdev.saldo.feature.widget

import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.DashboardTotals
import com.callbackdev.saldo.core.domain.model.PeriodTotals
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Currency

class QuickAddWidgetDataLoaderTest {

    private val eur = Currency.getInstance("EUR")
    private val clock = Clock.fixed(Instant.parse("2026-07-08T10:15:00Z"), ZoneId.of("Europe/Rome"))

    private val accountRepository = mockk<AccountRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val userPreferences = mockk<UserPreferencesRepository>()

    private fun account(id: Long, archived: Boolean = false) = Account(
        id = id,
        name = "Account $id",
        type = AccountType.CHECKING,
        currency = eur,
        initialBalance = BigDecimal.ZERO,
        isArchived = archived,
    )

    private fun category(id: Long) = Category(
        id = id,
        name = "Category $id",
        type = CategoryType.EXPENSE,
        color = 0x66BB6A,
        icon = "shopping_cart",
        sortOrder = id.toInt(),
    )

    private val checking = account(1L)
    private val cash = account(2L)
    private val categories = (1L..6L).map(::category)

    private fun loader(
        accounts: List<Account> = listOf(checking, cash),
        available: List<Category> = categories,
        mostUsed: List<Long> = emptyList(),
        defaultAccountId: Long? = null,
        lastUsedAccountId: Long? = null,
        todaySpend: BigDecimal = BigDecimal("-24.30"),
    ): QuickAddWidgetDataLoader {
        every { accountRepository.observeAccountsWithBalance() } returns
            flowOf(accounts.map { AccountWithBalance(it, BigDecimal.ZERO) })
        every { categoryRepository.observeCategories(any<CategoryType>()) } returns flowOf(available)
        coEvery { transactionRepository.mostUsedCategoryIds(any(), any(), any()) } returns mostUsed
        every { transactionRepository.observeDashboardTotals(any(), any()) } returns
            flowOf(DashboardTotals(today = PeriodTotals(spend = todaySpend)))
        every { userPreferences.defaultAccountId } returns flowOf(defaultAccountId)
        every { userPreferences.lastUsedAccountId } returns flowOf(lastUsedAccountId)
        every { userPreferences.primaryCurrencyOverride } returns flowOf(null)
        return QuickAddWidgetDataLoader(
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            transactionRepository = transactionRepository,
            userPreferences = userPreferences,
            clock = clock,
        )
    }

    @Test
    fun `the most used categories lead and the user's own order fills the rest`() = runTest {
        val data = loader(mostUsed = listOf(5L, 3L)).load(QuickAddWidgetConfig(), categoryLimit = 4)
        assertEquals(listOf(5L, 3L, 1L, 2L), data.categories.map { it.id })
    }

    @Test
    fun `with no history at all the grid is simply the user's own order`() = runTest {
        val data = loader(mostUsed = emptyList()).load(QuickAddWidgetConfig(), categoryLimit = 4)
        assertEquals(listOf(1L, 2L, 3L, 4L), data.categories.map { it.id })
    }

    @Test
    fun `a most used category that no longer exists is skipped, not left as a hole`() = runTest {
        val data = loader(mostUsed = listOf(99L, 4L)).load(QuickAddWidgetConfig(), categoryLimit = 3)
        assertEquals(listOf(4L, 1L, 2L), data.categories.map { it.id })
    }

    @Test
    fun `pinned categories keep the order the user pinned them in`() = runTest {
        val config = QuickAddWidgetConfig(pinnedCategoryIds = listOf(6L, 2L, 4L))
        val data = loader(mostUsed = listOf(1L)).load(config, categoryLimit = 4)
        assertEquals(listOf(6L, 2L, 4L), data.categories.map { it.id })
    }

    @Test
    fun `the configured account is used when it is still active`() = runTest {
        val data = loader().load(QuickAddWidgetConfig(accountId = cash.id), categoryLimit = 4)
        assertEquals(cash.id, data.account?.id)
    }

    @Test
    fun `an archived configured account falls back to the app default instead of dying`() = runTest {
        val data = loader(
            accounts = listOf(checking, account(2L, archived = true)),
            defaultAccountId = checking.id,
        ).load(QuickAddWidgetConfig(accountId = 2L), categoryLimit = 4)
        assertEquals(checking.id, data.account?.id)
    }

    @Test
    fun `today's spend is shown as a positive amount, since it reads as what left the wallet`() = runTest {
        val data = loader().load(QuickAddWidgetConfig(), categoryLimit = 4)
        assertTrue(data.todayTotal.orEmpty().contains("24"))
        assertTrue(!data.todayTotal.orEmpty().contains("-"))
    }

    @Test
    fun `the total is not computed at all when the widget does not show it`() = runTest {
        val data = loader().load(QuickAddWidgetConfig(showTodayTotal = false), categoryLimit = 4)
        assertNull(data.todayTotal)
    }

    @Test
    fun `no account and no category means the widget is not ready`() = runTest {
        val data = loader(accounts = emptyList(), available = emptyList())
            .load(QuickAddWidgetConfig(), categoryLimit = 4)
        assertTrue(!data.isReady)
    }

    @Test
    fun `an income widget asks for income categories`() = runTest {
        val data = loader().load(QuickAddWidgetConfig(type = TransactionType.INCOME), categoryLimit = 4)
        assertEquals(TransactionType.INCOME, data.type)
    }
}
