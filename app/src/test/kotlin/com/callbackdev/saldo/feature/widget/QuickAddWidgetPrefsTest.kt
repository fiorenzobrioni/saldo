package com.callbackdev.saldo.feature.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.callbackdev.saldo.core.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The widget's per-instance configuration survives reboots in DataStore
 * preferences, which have no nullable Long and no list type: these pin the
 * encoding so a stored widget cannot come back misconfigured after an update.
 */
class QuickAddWidgetPrefsTest {

    @Test
    fun `an unconfigured widget reads as the working defaults`() {
        val config = QuickAddWidgetPrefs.read(mutablePreferencesOf())
        assertNull(config.accountId)
        assertEquals(TransactionType.EXPENSE, config.type)
        assertTrue(config.pinnedCategoryIds.isEmpty())
        assertTrue(config.usesMostUsed)
        assertTrue(config.showTodayTotal)
    }

    @Test
    fun `a full configuration round-trips`() {
        val preferences = mutablePreferencesOf(
            QuickAddWidgetPrefs.AccountId to QuickAddWidgetPrefs.encodeAccountId(7L),
            QuickAddWidgetPrefs.Type to TransactionType.INCOME.name,
            QuickAddWidgetPrefs.PinnedCategoryIds to QuickAddWidgetPrefs.encodePinned(listOf(3L, 1L, 9L)),
            QuickAddWidgetPrefs.ShowTodayTotal to false,
        )
        val config = QuickAddWidgetPrefs.read(preferences)
        assertEquals(7L, config.accountId)
        assertEquals(TransactionType.INCOME, config.type)
        assertEquals(listOf(3L, 1L, 9L), config.pinnedCategoryIds)
        assertTrue(!config.usesMostUsed)
        assertTrue(!config.showTodayTotal)
    }

    @Test
    fun `the sentinel for no account reads back as no account, not as account -1`() {
        val preferences = mutablePreferencesOf(
            QuickAddWidgetPrefs.AccountId to QuickAddWidgetPrefs.encodeAccountId(null),
        )
        assertNull(QuickAddWidgetPrefs.read(preferences).accountId)
    }

    @Test
    fun `an unknown movement type falls back to expense rather than throwing`() {
        val preferences = mutablePreferencesOf(QuickAddWidgetPrefs.Type to "SOMETHING_ELSE")
        assertEquals(TransactionType.EXPENSE, QuickAddWidgetPrefs.read(preferences).type)
    }

    @Test
    fun `a malformed pinned list degrades to the adaptive grid`() {
        val preferences = mutablePreferencesOf(QuickAddWidgetPrefs.PinnedCategoryIds to "3,not-a-number,")
        val config = QuickAddWidgetPrefs.read(preferences)
        assertEquals(listOf(3L), config.pinnedCategoryIds)
    }

    @Test
    fun `an empty pinned string is the adaptive grid, not a widget with no categories`() {
        val preferences = mutablePreferencesOf(QuickAddWidgetPrefs.PinnedCategoryIds to "")
        assertTrue(QuickAddWidgetPrefs.read(preferences).usesMostUsed)
    }

    @Test
    fun `an unconfigured widget follows the system theme`() {
        val config = QuickAddWidgetPrefs.read(mutablePreferencesOf())
        assertEquals(WidgetAppearance.SYSTEM, config.appearance)
    }

    @Test
    fun `a forced appearance round-trips`() {
        val preferences = mutablePreferencesOf(QuickAddWidgetPrefs.Appearance to WidgetAppearance.DARK.name)
        assertEquals(WidgetAppearance.DARK, QuickAddWidgetPrefs.read(preferences).appearance)
    }

    @Test
    fun `an unknown appearance falls back to following the system`() {
        val preferences = mutablePreferencesOf(QuickAddWidgetPrefs.Appearance to "NEON")
        assertEquals(WidgetAppearance.SYSTEM, QuickAddWidgetPrefs.read(preferences).appearance)
    }

    /**
     * The selector on the home screen and "starts on" in the settings used to
     * share a key, so toggling the widget to income quietly rewrote the
     * configured value and the settings screen showed a choice nobody had made
     * there. They are different things and they live in different keys.
     */
    @Test
    fun `the runtime type never touches the configured one`() {
        val preferences = mutablePreferencesOf(
            QuickAddWidgetPrefs.Type to TransactionType.EXPENSE.name,
            QuickAddWidgetPrefs.CurrentType to TransactionType.INCOME.name,
        )
        val config = QuickAddWidgetPrefs.read(preferences)
        assertEquals(TransactionType.EXPENSE, config.type, "The configured start must not move")
        assertEquals(TransactionType.INCOME, config.effectiveType, "The widget draws where it is now")
    }

