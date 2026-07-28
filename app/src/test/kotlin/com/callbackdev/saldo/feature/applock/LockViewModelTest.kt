package com.callbackdev.saldo.feature.applock

import com.callbackdev.saldo.core.common.applock.AppLockManager
import com.callbackdev.saldo.core.common.applock.AppLockRepository
import com.callbackdev.saldo.core.common.applock.AppLockState
import com.callbackdev.saldo.core.common.applock.PinResult
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class LockViewModelTest {

    private val lockStateFlow = MutableStateFlow(AppLockState.LOCKED)

    private val appLockManager = mockk<AppLockManager>(relaxUnitFun = true) {
        every { state } returns lockStateFlow
        coEvery { lockoutRemainingMillis() } returns 0L
        coEvery { submitPin(any()) } returns PinResult.Rejected(0L)
    }

    private val appLockRepository = mockk<AppLockRepository> {
        every { biometricUnlockEnabled } returns flowOf(false)
    }

    private val biometricAvailability = mockk<BiometricAvailability> {
        every { canUseBiometrics() } returns false
    }

    private fun viewModel(): LockViewModel = LockViewModel(
        appLockManager = appLockManager,
        appLockRepository = appLockRepository,
        biometricAvailability = biometricAvailability,
    )

    @Test
    fun `digits accumulate and the sixth auto-submits`() = runTest {
        coEvery { appLockManager.submitPin("123456") } returns PinResult.Unlocked
        val viewModel = viewModel()

        listOf(1, 2, 3, 4, 5).forEach(viewModel::onDigit)
        assertEquals(5, viewModel.uiState.value.filledDigits)
        coVerify(exactly = 0) { appLockManager.submitPin(any()) }

        viewModel.onDigit(6)

        coVerify(exactly = 1) { appLockManager.submitPin("123456") }
        assertEquals(0, viewModel.uiState.value.filledDigits)
        assertFalse(viewModel.uiState.value.showError)
    }

    @Test
    fun `backspace drops the last digit`() = runTest {
        val viewModel = viewModel()

        viewModel.onDigit(1)
        viewModel.onDigit(2)
        viewModel.onBackspace()

        assertEquals(1, viewModel.uiState.value.filledDigits)
    }

    @Test
    fun `a rejection clears the dots, shows the error and shakes`() = runTest {
        val viewModel = viewModel()

        repeat(6) { viewModel.onDigit(0) }

        val state = viewModel.uiState.value
        assertEquals(0, state.filledDigits)
        assertTrue(state.showError)
        assertEquals(1, state.shakeTick)
        // The next digit clears the error message.
        viewModel.onDigit(1)
        assertFalse(viewModel.uiState.value.showError)
    }

    @Test
    fun `a rejection with a cooldown starts the countdown and blocks digits`() = runTest {
        coEvery { appLockManager.submitPin(any()) } returns PinResult.Rejected(30_000L)
        val viewModel = viewModel()

        repeat(6) { viewModel.onDigit(0) }
        assertEquals(30L, viewModel.uiState.value.lockoutRemainingSeconds)

        // Digits are ignored while the cooldown runs.
        viewModel.onDigit(1)
        assertEquals(0, viewModel.uiState.value.filledDigits)
        coVerify(exactly = 1) { appLockManager.submitPin(any()) }

        // advanceTimeBy stops short of tasks scheduled exactly at the target
        // time; runCurrent executes the tick that lands on the boundary.
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(20L, viewModel.uiState.value.lockoutRemainingSeconds)

        advanceTimeBy(20_000L)
        runCurrent()
        assertEquals(0L, viewModel.uiState.value.lockoutRemainingSeconds)
    }

    @Test
    fun `a persisted cooldown is picked up when the screen locks`() = runTest {
        coEvery { appLockManager.lockoutRemainingMillis() } returns 12_000L

        val viewModel = viewModel()

        assertEquals(12L, viewModel.uiState.value.lockoutRemainingSeconds)
    }

    @Test
    fun `re-locking resets a half-typed entry`() = runTest {
        lockStateFlow.value = AppLockState.UNLOCKED
        val viewModel = viewModel()
        viewModel.onDigit(1)
        viewModel.onDigit(2)

        lockStateFlow.value = AppLockState.LOCKED

        assertEquals(0, viewModel.uiState.value.filledDigits)
    }

    @Test
    fun `biometric success delegates to the manager`() = runTest {
        val viewModel = viewModel()

        viewModel.onBiometricUnlocked()

        coVerify(exactly = 1) { appLockManager.unlockWithBiometrics() }
    }

    @Test
    fun `biometrics are offered only when enabled and available`() = runTest {
        every { appLockRepository.biometricUnlockEnabled } returns flowOf(true)
        every { biometricAvailability.canUseBiometrics() } returns true

        assertTrue(viewModel().uiState.value.biometricsOffered)

        every { biometricAvailability.canUseBiometrics() } returns false
        assertFalse(viewModel().uiState.value.biometricsOffered)
    }
}
