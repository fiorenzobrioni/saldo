package com.callbackdev.saldo.feature.widget

import android.content.Context
import com.callbackdev.saldo.core.common.prefs.ThemeMode
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Currency

class QuickAddWidgetDataLoaderTest {

    private val eur = Currency.getInstance("EUR")

    private val context = mockk<Context>(relaxed = true)
    private val accountRepository = mockk<AccountRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
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
    ): QuickAddWidgetDataLoader {
        every { accountRepository.observeAccounts() } returns flowOf(accounts)
        every { categoryRepository.observeCategories(any<CategoryType>()) } returns flowOf(available)
        // Brand palette, forced light: theme resolution never touches the
        // (mocked) context, so the JVM can run what is otherwise device code.
        every { userPreferences.themePreferences } returns
            flowOf(ThemePreferences(mode = ThemeMode.LIGHT, useDynamicColor = false))
        return QuickAddWidgetDataLoader(
            context = context,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            userPreferences = userPreferences,
        )
    }

    /**
     * No usage-derived reordering: the widget shows the categories exactly as
     * the app's categories screen orders them, so the grid the user learned
     * yesterday is the grid they find today - and recording a movement never
     * has to redraw a widget.
     */
    @Test
    fun `the grid is the user's own category order`() = runTest {
        val data = loader().load(QuickAddWidgetConfig(), categoryLimit = 4)
        assertEquals(listOf(1L, 2L, 3L, 4L), data.categories.map { it.id })
    }

    @Test
    fun `pinned categories keep the order the user pinned them in`() = runTest {
        val config = QuickAddWidgetConfig(pinnedCategoryIds = listOf(6L, 2L, 4L))
        val data = loader().load(config, categoryLimit = 4)
        assertEquals(listOf(6L, 2L, 4L), data.categories.map { it.id })
    }

    @Test
    fun `a pinned category that no longer exists is skipped, not left as a hole`() = runTest {
        val config = QuickAddWidgetConfig(pinnedCategoryIds = listOf(99L, 4L, 1L))
        val data = loader().load(config, categoryLimit = 3)
        assertEquals(listOf(4L, 1L), data.categories.map { it.id })
    }

    /**
     * The widget hands the quick-entry sheet the pinned account when it is
     * still alive, and nothing otherwise: the app default is resolved by the
     * sheet at open time, so the widget never redraws to track it.
     */
    @Test
    fun `a widget pinned to a live account carries its id and its name`() = runTest {
        val data = loader().load(QuickAddWidgetConfig(accountId = cash.id), categoryLimit = 4)
        assertEquals(cash.id, data.pinnedAccountId)
        assertEquals(cash.name, data.pinnedAccountName)
    }

    @Test
    fun `a widget following the default account pins nothing`() = runTest {
        val data = loader().load(QuickAddWidgetConfig(), categoryLimit = 4)
        assertNull(data.pinnedAccountId)
        assertNull(data.pinnedAccountName)
    }

    @Test
    fun `an archived pinned account loses the pin and the badge, not the widget`() = runTest {
        val data = loader(accounts = listOf(checking, account(2L, archived = true)))
            .load(QuickAddWidgetConfig(accountId = 2L), categoryLimit = 4)
        assertNull(data.pinnedAccountId)
        assertNull(data.pinnedAccountName)
        assertTrue(data.isReady, "The widget must fall back to the app default, not die")
    }

    @Test
    fun `no account and no category means the widget is not ready`() = runTest {
        val data = loader(accounts = emptyList(), available = emptyList())
            .load(QuickAddWidgetConfig(), categoryLimit = 4)
        assertTrue(!data.isReady)
    }

    @Test
    fun `an account whose every row is archived is no account at all`() = runTest {
        val data = loader(accounts = listOf(account(1L, archived = true)))
            .load(QuickAddWidgetConfig(), categoryLimit = 4)
        assertTrue(!data.isReady)
    }

    @Test
    fun `an income widget asks for income categories`() = runTest {
        val data = loader().load(QuickAddWidgetConfig(type = TransactionType.INCOME), categoryLimit = 4)
        assertEquals(TransactionType.INCOME, data.type)
    }

    /**
     * The widget's composition reloads on every change of its inputs, with no
     * "we already had this one" shortcut, because the state a session starts on
     * is also a state the user comes back to. That only holds if `load` is
     * stateless: the day someone adds a cache in here, switching type and
     * switching back would leave the widget showing the type it just left.
     * (`loadShared` below is allowed its cache precisely because the revision
     * in its key changes with the data.)
     */
    @Test
    fun `switching type and back reloads both times`() = runTest {
        val subject = loader()
        val expense = QuickAddWidgetConfig(type = TransactionType.EXPENSE)
        val income = QuickAddWidgetConfig(type = TransactionType.INCOME)

        val first = subject.load(expense, categoryLimit = 4)
        val second = subject.load(income, categoryLimit = 4)
        val back = subject.load(expense, categoryLimit = 4)

        assertEquals(TransactionType.EXPENSE, first.type)
        assertEquals(TransactionType.INCOME, second.type)
        assertEquals(TransactionType.EXPENSE, back.type)
        assertEquals(first.categories.map { it.id }, back.categories.map { it.id })
    }

    /**
     * A Responsive widget composes once per size bucket, every one asking for
     * the same snapshot: the shared load must collapse those into one database
     * pass and one theme resolution.
     */
    @Test
    fun `the shared load reads the database once per config and revision`() = runTest {
        val subject = loader()
        val config = QuickAddWidgetConfig()

        val first = subject.loadShared(config, revision = 1L)
        val second = subject.loadShared(config, revision = 1L)

        assertEquals(first, second)
        verify(exactly = 1) { accountRepository.observeAccounts() }
    }

    @Test
    fun `the shared snapshot carries the resolved theme with the data`() = runTest {
        val snapshot = loader().loadShared(QuickAddWidgetConfig(), revision = 1L)
        // Forced light: both branches must be the same scheme, or the launcher
        // could flip a widget its user pinned to one side.
        assertEquals(snapshot.theme.lightScheme, snapshot.theme.darkScheme)
    }

    @Test
    fun `a revision bump makes the shared load read again`() = runTest {
        val subject = loader()
        val config = QuickAddWidgetConfig()

        subject.loadShared(config, revision = 1L)
        subject.loadShared(config, revision = 2L)

        verify(exactly = 2) { accountRepository.observeAccounts() }
    }

    @Test
    fun `a config change makes the shared load read again`() = runTest {
        val subject = loader()

        subject.loadShared(QuickAddWidgetConfig(), revision = 1L)
        subject.loadShared(QuickAddWidgetConfig(type = TransactionType.INCOME), revision = 1L)

        verify(exactly = 2) { accountRepository.observeAccounts() }
    }

    /**
     * Two widgets with different configurations render with the same revision,
     * and their per-bucket compositions interleave: the cache must hold both,
     * or the alternation would evict on every call and reload once per bucket.
     */
    @Test
    fun `two widgets with different configs do not evict each other`() = runTest {
        val subject = loader()
        val grid = QuickAddWidgetConfig()
        val bar = QuickAddWidgetConfig(accountId = cash.id)

        subject.loadShared(grid, revision = 1L)
        subject.loadShared(bar, revision = 1L)
        subject.loadShared(grid, revision = 1L)
        subject.loadShared(bar, revision = 1L)

        verify(exactly = 2) { accountRepository.observeAccounts() }
    }
}
