package com.callbackdev.saldo.feature.widget

import android.content.Context
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything one widget render needs, produced in a single pass: the data
 * snapshot and the resolved palette. Bundled because they go stale together -
 * any change that affects either reaches the widget as a revision bump - and
 * because resolving them once here is what spares the per-bucket compositions
 * from doing it a dozen times each (see [QuickAddWidgetDataLoader.loadShared]).
 */
data class QuickAddWidgetSnapshot(
    val data: QuickAddWidgetData,
    val theme: QuickAddWidgetTheme,
)

/**
 * Reads everything a quick-add widget needs in one pass. Kept out of the
 * widget class because a `GlanceAppWidget` is instantiated by the framework and
 * cannot be injected; the widget reaches this through `WidgetEntryPoint`.
 *
 * The reads are deliberately cheap: plain account rows (never the computed
 * balances - the widget shows none, and the balance query is the most
 * expensive one in the project) and the categories of one type in the order
 * of the app's own categories screen. No transaction table reads at all.
 */
@Singleton
class QuickAddWidgetDataLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val userPreferences: UserPreferencesRepository,
) {

    private val sharedLock = Mutex()

    /**
     * A handful of entries rather than one: two widgets with different
     * configurations refresh with the same revision, and a single-entry cache
     * would evict on every alternation between them, turning "one database
     * pass per render" back into one per bucket.
     */
    private val shared = object : LinkedHashMap<Pair<QuickAddWidgetConfig, Long>, QuickAddWidgetSnapshot>(
        SHARED_CACHE_CAPACITY,
        LOAD_FACTOR,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Pair<QuickAddWidgetConfig, Long>, QuickAddWidgetSnapshot>,
        ): Boolean = size > SHARED_CACHE_CAPACITY
    }

    /**
     * [load] plus the resolved theme, deduplicated across the sizes of one
     * render. A Responsive widget composes its content once per bucket, all
     * with the same config and revision, and every one of those compositions
     * asks for the same snapshot: this hands them a single database pass and a
     * single theme resolution instead of one each.
     *
     * The revision is part of the key, which is what keeps the cache honest:
     * any data or theme-settings change reaches the widget only as a revision
     * bump (see `WidgetRefreshWatcher`), so a hit can never serve a stale
     * snapshot - equal key, equal snapshot, by construction. [load] itself
     * stays stateless for everyone else.
     */
    suspend fun loadShared(config: QuickAddWidgetConfig, revision: Long): QuickAddWidgetSnapshot =
        sharedLock.withLock {
            val key = config to revision
            shared[key] ?: QuickAddWidgetSnapshot(
                data = load(config),
                theme = resolveWidgetTheme(context, userPreferences.themePreferences.first(), config),
            ).also { shared[key] = it }
        }

    /**
     * [categoryLimit] exists for tests and for nothing else: the widget takes
     * every category, ordered, and its layout decides how many rows of them fit.
     */
    suspend fun load(config: QuickAddWidgetConfig, categoryLimit: Int = Int.MAX_VALUE): QuickAddWidgetData {
        val active = accountRepository.observeAccounts().first().filter { !it.isArchived }
        // An account configured on the widget and later archived or deleted
        // must not leave the widget dead: it degrades to following the app
        // default, which the quick-entry sheet resolves at open time.
        val pinned = active.firstOrNull { it.id == config.accountId }

        val available = categoryRepository.observeCategories(config.effectiveType.categoryType()).first()
        val categories = pick(available, config, categoryLimit)

        return QuickAddWidgetData(
            type = config.effectiveType,
            categories = categories,
            hasAccounts = active.isNotEmpty(),
            pinnedAccountId = pinned?.id,
            pinnedAccountName = pinned?.name,
        )
    }

    /**
     * Pinned categories keep the order the user chose; otherwise the grid is
     * simply the app's own category order, the one arranged in the categories
     * screen. No usage-derived reordering: the grid the user learned yesterday
     * is the grid they find today.
     */
    private fun pick(
        available: List<Category>,
        config: QuickAddWidgetConfig,
        limit: Int,
    ): List<Category> {
        if (!config.usesCustomCategories) return available.take(limit)
        val byId = available.associateBy { it.id }
        return config.pinnedCategoryIds.mapNotNull(byId::get).take(limit)
    }

    private fun TransactionType.categoryType(): CategoryType = when (this) {
        TransactionType.INCOME -> CategoryType.INCOME
        else -> CategoryType.EXPENSE
    }

    private companion object {
        /** Comfortably above the number of differently-configured widgets on one home screen. */
        const val SHARED_CACHE_CAPACITY = 8
        const val LOAD_FACTOR = 0.75f
    }
}
