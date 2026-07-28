package com.callbackdev.saldo.feature.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.applock.APP_LOCK_PIN_LENGTH
import com.callbackdev.saldo.core.common.applock.AppLockRepository
import com.callbackdev.saldo.core.common.applock.AutoLockTimeout
import com.callbackdev.saldo.core.common.applock.PinHasher
import com.callbackdev.saldo.core.common.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Why a PIN is being asked in the Security screen. */
enum class PinFlowPurpose { ENABLE, DISABLE, CHANGE }

/** Which step of the flow is on screen. */
enum class PinFlowStep { VERIFY, CREATE, CONFIRM }

/** What went wrong on the last completed entry, if anything. */
enum class PinFlowError { WRONG_PIN, MISMATCH }

/**
 * One in-progress PIN flow. Only the digit count is exposed; the digits
 * themselves stay inside the ViewModel and are never written to
 * `SavedStateHandle` (a clear-text PIN must not enter the saved instance
 * state). Rotation survives through the ViewModel; process death drops the
 * flow, which is correct.
 */
data class PinFlowState(
    val purpose: PinFlowPurpose,
    val step: PinFlowStep,
    val filledDigits: Int = 0,
    val error: PinFlowError? = null,
    /** Increments on every rejection, driving the indicator shake. */
    val shakeTick: Int = 0,
)

data class SecurityUiState(
    val lockEnabled: Boolean = false,
    val biometricUnlockEnabled: Boolean = false,
    /** Whether strong biometrics are enrolled; hides the switch entirely otherwise. */
    val biometricAvailable: Boolean = false,
    val secureScreenEnabled: Boolean = false,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.IMMEDIATELY,
    /** Non-null while a PIN entry pane replaces the settings list. */
    val pinFlow: PinFlowState? = null,
)

/**
 * Drives the Security settings screen: the lock switch (whose enabling is
 * only persisted after the create+confirm flow lands), PIN changes, the
 * biometric opt-in and the screen-privacy flag. Verification here runs
 * without the lock screen's cooldown: these flows are only reachable with
 * the app already unlocked.
 */
