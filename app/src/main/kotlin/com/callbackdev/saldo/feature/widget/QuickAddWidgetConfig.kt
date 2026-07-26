package com.callbackdev.saldo.feature.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.callbackdev.saldo.core.domain.model.TransactionType

/**
 * Per-instance settings of a placed quick-add widget, stored in the widget's
 * own Glance preferences (one record per `GlanceId`), so two widgets on the
 * same home screen can add to two different accounts.
 *
 * Every field has a working default: the widget is usable the moment it is
 * dropped, and its configuration screen is an option rather than a toll gate.
 */
data class QuickAddWidgetConfig(
    /** Null means "resolve the app default account at render time". */
    val accountId: Long? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    /** Empty means "the most used categories", the adaptive default. */
    val pinnedCategoryIds: List<Long> = emptyList(),
    val showTodayTotal: Boolean = true,
    val appearance: WidgetAppearance = WidgetAppearance.SYSTEM,
) {
    val usesMostUsed: Boolean get() = pinnedCategoryIds.isEmpty()
}

/**
 * How a placed widget picks its background. A widget lives on the wallpaper,
 * not inside the app, so it can legitimately need a different answer from the
 * one Settings gives the app: light app, dark wallpaper.
 *
 * An arbitrary background color and an opacity slider were built and then
 * removed on the user's call: the widget's job is to look like Saldo, and every
 * extra degree of freedom was one more way for it not to.
 */
enum class WidgetAppearance { SYSTEM, LIGHT, DARK }

object QuickAddWidgetPrefs {

    val AccountId = longPreferencesKey("quick_add_account_id")
    val Type = stringPreferencesKey("quick_add_type")
    val PinnedCategoryIds = stringPreferencesKey("quick_add_pinned_category_ids")
    val ShowTodayTotal = booleanPreferencesKey("quick_add_show_today_total")

    /**
     * Bumped by [WidgetRefreshWatcher] when the underlying data moves. The
     * widget state is the only channel a Glance session listens to, so a
     * movement being recorded has to arrive as a state change or the
     * recomposition would render the very same snapshot.
     */
    val Revision = longPreferencesKey("quick_add_revision")

    val Appearance = stringPreferencesKey("quick_add_appearance")

    /**
     * Written once the configuration screen is confirmed, so that screen knows
     * whether it is placing a widget or editing one already on the home screen.
     * Android gives no flag of its own: the same activity and the same intent
     * serve both, and only the stored state can tell them apart.
     */
    val Configured = booleanPreferencesKey("quick_add_configured")

    /** Absent account id is stored as [NO_ACCOUNT] because DataStore has no nullable Long. */
    private const val NO_ACCOUNT = -1L
    private const val SEPARATOR = ","

    fun read(preferences: Preferences): QuickAddWidgetConfig = QuickAddWidgetConfig(
        accountId = preferences[AccountId]?.takeIf { it != NO_ACCOUNT },
        type = preferences[Type]?.let { stored ->
            TransactionType.entries.firstOrNull { it.name == stored }
        } ?: TransactionType.EXPENSE,
        pinnedCategoryIds = preferences[PinnedCategoryIds]
            ?.split(SEPARATOR)
            ?.mapNotNull(String::toLongOrNull)
            .orEmpty(),
        showTodayTotal = preferences[ShowTodayTotal] ?: true,
        appearance = preferences[Appearance]?.let { stored ->
            WidgetAppearance.entries.firstOrNull { it.name == stored }
        } ?: WidgetAppearance.SYSTEM,
    )

    fun isConfigured(preferences: Preferences): Boolean = preferences[Configured] == true

    fun encodeAccountId(accountId: Long?): Long = accountId ?: NO_ACCOUNT

    fun encodePinned(categoryIds: List<Long>): String = categoryIds.joinToString(SEPARATOR)
}
