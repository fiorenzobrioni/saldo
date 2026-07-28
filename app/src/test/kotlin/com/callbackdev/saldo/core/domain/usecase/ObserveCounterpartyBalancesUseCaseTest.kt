package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.CounterpartyTotal
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

class ObserveCounterpartyBalancesUseCaseTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val usd: Currency = Currency.getInstance("USD")

    private val transactionRepository = mockk<TransactionRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val userPreferences = mockk<UserPreferencesRepository>()

    private val checking = Account(
        id = 1L,
        name = "Conto",
        type = AccountType.CHECKING,
        currency = eur,
        initialBalance = BigDecimal.ZERO,
    )

    private fun useCase(
        totals: List<CounterpartyTotal>,
        override: Currency? = null,
    ): ObserveCounterpartyBalancesUseCase {
        every { transactionRepository.observeCounterpartyTotals() } returns flowOf(totals)
        every { accountRepository.observeAccountsWithBalance() } returns
            flowOf(listOf(AccountWithBalance(checking, BigDecimal.ZERO)))
        every { userPreferences.primaryCurrencyOverride } returns flowOf(override)
        return ObserveCounterpartyBalancesUseCase(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            userPreferences = userPreferences,
        )
    }

    private fun total(
        name: String,
        amount: String,
        currency: Currency = eur,
        count: Int = 1,
        day: LocalDate = LocalDate.of(2026, 7, 1),
    ) = CounterpartyTotal(
        name = name,
        currency = currency,
        total = BigDecimal(amount),
        count = count,
        lastActivity = day,
    )

    @Test
    fun `a loan out is a credit and a repayment nets against it`() = runTest {
        // Lent 100, got 30 back: 70 still out.
        val ledger = useCase(listOf(total("Marta", "-70.00", count = 2))).invoke().first()

        val entry = ledger.entries.single()
        assertEquals(BigDecimal("-70.00"), entry.amountIn(eur))
        assertEquals(2, entry.movementCount)
        assertFalse(entry.isSettled)
        assertEquals(BigDecimal("70.00"), ledger.owedToYou)
        assertEquals(BigDecimal.ZERO, ledger.youOwe)
    }

    @Test
    fun `money received from a person is a debt, and the two totals never net out`() = runTest {
        val ledger = useCase(
            listOf(
                total("Marta", "-200.00"),
                total("Luca", "200.00"),
            ),
        ).invoke().first()

        assertEquals(BigDecimal("200.00"), ledger.owedToYou)
        assertEquals(BigDecimal("200.00"), ledger.youOwe)
        assertTrue(ledger.hasOpenPositions)
    }

    @Test
    fun `a counterparty with only repayments reads as a debt still open`() = runTest {
        // Received 50 and never gave anything back.
        val ledger = useCase(listOf(total("Luca", "50.00"))).invoke().first()

        assertEquals(BigDecimal("50.00"), ledger.entries.single().amountIn(eur))
        assertEquals(BigDecimal("50.00"), ledger.youOwe)
        assertEquals(BigDecimal.ZERO, ledger.owedToYou)
    }

    @Test
    fun `spellings differing by case or accents are the same person`() = runTest {
        val ledger = useCase(
            listOf(
                total("Nicolò", "-100.00", day = LocalDate.of(2026, 6, 1)),
                total("nicolo", "40.00", day = LocalDate.of(2026, 7, 10)),
                total("  NICOLÒ ", "10.00", day = LocalDate.of(2026, 7, 2)),
            ),
        ).invoke().first()

        val entry = ledger.entries.single()
        assertEquals(BigDecimal("-50.00"), entry.amountIn(eur))
        assertEquals(3, entry.movementCount)
        assertEquals(LocalDate.of(2026, 7, 10), entry.lastActivity)
        // The most recent spelling is the one shown: fixing a name once fixes it everywhere.
        assertEquals("nicolo", entry.name)
    }

    @Test
    fun `a fully repaid loan is settled and moves below the open ones`() = runTest {
        val ledger = useCase(
            listOf(
                total("Marta", "0.00", count = 2, day = LocalDate.of(2026, 7, 20)),
                total("Luca", "-25.00", day = LocalDate.of(2026, 7, 5)),
            ),
        ).invoke().first()

        assertEquals(listOf("Luca", "Marta"), ledger.entries.map { it.name })
        assertTrue(ledger.entries.last().isSettled)
        assertFalse(ledger.entries.first().isSettled)
        assertTrue(ledger.hasOpenEntries)
        assertEquals(BigDecimal("25.00"), ledger.owedToYou)
    }

    @Test
    fun `open positions are listed most recent first`() = runTest {
        val ledger = useCase(
            listOf(
                total("Marta", "-10.00", day = LocalDate.of(2026, 5, 1)),
                total("Luca", "-10.00", day = LocalDate.of(2026, 7, 1)),
                total("Sara", "-10.00", day = LocalDate.of(2026, 6, 1)),
            ),
        ).invoke().first()

        assertEquals(listOf("Luca", "Sara", "Marta"), ledger.entries.map { it.name })
    }

    @Test
    fun `positions in another currency stay out of the totals and are flagged`() = runTest {
        val ledger = useCase(
            listOf(
                total("Marta", "-100.00"),
                total("Marta", "-80.00", currency = usd),
            ),
        ).invoke().first()

        val entry = ledger.entries.single()
        assertEquals(BigDecimal("-100.00"), entry.amountIn(eur))
        assertEquals(BigDecimal("-80.00"), entry.amountIn(usd))
        // The primary currency comes first, so a row shows the figure it should.
        assertEquals(eur, entry.amounts.first().currency)
        assertEquals(BigDecimal("100.00"), ledger.owedToYou)
        assertTrue(ledger.hasOtherCurrencies)
    }

    @Test
    fun `the explicit primary currency decides which positions add up`() = runTest {
        val ledger = useCase(
            listOf(
                total("Marta", "-100.00"),
                total("Luca", "-80.00", currency = usd),
            ),
            override = usd,
        ).invoke().first()

        assertEquals(usd, ledger.currency)
        assertEquals(BigDecimal("80.00"), ledger.owedToYou)
        assertNull(ledger.entries.first { it.name == "Marta" }.amountIn(usd))
        assertTrue(ledger.hasOtherCurrencies)
    }

    @Test
    fun `no counterparty movements is an empty ledger`() = runTest {
        val ledger = useCase(emptyList()).invoke().first()

        assertTrue(ledger.isEmpty)
        assertFalse(ledger.hasOpenPositions)
        assertFalse(ledger.hasOtherCurrencies)
        assertEquals(eur, ledger.currency)
    }
}
