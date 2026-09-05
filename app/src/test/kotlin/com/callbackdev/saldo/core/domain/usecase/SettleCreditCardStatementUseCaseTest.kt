package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.CreditCardConfig
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRunner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency

class SettleCreditCardStatementUseCaseTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val zone: ZoneId = ZoneId.of("Europe/Rome")

    // A fixed "today" of 2024-03-25: the Feb 20 cycle (due Mar 5) is owed.
    private val clock: Clock = Clock.fixed(
        LocalDate.of(2024, 3, 25).atStartOfDay(zone).toInstant(),
        zone,
    )

    private val accountRepository = mockk<AccountRepository>(relaxUnitFun = true)
    private val transactionRepository = mockk<TransactionRepository>()
    private val runner = object : TransactionRunner {
        override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
    }

    private val useCase = SettleCreditCardStatementUseCase(
        accountRepository,
        transactionRepository,
        runner,
        clock,
    )

    private val card = Account(
        id = 1L,
        name = "Credit card",
        type = AccountType.CREDIT_CARD,
        currency = eur,
        initialBalance = BigDecimal.ZERO,
        creditCard = CreditCardConfig(
            statementClosingDay = 20,
            paymentDueDay = 5,
            linkedAccountId = 2L,
            // Seeded at creation (before the Feb cycle), as the editor does.
            lastSettledClosing = LocalDate.of(2024, 1, 20),
        ),
    )
    private val linked = Account(
        id = 2L,
        name = "Checking",
        type = AccountType.CHECKING,
        currency = eur,
        initialBalance = BigDecimal("1000.00"),
    )

    /** The card received [total] in transfers over its whole life (settlements and manual payments). */
    private fun paymentsReceived(total: BigDecimal) {
        coEvery {
            transactionRepository.sumIncomingTransfers(eq(1L), any(), any(), eq(eur))
        } returns total
    }

    @Test
    fun `settles the due cycle with a transfer and advances the watermark`() = runTest {
        coEvery { accountRepository.getAccount(1L) } returns card
        coEvery { accountRepository.getAccount(2L) } returns linked
        coEvery {
            transactionRepository.sumOwnMovements(eq(1L), any(), any(), eq(eur))
        } returns BigDecimal("-90.00")
        paymentsReceived(BigDecimal.ZERO)
        val slot = slot<Transaction>()
        coEvery { transactionRepository.upsert(capture(slot)) } returns 10L

        val result = useCase(1L)

        assertTrue(result is StatementSettlement.Settled)
        assertEquals(BigDecimal("90.00"), (result as StatementSettlement.Settled).amount)
        val transfer = slot.captured
        assertEquals(TransactionType.TRANSFER, transfer.type)
        assertEquals(2L, transfer.accountId)
        assertEquals(1L, transfer.transferAccountId)
        assertEquals(BigDecimal("-90.00"), transfer.amount)
        assertEquals(BigDecimal("90.00"), transfer.transferAmount)
        coVerify { accountRepository.updateSettlementWatermark(1L, LocalDate.of(2024, 2, 20)) }
    }

    @Test
    fun `nothing owed still advances the watermark without a transfer`() = runTest {
        coEvery { accountRepository.getAccount(1L) } returns card
        coEvery { accountRepository.getAccount(2L) } returns linked
        coEvery {
            transactionRepository.sumOwnMovements(eq(1L), any(), any(), eq(eur))
        } returns BigDecimal.ZERO
        paymentsReceived(BigDecimal.ZERO)

        val result = useCase(1L)

        assertTrue(result is StatementSettlement.Settled)
        assertEquals(BigDecimal.ZERO, (result as StatementSettlement.Settled).amount)
        coVerify(exactly = 0) { transactionRepository.upsert(any()) }
        coVerify { accountRepository.updateSettlementWatermark(1L, LocalDate.of(2024, 2, 20)) }
    }

    @Test
    fun `a payment made by hand before the due date lowers the statement instead of doubling`() = runTest {
        // 90.00 charged in the Feb cycle, 60.00 already transferred to the card
        // by the user: the settlement posts only the 30.00 still owed.
        coEvery { accountRepository.getAccount(1L) } returns card
        coEvery { accountRepository.getAccount(2L) } returns linked
        coEvery {
            transactionRepository.sumOwnMovements(eq(1L), any(), any(), eq(eur))
        } returns BigDecimal("-90.00")
        paymentsReceived(BigDecimal("60.00"))
        val slot = slot<Transaction>()
        coEvery { transactionRepository.upsert(capture(slot)) } returns 12L

        val result = useCase(1L)

        assertEquals(BigDecimal("30.00"), (result as StatementSettlement.Settled).amount)
        assertEquals(BigDecimal("30.00"), slot.captured.transferAmount)
        coVerify { accountRepository.updateSettlementWatermark(1L, LocalDate.of(2024, 2, 20)) }
    }

    @Test
    fun `a statement fully paid by hand is consumed without a transfer`() = runTest {
        coEvery { accountRepository.getAccount(1L) } returns card
        coEvery { accountRepository.getAccount(2L) } returns linked
        coEvery {
            transactionRepository.sumOwnMovements(eq(1L), any(), any(), eq(eur))
        } returns BigDecimal("-90.00")
        paymentsReceived(BigDecimal("90.00"))

        val result = useCase(1L)

        assertEquals(BigDecimal.ZERO, (result as StatementSettlement.Settled).amount)
        coVerify(exactly = 0) { transactionRepository.upsert(any()) }
        coVerify { accountRepository.updateSettlementWatermark(1L, LocalDate.of(2024, 2, 20)) }
    }

    @Test
    fun `credit left by an earlier cycle reduces the next statement`() = runTest {
        // Two cycles owed (watermark before Jan 20). January ended in credit: a
        // 50.00 refund and nothing spent, so the card is at +50.00 at its
        // closing. February charged 140.00; the statements read 0 and 90.
        val behind = card.copy(
            creditCard = card.creditCard!!.copy(lastSettledClosing = LocalDate.of(2023, 12, 20)),
        )
        coEvery { accountRepository.getAccount(1L) } returns behind
        coEvery { accountRepository.getAccount(2L) } returns linked
        val januaryEnd = LocalDate.of(2024, 1, 21).atStartOfDay(zone).toInstant()
        coEvery {
            transactionRepository.sumOwnMovements(eq(1L), any(), match { it <= januaryEnd }, eq(eur))
        } returns BigDecimal("50.00")
        coEvery {
            transactionRepository.sumOwnMovements(eq(1L), any(), match { it > januaryEnd }, eq(eur))
        } returns BigDecimal("-90.00")
        paymentsReceived(BigDecimal.ZERO)
        val slot = slot<Transaction>()
        coEvery { transactionRepository.upsert(capture(slot)) } returns 13L

        val due = com.callbackdev.saldo.core.domain.creditcard.BillingCycleCalculator
            .dueStatements(LocalDate.of(2024, 3, 25), behind.creditCard!!)
        val amounts = useCase.statementAmounts(behind, due)
        val result = useCase(1L)

        assertEquals(listOf(BigDecimal.ZERO, BigDecimal("90.00")), amounts)
        assertEquals(BigDecimal("90.00"), (result as StatementSettlement.Settled).amount)
        assertEquals(LocalDate.of(2024, 2, 20), result.cycle.closing)
    }

    @Test
    fun `payments settle the oldest cycle first and later cycles keep their own charges`() = runTest {
        // January charged 100.00, February 140.00 more (240.00 through Feb 20);
        // 130.00 was paid by hand: January is covered (100) and 30 of February.
        val behind = card.copy(
            creditCard = card.creditCard!!.copy(lastSettledClosing = LocalDate.of(2023, 12, 20)),
        )
        coEvery { accountRepository.getAccount(1L) } returns behind
        coEvery { accountRepository.getAccount(2L) } returns linked
        val januaryEnd = LocalDate.of(2024, 1, 21).atStartOfDay(zone).toInstant()
        coEvery {
            transactionRepository.sumOwnMovements(eq(1L), any(), match { it <= januaryEnd }, eq(eur))
        } returns BigDecimal("-100.00")
        coEvery {
            transactionRepository.sumOwnMovements(eq(1L), any(), match { it > januaryEnd }, eq(eur))
        } returns BigDecimal("-240.00")
        paymentsReceived(BigDecimal("130.00"))

        val due = com.callbackdev.saldo.core.domain.creditcard.BillingCycleCalculator
            .dueStatements(LocalDate.of(2024, 3, 25), behind.creditCard!!)
        val amounts = useCase.statementAmounts(behind, due)

        assertEquals(listOf(BigDecimal.ZERO, BigDecimal("110.00")), amounts)
    }

    @Test
    fun `an empty older cycle is skipped so the settlement pays the advertised one`() = runTest {
        // Two cycles owed (watermark before Jan 20): Jan 20 empty, Feb 20 owing.
        // The call to action only lists non-empty statements, so settling the
        // empty one first would look like a button that does nothing.
        val behind = card.copy(
            creditCard = card.creditCard!!.copy(lastSettledClosing = LocalDate.of(2023, 12, 20)),
        )
        coEvery { accountRepository.getAccount(1L) } returns behind
        coEvery { accountRepository.getAccount(2L) } returns linked
        val januaryEnd = LocalDate.of(2024, 1, 21).atStartOfDay(zone).toInstant()
        coEvery {
            transactionRepository.sumOwnMovements(eq(1L), any(), match { it <= januaryEnd }, eq(eur))
        } returns BigDecimal.ZERO
        coEvery {
            transactionRepository.sumOwnMovements(eq(1L), any(), match { it > januaryEnd }, eq(eur))
        } returns BigDecimal("-140.00")
        paymentsReceived(BigDecimal.ZERO)
        val slot = slot<Transaction>()
        coEvery { transactionRepository.upsert(capture(slot)) } returns 11L

        val result = useCase(1L)

        assertTrue(result is StatementSettlement.Settled)
        val settled = result as StatementSettlement.Settled
        assertEquals(BigDecimal("140.00"), settled.amount)
        assertEquals(LocalDate.of(2024, 2, 20), settled.cycle.closing)
        assertEquals(BigDecimal("140.00"), slot.captured.transferAmount)
        // The watermark jumps past the empty January cycle in one go.
        coVerify { accountRepository.updateSettlementWatermark(1L, LocalDate.of(2024, 2, 20)) }
        coVerify(exactly = 0) { accountRepository.updateSettlementWatermark(1L, LocalDate.of(2024, 1, 20)) }
    }

    @Test
    fun `already settled cycle yields nothing due`() = runTest {
        val settled = card.copy(
            creditCard = card.creditCard!!.copy(lastSettledClosing = LocalDate.of(2024, 2, 20)),
        )
        coEvery { accountRepository.getAccount(1L) } returns settled
        coEvery { accountRepository.getAccount(2L) } returns linked

        val result = useCase(1L)

        assertEquals(StatementSettlement.NothingDue, result)
        coVerify(exactly = 0) { transactionRepository.upsert(any()) }
    }

    @Test
    fun `a currency mismatch on the linked account is not settleable`() = runTest {
        coEvery { accountRepository.getAccount(1L) } returns card
        coEvery { accountRepository.getAccount(2L) } returns
            linked.copy(currency = Currency.getInstance("USD"))

        val result = useCase(1L)

        assertEquals(StatementSettlement.NotSettleable, result)
        coVerify(exactly = 0) { transactionRepository.upsert(any()) }
    }
}
