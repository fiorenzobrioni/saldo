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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val sharedLock = Mutex()
    private var sharedKey: Pair<QuickAddWidgetConfig, Long>? = null
    private var sharedData: QuickAddWidgetData? = null

    /**
     * [load] deduplicated across the sizes of one render. A Responsive widget
     * composes its content once per bucket, all with the same config and
     * revision, and every one of those compositions asks for the same snapshot:
     * this hands them a single database pass instead of eleven.
     *
     * The cache holds exactly one entry and the revision is part of the key,
     * which is what keeps it honest: any data change reaches the widget only as
     * a revision bump (see `WidgetRefreshWatcher`), so a hit can never serve a
     * stale snapshot - equal key, equal data, by construction. [load] itself
     * stays stateless for everyone else.
     */
    suspend fun loadShared(config: QuickAddWidgetConfig, revision: Long): QuickAddWidgetData =
        sharedLock.withLock {
            val key = config to revision
            sharedData?.takeIf { sharedKey == key }
                ?: load(config).also {
                    sharedKey = key
                    sharedData = it
                }
        }

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

        val todayTotal = if (config.showTodayTotal) {
            formatTodayTotal(accounts, config.effectiveType)
        } else {
            null
        }

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

    /**
     * Today's number next to the selector, matching the type the widget is
     * showing: spend on an expense widget, earnings on an income one. The old
     * behaviour showed spend on both, which put an unexplained outgoing total
     * on a widget whose every control said "income".
     */
    private suspend fun formatTodayTotal(
        accounts: List<AccountWithBalance>,
        type: TransactionType,
    ): String? {
        if (accounts.isEmpty()) return null
        val currency = primaryCurrency(accounts, userPreferences.primaryCurrencyOverride.first())
        val today = LocalDate.now(clock)
        val totals = transactionRepository
            .observeDashboardTotals(DashboardWindows.around(today, clock.zone), currency)
            .first()
        // Spend arrives as a negative magnitude (the effect on the account); the
        // widget shows what moved today, so both types read as a positive.
        val amount = when (type) {
            TransactionType.INCOME -> totals.today.income
            else -> totals.today.spend
        }
        return MoneyFormatter.format(amount.abs(), currency)
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
