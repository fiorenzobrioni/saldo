package com.callbackdev.saldo

import app.cash.turbine.test
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.util.Currency

@ExtendWith(MainDispatcherExtension::class)
class MainViewModelTest {

    private val userPreferences = mockk<UserPreferencesRepository>(relaxUnitFun = true)
    private val accountRepository = mockk<AccountRepository>()

    private val someAccount = AccountWithBalance(
        account = Account(
            name = "Checking",
            type = AccountType.CHECKING,
            currency = Currency.getInstance("EUR"),
            initialBalance = BigDecimal.ZERO,
            id = 1L,
        ),
        balance = BigDecimal.ZERO,
    )

    private fun viewModel(
        onboardingCompleted: Boolean?,
        accounts: List<AccountWithBalance> = emptyList(),
    ): MainViewModel {
        every { userPreferences.onboardingCompleted } returns flowOf(onboardingCompleted)
        every { accountRepository.observeAccountsWithBalance() } returns flowOf(accounts)
        return MainViewModel(userPreferences, accountRepository)
    }

    private suspend fun MainViewModel.awaitDecision(): LaunchGate {
        var decision = LaunchGate.LOADING
        gate.test {
            var state = awaitItem()
            while (state == LaunchGate.LOADING) state = awaitItem()
            decision = state
            cancelAndIgnoreRemainingEvents()
        }
        return decision
    }

    @Test
    fun `completed flag opens the app directly`() = runTest {
        assertEquals(LaunchGate.APP, viewModel(onboardingCompleted = true).awaitDecision())
    }

    @Test
    fun `missing flag with existing accounts opens the app and writes the flag silently`() = runTest {
        val viewModel = viewModel(onboardingCompleted = null, accounts = listOf(someAccount))

        assertEquals(LaunchGate.APP, viewModel.awaitDecision())
        // The existing install is marked so the DB probe never runs again.
        coVerify(exactly = 1) { userPreferences.setOnboardingCompleted() }
    }

    @Test
    fun `missing flag with an empty database shows the onboarding`() = runTest {
        val viewModel = viewModel(onboardingCompleted = null)

        assertEquals(LaunchGate.ONBOARDING, viewModel.awaitDecision())
        coVerify(exactly = 0) { userPreferences.setOnboardingCompleted() }
    }

    @Test
    fun `a preferences read failure opens the app rather than blocking the user`() = runTest {
        every { userPreferences.onboardingCompleted } returns flow { error("datastore corrupted") }
        every { accountRepository.observeAccountsWithBalance() } returns flowOf(emptyList())
        val viewModel = MainViewModel(userPreferences, accountRepository)

        assertEquals(LaunchGate.APP, viewModel.awaitDecision())
    }

    @Test
    fun `an account read failure opens the app rather than risking onboarding an existing user`() = runTest {
        every { userPreferences.onboardingCompleted } returns flowOf(null)
        every { accountRepository.observeAccountsWithBalance() } returns flow { error("db error") }
        val viewModel = MainViewModel(userPreferences, accountRepository)

        assertEquals(LaunchGate.APP, viewModel.awaitDecision())
    }

    @Test
    fun `completing the onboarding flips the gate and persists the flag`() = runTest {
        val viewModel = viewModel(onboardingCompleted = null)
        assertEquals(LaunchGate.ONBOARDING, viewModel.awaitDecision())

        viewModel.completeOnboarding()

        assertEquals(LaunchGate.APP, viewModel.awaitDecision())
        coVerify(exactly = 1) { userPreferences.setOnboardingCompleted() }
    }
}
