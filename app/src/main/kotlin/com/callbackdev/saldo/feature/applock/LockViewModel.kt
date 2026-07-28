package com.callbackdev.saldo.feature.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.applock.APP_LOCK_PIN_LENGTH
import com.callbackdev.saldo.core.common.applock.AppLockManager
import com.callbackdev.saldo.core.common.applock.AppLockRepository
import com.callbackdev.saldo.core.common.applock.AppLockState
import com.callbackdev.saldo.core.common.applock.PinResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the lock screen draws. The PIN digits themselves never leave the
 * ViewModel (only their count does), and nothing here touches
 * `SavedStateHandle`: a PIN in progress must not be written into the saved
 * instance state. Rotation survives through the ViewModel; process death
 * resets the entry, which is correct for a lock screen.
 */
data class LockUiState(
    val filledDigits: Int = 0,
    val isVerifying: Boolean = false,
    val showError: Boolean = false,
    /** Increments on every rejection, driving the indicator shake. */
    val shakeTick: Int = 0,
    /** Seconds left of the failed-attempt cooldown; 0 when the keypad is live. */
    val lockoutRemainingSeconds: Long = 0,
    /** Whether the biometric shortcut is enabled and the hardware can serve it. */
    val biometricsOffered: Boolean = false,
)

/** Drives [LockScreen] (and the widget sheet's lock pane) against [AppLockManager]. */
@HiltViewModel
class LockViewModel @Inject constructor(
    private val appLockManager: AppLockManager,
    appLockRepository: AppLockRepository,
    biometricAvailability: BiometricAvailability,
) : ViewModel() {

    /** The gate itself; the hosting activity switches content on it. */
    val lockState: StateFlow<AppLockState> = appLockManager.state

    private val _uiState = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    /** The digits typed so far; deliberately kept out of [uiState]. */
    private var enteredPin = ""

    private var countdownJob: Job? = null

    init {
        viewModelScope.launch {
            // Every entry into LOCKED starts a fresh pad and re-reads the
            // persisted cooldown (which survives process death on purpose).
            appLockManager.state.collect { state ->
                if (state == AppLockState.LOCKED) {
                    resetEntry()
                    startCountdown(appLockManager.lockoutRemainingMillis())
                }
            }
        }
        viewModelScope.launch {
            val available = biometricAvailability.canUseBiometrics()
            appLockRepository.biometricUnlockEnabled.collect { enabled ->
                _uiState.update { it.copy(biometricsOffered = enabled && available) }
            }
        }
    }

    fun onDigit(digit: Int) {
        val state = _uiState.value
        if (state.isVerifying || state.lockoutRemainingSeconds > 0) return
        if (enteredPin.length >= APP_LOCK_PIN_LENGTH) return
        enteredPin += digit.toString()
        _uiState.update {
            it.copy(filledDigits = enteredPin.length, showError = false)
        }
        if (enteredPin.length == APP_LOCK_PIN_LENGTH) {
            submit()
        }
    }

    fun onBackspace() {
        if (_uiState.value.isVerifying) return
        if (enteredPin.isEmpty()) return
        enteredPin = enteredPin.dropLast(1)
        _uiState.update { it.copy(filledDigits = enteredPin.length, showError = false) }
    }

    /** Called by the biometric prompt's success callback. */
    fun onBiometricUnlocked() {
        viewModelScope.launch { appLockManager.unlockWithBiometrics() }
    }

    private fun submit() {
        _uiState.update { it.copy(isVerifying = true) }
        viewModelScope.launch {
            val result = appLockManager.submitPin(enteredPin)
            enteredPin = ""
            when (result) {
                // The state flow flips to UNLOCKED and the screen leaves; the
                // pad is still reset so a re-lock never shows stale dots.
                PinResult.Unlocked -> _uiState.update {
                    it.copy(filledDigits = 0, isVerifying = false, showError = false)
                }
                is PinResult.Rejected -> {
                    _uiState.update {
                        it.copy(
                            filledDigits = 0,
                            isVerifying = false,
                            showError = true,
                            shakeTick = it.shakeTick + 1,
                        )
                    }
                    startCountdown(result.lockoutRemainingMillis)
                }
            }
        }
    }

    /** Ticks the cooldown once a second; a zero [remainingMillis] just clears it. */
    private fun startCountdown(remainingMillis: Long) {
        countdownJob?.cancel()
        if (remainingMillis <= 0) {
            _uiState.update { it.copy(lockoutRemainingSeconds = 0) }
            return
        }
        countdownJob = viewModelScope.launch {
            var remaining = remainingMillis
            while (remaining > 0) {
                _uiState.update {
                    it.copy(lockoutRemainingSeconds = ceilToSeconds(remaining))
                }
                delay(TICK_MILLIS)
                remaining -= TICK_MILLIS
            }
            _uiState.update { it.copy(lockoutRemainingSeconds = 0) }
        }
    }

    private fun resetEntry() {
        enteredPin = ""
        _uiState.update {
            it.copy(filledDigits = 0, isVerifying = false, showError = false)
        }
    }

    private fun ceilToSeconds(millis: Long): Long = (millis + TICK_MILLIS - 1) / TICK_MILLIS

    private companion object {
        const val TICK_MILLIS = 1_000L
    }
}
