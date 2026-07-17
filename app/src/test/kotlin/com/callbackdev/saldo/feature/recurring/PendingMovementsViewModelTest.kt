package com.callbackdev.saldo.feature.recurring

import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Currency

@ExtendWith(MainDispatcherExtension::class)
class PendingMovementsViewModelTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-09T09:00:00Z"), ZoneId.of("Europe/Rome"))

    private val transactionRepository = mockk<TransactionRepository>(relaxed = true)
    private val recurringRuleRepository = mockk<RecurringRuleRepository>()
    private val accountRepository = mockk<AccountRepository>()

    private fun pending(amountMinorSign: BigDecimal) = Transaction(
        id = 7L,
        type = TransactionType.EXPENSE,
        amount = amountMinorSign,
        currency = eur,
        accountId = 3L,
        timestamp = Instant.parse("2026-07-07T12:00:00Z"),
        zoneOffset = ZoneOffset.ofHours(2),
        recurringRuleId = 1L,
        isPending = true,
    )

    private fun viewModel(pending: List<Transaction>): PendingMovementsViewModel {
        every { transactionRepository.observePendingTransactions() } returns flowOf(pending)
        every { recurringRuleRepository.observeRules() } returns flowOf(emptyList<RecurringRule>())
        every { accountRepository.observeAccountsWithBalance() } returns flowOf(emptyList<AccountWithBalance>())
        return PendingMovementsViewModel(
            transactionRepository,
            recurringRuleRepository,
            accountRepository,
            clock,
        )
    }

    @Test
    fun `confirm applies the sign and clears the pending flag`() = runTest {
        val saved = slot<Transaction>()
        coEvery { transactionRepository.upsert(capture(saved)) } returns 7L
        val viewModel = viewModel(listOf(pending(BigDecimal.ZERO)))

        viewModel.confirm(pending(BigDecimal.ZERO), BigDecimal("15.00"))

        assertEquals(BigDecimal("-15.00"), saved.captured.amount)
        assertFalse(saved.captured.isPending)
    }

    private fun pendingTransfer(destinationCurrency: Currency) = Transaction(
        id = 8L,
        type = TransactionType.TRANSFER,
        amount = BigDecimal("-100.00"),
        currency = eur,
        accountId = 3L,
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
        val usd = Currency.getInstance("USD")
        val saved = slot<Transaction>()
        coEvery { transactionRepository.upsert(capture(saved)) } returns 8L
        val movement = pendingTransfer(destinationCurrency = usd)
        val viewModel = viewModel(listOf(movement))

        viewModel.confirm(movement, BigDecimal("108.50"))

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
        // Same-currency transfer in confirm mode: destination equals source.
        val movement = pendingTransfer(destinationCurrency = eur).copy(transferAmount = BigDecimal("100.00"))
        val viewModel = viewModel(listOf(movement))

        viewModel.confirm(movement, BigDecimal("120.00"))

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
        val movement = pending(BigDecimal("-12.99"))
        val viewModel = viewModel(listOf(movement))

        viewModel.skip(movement)

        coVerify { transactionRepository.delete(any()) }
        assertEquals(7L, deleted.captured.id)
    }
}
