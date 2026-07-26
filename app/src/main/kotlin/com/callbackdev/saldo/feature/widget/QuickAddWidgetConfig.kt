package com.callbackdev.saldo.feature.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
    /** 0xRRGGBB, only meaningful when [appearance] is [WidgetAppearance.CUSTOM]. */
    val backgroundColor: Int = WidgetPalette.default,
    /** Background opacity in percent. The tiles keep their own tint regardless. */
    val backgroundOpacity: Int = FULLY_OPAQUE,
) {
    val usesMostUsed: Boolean get() = pinnedCategoryIds.isEmpty()

    companion object {
        const val FULLY_OPAQUE = 100
    }
}

/**
 * How a placed widget picks its background. A widget lives on the wallpaper,
 * not inside the app, so it can legitimately need a different answer from the
 * one Settings gives the app: light app, dark wallpaper.
 */
enum class WidgetAppearance { SYSTEM, LIGHT, DARK, CUSTOM }

/**
 * Backgrounds offered for [WidgetAppearance.CUSTOM]. Pure white and pure black
 * lead because they are the two a wallpaper most often calls for, and the rest
 * are neutrals rather than hues: the color on a quick-add widget belongs to the
 * category icons, and a tinted background would fight them.
 */
object WidgetPalette {

    @Suppress("MagicNumber")
    val colors: List<Int> = listOf(
        0xFFFFFF, // pure white
        0x000000, // pure black
        0xFAFDFC, // brand light background
        0x191C1C, // brand dark background
        0xECEFEE,
        0x2E3132,
        0x5A6162,
        0x8E9899,
    )

    val default: Int get() = colors.first()
}

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
    val BackgroundColor = intPreferencesKey("quick_add_background_color")
    val BackgroundOpacity = intPreferencesKey("quick_add_background_opacity")

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
        backgroundColor = preferences[BackgroundColor] ?: WidgetPalette.default,
        // Clamped on read as well as on write: a value out of range would
        // otherwise render an invisible widget with no way back except the
        // configuration screen the user cannot see to reach.
        backgroundOpacity = (preferences[BackgroundOpacity] ?: QuickAddWidgetConfig.FULLY_OPAQUE)
            .coerceIn(0, QuickAddWidgetConfig.FULLY_OPAQUE),
    )

    fun encodeAccountId(accountId: Long?): Long = accountId ?: NO_ACCOUNT

    fun encodePinned(categoryIds: List<Long>): String = categoryIds.joinToString(SEPARATOR)
}
