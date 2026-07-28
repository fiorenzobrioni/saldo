package com.callbackdev.saldo.core.common.applock

import com.callbackdev.saldo.core.common.di.ApplicationScope
import com.callbackdev.saldo.core.common.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fixed PIN length (ADR 39): six digits is the current platform convention,
 * and a fixed length is what enables auto-submit on the last digit (no
 * confirm key on the keypad).
 */
const val APP_LOCK_PIN_LENGTH = 6

/** Where the lock gate stands: still deciding, covering the app, or out of the way. */
enum class AppLockState { EVALUATING, LOCKED, UNLOCKED }

/** Outcome of a PIN submission. */
sealed interface PinResult {
    /** The PIN matched; the gate is already open. */
    data object Unlocked : PinResult

    /**
     * The PIN was wrong or refused. [lockoutRemainingMillis] is how long PIN
     * entry stays disabled (0 when no cooldown applies); biometrics remain
     * available either way.
     */
    data class Rejected(val lockoutRemainingMillis: Long) : PinResult
}

/**
 * The app-lock state machine (ADR 39): one per process, shared by every
 * activity, so unlocking the app also opens the widget sheet and vice versa.
 *
 * The gate starts [AppLockState.EVALUATING] and the UI draws an opaque
 * surface until it resolves: the deliberate inversion of the launch gate's
 * fail-open (`MainViewModel`), because showing balances to the wrong hands
 * is worse than one blank frame. The fail-closed stance only holds while a
 * verifiable PIN exists, though: an unreadable store means there is nothing
 * to verify against, and locking forever would brick the app, so the read
 * error path degrades to "lock disabled" (the PIN is lost, the financial
 * data - which does not depend on it - never is).
 *
 * Re-locking is armed by [onAppBackgrounded]/[onAppForegrounded], driven by
 * [AppLockLifecycleObserver]: configuration changes never pass through them,
 * and process death locks by construction (this state is in-memory only).
 * The failed-attempt cooldown is persisted instead, so killing the process
 * does not reset it.
 */
@Singleton
class AppLockManager @Inject constructor(
    private val repository: AppLockRepository,
    private val pinHasher: PinHasher,
    private val clock: Clock,
    @ApplicationScope scope: CoroutineScope,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {

    private val _state = MutableStateFlow(AppLockState.EVALUATING)
    val state: StateFlow<AppLockState> = _state.asStateFlow()

    // Snapshots of the reactive preferences, so the lifecycle hooks (called on
    // the main thread, outside any coroutine) can consult them synchronously.
    @Volatile
    private var lockEnabled = false

    @Volatile
    private var timeoutMillis = AutoLockTimeout.IMMEDIATELY.millis

    /** When the app last left the foreground; null until it does. */
    @Volatile
    private var backgroundedAtMillis: Long? = null

    init {
        scope.launch {
            repository.lockEnabled
                // See the class doc: an unreadable store degrades to
                // "disabled" because a lock with no verifiable PIN could
                // never be opened again.
                .catch { emit(false) }
                .collect { enabled ->
                    lockEnabled = enabled
                    if (!enabled) {
                        // Turning the lock off from Settings (or erasing all
                        // data) opens the gate reactively.
                        _state.value = AppLockState.UNLOCKED
                    } else if (_state.value == AppLockState.EVALUATING) {
                        // Only the first resolution locks: enabling the lock
                        // from Settings must not lock the user out of the
                        // session they are enabling it in.
                        _state.value = AppLockState.LOCKED
                    }
                }
        }
        scope.launch {
            repository.autoLockTimeout
                .catch { emit(AutoLockTimeout.IMMEDIATELY) }
                .collect { timeoutMillis = it.millis }
        }
    }

    /**
     * Verifies [pin] against the stored hash, tracking failed attempts and
     * their progressive cooldown. Safe to call from the main dispatcher: the
     * derivation runs on [defaultDispatcher].
     */
    suspend fun submitPin(pin: String): PinResult {
        val remaining = lockoutRemainingMillis()
        if (remaining > 0) return PinResult.Rejected(remaining)
        val stored = repository.storedPin.first()
        if (stored == null) {
            // No verifiable PIN (corruption handler reset the store while
            // locked): nothing can ever match, so the only honest move is
            // to open. Same fail-open-on-loss rationale as the class doc.
            _state.value = AppLockState.UNLOCKED
            return PinResult.Unlocked
        }
        val matches = withContext(defaultDispatcher) { pinHasher.matches(pin, stored) }
        return if (matches) {
            repository.clearFailedAttempts()
            _state.value = AppLockState.UNLOCKED
            PinResult.Unlocked
        } else {
            val attempts = repository.failedAttempts.first() + 1
            val cooldown = cooldownMillisFor(attempts)
            repository.recordFailedAttempt(
                attempts = attempts,
                lockoutUntilEpochMilli = cooldown?.let { clock.millis() + it },
            )
            PinResult.Rejected(cooldown ?: 0L)
        }
    }

    /** Opens the gate after a successful biometric authentication. */
    suspend fun unlockWithBiometrics() {
        repository.clearFailedAttempts()
        _state.value = AppLockState.UNLOCKED
    }

    /** How long PIN entry stays refused, in millis; 0 when no cooldown is running. */
    suspend fun lockoutRemainingMillis(): Long {
        val until = repository.lockoutUntilEpochMilli.first() ?: return 0L
        return (until - clock.millis()).coerceAtLeast(0L)
    }

    /** The whole app (any activity) left the foreground; starts the re-lock timer. */
    fun onAppBackgrounded() {
        backgroundedAtMillis = clock.millis()
    }

    /** The app came back to the foreground; re-locks when the timeout has elapsed. */
    fun onAppForegrounded() {
        val awayMillis = backgroundedAtMillis?.let { clock.millis() - it } ?: return
        if (lockEnabled && _state.value == AppLockState.UNLOCKED && awayMillis >= timeoutMillis) {
            _state.value = AppLockState.LOCKED
        }
    }

    /**
     * Progressive cooldown: nothing for the first misses, then 30s at the
     * [FREE_ATTEMPTS]th consecutive failure, doubling on each further one up
     * to a 5 minute cap. Slows a guessing hand to a crawl without ever
     * locking the legitimate owner out for good.
     */
    private fun cooldownMillisFor(attempts: Int): Long? {
        if (attempts < FREE_ATTEMPTS) return null
        val doublings = (attempts - FREE_ATTEMPTS).coerceAtMost(MAX_DOUBLINGS)
        return (BASE_COOLDOWN_MILLIS shl doublings).coerceAtMost(MAX_COOLDOWN_MILLIS)
    }

    private companion object {
        const val FREE_ATTEMPTS = 5
        const val BASE_COOLDOWN_MILLIS = 30_000L
        const val MAX_COOLDOWN_MILLIS = 300_000L

        /** Enough shifts to pass the cap; keeps the shift far from overflow. */
        const val MAX_DOUBLINGS = 4
    }
}