@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val appLockRepository: AppLockRepository,
    private val pinHasher: PinHasher,
    biometricAvailability: BiometricAvailability,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val pinFlow = MutableStateFlow<PinFlowState?>(null)

    /** The digits typed in the current step; deliberately kept out of the state. */
    private var enteredPin = ""

    /** The PIN accepted at the CREATE step, awaiting confirmation. */
    private var pinToConfirm: String? = null

    /** Resolved once: enrolment changes are picked up on the next screen entry. */
    private val biometricAvailable = biometricAvailability.canUseBiometrics()

    val uiState: StateFlow<SecurityUiState> = combine(
        appLockRepository.lockEnabled,
        appLockRepository.biometricUnlockEnabled,
        appLockRepository.secureScreenEnabled,
        appLockRepository.autoLockTimeout,
        pinFlow,
    ) { lockEnabled, biometricEnabled, secureScreen, timeout, flow ->
        SecurityUiState(
            lockEnabled = lockEnabled,
            biometricUnlockEnabled = biometricEnabled,
            biometricAvailable = biometricAvailable,
            secureScreenEnabled = secureScreen,
            autoLockTimeout = timeout,
            pinFlow = flow,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SecurityUiState(),
    )

    /** The lock switch: ON starts the create flow, OFF asks for the current PIN first. */
    fun onAppLockToggled(enabled: Boolean) {
        val current = uiState.value.lockEnabled
        when {
            enabled && !current -> startFlow(PinFlowPurpose.ENABLE, PinFlowStep.CREATE)
            !enabled && current -> startFlow(PinFlowPurpose.DISABLE, PinFlowStep.VERIFY)
        }
    }

    fun onChangePinClicked() {
        startFlow(PinFlowPurpose.CHANGE, PinFlowStep.VERIFY)
    }

    /** Back or the top bar close while a flow is showing: drop it, change nothing. */
    fun onFlowDismissed() {
        clearEntry()
        pinToConfirm = null
        pinFlow.value = null
    }

    fun onDigit(digit: Int) {
        val flow = pinFlow.value ?: return
        if (enteredPin.length >= APP_LOCK_PIN_LENGTH) return
        enteredPin += digit.toString()
        pinFlow.value = flow.copy(filledDigits = enteredPin.length, error = null)
        if (enteredPin.length == APP_LOCK_PIN_LENGTH) {
            onEntryCompleted(flow)
        }
    }

    fun onBackspace() {
        val flow = pinFlow.value ?: return
        if (enteredPin.isEmpty()) return
        enteredPin = enteredPin.dropLast(1)
        pinFlow.value = flow.copy(filledDigits = enteredPin.length, error = null)
    }

    /**
     * Persists the biometric opt-in. The screen only calls this with `true`
     * after a successful confirmation prompt: a toggle that was never proven
     * to work is worse than no toggle.
     */
    fun setBiometricUnlockEnabled(enabled: Boolean) {
        viewModelScope.launch { appLockRepository.setBiometricUnlockEnabled(enabled) }
    }

    fun onAutoLockTimeoutSelected(timeout: AutoLockTimeout) {
        viewModelScope.launch { appLockRepository.setAutoLockTimeout(timeout) }
    }

    fun onSecureScreenChanged(enabled: Boolean) {
        viewModelScope.launch { appLockRepository.setSecureScreenEnabled(enabled) }
    }

    private fun startFlow(purpose: PinFlowPurpose, step: PinFlowStep) {
        clearEntry()
        pinToConfirm = null
        pinFlow.value = PinFlowState(purpose = purpose, step = step)
    }

    private fun onEntryCompleted(flow: PinFlowState) {
        val pin = enteredPin
        when (flow.step) {
            PinFlowStep.VERIFY -> verify(flow, pin)
            PinFlowStep.CREATE -> {
                pinToConfirm = pin
                clearEntry()
                pinFlow.value = flow.copy(step = PinFlowStep.CONFIRM, filledDigits = 0, error = null)
            }
            PinFlowStep.CONFIRM -> confirm(flow, pin)
        }
    }

    private fun verify(flow: PinFlowState, pin: String) {
        viewModelScope.launch {
            val stored = appLockRepository.storedPin.first()
            val matches = stored != null &&
                withContext(defaultDispatcher) { pinHasher.matches(pin, stored) }
            clearEntry()
            if (!matches) {
                pinFlow.value = flow.copy(
                    filledDigits = 0,
                    error = PinFlowError.WRONG_PIN,
                    shakeTick = flow.shakeTick + 1,
                )
                return@launch
            }
            when (flow.purpose) {
                PinFlowPurpose.DISABLE -> {
                    appLockRepository.disableLock()
                    pinFlow.value = null
                }
                PinFlowPurpose.CHANGE -> {
                    pinFlow.value = flow.copy(step = PinFlowStep.CREATE, filledDigits = 0, error = null)
                }
                // ENABLE never verifies; exhaustiveness only.
                PinFlowPurpose.ENABLE -> pinFlow.value = null
            }
        }
    }

    private fun confirm(flow: PinFlowState, pin: String) {
        val expected = pinToConfirm
        clearEntry()
        if (expected == null || pin != expected) {
            // Mismatch: back to square one of the creation, never a silent save.
            pinToConfirm = null
            pinFlow.value = flow.copy(
                step = PinFlowStep.CREATE,
                filledDigits = 0,
                error = PinFlowError.MISMATCH,
                shakeTick = flow.shakeTick + 1,
            )
            return
        }
        viewModelScope.launch {
            val stored = withContext(defaultDispatcher) { pinHasher.create(pin) }
            when (flow.purpose) {
                PinFlowPurpose.ENABLE -> appLockRepository.enableLock(stored)
                PinFlowPurpose.CHANGE -> appLockRepository.setPin(stored)
                // DISABLE never reaches CONFIRM; exhaustiveness only.
                PinFlowPurpose.DISABLE -> Unit
            }
            pinToConfirm = null
            pinFlow.value = null
        }
    }

    private fun clearEntry() {
        enteredPin = ""
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
