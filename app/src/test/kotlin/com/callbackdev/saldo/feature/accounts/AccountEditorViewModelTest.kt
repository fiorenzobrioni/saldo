package com.callbackdev.saldo.feature.accounts

import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import app.cash.turbine.test
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.navigation.AccountEditorRoute
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency

@ExtendWith(MainDispatcherExtension::class)
class AccountEditorViewModelTest {

    private val eur = Currency.getInstance("EUR")
    private val fixedInstant = Instant.parse("2026-07-08T10:15:00Z")

    private val accountRepository = mockk<AccountRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val userPreferences = mockk<UserPreferencesRepository>()
    private val clock = Clock.fixed(fixedInstant, ZoneId.of("Europe/Rome"))

    private fun viewModel(
        route: AccountEditorRoute = AccountEditorRoute(),
        currencyOverride: Currency? = null,
        accounts: List<AccountWithBalance> = emptyList(),
    ): AccountEditorViewModel {
        every { accountRepository.observeAccountsWithBalance() } returns flowOf(accounts)
        every { userPreferences.primaryCurrencyOverride } returns flowOf(currencyOverride)
        every { userPreferences.currencyConversionEnabled } returns flowOf(true)
        coEvery { accountRepository.nextSortOrder(any()) } returns 0
        return AccountEditorViewModel(
            route,
            accountRepository,
            transactionRepository,
            userPreferences,
            clock,
        )
    }

    private fun accountWithBalance(id: Long, currency: Currency): AccountWithBalance =
        AccountWithBalance(
            account = Account(
                id = id,
                name = "Account $id",
                type = AccountType.CHECKING,
                currency = currency,
                initialBalance = BigDecimal.ZERO,
            ),
            balance = BigDecimal.ZERO,
        )

