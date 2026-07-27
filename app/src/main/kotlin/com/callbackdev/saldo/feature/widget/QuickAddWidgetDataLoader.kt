package com.callbackdev.saldo.feature.widget

import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.account.DefaultAccountResolver
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.DashboardWindows
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.model.primaryCurrency
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads everything a quick-add widget needs in one pass. Kept out of the
 * widget class because a `GlanceAppWidget` is instantiated by the framework and
 * cannot be injected; the widget reaches this through `WidgetEntryPoint`.
 */
@Singleton
class QuickAddWidgetDataLoader @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val userPreferences: UserPreferencesRepository,
    private val clock: Clock,
) {

    /**
     * [categoryLimit] exists for tests and for nothing else: the widget takes
     * every category, ordered, and its layout decides how many rows of them fit.
     */
    suspend fun load(config: QuickAddWidgetConfig, categoryLimit: Int = Int.MAX_VALUE): QuickAddWidgetData {
        val accounts = accountRepository.observeAccountsWithBalance().first()
        val active = accounts.map { it.account }.filter { !it.isArchived }
        // An account configured on the widget and later archived or deleted must
        // not leave the widget dead: fall back to the app's own default chain.
        val account = active.firstOrNull { it.id == config.accountId }
            ?: DefaultAccountResolver.resolve(
                accounts = active,
                defaultAccountId = userPreferences.defaultAccountId.first(),
                lastUsedAccountId = userPreferences.lastUsedAccountId.first(),
            )

        val available = categoryRepository.observeCategories(config.effectiveType.categoryType()).first()
        val categories = pick(available, config, categoryLimit)

        val todayTotal = if (config.showTodayTotal) formatTodaySpend(accounts) else null

        return QuickAddWidgetData(
            type = config.effectiveType,
            account = account,
            categories = categories,
            todayTotal = todayTotal,
            showTodayTotal = config.showTodayTotal,
        )
    }

    /**
     * Pinned categories keep the order the user chose; otherwise the most used
     * ones lead and the user's own order fills the remaining slots, so the grid
     * is never short even on a fresh install with no history.
     */
    private suspend fun pick(
        available: List<Category>,
        config: QuickAddWidgetConfig,
        limit: Int,
    ): List<Category> {
        val byId = available.associateBy { it.id }
        if (!config.usesMostUsed) {
            return config.pinnedCategoryIds.mapNotNull(byId::get).take(limit)
        }
        val since = LocalDate.now(clock).minusDays(MOST_USED_WINDOW_DAYS).atStartOfDay(clock.zone).toInstant()
        val mostUsed = transactionRepository
            .mostUsedCategoryIds(config.effectiveType, since, available.size.coerceAtLeast(1))
            .mapNotNull(byId::get)
        return (mostUsed + available).distinctBy { it.id }.take(limit)
    }

    private suspend fun formatTodaySpend(accounts: List<AccountWithBalance>): String? {
        if (accounts.isEmpty()) return null
        val currency = primaryCurrency(accounts, userPreferences.primaryCurrencyOverride.first())
        val today = LocalDate.now(clock)
        val totals = transactionRepository
            .observeDashboardTotals(DashboardWindows.around(today, clock.zone), currency)
            .first()
        // Spend arrives as a negative magnitude (the effect on the account); the
        // widget shows what left the wallet today, so it reads as a positive.
        return MoneyFormatter.format(totals.today.spend.abs(), currency)
    }

    private fun TransactionType.categoryType(): CategoryType = when (this) {
        TransactionType.INCOME -> CategoryType.INCOME
        else -> CategoryType.EXPENSE
    }

    private companion object {
        /** Two months of history: long enough to be stable, short enough to follow a change of habits. */
        const val MOST_USED_WINDOW_DAYS = 60L
    }
}
