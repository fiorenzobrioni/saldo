package com.callbackdev.saldo.feature.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.callbackdev.saldo.core.domain.model.TransactionType

/**
 * Per-instance settings of a placed quick-add widget, keyed by app widget id in
 * the shared widget store (see [WidgetConfigStore]), so two widgets on the same
 * home screen can add to two different accounts.
 *
 * Every field has a working default: the widget is usable the moment it is
 * dropped, and its configuration screen is an option rather than a toll gate.
 *
 * Deliberately small. The widget is a static entry point into the app - no
 * balances, no totals, no adaptive ordering - so the whole configuration is
 * "where do taps land and what does it look like", and nothing here changes
 * behind the user's back.
 */
data class QuickAddWidgetConfig(
    /** Null means "let the quick-entry sheet resolve the app default account". */
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
    /**
     * Empty means "every category, in the order of the app's own categories
     * screen" - the same order the user arranged there. A non-empty list is a
     * hand-picked subset in a hand-picked order.
     */
    val pinnedCategoryIds: List<Long> = emptyList(),
    val appearance: WidgetAppearance = WidgetAppearance.SYSTEM,
    val buttons: WidgetActionButtons = WidgetActionButtons.BOTH,
    /**
     * The app icon beside the two buttons of the single-row layout. On by
     * default (the user's call, after living with it off): the single row has
     * no other way into the app, where every taller layout carries an "open
     * Saldo" tile. Still a switch, for whoever wants the row to be only the
     * two buttons.
     */
    val showAppShortcut: Boolean = true,
) {
    /** True when the grid is a hand-picked subset rather than the app's own order. */
    val usesCustomCategories: Boolean get() = pinnedCategoryIds.isNotEmpty()

    /** What the widget actually draws: the runtime choice if there is one. */
    val effectiveType: TransactionType get() = currentType ?: type

    /** True when the single-row layout should draw this type's button. */
    fun showsButton(candidate: TransactionType): Boolean = buttons.shows(candidate)
}

/** Which buttons the single-row layout offers. */
enum class WidgetActionButtons {
    BOTH,
    EXPENSE_ONLY,
    INCOME_ONLY,
    ;

    fun shows(candidate: TransactionType): Boolean = when (this) {
        BOTH -> true
        EXPENSE_ONLY -> candidate == TransactionType.EXPENSE
        INCOME_ONLY -> candidate == TransactionType.INCOME
    }
}

/**
 * How a placed widget picks its palette. A widget lives on the wallpaper,
 * not inside the app, so it can legitimately need a different answer from the
 * one Settings gives the app: light app, dark wallpaper. The background is
 * always the solid app surface of the chosen side - the opacity slider and the
 * wallpaper-hint ink are gone on purpose (a translucent widget needed a
 * wallpaper listener and a full redraw on every wallpaper change, for a
 * surface that is meant to be a static entry point).
 *
 * [TRANSPARENT] is a legacy stored value only: it was the fourth selector
 * option before the opacity slider existed (itself since removed), and widgets
 * configured back then still carry it. [QuickAddWidgetPrefs.read] normalizes it
 * to [SYSTEM]; nothing writes it anymore and the settings screen no longer
 * offers it.
 */
enum class WidgetAppearance { SYSTEM, LIGHT, DARK, TRANSPARENT }


/**
 * How a widget's settings are spelled in the shared preferences file.
 *
 * Every key carries the app widget id, because the whole feature lives in one
 * file now (see [WidgetPreferencesModule]) rather than one per instance. The
 * unsuffixed names are still read once, by [readLegacy], to carry across the
 * widgets configured while the state belonged to Glance.
 */
object QuickAddWidgetPrefs {

    fun accountId(appWidgetId: Int) = longPreferencesKey(key(ACCOUNT_ID, appWidgetId))
    fun type(appWidgetId: Int) = stringPreferencesKey(key(TYPE, appWidgetId))
    fun pinnedCategoryIds(appWidgetId: Int) = stringPreferencesKey(key(PINNED, appWidgetId))
    fun appearance(appWidgetId: Int) = stringPreferencesKey(key(APPEARANCE, appWidgetId))
    fun buttons(appWidgetId: Int) = stringPreferencesKey(key(BUTTONS, appWidgetId))
    fun showAppShortcut(appWidgetId: Int) = booleanPreferencesKey(key(SHORTCUT, appWidgetId))

    /** The selector's runtime choice, kept apart from the configured start type. */
    fun currentType(appWidgetId: Int) = stringPreferencesKey(key(CURRENT_TYPE, appWidgetId))

