package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.common.applock.AppLockRepository
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.common.recurrencescan.RecurrenceScanStore
import com.callbackdev.saldo.core.domain.repository.BackupRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Announces that the app was reset to its factory state, so the shell can send
 * the user back to the onboarding instead of leaving them on an empty app.
 *
 * A signal rather than a reactive read of the onboarding flag: the flag is also
 * written mid-onboarding (an existing install that creates its first account),
 * so a ViewModel watching it would jump out of the flow at the wrong moment.
 * The launch gate stays a one-shot decision and listens for this instead.
 */
@Singleton
class AppResetCoordinator @Inject constructor() {

    private val _events = Channel<Unit>(Channel.CONFLATED)
    val events: Flow<Unit> = _events.receiveAsFlow()

    suspend fun publish() {
        _events.send(Unit)
    }
}

/**
 * Returns the app to the state of a fresh install: every table emptied and the
 * default categories replanted ([BackupRepository.eraseAll]), every stored
 * preference dropped, and the launch gate pushed back to the onboarding.
 *
 * Order matters. The database goes first and in its own transaction, so a
 * failure leaves both the data *and* the preferences untouched, rather than an
 * intact database with the currency, theme and default account forgotten.
 * Preferences are cleared only once the data is actually gone.
 *
 * There is no undo: the confirmation step in the UI is the only guard, which is
 * why it states what will be lost and when the last backup was taken.
 */
class EraseAllDataUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
    private val userPreferences: UserPreferencesRepository,
    private val appLockRepository: AppLockRepository,
    private val recurrenceScanStore: RecurrenceScanStore,
    private val resetCoordinator: AppResetCoordinator,
) {

    suspend operator fun invoke() {
        backupRepository.eraseAll()
        userPreferences.clear()
        // Deliberate and explicit (ADR 39): the PIN lives in its own store
        // precisely so it cannot be wiped as a side effect, and a factory
        // reset removes it on purpose - back to a fresh install, and whoever
        // triggers this is already past the lock.
        appLockRepository.clear()
        // The scan result describes movements that no longer exist (ADR 43).
        recurrenceScanStore.clear()
        resetCoordinator.publish()
    }
}