    @Test
    fun `saving a new account persists the parsed form`() = runTest {
        val saved = slot<Account>()
        coEvery { accountRepository.upsert(capture(saved)) } returns 1L
        val viewModel = viewModel()

        viewModel.onNameChanged("  Conto Intesa  ")
        viewModel.onTypeChanged(AccountType.PREPAID_CARD)
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
            assertEquals(AccountType.PREPAID_CARD, type)
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
    fun `a credit card saves with zero balance, its config and a seeded watermark`() = runTest {
        val saved = slot<Account>()
        coEvery { accountRepository.upsert(capture(saved)) } returns 1L
        val viewModel = viewModel()

        viewModel.onNameChanged("Carta di credito")
        viewModel.onTypeChanged(AccountType.CREDIT_CARD)
        // Typed before switching type, or by any stale state: must be ignored.
        viewModel.onInitialBalanceChanged("999")
        viewModel.onStatementClosingDayChanged(20)
        viewModel.onPaymentDueDayChanged(5)
        viewModel.onLinkedAccountChanged(2L)
        viewModel.onCreditLimitChanged("1500")
        viewModel.onStatementAutoPostChanged(true)
        viewModel.save()

        viewModel.events.test {
            assertEquals(AccountEditorEvent.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        with(saved.captured) {
            // No initial debt: a credit card always starts from zero.
            assertEquals(BigDecimal.ZERO, initialBalance)
            val config = creditCard!!
            assertEquals(20, config.statementClosingDay)
            assertEquals(5, config.paymentDueDay)
            assertEquals(2L, config.linkedAccountId)
            assertEquals(BigDecimal("1500"), config.creditLimit)
            assertTrue(config.autoPost)
            // Watermark seeded at the last closing before today (clock is
            // 2026-07-08, closing day 20): history is never back-charged.
            assertEquals(LocalDate.of(2026, 6, 20), config.lastSettledClosing)
        }
    }

    @Test
    fun `a non credit card saves without a credit card config`() = runTest {
        val saved = slot<Account>()
        coEvery { accountRepository.upsert(capture(saved)) } returns 1L
        val viewModel = viewModel()

        viewModel.onNameChanged("Postepay")
        viewModel.onTypeChanged(AccountType.PREPAID_CARD)
        viewModel.save()

        viewModel.events.test {
            assertEquals(AccountEditorEvent.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(null, saved.captured.creditCard)
    }

    @Test
    fun `selecting savings presets budget exclusion until the user decides`() = runTest {
        val viewModel = viewModel()

        viewModel.onTypeChanged(AccountType.SAVINGS)
        assertFalse(viewModel.uiState.value.isIncludedInBudget)

        // Switching away restores the preset for ordinary accounts.
        viewModel.onTypeChanged(AccountType.CHECKING)
        assertTrue(viewModel.uiState.value.isIncludedInBudget)

        // An explicit user choice survives any later type change.
        viewModel.onIncludedInBudgetChanged(false)
        viewModel.onTypeChanged(AccountType.SAVINGS)
        viewModel.onTypeChanged(AccountType.CHECKING)
        assertFalse(viewModel.uiState.value.isIncludedInBudget)
    }

    @Test
    fun `selecting loan presets total and budget exclusion until the user decides`() = runTest {
        val viewModel = viewModel()

        viewModel.onTypeChanged(AccountType.LOAN)
        assertFalse(viewModel.uiState.value.isIncludedInTotal)
        assertFalse(viewModel.uiState.value.isIncludedInBudget)

        // Switching away restores the presets for ordinary accounts.
        viewModel.onTypeChanged(AccountType.CHECKING)
        assertTrue(viewModel.uiState.value.isIncludedInTotal)
        assertTrue(viewModel.uiState.value.isIncludedInBudget)

        // An explicit user choice survives any later type change.
        viewModel.onIncludedInTotalChanged(false)
        viewModel.onTypeChanged(AccountType.LOAN)
        viewModel.onTypeChanged(AccountType.CHECKING)
        assertFalse(viewModel.uiState.value.isIncludedInTotal)
    }

    @Test
    fun `turning the total back on for a loan survives re-selecting the type`() = runTest {
        val viewModel = viewModel()

        viewModel.onTypeChanged(AccountType.LOAN)
        // The patrimonial reading is the user's explicit choice: the preset
        // must never claw it back.
        viewModel.onIncludedInTotalChanged(true)
        viewModel.onTypeChanged(AccountType.CHECKING)
        viewModel.onTypeChanged(AccountType.LOAN)

        assertTrue(viewModel.uiState.value.isIncludedInTotal)
    }

    @Test
    fun `editing a loan keeps the persisted inclusion flags`() = runTest {
        val existing = Account(
            id = 7L,
            name = "Mutuo",
            type = AccountType.LOAN,
            currency = eur,
            initialBalance = BigDecimal("-120000.00"),
            isIncludedInTotal = true,
            isIncludedInBudget = true,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        coEvery { accountRepository.getAccount(7L) } returns existing
        coEvery { transactionRepository.countForAccount(7L) } returns 3

        val viewModel = viewModel(AccountEditorRoute(accountId = 7L))

        // The persisted (user's) choices are never overwritten by the preset.
        assertTrue(viewModel.uiState.value.isIncludedInTotal)
        assertTrue(viewModel.uiState.value.isIncludedInBudget)
    }

    @Test
    fun `saving a loan with a zero, positive or missing balance surfaces validation`() = runTest {
        val viewModel = viewModel()
        viewModel.onNameChanged("Prestito auto")
        viewModel.onTypeChanged(AccountType.LOAN)

        listOf("", "0", "500").forEach { input ->
            viewModel.onInitialBalanceChanged(input)
            viewModel.save()
            assertTrue(viewModel.uiState.value.showValidation, "input \"$input\" must be rejected")
        }
        coVerify(exactly = 0) { accountRepository.upsert(any()) }
    }

    @Test
    fun `a loan saves its negative remaining debt and the exclusion presets`() = runTest {
        val saved = slot<Account>()
        coEvery { accountRepository.upsert(capture(saved)) } returns 1L
        val viewModel = viewModel()

        viewModel.onNameChanged("Prestito auto")
        viewModel.onTypeChanged(AccountType.LOAN)
        viewModel.onInitialBalanceChanged("-8400,50")
        viewModel.save()

        viewModel.events.test {
            assertEquals(AccountEditorEvent.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        with(saved.captured) {
            assertEquals(AccountType.LOAN, type)
            assertEquals(BigDecimal("-8400.50"), initialBalance)
            assertFalse(isIncludedInTotal)
            assertFalse(isIncludedInBudget)
            assertEquals(null, creditCard)
        }
    }

    @Test
    fun `the loan balance rule does not touch other types`() = runTest {
        val saved = slot<Account>()
        coEvery { accountRepository.upsert(capture(saved)) } returns 1L
        val viewModel = viewModel()

        // A positive initial balance stays legal for an ordinary account.
        viewModel.onNameChanged("Conto")
        viewModel.onInitialBalanceChanged("500")
        viewModel.save()

        viewModel.events.test {
            assertEquals(AccountEditorEvent.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(BigDecimal("500"), saved.captured.initialBalance)
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
        viewModel.onTypeChanged(AccountType.PREPAID_CARD)
        assertEquals("home", viewModel.uiState.value.icon)
    }

    @Test
    fun `type changes drive the color until the user picks one`() = runTest {
        val viewModel = viewModel()

        viewModel.onTypeChanged(AccountType.SAVINGS)
        assertEquals(AccountVisuals.defaultColorFor(AccountType.SAVINGS), viewModel.uiState.value.color)

        viewModel.onColorSelected(AccountVisuals.colors[5])
        viewModel.onTypeChanged(AccountType.CREDIT_CARD)
        assertEquals(AccountVisuals.colors[5], viewModel.uiState.value.color)
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

    @Test
    fun `preselecting savings seeds the type, excludes budget, and opens clean`() = runTest {
        val viewModel = viewModel(AccountEditorRoute(initialTypeName = "SAVINGS"))

        val state = viewModel.uiState.value
        assertEquals(AccountType.SAVINGS, state.type)
        assertFalse(state.isIncludedInBudget)
        assertEquals(AccountVisuals.defaultIconFor(AccountType.SAVINGS), state.icon)

        // The seeded state is the baseline: the shortcut must not open dirty.
        viewModel.hasUnsavedChanges.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a preselected savings account saves excluded from the budget`() = runTest {
        val saved = slot<Account>()
        coEvery { accountRepository.upsert(capture(saved)) } returns 1L
        val viewModel = viewModel(AccountEditorRoute(initialTypeName = "SAVINGS"))

        viewModel.onNameChanged("Risparmi")
        viewModel.save()

        viewModel.events.test {
            assertEquals(AccountEditorEvent.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        with(saved.captured) {
            assertEquals(AccountType.SAVINGS, type)
            assertFalse(isIncludedInBudget)
        }
    }

    @Test
    fun `an unknown preselected type falls back to checking`() = runTest {
        val viewModel = viewModel(AccountEditorRoute(initialTypeName = "NOT_A_TYPE"))

        assertEquals(AccountType.CHECKING, viewModel.uiState.value.type)
    }

    @Test
    fun `a new account opens in the explicit primary currency, not the locale one`() = runTest {
        val chf = Currency.getInstance("CHF")

        val viewModel = viewModel(currencyOverride = chf)

        assertEquals(chf, viewModel.uiState.value.currency)
    }

    @Test
    fun `without an explicit choice a new account follows the accounts' plurality`() = runTest {
        val usd = Currency.getInstance("USD")

        val viewModel = viewModel(
            accounts = listOf(
                accountWithBalance(1L, usd),
                accountWithBalance(2L, usd),
                accountWithBalance(3L, eur),
            ),
        )

        assertEquals(usd, viewModel.uiState.value.currency)
    }

    @Test
    fun `the preselected currency is the baseline, so the form opens clean`() = runTest {
        val viewModel = viewModel(currencyOverride = Currency.getInstance("CHF"))

        viewModel.hasUnsavedChanges.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `editing keeps the account's own currency, whatever the primary one is`() = runTest {
        val usd = Currency.getInstance("USD")
        val existing = Account(
            id = 5L,
            name = "Revolut USD",
            type = AccountType.DIGITAL_WALLET,
            currency = usd,
            initialBalance = BigDecimal("100.00"),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        coEvery { accountRepository.getAccount(5L) } returns existing
        coEvery { transactionRepository.countForAccount(5L) } returns 0

        val viewModel = viewModel(
            route = AccountEditorRoute(accountId = 5L),
            currencyOverride = Currency.getInstance("CHF"),
        )

        assertEquals(usd, viewModel.uiState.value.currency)
    }
}
