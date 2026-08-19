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
 *
 * Since the widget stopped going through Glance the records of every instance
 * share one file, keyed by app widget id, so the isolation between them is part
 * of what has to be pinned here - a key collision would silently make two
 * widgets one.
 */
class QuickAddWidgetPrefsTest {

    private val id = 42
    private val other = 43

    @Test
    fun `an unconfigured widget reads as the working defaults`() {
        val config = QuickAddWidgetPrefs.read(mutablePreferencesOf(), id)
        assertNull(config.accountId)
        assertEquals(TransactionType.EXPENSE, config.type)
        assertTrue(config.pinnedCategoryIds.isEmpty())
        assertTrue(!config.usesCustomCategories)
    }

    @Test
    fun `a full configuration round-trips`() {
        val stored = QuickAddWidgetConfig(
            accountId = 7L,
            type = TransactionType.INCOME,
            pinnedCategoryIds = listOf(3L, 1L, 9L),
            appearance = WidgetAppearance.DARK,
            buttons = WidgetActionButtons.INCOME_ONLY,
            showAppShortcut = false,
        )
        val preferences = mutablePreferencesOf().also {
            QuickAddWidgetPrefs.write(it, id, stored)
        }

        val config = QuickAddWidgetPrefs.read(preferences, id)
        assertEquals(7L, config.accountId)
        assertEquals(TransactionType.INCOME, config.type)
        assertEquals(listOf(3L, 1L, 9L), config.pinnedCategoryIds)
        assertEquals(WidgetAppearance.DARK, config.appearance)
        assertEquals(WidgetActionButtons.INCOME_ONLY, config.buttons)
        assertTrue(!config.showAppShortcut)
        assertTrue(config.usesCustomCategories)
    }

    /**
     * One file for every widget, so the whole feature rests on the keys carrying
     * the id: without it, configuring one widget would reconfigure all of them.
     */
    @Test
    fun `two widgets in one file keep their own settings`() {
        val preferences = mutablePreferencesOf().also {
            QuickAddWidgetPrefs.write(it, id, QuickAddWidgetConfig(accountId = 7L))
            QuickAddWidgetPrefs.write(
                it,
                other,
                QuickAddWidgetConfig(accountId = 9L, type = TransactionType.INCOME),
            )
        }

        assertEquals(7L, QuickAddWidgetPrefs.read(preferences, id).accountId)
        assertEquals(TransactionType.EXPENSE, QuickAddWidgetPrefs.read(preferences, id).type)
        assertEquals(9L, QuickAddWidgetPrefs.read(preferences, other).accountId)
        assertEquals(TransactionType.INCOME, QuickAddWidgetPrefs.read(preferences, other).type)
    }

    /** A removed widget must not leave its settings for the next id to inherit. */
    @Test
    fun `clearing one widget leaves the other untouched`() {
        val preferences = mutablePreferencesOf().also {
            QuickAddWidgetPrefs.write(it, id, QuickAddWidgetConfig(accountId = 7L))
            QuickAddWidgetPrefs.write(it, other, QuickAddWidgetConfig(accountId = 9L))
            QuickAddWidgetPrefs.clear(it, id)
        }

        assertNull(QuickAddWidgetPrefs.read(preferences, id).accountId)
        assertEquals(9L, QuickAddWidgetPrefs.read(preferences, other).accountId)
    }

    @Test
    fun `the sentinel for no account reads back as no account, not as account -1`() {
        val preferences = mutablePreferencesOf().also {
            QuickAddWidgetPrefs.write(it, id, QuickAddWidgetConfig(accountId = null))
        }
        assertNull(QuickAddWidgetPrefs.read(preferences, id).accountId)
    }

    @Test
    fun `an unknown movement type falls back to expense rather than throwing`() {
        val preferences = mutablePreferencesOf(
            QuickAddWidgetPrefs.type(id) to "SOMETHING_ELSE",
        )
        assertEquals(TransactionType.EXPENSE, QuickAddWidgetPrefs.read(preferences, id).type)
    }

    @Test
    fun `a malformed pinned list keeps what parses rather than throwing`() {
        val preferences = mutablePreferencesOf(
            QuickAddWidgetPrefs.pinnedCategoryIds(id) to "3,not-a-number,",
        )
        assertEquals(listOf(3L), QuickAddWidgetPrefs.read(preferences, id).pinnedCategoryIds)
    }

    @Test
    fun `an empty pinned string is the full grid, not a widget with no categories`() {
        val preferences = mutablePreferencesOf(QuickAddWidgetPrefs.pinnedCategoryIds(id) to "")
        assertTrue(!QuickAddWidgetPrefs.read(preferences, id).usesCustomCategories)
    }

    @Test
    fun `an unconfigured widget follows the system theme`() {
        assertEquals(
            WidgetAppearance.SYSTEM,
            QuickAddWidgetPrefs.read(mutablePreferencesOf(), id).appearance,
        )
    }

