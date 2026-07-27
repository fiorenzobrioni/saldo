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
    /**
     * The type the widget starts on, set in its settings and changed nowhere
     * else.
     */
    val type: TransactionType = TransactionType.EXPENSE,
    /**
     * The type the widget is showing right now, moved by the selector on the
     * home screen. Deliberately a separate key from [type]: they shared one
     * until a user pointed out that toggling the widget to income silently
     * rewrote "starts on", so the settings screen showed a value nobody had
     * chosen there. Runtime state and configuration are different things and
     * now live apart. Null means the widget is on its configured start.
     */
    val currentType: TransactionType? = null,
    /** Empty means "the most used categories", the adaptive default. */
    val pinnedCategoryIds: List<Long> = emptyList(),
    val showTodayTotal: Boolean = true,
    val appearance: WidgetAppearance = WidgetAppearance.SYSTEM,
    val buttons: WidgetActionButtons = WidgetActionButtons.BOTH,
    /**
     * The app icon beside the two buttons of the single-row layout. Off by
     * default: the taller layouts already carry an "open Saldo" tile, and the
     * row is the size where every element has to earn its width.
     */
    val showAppShortcut: Boolean = false,
) {
    val usesMostUsed: Boolean get() = pinnedCategoryIds.isEmpty()

    /** What the widget actually draws: the runtime choice if there is one. */
    val effectiveType: TransactionType get() = currentType ?: type

    /** True when the single-row layout should draw this type's button. */
    fun showsButton(candidate: TransactionType): Boolean = when (buttons) {
        WidgetActionButtons.BOTH -> true
        WidgetActionButtons.EXPENSE_ONLY -> candidate == TransactionType.EXPENSE
        WidgetActionButtons.INCOME_ONLY -> candidate == TransactionType.INCOME
    }
}

/** Which buttons the single-row layout offers. */
enum class WidgetActionButtons { BOTH, EXPENSE_ONLY, INCOME_ONLY }

/**
 * How a placed widget picks its background. A widget lives on the wallpaper,
 * not inside the app, so it can legitimately need a different answer from the
 * one Settings gives the app: light app, dark wallpaper.
 *
 * An arbitrary background color and an opacity slider were built and then
 * removed on the user's call: the widget's job is to look like Saldo, and every
 * extra degree of freedom was one more way for it not to.
 */
enum class WidgetAppearance { SYSTEM, LIGHT, DARK, TRANSPARENT }

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
    val Buttons = stringPreferencesKey("quick_add_buttons")
    val ShowAppShortcut = booleanPreferencesKey("quick_add_show_app_shortcut")

    /** The selector's runtime choice, kept apart from the configured [Type]. */
    val CurrentType = stringPreferencesKey("quick_add_current_type")

    /** Absent account id is stored as [NO_ACCOUNT] because DataStore has no nullable Long. */
    private const val NO_ACCOUNT = -1L
    private const val SEPARATOR = ","

    fun read(preferences: Preferences): QuickAddWidgetConfig = QuickAddWidgetConfig(
        accountId = preferences[AccountId]?.takeIf { it != NO_ACCOUNT },
        type = preferences[Type]?.movementType() ?: TransactionType.EXPENSE,
        currentType = preferences[CurrentType]?.movementType(),
        pinnedCategoryIds = preferences[PinnedCategoryIds]
            ?.split(SEPARATOR)
            ?.mapNotNull(String::toLongOrNull)
            .orEmpty(),
        showTodayTotal = preferences[ShowTodayTotal] ?: true,
        appearance = preferences[Appearance]?.let { stored ->
            WidgetAppearance.entries.firstOrNull { it.name == stored }
        } ?: WidgetAppearance.SYSTEM,
        buttons = preferences[Buttons]?.let { stored ->
            WidgetActionButtons.entries.firstOrNull { it.name == stored }
        } ?: WidgetActionButtons.BOTH,
        showAppShortcut = preferences[ShowAppShortcut] ?: false,
    )

    private fun String.movementType(): TransactionType? =
        TransactionType.entries.firstOrNull { it.name == this }

    fun encodeAccountId(accountId: Long?): Long = accountId ?: NO_ACCOUNT

    fun encodePinned(categoryIds: List<Long>): String = categoryIds.joinToString(SEPARATOR)
}
