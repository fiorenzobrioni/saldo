package com.callbackdev.saldo.core.common.applock

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockManagerTest {

    private val lockEnabledFlow = MutableStateFlow(true)
    private val timeoutFlow = MutableStateFlow(AutoLockTimeout.IMMEDIATELY)
    private val storedPinFlow = MutableStateFlow<StoredPin?>(STORED_PIN)
    private val failedAttemptsFlow = MutableStateFlow(0)
    private val lockoutFlow = MutableStateFlow<Long?>(null)

    private val repository = mockk<AppLockRepository>(relaxUnitFun = true) {
        every { lockEnabled } returns lockEnabledFlow
        every { autoLockTimeout } returns timeoutFlow
        every { storedPin } returns storedPinFlow
        every { failedAttempts } returns failedAttemptsFlow
        every { lockoutUntilEpochMilli } returns lockoutFlow
        coEvery { recordFailedAttempt(any(), any()) } coAnswers {
            failedAttemptsFlow.value = firstArg()
            lockoutFlow.value = secondArg()
        }
        coEvery { clearFailedAttempts() } coAnswers {
            failedAttemptsFlow.value = 0
            lockoutFlow.value = null
        }
    }

    /** Real hashing would burn 150k PBKDF2 rounds per call; the match is stubbed. */
    private val pinHasher = mockk<PinHasher> {
        every { matches(any(), any()) } answers { firstArg<String>() == CORRECT_PIN }
    }

    private var nowMillis = 1_000_000L
    private val clock = mockk<Clock> {
        every { millis() } answers { nowMillis }
    }

    private fun TestScope.manager(): AppLockManager {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return AppLockManager(
            repository = repository,
            pinHasher = pinHasher,
            clock = clock,
            scope = CoroutineScope(dispatcher),
            defaultDispatcher = dispatcher,
        )
    }

    @Test
    fun `starts locked when the lock is enabled, unlocked when it is not`() = runTest {
        assertEquals(AppLockState.LOCKED, manager().state.value)

        lockEnabledFlow.value = false
        assertEquals(AppLockState.UNLOCKED, manager().state.value)
    }

    @Test
    fun `the correct pin unlocks and resets the attempt counters`() = runTest {
        failedAttemptsFlow.value = 3
        val manager = manager()

        val result = manager.submitPin(CORRECT_PIN)

        assertEquals(PinResult.Unlocked, result)
        assertEquals(AppLockState.UNLOCKED, manager.state.value)
        assertEquals(0, failedAttemptsFlow.value)
    }

    @Test
    fun `a wrong pin is rejected without a cooldown before the fifth failure`() = runTest {
        val manager = manager()

        repeat(4) {
            val result = manager.submitPin(WRONG_PIN)
            assertEquals(PinResult.Rejected(0L), result)
        }

        assertEquals(AppLockState.LOCKED, manager.state.value)
        assertEquals(4, failedAttemptsFlow.value)
    }

    @Test
    fun `the fifth failure starts the cooldown, later ones double it up to the cap`() = runTest {
        val manager = manager()

        repeat(4) { manager.submitPin(WRONG_PIN) }
        assertEquals(PinResult.Rejected(30_000L), submitPastCooldown(manager)) // 5th
        assertEquals(PinResult.Rejected(60_000L), submitPastCooldown(manager)) // 6th
        assertEquals(PinResult.Rejected(120_000L), submitPastCooldown(manager)) // 7th
        assertEquals(PinResult.Rejected(240_000L), submitPastCooldown(manager)) // 8th
        assertEquals(PinResult.Rejected(300_000L), submitPastCooldown(manager)) // 9th, capped
        assertEquals(PinResult.Rejected(300_000L), submitPastCooldown(manager)) // still capped
    }

    @Test
    fun `during the cooldown even the correct pin is refused`() = runTest {
        val manager = manager()
        repeat(5) { manager.submitPin(WRONG_PIN) }

        val result = manager.submitPin(CORRECT_PIN)

        assertTrue(result is PinResult.Rejected && result.lockoutRemainingMillis > 0)
        assertEquals(AppLockState.LOCKED, manager.state.value)
    }

    @Test
    fun `a persisted cooldown deadline binds a freshly created manager`() = runTest {
        // Simulates a process death mid-cooldown: the deadline was persisted,
        // the new process must still honour it.
        failedAttemptsFlow.value = 5
        lockoutFlow.value = nowMillis + 20_000L
        val manager = manager()

        assertEquals(20_000L, manager.lockoutRemainingMillis())
        assertTrue(manager.submitPin(CORRECT_PIN) is PinResult.Rejected)

        nowMillis += 20_000L
        assertEquals(0L, manager.lockoutRemainingMillis())
        assertEquals(PinResult.Unlocked, manager.submitPin(CORRECT_PIN))
    }

    @Test
    fun `biometric success unlocks and resets the attempt counters`() = runTest {
        failedAttemptsFlow.value = 6
        lockoutFlow.value = nowMillis + 60_000L
        val manager = manager()

        manager.unlockWithBiometrics()

        assertEquals(AppLockState.UNLOCKED, manager.state.value)
        assertEquals(0, failedAttemptsFlow.value)
    }

    @Test
    fun `with Immediately any background re-locks on return`() = runTest {
        val manager = manager()
        manager.submitPin(CORRECT_PIN)

        manager.onAppBackgrounded()
        manager.onAppForegrounded()

        assertEquals(AppLockState.LOCKED, manager.state.value)
    }

    @Test
    fun `with a timed option a short background does not re-lock, a long one does`() = runTest {
        timeoutFlow.value = AutoLockTimeout.ONE_MINUTE
        val manager = manager()
        manager.submitPin(CORRECT_PIN)

        manager.onAppBackgrounded()
        nowMillis += 30_000L
        manager.onAppForegrounded()
        assertEquals(AppLockState.UNLOCKED, manager.state.value)

        manager.onAppBackgrounded()
        nowMillis += 60_000L
        manager.onAppForegrounded()
        assertEquals(AppLockState.LOCKED, manager.state.value)
    }

    @Test
    fun `disabling the lock from settings opens the gate reactively`() = runTest {
        val manager = manager()
        assertEquals(AppLockState.LOCKED, manager.state.value)

        lockEnabledFlow.value = false

        assertEquals(AppLockState.UNLOCKED, manager.state.value)
    }

    @Test
    fun `enabling the lock mid-session does not lock the user out of it`() = runTest {
        lockEnabledFlow.value = false
        val manager = manager()
        assertEquals(AppLockState.UNLOCKED, manager.state.value)

        // The user just set a PIN from Settings.
        lockEnabledFlow.value = true

        assertEquals(AppLockState.UNLOCKED, manager.state.value)
    }

    @Test
    fun `a locked state with no verifiable pin opens instead of bricking`() = runTest {
        val manager = manager()
        // The corruption handler reset the store while the gate was up.
        storedPinFlow.value = null

        val result = manager.submitPin("000000")

        assertEquals(PinResult.Unlocked, result)
        assertEquals(AppLockState.UNLOCKED, manager.state.value)
    }

    /** Skips past any running cooldown, then submits a wrong PIN. */
    private suspend fun submitPastCooldown(manager: AppLockManager): PinResult {
        nowMillis += manager.lockoutRemainingMillis()
        return manager.submitPin(WRONG_PIN)
    }

    private companion object {
        const val CORRECT_PIN = "123456"
        const val WRONG_PIN = "000000"
        val STORED_PIN = StoredPin(saltBase64 = "salt", hashBase64 = "hash", iterations = 1)
    }
}