    /** Absent account id is stored as [NO_ACCOUNT] because DataStore has no nullable Long. */
    private const val NO_ACCOUNT = -1L
    private const val SEPARATOR = ","

    private const val ACCOUNT_ID = "quick_add_account_id"
    private const val TYPE = "quick_add_type"
    private const val PINNED = "quick_add_pinned_category_ids"
    private const val APPEARANCE = "quick_add_appearance"
    private const val BUTTONS = "quick_add_buttons"
    private const val SHORTCUT = "quick_add_show_app_shortcut"
    private const val CURRENT_TYPE = "quick_add_current_type"

    private fun key(name: String, appWidgetId: Int) = "${name}_$appWidgetId"

    fun read(preferences: Preferences, appWidgetId: Int): QuickAddWidgetConfig = decode(
        appearance = preferences[appearance(appWidgetId)],
        accountId = preferences[accountId(appWidgetId)],
        type = preferences[type(appWidgetId)],
        currentType = preferences[currentType(appWidgetId)],
        pinned = preferences[pinnedCategoryIds(appWidgetId)],
        buttons = preferences[buttons(appWidgetId)],
        showAppShortcut = preferences[showAppShortcut(appWidgetId)],
    )

    /** The Glance-era per-instance file, whose keys carried no id. */
    fun readLegacy(preferences: Preferences): QuickAddWidgetConfig = decode(
        appearance = preferences[stringPreferencesKey(APPEARANCE)],
        accountId = preferences[longPreferencesKey(ACCOUNT_ID)],
        type = preferences[stringPreferencesKey(TYPE)],
        currentType = preferences[stringPreferencesKey(CURRENT_TYPE)],
        pinned = preferences[stringPreferencesKey(PINNED)],
        buttons = preferences[stringPreferencesKey(BUTTONS)],
        showAppShortcut = preferences[booleanPreferencesKey(SHORTCUT)],
    )

    fun write(preferences: MutablePreferences, appWidgetId: Int, config: QuickAddWidgetConfig) {
        preferences[accountId(appWidgetId)] = config.accountId ?: NO_ACCOUNT
        preferences[type(appWidgetId)] = config.type.name
        preferences[currentType(appWidgetId)] = config.effectiveType.name
        preferences[pinnedCategoryIds(appWidgetId)] =
            config.pinnedCategoryIds.joinToString(SEPARATOR)
        preferences[appearance(appWidgetId)] = config.appearance.name
        preferences[buttons(appWidgetId)] = config.buttons.name
        preferences[showAppShortcut(appWidgetId)] = config.showAppShortcut
    }

    fun clear(preferences: MutablePreferences, appWidgetId: Int) {
        preferences.remove(accountId(appWidgetId))
        preferences.remove(type(appWidgetId))
        preferences.remove(currentType(appWidgetId))
        preferences.remove(pinnedCategoryIds(appWidgetId))
        preferences.remove(appearance(appWidgetId))
        preferences.remove(buttons(appWidgetId))
        preferences.remove(showAppShortcut(appWidgetId))
    }

    @Suppress("LongParameterList")
    private fun decode(
        appearance: String?,
        accountId: Long?,
        type: String?,
        currentType: String?,
        pinned: String?,
        buttons: String?,
        showAppShortcut: Boolean?,
    ): QuickAddWidgetConfig {
        val storedAppearance = appearance?.let { stored ->
            WidgetAppearance.entries.firstOrNull { it.name == stored }
        } ?: WidgetAppearance.SYSTEM
        return QuickAddWidgetConfig(
            accountId = accountId?.takeIf { it != NO_ACCOUNT },
            type = type?.movementType() ?: TransactionType.EXPENSE,
            currentType = currentType?.movementType(),
            pinnedCategoryIds = pinned
                ?.split(SEPARATOR)
                ?.mapNotNull(String::toLongOrNull)
                .orEmpty(),
            // The pre-slider TRANSPARENT value reads back as a solid
            // system-following background: transparency is not offered anymore.
            appearance = when (storedAppearance) {
                WidgetAppearance.TRANSPARENT -> WidgetAppearance.SYSTEM
                else -> storedAppearance
            },
            buttons = buttons?.let { stored ->
                WidgetActionButtons.entries.firstOrNull { it.name == stored }
            } ?: WidgetActionButtons.BOTH,
            showAppShortcut = showAppShortcut ?: true,
        )
    }

    private fun String.movementType(): TransactionType? =
        TransactionType.entries.firstOrNull { it.name == this }
}
