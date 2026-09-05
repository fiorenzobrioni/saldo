package com.callbackdev.saldo.core.common.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.callbackdev.saldo.core.domain.backup.SettingsBackup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What the settings half of a backup promises: the snapshot reports what is
 * *stored* (not what a reader would resolve), the restore reproduces the
 * exporting install exactly, and it never touches the preferences that describe
 * this device.
 */
class SettingsBackupStoreTest {

    private val dataStore = FakePreferencesDataStore()
    private val store = SettingsBackupStore(dataStore)

    @Test
    fun `an untouched install snapshots nothing set`() = runTest {
        assertEquals(SettingsBackup(), store.snapshotSettings())
    }

    @Test
    fun `the snapshot reports stored values, never resolved defaults`() = runTest {
        val preferences = UserPreferencesRepository(dataStore)
        preferences.setThemeMode(ThemeMode.DARK)
        preferences.setCsvSeparator(CsvSeparator.COMMA)

        val snapshot = store.snapshotSettings()

        assertEquals("DARK", snapshot.themeMode)
        assertEquals("COMMA", snapshot.csvSeparator)
        // Never set, so it must stay unset: the restoring device keeps following
        // its own locale for the week start instead of inheriting one.
        assertNull(snapshot.firstDayOfWeek)
        assertNull(snapshot.currencyConversionEnabled)
    }

    @Test
    fun `every setting survives snapshot and restore`() = runTest {
        val exported = SettingsBackup(
            defaultAccountId = 7L,
            primaryCurrencyCode = "CHF",
            currencyConversionEnabled = false,
            themeMode = "LIGHT",
            useDynamicColor = true,
            renewalReminderEnabled = true,
            renewalReminderLeadDays = 7,
            backupReminderEnabled = true,
            backupReminderIntervalDays = 30,
            firstDayOfWeek = "SUNDAY",
            csvSeparator = "COMMA",
            backupEncryptionEnabled = true,
            dashboardShowBudget = false,
            dashboardShowSafeToSpend = false,
            dashboardShowRecentTransactions = false,
            dashboardShowSavingsGoals = false,
            dashboardShowCounterparties = false,
            dashboardShowUpcoming = false,
            dashboardShowRecapTeaser = false,
            balanceAccountsExpandedByDefault = false,
        )

        store.restoreSettings(exported)

        assertEquals(exported, store.snapshotSettings())
    }

    @Test
    fun `the restored values are the ones the app then reads`() = runTest {
        val preferences = UserPreferencesRepository(dataStore)

        store.restoreSettings(
            SettingsBackup(
                themeMode = "DARK",
                useDynamicColor = true,
                renewalReminderEnabled = true,
                renewalReminderLeadDays = 7,
                backupReminderEnabled = true,
                backupReminderIntervalDays = 30,
                backupEncryptionEnabled = true,
                dashboardShowBudget = false,
            ),
        )

        assertEquals(ThemeMode.DARK, preferences.themePreferences.first().mode)
        assertTrue(preferences.themePreferences.first().useDynamicColor)
        assertEquals(7, preferences.renewalReminderPreferences.first().leadDays)
        assertTrue(preferences.backupReminderPreferences.first().enabled)
        assertEquals(30, preferences.backupReminderPreferences.first().intervalDays)
        assertTrue(preferences.backupEncryptionEnabled.first())
        assertFalse(preferences.dashboardCardPreferences.first().showBudget)
    }

    @Test
    fun `a restore clears the settings the file does not carry`() = runTest {
        val preferences = UserPreferencesRepository(dataStore)
        preferences.setThemeMode(ThemeMode.DARK)
        preferences.setDefaultAccountId(3L)

        store.restoreSettings(SettingsBackup())

        assertEquals(SettingsBackup(), store.snapshotSettings())
        assertEquals(ThemeMode.SYSTEM, preferences.themePreferences.first().mode)
        assertNull(preferences.defaultAccountId.first())
    }

    @Test
    fun `a restore leaves this install's own history alone`() = runTest {
        val preferences = UserPreferencesRepository(dataStore)
        preferences.setLastUsedAccountId(9L)
        preferences.setLastBackupAt(1_700_000_000_000)
        preferences.setOnboardingCompleted()
        preferences.setLastRateSyncAttempt(1_700_000_500_000)

        store.restoreSettings(SettingsBackup(themeMode = "DARK"))

        assertEquals(9L, preferences.lastUsedAccountId.first()!!)
        assertEquals(1_700_000_000_000L, preferences.lastBackupAtEpochMilli.first()!!)
        assertTrue(preferences.onboardingCompleted.first() == true)
        assertEquals(1_700_000_500_000L, preferences.lastRateSyncAttemptEpochMilli.first()!!)
    }

    @Test
    fun `values that could not have come from the app are dropped, not stored`() = runTest {
        store.restoreSettings(
            SettingsBackup(
                primaryCurrencyCode = "EURO",
                themeMode = "NEON",
                csvSeparator = "TAB",
                firstDayOfWeek = "FRIDAY",
                renewalReminderLeadDays = 99,
            ),
        )

        val snapshot = store.snapshotSettings()
        assertNull(snapshot.primaryCurrencyCode)
        assertNull(snapshot.themeMode)
        assertNull(snapshot.csvSeparator)
        // A real day of the week, but not one the app offers.
        assertNull(snapshot.firstDayOfWeek)
        assertNull(snapshot.renewalReminderLeadDays)
    }

    /** In-memory [DataStore]: the real one needs a file and a scope. */
    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences = transform(state.value).also { state.value = it }
    }
}