    @Test
    fun `a widget left alone draws the type it was configured to start on`() {
        val preferences = mutablePreferencesOf(QuickAddWidgetPrefs.Type to TransactionType.INCOME.name)
        val config = QuickAddWidgetPrefs.read(preferences)
        assertNull(config.currentType)
        assertEquals(TransactionType.INCOME, config.effectiveType)
    }

    @Test
    fun `both buttons show until told otherwise`() {
        val config = QuickAddWidgetPrefs.read(mutablePreferencesOf())
        assertEquals(WidgetActionButtons.BOTH, config.buttons)
        assertTrue(config.showsButton(TransactionType.EXPENSE))
        assertTrue(config.showsButton(TransactionType.INCOME))
    }

    @Test
    fun `a single-button widget shows only the one it was set to`() {
        val expenseOnly = QuickAddWidgetPrefs.read(
            mutablePreferencesOf(QuickAddWidgetPrefs.Buttons to WidgetActionButtons.EXPENSE_ONLY.name),
        )
        assertTrue(expenseOnly.showsButton(TransactionType.EXPENSE))
        assertTrue(!expenseOnly.showsButton(TransactionType.INCOME))

        val incomeOnly = QuickAddWidgetPrefs.read(
            mutablePreferencesOf(QuickAddWidgetPrefs.Buttons to WidgetActionButtons.INCOME_ONLY.name),
        )
        assertTrue(!incomeOnly.showsButton(TransactionType.EXPENSE))
        assertTrue(incomeOnly.showsButton(TransactionType.INCOME))
    }

    @Test
    fun `an unknown button setting falls back to showing both`() {
        val preferences = mutablePreferencesOf(QuickAddWidgetPrefs.Buttons to "SOMETHING_ELSE")
        assertEquals(WidgetActionButtons.BOTH, QuickAddWidgetPrefs.read(preferences).buttons)
    }

    /**
     * TRANSPARENT was the fourth selector option before the opacity slider; a
     * widget configured back then must read as what its user meant - system
     * ink over no background - not as an enum value the UI no longer offers.
     */
    @Test
    fun `a legacy transparent appearance reads as system ink over no background`() {
        val preferences = mutablePreferencesOf(
            QuickAddWidgetPrefs.Appearance to WidgetAppearance.TRANSPARENT.name,
        )
        val config = QuickAddWidgetPrefs.read(preferences)
        assertEquals(WidgetAppearance.SYSTEM, config.appearance)
        assertEquals(0f, config.backgroundOpacity)
    }

    @Test
    fun `a legacy transparent widget that later saved an opacity keeps the saved one`() {
        val preferences = mutablePreferencesOf(
            QuickAddWidgetPrefs.Appearance to WidgetAppearance.TRANSPARENT.name,
            QuickAddWidgetPrefs.BackgroundOpacity to 0.8f,
        )
        val config = QuickAddWidgetPrefs.read(preferences)
        assertEquals(WidgetAppearance.SYSTEM, config.appearance)
        assertEquals(0.8f, config.backgroundOpacity)
    }

    @Test
    fun `the background opacity round-trips`() {
        val preferences = mutablePreferencesOf(QuickAddWidgetPrefs.BackgroundOpacity to 0.4f)
        assertEquals(0.4f, QuickAddWidgetPrefs.read(preferences).backgroundOpacity)
    }

    @Test
    fun `an unconfigured widget is fully opaque`() {
        assertEquals(1f, QuickAddWidgetPrefs.read(mutablePreferencesOf()).backgroundOpacity)
    }

    @Test
    fun `an out-of-range stored opacity is clamped rather than trusted`() {
        val preferences = mutablePreferencesOf(QuickAddWidgetPrefs.BackgroundOpacity to 3f)
        assertEquals(1f, QuickAddWidgetPrefs.read(preferences).backgroundOpacity)
    }

    @Test
    fun `the app shortcut is off until it is asked for`() {
        assertTrue(!QuickAddWidgetPrefs.read(mutablePreferencesOf()).showAppShortcut)
        val enabled = mutablePreferencesOf(QuickAddWidgetPrefs.ShowAppShortcut to true)
        assertTrue(QuickAddWidgetPrefs.read(enabled).showAppShortcut)
    }

    /**
     * The revision is how a data change reaches a Glance session at all: the
     * composition only listens to its own widget state, so if this key were
     * ever dropped from the state the widget would render a frozen snapshot
     * for the whole life of the session.
     */
    @Test
    fun `the refresh revision lives in the same widget state as the configuration`() {
        val preferences = mutablePreferencesOf(
            QuickAddWidgetPrefs.Type to TransactionType.INCOME.name,
            QuickAddWidgetPrefs.Revision to 7L,
        )
        assertEquals(7L, preferences[QuickAddWidgetPrefs.Revision])
        // Bumping it must not disturb the configuration next to it.
        assertEquals(TransactionType.INCOME, QuickAddWidgetPrefs.read(preferences).type)
    }
}
