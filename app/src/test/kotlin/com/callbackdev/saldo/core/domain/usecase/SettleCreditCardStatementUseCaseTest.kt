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

    @Test
    fun `settles the due cycle with a transfer and advances the watermark`() = runTest {
        coEvery { accountRepository.getAccount(1L) } returns card
        coEvery { accountRepository.getAccount(2L) } returns linked
        coEvery {
            transactionRepository.sumOwnMovements(eq(1L), any(), any(), eq(eur))
        } returns BigDecimal("-90.00")
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

        val result = useCase(1L)

        assertTrue(result is StatementSettlement.Settled)
        assertEquals(BigDecimal.ZERO, (result as StatementSettlement.Settled).amount)
        coVerify(exactly = 0) { transactionRepository.upsert(any()) }
        coVerify { accountRepository.updateSettlementWatermark(1L, LocalDate.of(2024, 2, 20)) }
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
