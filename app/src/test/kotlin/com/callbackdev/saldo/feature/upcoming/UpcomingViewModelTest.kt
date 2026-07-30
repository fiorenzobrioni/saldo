package com.callbackdev.saldo.feature.upcoming

import app.cash.turbine.test
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.model.UpcomingOrigin
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.rates.ConversionState
import com.callbackdev.saldo.core.domain.usecase.ObserveConversionStateUseCase
import com.callbackdev.saldo.core.domain.usecase.ObserveUpcomingMovementsUseCase
import com.callbackdev.saldo.navigation.UpcomingRoute
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
import java.time.ZoneOffset
import java.util.Currency

@ExtendWith(MainDispatcherExtension::class)
class UpcomingViewModelTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val usd: Currency = Currency.getInstance("USD")
    private val zone: ZoneId = ZoneId.of("Europe/Rome")

    // Thursday 9 July 2026, so "tomorrow" is the 10th.
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-09T09:00:00Z"), zone)

    private val transactionRepository = mockk<TransactionRepository>(relaxed = true)
    private val recurringRuleRepository = mockk<RecurringRuleRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val userPreferences = mockk<UserPreferencesRepository>()
    private val observeConversionState = mockk<ObserveConversionStateUseCase>()

    private fun account(id: Long, currency: Currency = eur) = AccountWithBalance(
        account = Account(
            id = id,
            name = "account-$id",
            type = AccountType.CASH,
            currency = currency,
            initialBalance = BigDecimal.ZERO,
        ),
        balance = BigDecimal.ZERO,
    )

    private fun movement(
        id: Long,
        date: LocalDate,
        amount: String = "-10.00",
        type: TransactionType = TransactionType.EXPENSE,
        currency: Currency = eur,
        isPending: Boolean = false,
        ruleId: Long? = null,
        occurrence: LocalDate? = null,
        description: String? = "movement-$id",
    ) = Transaction(
        id = id,
        type = type,
        amount = BigDecimal(amount),
        currency = currency,
        accountId = 1L,
        timestamp = date.atTime(12, 0).atZone(zone).toInstant(),
        zoneOffset = ZoneOffset.ofHours(2),
        description = description,
        recurringRuleId = ruleId,
        isPending = isPending,
        recurringOccurrenceDate = occurrence,
    )

    private fun viewModel(
        future: List<Transaction> = emptyList(),
        pending: List<Transaction> = emptyList(),
        accounts: List<AccountWithBalance> = listOf(account(1L)),
        route: UpcomingRoute = UpcomingRoute(),
    ): UpcomingViewModel {
        every { transactionRepository.observeTransactionsFrom(any()) } returns flowOf(future)
        every { transactionRepository.observePendingTransactions() } returns flowOf(pending)
        every { accountRepository.observeAccountsWithBalance() } returns flowOf(accounts)
        every { categoryRepository.observeCategories() } returns flowOf(emptyList<Category>())
        every { recurringRuleRepository.observeRules() } returns flowOf(emptyList<RecurringRule>())
        every { userPreferences.primaryCurrencyOverride } returns flowOf(null)
        every { observeConversionState() } returns flowOf(ConversionState.INACTIVE)
        return UpcomingViewModel(
            route,
            ObserveUpcomingMovementsUseCase(
                transactionRepository,
                accountRepository,
                userPreferences,
                observeConversionState,
                clock,
            ),
            transactionRepository,
            accountRepository,
            categoryRepository,
            recurringRuleRepository,
            clock,
        )
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<UpcomingUiState>.awaitLoaded(): UpcomingUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }

    @Test
    fun `future movements and pending occurrences share one list ordered by date`() = runTest {
        val viewModel = viewModel(
            future = listOf(
                movement(1L, LocalDate.of(2026, 7, 20)),
                movement(2L, LocalDate.of(2026, 7, 12)),
            ),
            pending = listOf(
                movement(3L, LocalDate.of(2026, 7, 15), isPending = true, ruleId = 4L),
            ),
        )

        viewModel.uiState.test {
            val state = awaitLoaded()

            assertEquals(listOf(2L, 3L, 1L), state.items.map { it.id })
            assertEquals(
                listOf(
                    LocalDate.of(2026, 7, 12),
                    LocalDate.of(2026, 7, 15),
                    LocalDate.of(2026, 7, 20),
                ),
                state.items.map { it.date },
            )
        }
    }

    @Test
    fun `origin tells manual, rule-generated and to-confirm movements apart`() = runTest {
        val viewModel = viewModel(
            future = listOf(
                movement(1L, LocalDate.of(2026, 7, 12)),
                movement(2L, LocalDate.of(2026, 7, 13), ruleId = 9L),
            ),
            pending = listOf(movement(3L, LocalDate.of(2026, 7, 14), isPending = true, ruleId = 9L)),
        )

        viewModel.uiState.test {
            val state = awaitLoaded()

            assertEquals(
                listOf(UpcomingOrigin.MANUAL, UpcomingOrigin.RECURRING, UpcomingOrigin.PENDING),
                state.items.map { it.movement.origin },
            )
        }
    }

    @Test
    fun `a pending movement is dated on its occurrence, not on the row's own date`() = runTest {
        // Generated on the 6th for the occurrence of the 8th: what is due is
        // the occurrence, which is what the list must order and show.
        val viewModel = viewModel(
            pending = listOf(
                movement(
                    1L,
                    LocalDate.of(2026, 7, 6),
                    isPending = true,
                    ruleId = 4L,
                    occurrence = LocalDate.of(2026, 7, 8),
                ),
            ),
        )

        viewModel.uiState.test {
            assertEquals(LocalDate.of(2026, 7, 8), awaitLoaded().items.single().date)
        }
    }

    @Test
    fun `the totals keep expenses and incomes apart and leave transfers out`() = runTest {
        val viewModel = viewModel(
            future = listOf(
                movement(1L, LocalDate.of(2026, 7, 12), amount = "-30.00"),
                movement(2L, LocalDate.of(2026, 7, 13), amount = "-20.00"),
                movement(
                    3L,
                    LocalDate.of(2026, 7, 14),
                    amount = "500.00",
                    type = TransactionType.INCOME,
                ),
                movement(
                    4L,
                    LocalDate.of(2026, 7, 15),
                    amount = "-999.00",
                    type = TransactionType.TRANSFER,
                ),
            ),
        )

        viewModel.uiState.test {
            val ledger = awaitLoaded().ledger

            assertEquals(BigDecimal("50.00"), ledger.outgoing)
            assertEquals(BigDecimal("500.00"), ledger.incoming)
        }
    }

    @Test
    fun `movements in another currency stay in the list but out of the totals`() = runTest {
        val viewModel = viewModel(
            future = listOf(
                movement(1L, LocalDate.of(2026, 7, 12), amount = "-30.00"),
                movement(2L, LocalDate.of(2026, 7, 13), amount = "-70.00", currency = usd),
            ),
        )

        viewModel.uiState.test {
            val state = awaitLoaded()

            assertEquals(2, state.items.size)
            assertEquals(BigDecimal("30.00"), state.ledger.outgoing)
            assertTrue(state.ledger.hasOtherCurrencies)
        }
    }

    @Test
    fun `an empty list is empty, not filtered-empty`() = runTest {
        viewModel().uiState.test {
            val state = awaitLoaded()

            assertTrue(state.isEmpty)
            assertFalse(state.isFilteredEmpty)
            assertFalse(state.showFilters)
        }
    }

    @Test
    fun `the route opens the screen on the confirmation queue`() = runTest {
        val viewModel = viewModel(
            future = listOf(movement(1L, LocalDate.of(2026, 7, 12))),
            pending = listOf(movement(2L, LocalDate.of(2026, 7, 13), isPending = true, ruleId = 4L)),
            route = UpcomingRoute(pendingOnly = true),
        )

        viewModel.uiState.test {
            val state = awaitLoaded()

            assertEquals(UpcomingFilter.PENDING, state.filter)
            assertEquals(listOf(2L), state.items.map { it.id })
            assertTrue(state.showFilters)
        }
    }

    @Test
    fun `clearing the queue while filtered on it reads as filtered-empty`() = runTest {
        // Nothing pending, but the whole list is not empty: the screen must say
        // "all confirmed" rather than "nothing ahead".
        val viewModel = viewModel(future = listOf(movement(1L, LocalDate.of(2026, 7, 12))))
        viewModel.onFilterSelected(UpcomingFilter.PENDING)

        viewModel.uiState.test {
            val state = awaitLoaded()

            assertTrue(state.isFilteredEmpty)
            assertFalse(state.isEmpty)
        }
    }

    // --- Confirmation, absorbed from the former pending-only screen ---

    @Test
    fun `confirm applies the sign and clears the pending flag`() = runTest {
        val saved = slot<Transaction>()
        coEvery { transactionRepository.upsert(capture(saved)) } returns 7L
        val target = movement(7L, LocalDate.of(2026, 7, 8), amount = "0.00", isPending = true)
        val viewModel = viewModel(pending = listOf(target))

        viewModel.confirm(target, BigDecimal("15.00"))

        assertEquals(BigDecimal("-15.00"), saved.captured.amount)
        assertFalse(saved.captured.isPending)
    }

    private fun pendingTransfer(destinationCurrency: Currency) = Transaction(
        id = 8L,
        type = TransactionType.TRANSFER,
        amount = BigDecimal("-100.00"),
        currency = eur,
        accountId = 1L,
        timestamp = Instant.parse("2026-07-07T12:00:00Z"),
        zoneOffset = ZoneOffset.ofHours(2),
        transferAccountId = 5L,
        transferAmount = null,
        transferCurrency = destinationCurrency,
        recurringRuleId = 1L,
        isPending = true,
    )

    @Test
    fun `confirming a cross-currency transfer sets the received amount and keeps the source fixed`() = runTest {
        val saved = slot<Transaction>()
        coEvery { transactionRepository.upsert(capture(saved)) } returns 8L
        val target = pendingTransfer(destinationCurrency = usd)
        val viewModel = viewModel(pending = listOf(target))

        viewModel.confirm(target, BigDecimal("108.50"))

        with(saved.captured) {
            // The source leg entered at generation stays untouched.
            assertEquals(BigDecimal("-100.00"), amount)
            assertEquals(0, transferAmount!!.compareTo(BigDecimal("108.50")))
            assertFalse(isPending)
        }
    }

    @Test
    fun `confirming a same-currency transfer moves both legs by the amount`() = runTest {
        val saved = slot<Transaction>()
        coEvery { transactionRepository.upsert(capture(saved)) } returns 8L
        val target = pendingTransfer(destinationCurrency = eur)
            .copy(transferAmount = BigDecimal("100.00"))
        val viewModel = viewModel(pending = listOf(target))

        viewModel.confirm(target, BigDecimal("120.00"))

        with(saved.captured) {
            assertEquals(BigDecimal("-120.00"), amount)
            assertEquals(0, transferAmount!!.compareTo(BigDecimal("120.00")))
            assertFalse(isPending)
        }
    }

    @Test
    fun `skip deletes the pending movement`() = runTest {
        val deleted = slot<Transaction>()
        coEvery { transactionRepository.delete(capture(deleted)) } returns Unit
        val target = movement(7L, LocalDate.of(2026, 7, 8), amount = "-12.99", isPending = true)
        val viewModel = viewModel(pending = listOf(target))

        viewModel.skip(target)

        coVerify { transactionRepository.delete(any()) }
        assertEquals(7L, deleted.captured.id)
    }
}
