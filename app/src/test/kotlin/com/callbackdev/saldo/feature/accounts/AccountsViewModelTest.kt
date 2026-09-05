package com.callbackdev.saldo.feature.accounts

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.creditcard.BillingCycle
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.rates.ConversionState
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.usecase.DueStatement
import com.callbackdev.saldo.core.domain.usecase.ObserveConversionStateUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveDueStatementsUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveLoanProgressUseCase
import com.callbackdev.saldo.core.domain.usecase.SettleCreditCardStatementUseCase
import com.callbackdev.saldo.core.domain.usecase.StatementSettlement
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency

@ExtendWith(MainDispatcherExtension::class)
class AccountsViewModelTest {

    private val eur = Currency.getInstance("EUR")

    private val accountRepository = mockk<AccountRepository>(relaxUnitFun = true)
    private val observeDueStatements = mockk<ObserveDueStatementsUseCase>()
    private val observeLoanProgress = mockk<ObserveLoanProgressUseCase>()
    private val settleStatement = mockk<SettleCreditCardStatementUseCase>()
    private val userPreferences = mockk<UserPreferencesRepository>()
    private val observeConversionState = mockk<ObserveConversionStateUseCase>()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-21T10:00:00Z"), ZoneId.of("Europe/Rome"))

    private fun account(
        id: Long = 1L,
        archived: Boolean = false,
    ) = Account(
        id = id,
        name = "Checking",
        type = AccountType.CHECKING,
        currency = eur,
        initialBalance = BigDecimal("100.00"),
        isArchived = archived,
    )

    private fun viewModel(
        accounts: List<AccountWithBalance> = emptyList(),
        dueStatements: List<DueStatement> = emptyList(),
    ): AccountsViewModel {
        every { accountRepository.observeAccountsWithBalance() } returns flowOf(accounts)
        every { accountRepository.observeAccountsWithBalanceAsOfToday(any()) } returns flowOf(accounts)
        every { observeDueStatements() } returns flowOf(dueStatements)
        every { observeLoanProgress() } returns flowOf(emptyMap())
        every { userPreferences.primaryCurrencyOverride } returns flowOf(null)
        every { observeConversionState() } returns flowOf(ConversionState.INACTIVE)
        return AccountsViewModel(
            accountRepository,
            observeDueStatements,
            observeLoanProgress,
            settleStatement,
            userPreferences,
            observeConversionState,
            clock,
        )
    }

    private suspend fun ReceiveTurbine<AccountsUiState>.awaitLoaded(): AccountsUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }

    private fun statement(closing: LocalDate, amount: String) = DueStatement(
        accountId = 1L,
        cardName = "Credit card",
        amount = BigDecimal(amount),
        currency = eur,
        cycle = BillingCycle(
            start = closing.minusMonths(1).plusDays(1),
            closing = closing,
            paymentDue = closing.plusMonths(1).withDayOfMonth(5),
        ),
        autoPosted = false,
    )

    @Test
    fun `ui state splits active and archived accounts`() = runTest {
        val active = AccountWithBalance(account(id = 1L), BigDecimal("74.50"))
        val archived = AccountWithBalance(account(id = 2L, archived = true), BigDecimal.ZERO)
        val viewModel = viewModel(accounts = listOf(active, archived))

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(listOf(active), state.activeGroups.flatMap { it.accounts })
            assertEquals(listOf(archived), state.archived)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the due statement shown per card is the oldest cycle`() = runTest {
        // Oldest first, as the observe use case emits them: the CTA must show
        // the oldest, because settlement always pays the oldest cycle first.
        val older = statement(LocalDate.of(2026, 5, 20), "90.00")
        val newer = statement(LocalDate.of(2026, 6, 20), "40.00")
        val viewModel = viewModel(
            accounts = listOf(AccountWithBalance(account(id = 1L), BigDecimal("-130.00"))),
            dueStatements = listOf(older, newer),
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(older, state.dueStatement(1L))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `settling a statement from the list reports the amount transferred`() = runTest {
        val card = account(id = 1L)
        val cycle = statement(LocalDate.of(2026, 6, 20), "40.00").cycle
        coEvery { settleStatement.invoke(1L, any()) } returns
            StatementSettlement.Settled(1L, card.name, cycle, BigDecimal("40.00"))
        coEvery { accountRepository.getAccount(1L) } returns card
        val viewModel = viewModel(accounts = listOf(AccountWithBalance(card, BigDecimal("-40.00"))))

        viewModel.settleStatement(1L)

        viewModel.events.test {
            assertEquals(AccountsEvent.StatementSettled(BigDecimal("40.00"), eur), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
