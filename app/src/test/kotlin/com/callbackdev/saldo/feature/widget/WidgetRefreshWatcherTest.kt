package com.callbackdev.saldo.feature.widget

import android.content.Context
import app.cash.turbine.test
import com.callbackdev.saldo.core.common.prefs.ThemeMode
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Currency

/**
 * The widget redraws on a signal, so anything missing from that signal is a
 * widget that silently stops keeping up. The account flow in particular was
 * missing once: a widget placed before onboarding shows the "open Saldo to get
 * started" tile, and creating the first account - the very thing that makes it
 * usable - produced no signal, so it stayed a dead tile that only opened the app.
 *
 * Equally deliberate is what is *not* here: no transactions flow. The widget
 * shows no totals and no usage-derived ordering, so a recorded movement must
 * not redraw it - that absence is the whole point of it being a static entry
 * point, and adding the flow back would reintroduce a full refresh (database
 * pass, one render per breakpoint, a RemoteViews payload to the launcher) on
 * every single movement.
 */
class WidgetRefreshWatcherTest {

    private val categories = MutableStateFlow(emptyList<Category>())
    private val accounts = MutableStateFlow(emptyList<Account>())
    private val theme = MutableStateFlow(ThemePreferences())

    private val categoryRepository = mockk<CategoryRepository> {
        every { observeCategories() } returns categories
    }
    private val accountRepository = mockk<AccountRepository> {
        every { observeAccounts() } returns accounts
    }
    private val userPreferences = mockk<UserPreferencesRepository> {
        every { themePreferences } returns theme
    }

    private val watcher = WidgetRefreshWatcher(
        context = mockk<Context>(relaxed = true),
        categoryRepository = categoryRepository,
        accountRepository = accountRepository,
        userPreferences = userPreferences,
        updater = mockk<WidgetUpdater>(relaxed = true),
        loader = mockk<QuickAddWidgetDataLoader>(relaxed = true),
    )

    private val account = Account(
        id = 1L,
        name = "Checking",
        type = AccountType.CHECKING,
        currency = Currency.getInstance("EUR"),
        initialBalance = BigDecimal.ZERO,
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

    /** A pinned account's badge is its name: renaming it must reach the launcher. */
    @Test
    fun `an account rename signals a redraw`() = runTest {
        accounts.value = listOf(account)
        watcher.refreshSignals().test {
            accounts.value = listOf(account.copy(name = "Renamed"))
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The theme is part of what a widget draws. Without this signal, switching
     * the app's theme mode or dynamic color left placed widgets in the old
     * palette until the next change happened to redraw them.
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
