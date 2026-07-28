package com.callbackdev.saldo.feature.applock

import com.callbackdev.saldo.core.common.applock.AppLockRepository
import com.callbackdev.saldo.core.common.applock.AutoLockTimeout
import com.callbackdev.saldo.core.common.applock.PinHasher
import com.callbackdev.saldo.core.common.applock.StoredPin
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class SecurityViewModelTest {

    private val lockEnabledFlow = MutableStateFlow(false)
    private val biometricEnabledFlow = MutableStateFlow(false)
    private val secureScreenFlow = MutableStateFlow(false)
    private val timeoutFlow = MutableStateFlow(AutoLockTimeout.IMMEDIATELY)
    private val storedPinFlow = MutableStateFlow<StoredPin?>(null)

    private val repository = mockk<AppLockRepository>(relaxUnitFun = true) {
        every { lockEnabled } returns lockEnabledFlow
        every { biometricUnlockEnabled } returns biometricEnabledFlow
        every { secureScreenEnabled } returns secureScreenFlow
        every { autoLockTimeout } returns timeoutFlow
        every { storedPin } returns storedPinFlow
    }

    /** Real derivation is 150k rounds per call; the tests stub the crypto. */
    private val pinHasher = mockk<PinHasher> {
        every { create(any()) } answers { StoredPin("salt", firstArg(), 1) }
        every { matches(any(), any()) } answers {
            firstArg<String>() == secondArg<StoredPin>().hashBase64
        }
    }

    private val biometricAvailability = mockk<BiometricAvailability> {
        every { canUseBiometrics() } returns true
    }

    private fun viewModel(): SecurityViewModel = SecurityViewModel(
        appLockRepository = repository,
        pinHasher = pinHasher,
        biometricAvailability = biometricAvailability,
        defaultDispatcher = UnconfinedTestDispatcher(),
    )

    /** uiState is WhileSubscribed: the combine only runs with a collector attached. */
    private fun TestScope.collecting(viewModel: SecurityViewModel): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

    private fun enterPin(viewModel: SecurityViewModel, pin: String) {
        pin.forEach { viewModel.onDigit(it.digitToInt()) }
    }

    @Test
    fun `enabling walks create and confirm, then persists the lock`() = runTest {
        val viewModel = viewModel()
        collecting(viewModel)

        viewModel.onAppLockToggled(true)
        assertEquals(PinFlowStep.CREATE, viewModel.uiState.value.pinFlow?.step)

        enterPin(viewModel, "123456")
        assertEquals(PinFlowStep.CONFIRM, viewModel.uiState.value.pinFlow?.step)

        val stored = slot<StoredPin>()
        coEvery { repository.enableLock(capture(stored)) } returns Unit
        enterPin(viewModel, "123456")

        assertNull(viewModel.uiState.value.pinFlow)
        // The persisted entry verifies the PIN that was typed (stubbed hasher
        // stores the pin as the hash).
        assertTrue(pinHasher.matches("123456", stored.captured))
    }

    @Test
    fun `a confirm mismatch returns to create with an error, nothing persisted`() = runTest {
        val viewModel = viewModel()
        collecting(viewModel)

        viewModel.onAppLockToggled(true)
        enterPin(viewModel, "123456")
        enterPin(viewModel, "654321")

        val flow = viewModel.uiState.value.pinFlow
        assertEquals(PinFlowStep.CREATE, flow?.step)
        assertEquals(PinFlowError.MISMATCH, flow?.error)
        assertEquals(1, flow?.shakeTick)
        coVerify(exactly = 0) { repository.enableLock(any()) }
    }

    @Test
    fun `disabling asks for the current pin and only a match turns the lock off`() = runTest {
        lockEnabledFlow.value = true
        storedPinFlow.value = StoredPin("salt", "123456", 1)
        val viewModel = viewModel()
        collecting(viewModel)

        viewModel.onAppLockToggled(false)
        assertEquals(PinFlowStep.VERIFY, viewModel.uiState.value.pinFlow?.step)

        enterPin(viewModel, "000000")
        assertEquals(PinFlowError.WRONG_PIN, viewModel.uiState.value.pinFlow?.error)
        coVerify(exactly = 0) { repository.disableLock() }

        enterPin(viewModel, "123456")
        assertNull(viewModel.uiState.value.pinFlow)
        coVerify(exactly = 1) { repository.disableLock() }
    }

    @Test
    fun `changing the pin verifies the old one and stores the new one`() = runTest {
        lockEnabledFlow.value = true
        storedPinFlow.value = StoredPin("salt", "123456", 1)
        val viewModel = viewModel()
        collecting(viewModel)

        viewModel.onChangePinClicked()
        enterPin(viewModel, "123456")
        assertEquals(PinFlowStep.CREATE, viewModel.uiState.value.pinFlow?.step)

        val stored = slot<StoredPin>()
        coEvery { repository.setPin(capture(stored)) } returns Unit
        enterPin(viewModel, "999999")
        enterPin(viewModel, "999999")

        assertNull(viewModel.uiState.value.pinFlow)
        assertTrue(pinHasher.matches("999999", stored.captured))
        coVerify(exactly = 0) { repository.enableLock(any()) }
    }

    @Test
    fun `dismissing a flow changes nothing`() = runTest {
        val viewModel = viewModel()
        collecting(viewModel)

        viewModel.onAppLockToggled(true)
        enterPin(viewModel, "123")
        viewModel.onFlowDismissed()

        assertNull(viewModel.uiState.value.pinFlow)
        coVerify(exactly = 0) { repository.enableLock(any()) }
    }

    @Test
    fun `backspace edits the current entry`() = runTest {
        val viewModel = viewModel()
        collecting(viewModel)

        viewModel.onAppLockToggled(true)
        enterPin(viewModel, "12")
        viewModel.onBackspace()

        assertEquals(1, viewModel.uiState.value.pinFlow?.filledDigits)
    }

    @Test
    fun `the simple preferences write straight through`() = runTest {
        val viewModel = viewModel()

        viewModel.setBiometricUnlockEnabled(true)
        viewModel.onAutoLockTimeoutSelected(AutoLockTimeout.FIVE_MINUTES)
        viewModel.onSecureScreenChanged(true)

        coVerify { repository.setBiometricUnlockEnabled(true) }
        coVerify { repository.setAutoLockTimeout(AutoLockTimeout.FIVE_MINUTES) }
        coVerify { repository.setSecureScreenEnabled(true) }
    }

    @Test
    fun `biometric availability gates the switch's visibility`() = runTest {
        every { biometricAvailability.canUseBiometrics() } returns false
        val viewModel = viewModel()
        collecting(viewModel)

        assertEquals(false, viewModel.uiState.value.biometricAvailable)
    }
}
