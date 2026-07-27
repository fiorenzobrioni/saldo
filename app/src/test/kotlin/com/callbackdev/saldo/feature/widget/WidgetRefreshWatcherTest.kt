package com.callbackdev.saldo.feature.widget

import android.content.Context
import app.cash.turbine.test
import com.callbackdev.saldo.core.common.prefs.ThemeMode
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.util.Currency

/**
 * The widget redraws on a signal, so anything missing from that signal is a
 * widget that silently stops keeping up. The account flow in particular was
 * missing: a widget placed before onboarding shows the "open Saldo to get
 * started" tile, and creating the first account - the very thing that makes it
 * usable - produced no signal, so it stayed a dead tile that only opened the app.
 */
class WidgetRefreshWatcherTest {

    private val transactions = MutableStateFlow(emptyList<Transaction>())
    private val categories = MutableStateFlow(emptyList<Category>())
    private val accounts = MutableStateFlow(emptyList<AccountWithBalance>())
    private val theme = MutableStateFlow(ThemePreferences())

    private val transactionRepository = mockk<TransactionRepository> {
        every { observeRecentTransactions(any()) } returns transactions
    }
    private val categoryRepository = mockk<CategoryRepository> {
        every { observeCategories() } returns categories
    }
    private val accountRepository = mockk<AccountRepository> {
        every { observeAccountsWithBalance() } returns accounts
    }
    private val userPreferences = mockk<UserPreferencesRepository> {
        every { themePreferences } returns theme
    }

    private val watcher = WidgetRefreshWatcher(
        context = mockk<Context>(relaxed = true),
        transactionRepository = transactionRepository,
        categoryRepository = categoryRepository,
        accountRepository = accountRepository,
        userPreferences = userPreferences,
        clock = Clock.systemUTC(),
    )

    private val account = AccountWithBalance(
        Account(
            id = 1L,
            name = "Checking",
            type = AccountType.CHECKING,
            currency = Currency.getInstance("EUR"),
            initialBalance = BigDecimal.ZERO,
        ),
        BigDecimal.ZERO,
    )

    private val category = Category(
        id = 10L,
        name = "Groceries",
        type = CategoryType.EXPENSE,
        color = 0x66BB6A,
        icon = "shopping_cart",
    )

    @Test
    fun `the first account signals a redraw`() = runTest {
        watcher.refreshSignals().test {
            accounts.value = listOf(account)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a category change signals a redraw`() = runTest {
        watcher.refreshSignals().test {
            categories.value = listOf(category)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a recorded movement signals a redraw`() = runTest {
        watcher.refreshSignals().test {
            transactions.value = listOf(mockk(relaxed = true))
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The theme is part of what a widget draws. Without this signal, switching
     * the app's theme mode or dynamic color left placed widgets in the old
     * palette until the next movement happened to redraw them.
     */
    @Test
    fun `a theme change signals a redraw`() = runTest {
        watcher.refreshSignals().test {
            theme.value = ThemePreferences(mode = ThemeMode.DARK)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the state already on screen is not a redraw`() = runTest {
        watcher.refreshSignals().test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
