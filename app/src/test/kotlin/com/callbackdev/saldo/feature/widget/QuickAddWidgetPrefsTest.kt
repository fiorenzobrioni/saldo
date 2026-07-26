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
}
