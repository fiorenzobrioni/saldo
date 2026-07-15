package com.callbackdev.saldo.feature.accounts

import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import app.cash.turbine.test
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.navigation.AccountEditorRoute
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Currency

@ExtendWith(MainDispatcherExtension::class)
class AccountEditorViewModelTest {

    private val eur = Currency.getInstance("EUR")
    private val fixedInstant = Instant.parse("2026-07-08T10:15:00Z")

    private val accountRepository = mockk<AccountRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val clock = Clock.fixed(fixedInstant, ZoneId.of("Europe/Rome"))

    private fun viewModel(route: AccountEditorRoute = AccountEditorRoute()) =
        AccountEditorViewModel(route, accountRepository, transactionRepository, clock)

    @Test
    fun `saving a new account persists the parsed form`() = runTest {
        val saved = slot<Account>()
        coEvery { accountRepository.upsert(capture(saved)) } returns 1L
        val viewModel = viewModel()

        viewModel.onNameChanged("  Conto Intesa  ")
        viewModel.onTypeChanged(AccountType.CARD)
        viewModel.onCurrencyChanged(eur)
        viewModel.onInitialBalanceChanged("1234,56")
        viewModel.onColorSelected(AccountVisuals.colors[3])
        viewModel.onIconSelected("savings")
        viewModel.onIncludedInTotalChanged(false)
        viewModel.onIncludedInBudgetChanged(false)
        viewModel.save()

        viewModel.events.test {
            assertEquals(AccountEditorEvent.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        with(saved.captured) {
            assertEquals(0L, id)
            assertEquals("Conto Intesa", name)
            assertEquals(AccountType.CARD, type)
            assertEquals(eur, currency)
            assertEquals(BigDecimal("1234.56"), initialBalance)
            assertEquals(AccountVisuals.colors[3], color)
            assertEquals("savings", icon)
            assertFalse(isIncludedInTotal)
            assertFalse(isIncludedInBudget)
            assertFalse(isArchived)
            assertEquals(fixedInstant, createdAt)
        }
    }

    @Test
    fun `an empty initial balance defaults to zero`() = runTest {
        val saved = slot<Account>()
        coEvery { accountRepository.upsert(capture(saved)) } returns 1L
        val viewModel = viewModel()

        viewModel.onNameChanged("Cash")
        viewModel.save()

        viewModel.events.test {
            assertEquals(AccountEditorEvent.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(BigDecimal.ZERO, saved.captured.initialBalance)
    }

    @Test
    fun `saving without a name surfaces validation and persists nothing`() = runTest {
        val viewModel = viewModel()

        viewModel.save()

        assertTrue(viewModel.uiState.value.showValidation)
        coVerify(exactly = 0) { accountRepository.upsert(any()) }
    }

    @Test
    fun `editing loads the account and locks the currency when it has movements`() = runTest {
        val existing = Account(
            id = 5L,
            name = "Revolut",
            type = AccountType.DIGITAL_WALLET,
            currency = eur,
            initialBalance = BigDecimal("100.00"),
            color = AccountVisuals.colors[5],
            icon = "wallet",
            isIncludedInTotal = false,
            isIncludedInBudget = false,
            sortOrder = 4,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        coEvery { accountRepository.getAccount(5L) } returns existing
        coEvery { transactionRepository.countForAccount(5L) } returns 2

        val viewModel = viewModel(AccountEditorRoute(accountId = 5L))

        with(viewModel.uiState.value) {
            assertFalse(isLoading)
            assertFalse(isNew)
            assertEquals("Revolut", name)
            assertEquals(AccountType.DIGITAL_WALLET, type)
            assertTrue(isCurrencyLocked)
            assertEquals("100", initialBalanceInput)
            assertFalse(isIncludedInTotal)
            assertFalse(isIncludedInBudget)
        }
    }

    @Test
    fun `saving an edited account keeps identity and metadata`() = runTest {
        val existing = Account(
            id = 5L,
            name = "Revolut",
            type = AccountType.DIGITAL_WALLET,
            currency = eur,
            initialBalance = BigDecimal("100.00"),
            sortOrder = 4,
            isArchived = true,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        coEvery { accountRepository.getAccount(5L) } returns existing
        coEvery { transactionRepository.countForAccount(5L) } returns 0
        val saved = slot<Account>()
        coEvery { accountRepository.upsert(capture(saved)) } returns 5L

        val viewModel = viewModel(AccountEditorRoute(accountId = 5L))
        viewModel.onNameChanged("Revolut EUR")
        viewModel.save()

        with(saved.captured) {
            assertEquals(5L, id)
            assertEquals("Revolut EUR", name)
            assertEquals(4, sortOrder)
            assertTrue(isArchived)
            assertEquals(Instant.parse("2026-01-01T00:00:00Z"), createdAt)
        }
    }

    @Test
    fun `type changes drive the icon until the user picks one`() = runTest {
        val viewModel = viewModel()

        viewModel.onTypeChanged(AccountType.CASH)
        assertEquals(AccountVisuals.defaultIconFor(AccountType.CASH), viewModel.uiState.value.icon)

        viewModel.onIconSelected("home")
        viewModel.onTypeChanged(AccountType.CARD)
        assertEquals("home", viewModel.uiState.value.icon)
    }

    @Test
    fun `switching to a zero-decimal currency drops typed decimals`() = runTest {
        val viewModel = viewModel()

        viewModel.onCurrencyChanged(eur)
        viewModel.onInitialBalanceChanged("12,34")
        viewModel.onCurrencyChanged(Currency.getInstance("JPY"))

        assertEquals("12", viewModel.uiState.value.initialBalanceInput)
    }

    @Test
    fun `a new form has no unsaved changes until a field is edited`() = runTest {
        val viewModel = viewModel()

        viewModel.hasUnsavedChanges.test {
            assertFalse(awaitItem())
            viewModel.onNameChanged("Cash")
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reverting an edit back to the initial value clears unsaved changes`() = runTest {
        val viewModel = viewModel()

        viewModel.hasUnsavedChanges.test {
            assertFalse(awaitItem())
            viewModel.onNameChanged("Cash")
            assertTrue(awaitItem())
            viewModel.onNameChanged("")
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a freshly loaded account reports no unsaved changes`() = runTest {
        val existing = Account(
            id = 5L,
            name = "Revolut",
            type = AccountType.DIGITAL_WALLET,
            currency = eur,
            initialBalance = BigDecimal("100.00"),
            color = AccountVisuals.colors[5],
            icon = "wallet",
            sortOrder = 4,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        coEvery { accountRepository.getAccount(5L) } returns existing
        coEvery { transactionRepository.countForAccount(5L) } returns 0

        val viewModel = viewModel(AccountEditorRoute(accountId = 5L))

        viewModel.hasUnsavedChanges.test {
            assertFalse(awaitItem())
            viewModel.onNameChanged("Revolut EUR")
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