    @Test
    fun `an unknown appearance falls back to following the system`() {
        val preferences = mutablePreferencesOf(QuickAddWidgetPrefs.appearance(id) to "NEON")
        assertEquals(WidgetAppearance.SYSTEM, QuickAddWidgetPrefs.read(preferences, id).appearance)
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
            QuickAddWidgetPrefs.type(id) to TransactionType.EXPENSE.name,
            QuickAddWidgetPrefs.currentType(id) to TransactionType.INCOME.name,
        )
        val config = QuickAddWidgetPrefs.read(preferences, id)
        assertEquals(TransactionType.EXPENSE, config.type, "The configured start must not move")
        assertEquals(TransactionType.INCOME, config.effectiveType, "The widget draws where it is now")
    }

    @Test
    fun `a widget left alone draws the type it was configured to start on`() {
        val preferences = mutablePreferencesOf(
            QuickAddWidgetPrefs.type(id) to TransactionType.INCOME.name,
        )
        val config = QuickAddWidgetPrefs.read(preferences, id)
        assertEquals(TransactionType.INCOME, config.effectiveType)
    }

    /**
     * Saving the settings puts the widget back on its configured start: leaving
     * the selector where it was would mean the widget ignored the value the user
     * had just chosen.
     */
    @Test
    fun `saving the configuration moves the widget onto its new start type`() {
        val preferences = mutablePreferencesOf(
            QuickAddWidgetPrefs.currentType(id) to TransactionType.INCOME.name,
        ).also {
            QuickAddWidgetPrefs.write(it, id, QuickAddWidgetConfig(type = TransactionType.EXPENSE))
        }
        assertEquals(TransactionType.EXPENSE, QuickAddWidgetPrefs.read(preferences, id).effectiveType)
    }

    @Test
    fun `both buttons show until told otherwise`() {
        val config = QuickAddWidgetPrefs.read(mutablePreferencesOf(), id)
        assertEquals(WidgetActionButtons.BOTH, config.buttons)
        assertTrue(config.showsButton(TransactionType.EXPENSE))
        assertTrue(config.showsButton(TransactionType.INCOME))
    }

    @Test
    fun `a single-button widget shows only the one it was set to`() {
        val expenseOnly = QuickAddWidgetPrefs.read(
            mutablePreferencesOf(
                QuickAddWidgetPrefs.buttons(id) to WidgetActionButtons.EXPENSE_ONLY.name,
            ),
            id,
        )
        assertTrue(expenseOnly.showsButton(TransactionType.EXPENSE))
        assertTrue(!expenseOnly.showsButton(TransactionType.INCOME))

        val incomeOnly = QuickAddWidgetPrefs.read(
            mutablePreferencesOf(
                QuickAddWidgetPrefs.buttons(id) to WidgetActionButtons.INCOME_ONLY.name,
            ),
            id,
        )
        assertTrue(!incomeOnly.showsButton(TransactionType.EXPENSE))
        assertTrue(incomeOnly.showsButton(TransactionType.INCOME))
    }

    @Test
    fun `an unknown button setting falls back to showing both`() {
        val preferences = mutablePreferencesOf(QuickAddWidgetPrefs.buttons(id) to "SOMETHING_ELSE")
        assertEquals(WidgetActionButtons.BOTH, QuickAddWidgetPrefs.read(preferences, id).buttons)
    }

    /**
     * TRANSPARENT was a selector option before the widget went solid-only; a
     * widget configured back then must read as a value the UI still offers,
     * never as one it no longer does.
     */
    @Test
    fun `a legacy transparent appearance reads as following the system`() {
        val preferences = mutablePreferencesOf(
            QuickAddWidgetPrefs.appearance(id) to WidgetAppearance.TRANSPARENT.name,
        )
        assertEquals(WidgetAppearance.SYSTEM, QuickAddWidgetPrefs.read(preferences, id).appearance)
    }

    /** On by default (user's call); an explicit off must survive the read. */
    @Test
    fun `the app shortcut is on by default and off stays off`() {
        assertTrue(QuickAddWidgetPrefs.read(mutablePreferencesOf(), id).showAppShortcut)
        val disabled = mutablePreferencesOf(QuickAddWidgetPrefs.showAppShortcut(id) to false)
        assertTrue(!QuickAddWidgetPrefs.read(disabled, id).showAppShortcut)
    }

    /**
     * Glance kept one file per widget, so its keys carried no id. A widget
     * configured under that build has to come across intact, or the update would
     * silently reset the account every placed widget adds to.
     */
    @Test
    fun `a Glance-era record is read back off its unsuffixed keys`() {
        val legacy = mutablePreferencesOf(
            androidx.datastore.preferences.core.longPreferencesKey("quick_add_account_id") to 7L,
            androidx.datastore.preferences.core.stringPreferencesKey("quick_add_type") to
                TransactionType.INCOME.name,
            androidx.datastore.preferences.core.stringPreferencesKey("quick_add_pinned_category_ids") to
                "3,1",
            androidx.datastore.preferences.core.booleanPreferencesKey("quick_add_show_app_shortcut") to
                false,
        )

        val config = QuickAddWidgetPrefs.readLegacy(legacy)
        assertEquals(7L, config.accountId)
        assertEquals(TransactionType.INCOME, config.type)
        assertEquals(listOf(3L, 1L), config.pinnedCategoryIds)
        assertTrue(!config.showAppShortcut)
    }
}
