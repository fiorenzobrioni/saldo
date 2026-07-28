package com.callbackdev.saldo.core.common.applock

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How long the app may stay in the background before it locks again.
 * [IMMEDIATELY] is the default; the timed options exist because a share sheet
 * or a SAF picker (backup export) also counts as leaving the foreground.
 */
enum class AutoLockTimeout(val millis: Long) {
    IMMEDIATELY(0L),
    ONE_MINUTE(60_000L),
    FIVE_MINUTES(300_000L),
}

/**
 * Persistence for the app lock (ADR 39), on its own `app_lock` DataStore.
 * Plain storage only: the state machine (locking, verification, cooldown)
 * lives in [AppLockManager]. Flows are deduplicated for the same reason as
 * in `UserPreferencesRepository`: `dataStore.data` re-emits on every write
 * of any key.
 */
@Singleton
class AppLockRepository @Inject constructor(
    @AppLockPreferences private val dataStore: DataStore<Preferences>,
) {

    /**
     * Whether the lock guards the app. True only while a verifiable PIN is
     * actually stored: a half-written or corrupted-and-reset store reads as
     * disabled, never as "locked with nothing to verify against".
     */
    val lockEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        (preferences[LOCK_ENABLED] ?: false) && preferences[PIN_HASH] != null
    }.distinctUntilChanged()

    /** The stored PIN material, or null when no complete entry exists. */
    val storedPin: Flow<StoredPin?> = dataStore.data.map { preferences ->
        val salt = preferences[PIN_SALT]
        val hash = preferences[PIN_HASH]
        val iterations = preferences[PIN_ITERATIONS]
        if (salt != null && hash != null && iterations != null) {
            StoredPin(saltBase64 = salt, hashBase64 = hash, iterations = iterations)
        } else {
            null
        }
    }.distinctUntilChanged()

    /** Whether the lock screen offers the biometric shortcut. */
    val biometricUnlockEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[BIOMETRIC_UNLOCK_ENABLED] ?: false
    }.distinctUntilChanged()

    /**
     * Whether `FLAG_SECURE` is applied to the app's windows: content hidden
     * in the recents thumbnail, screenshots blocked. Independent of the lock.
     */
    val secureScreenEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SECURE_SCREEN_ENABLED] ?: false
    }.distinctUntilChanged()

    val autoLockTimeout: Flow<AutoLockTimeout> = dataStore.data.map { preferences ->
        preferences[AUTO_LOCK_TIMEOUT]
            ?.let { stored -> AutoLockTimeout.entries.firstOrNull { it.name == stored } }
            ?: AutoLockTimeout.IMMEDIATELY
    }.distinctUntilChanged()

    /** Consecutive failed PIN attempts; persisted so killing the process does not reset them. */
    val failedAttempts: Flow<Int> = dataStore.data.map { preferences ->
        preferences[FAILED_ATTEMPTS] ?: 0
    }.distinctUntilChanged()

    /** Epoch millis until which PIN entry is refused, or null when no cooldown is running. */
    val lockoutUntilEpochMilli: Flow<Long?> =
        dataStore.data.map { preferences -> preferences[LOCKOUT_UNTIL_EPOCH_MILLI] }
            .distinctUntilChanged()

    /** Turns the lock on with a freshly confirmed [pin], starting from a clean attempt count. */
    suspend fun enableLock(pin: StoredPin) {
        dataStore.edit { preferences ->
            preferences.putPin(pin)
            preferences[LOCK_ENABLED] = true
            preferences.remove(FAILED_ATTEMPTS)
            preferences.remove(LOCKOUT_UNTIL_EPOCH_MILLI)
        }
    }

    /**
     * Turns the lock off and drops everything tied to it (PIN material,
     * biometric opt-in, attempt counters). The screen-privacy flag and the
     * timeout choice survive: the first is independent of the lock, the
     * second is worth remembering if the lock is re-enabled.
     */
    suspend fun disableLock() {
        dataStore.edit { preferences ->
            preferences.remove(LOCK_ENABLED)
            preferences.remove(PIN_SALT)
            preferences.remove(PIN_HASH)
            preferences.remove(PIN_ITERATIONS)
            preferences.remove(BIOMETRIC_UNLOCK_ENABLED)
            preferences.remove(FAILED_ATTEMPTS)
            preferences.remove(LOCKOUT_UNTIL_EPOCH_MILLI)
        }
    }

    /** Replaces the PIN (the change-PIN flow); the attempt counters restart clean. */
    suspend fun setPin(pin: StoredPin) {
        dataStore.edit { preferences ->
            preferences.putPin(pin)
            preferences.remove(FAILED_ATTEMPTS)
            preferences.remove(LOCKOUT_UNTIL_EPOCH_MILLI)
        }
    }

    suspend fun setBiometricUnlockEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[BIOMETRIC_UNLOCK_ENABLED] = enabled }
    }

    suspend fun setSecureScreenEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[SECURE_SCREEN_ENABLED] = enabled }
    }

    suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        dataStore.edit { preferences -> preferences[AUTO_LOCK_TIMEOUT] = timeout.name }
    }

    /** Persists a failed attempt and, when the cooldown applies, its deadline; one atomic write. */
    suspend fun recordFailedAttempt(attempts: Int, lockoutUntilEpochMilli: Long?) {
        dataStore.edit { preferences ->
            preferences[FAILED_ATTEMPTS] = attempts
            if (lockoutUntilEpochMilli == null) {
                preferences.remove(LOCKOUT_UNTIL_EPOCH_MILLI)
            } else {
                preferences[LOCKOUT_UNTIL_EPOCH_MILLI] = lockoutUntilEpochMilli
            }
        }
    }

    /** Resets the attempt counters after a successful unlock (PIN or biometric). */
    suspend fun clearFailedAttempts() {
        dataStore.edit { preferences ->
            preferences.remove(FAILED_ATTEMPTS)
            preferences.remove(LOCKOUT_UNTIL_EPOCH_MILLI)
        }
    }

    /**
     * Drops every app-lock preference, screen privacy included. Called by the
     * "erase all data" flow (explicitly, per ADR 39: never as a side effect
     * of clearing the user preferences), which returns the app to the state
     * of a fresh install.
     */
    suspend fun clear() {
        dataStore.edit { preferences -> preferences.clear() }
    }

    private fun MutablePreferences.putPin(pin: StoredPin) {
        this[PIN_SALT] = pin.saltBase64
        this[PIN_HASH] = pin.hashBase64
        this[PIN_ITERATIONS] = pin.iterations
    }

    private companion object {
        val LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_ITERATIONS = intPreferencesKey("pin_iterations")
        val BIOMETRIC_UNLOCK_ENABLED = booleanPreferencesKey("biometric_unlock_enabled")
        val SECURE_SCREEN_ENABLED = booleanPreferencesKey("secure_screen_enabled")
        val AUTO_LOCK_TIMEOUT = stringPreferencesKey("auto_lock_timeout")
        val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        val LOCKOUT_UNTIL_EPOCH_MILLI = longPreferencesKey("lockout_until_epoch_milli")
    }
}
