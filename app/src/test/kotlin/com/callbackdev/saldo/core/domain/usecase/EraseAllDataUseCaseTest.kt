package com.callbackdev.saldo.core.domain.usecase

import app.cash.turbine.test
import com.callbackdev.saldo.core.common.applock.AppLockRepository
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.common.recurrencescan.RecurrenceScanStore
import com.callbackdev.saldo.core.domain.repository.BackupRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EraseAllDataUseCaseTest {

    private val backupRepository = mockk<BackupRepository>(relaxUnitFun = true)
    private val userPreferences = mockk<UserPreferencesRepository>(relaxUnitFun = true)
    private val appLockRepository = mockk<AppLockRepository>(relaxUnitFun = true)
    private val recurrenceScanStore = mockk<RecurrenceScanStore>(relaxUnitFun = true)
    private val resetCoordinator = AppResetCoordinator()

    private val useCase = EraseAllDataUseCase(
        backupRepository,
        userPreferences,
        appLockRepository,
        recurrenceScanStore,
        resetCoordinator,
    )

    @Test
    fun `wipes the database, then the preferences, then announces the reset`() = runTest {
        useCase()

        // The data goes before the preferences: see the failure case below. The
        // app lock goes too, explicitly (ADR 39): a factory reset returns the
        // app to a fresh install, PIN included.
        coVerifyOrder {
            backupRepository.eraseAll()
            userPreferences.clear()
            appLockRepository.clear()
            // The scan result describes movements that no longer exist (ADR 43).
            recurrenceScanStore.clear()
        }
        resetCoordinator.events.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed wipe leaves the preferences alone`() = runTest {
        coEvery { backupRepository.eraseAll() } throws IllegalStateException("db is busy")

        assertThrows(IllegalStateException::class.java) { runTest { useCase() } }

        // Clearing them anyway would leave an intact database that has forgotten
        // its currency, theme and default account: worse than not erasing at all.
        coVerify(exactly = 0) { userPreferences.clear() }
        coVerify(exactly = 0) { appLockRepository.clear() }
        coVerify(exactly = 0) { recurrenceScanStore.clear() }
    }
}
