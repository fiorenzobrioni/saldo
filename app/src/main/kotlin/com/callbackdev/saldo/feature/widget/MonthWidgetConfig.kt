package com.callbackdev.saldo.feature.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.callbackdev.saldo.core.designsystem.theme.WidgetCardColor

/**
 * The per-instance settings of a month widget («In words» or «The month»),
 * kept in the same store as the quick-add widget's, under keys of their own.
 *
 * Every field has a working default: the card is right the moment it is
 * placed, and its settings screen is an option rather than a toll gate.
 */
data class MonthWidgetConfig(
    /** The family's default: a solid blue card. */
    val background: WidgetBackground = WidgetBackground.COLOR,
    val cardColor: WidgetCardColor = WidgetCardColor.BLUE,
    val opacityPct: Int = FullOpacity,
    /**
     * The reader's own switch for the figures. Even when on, the app lock
     * hides them (ADR 54): the switch can only ever hide more, never show
     * what the PIN guards.
     */
    val showAmounts: Boolean = true,
    /** «In words» only: the next recurring charge among its facts. */
    val showNextCharge: Boolean = true,
)

/** How a month widget's settings are spelled in the shared widget store. */
object MonthWidgetPrefs {

    private fun background(id: Int) = stringPreferencesKey("month_background_$id")
    private fun cardColor(id: Int) = stringPreferencesKey("month_card_color_$id")
    private fun opacity(id: Int) = intPreferencesKey("month_opacity_$id")
    private fun showAmounts(id: Int) = booleanPreferencesKey("month_show_amounts_$id")
    private fun showNextCharge(id: Int) = booleanPreferencesKey("month_show_next_charge_$id")

    fun read(preferences: Preferences, id: Int): MonthWidgetConfig {
        val default = MonthWidgetConfig()
        return MonthWidgetConfig(
            background = preferences[background(id)]
                ?.let { stored -> WidgetBackground.entries.firstOrNull { it.name == stored } }
                ?: default.background,
            cardColor = preferences[cardColor(id)]
                ?.let { stored -> WidgetCardColor.entries.firstOrNull { it.name == stored } }
                ?: default.cardColor,
            opacityPct = (preferences[opacity(id)] ?: default.opacityPct).coerceIn(0, FullOpacity),
            showAmounts = preferences[showAmounts(id)] ?: default.showAmounts,
            showNextCharge = preferences[showNextCharge(id)] ?: default.showNextCharge,
        )
    }

    fun write(preferences: MutablePreferences, id: Int, config: MonthWidgetConfig) {
        preferences[background(id)] = config.background.name
        preferences[cardColor(id)] = config.cardColor.name
        preferences[opacity(id)] = config.opacityPct.coerceIn(0, FullOpacity)
        preferences[showAmounts(id)] = config.showAmounts
        preferences[showNextCharge(id)] = config.showNextCharge
    }

    fun clear(preferences: MutablePreferences, id: Int) {
        preferences.remove(background(id))
        preferences.remove(cardColor(id))
        preferences.remove(opacity(id))
        preferences.remove(showAmounts(id))
        preferences.remove(showNextCharge(id))
    }

    /** True when [id] has a month record: a restore re-keys only what exists. */
    fun exists(preferences: Preferences, id: Int): Boolean = preferences[background(id)] != null
}
