package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.LoanProgress
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency

class ObserveLoanProgressUseCaseTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val usd: Currency = Currency.getInstance("USD")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-15T09:00:00Z"), ZoneId.of("Europe/Rome"))

    private val accountRepository = mockk<AccountRepository>()
    private val recurringRuleRepository = mockk<RecurringRuleRepository>()

    private fun loanAccount(
        id: Long = 7L,
        initialBalance: BigDecimal = BigDecimal("-12000.00"),
        type: AccountType = AccountType.LOAN,
        archived: Boolean = false,
    ) = Account(
        id = id,
        name = "Prestito auto",
        type = type,
        currency = eur,
        initialBalance = initialBalance,
        isArchived = archived,
    )

    private fun useCase(
        accounts: List<AccountWithBalance>,
        rules: List<RecurringRule> = emptyList(),
    ): ObserveLoanProgressUseCase {
        every { accountRepository.observeAccountsWithBalance() } returns flowOf(accounts)
        every { recurringRuleRepository.observeRules() } returns flowOf(rules)
        return ObserveLoanProgressUseCase(
            accountRepository = accountRepository,
            recurringRuleRepository = recurringRuleRepository,
            clock = clock,
        )
    }

    private fun installmentRule(
        amount: BigDecimal?,
        currency: Currency = eur,
        destination: Long = 7L,
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        startDate: LocalDate = LocalDate.of(2026, 1, 5),
    ) = RecurringRule(
        name = "Rata",
        type = TransactionType.TRANSFER,
        currency = currency,
        accountId = 1L,
        frequency = frequency,
        startDate = startDate,
        amount = amount,
        transferAccountId = destination,
        transferAmount = amount,
        transferCurrency = currency,
    )

    private suspend fun single(
        accounts: List<AccountWithBalance>,
        rules: List<RecurringRule> = emptyList(),
    ): LoanProgress = useCase(accounts, rules).invoke().first().getValue(7L)

    @Test
    fun `residual, fraction and estimates derive from the balance and the linked rule`() = runTest {
        val progress = single(
            accounts = listOf(AccountWithBalance(loanAccount(), BigDecimal("-8400.00"))),
            rules = listOf(installmentRule(BigDecimal("300"))),
        )

        assertEquals(BigDecimal("8400.00"), progress.residual)
        // 12000 declared, 8400 still owed: 30% repaid.
        assertEquals(0.3f, progress.fraction, 1e-6f)
        assertFalse(progress.isPaidOff)
        assertTrue(progress.hasLinkedRule)
        assertEquals(BigDecimal("300.00"), progress.plannedMonthly)
        assertEquals(BigDecimal("300"), progress.nextInstallmentAmount)
        // Day-5 monthly rule, today is 2026-07-15: next charge on August 5.
        assertEquals(LocalDate.of(2026, 8, 5), progress.nextInstallmentDate)
        // 8400 / 300 = 28 exact installments.
        assertEquals(28L, progress.remainingInstallments)
        assertEquals(LocalDate.of(2028, 11, 15), progress.projectedPayoffDate)
    }

    @Test
    fun `remaining installments round up`() = runTest {
        val progress = single(
            accounts = listOf(AccountWithBalance(loanAccount(), BigDecimal("-1000.00"))),
            rules = listOf(installmentRule(BigDecimal("300"))),
        )

        // 1000 / 300 = 3.33: the last partial installment still has to happen.
        assertEquals(4L, progress.remainingInstallments)
    }

    @Test
    fun `without a linked rule there is nothing to estimate`() = runTest {
        val progress = single(
            accounts = listOf(AccountWithBalance(loanAccount(), BigDecimal("-8400.00"))),
        )

        assertEquals(BigDecimal("8400.00"), progress.residual)
        assertFalse(progress.hasLinkedRule)
        assertNull(progress.plannedMonthly)
        assertNull(progress.nextInstallmentAmount)
        assertNull(progress.nextInstallmentDate)
        assertNull(progress.remainingInstallments)
        assertNull(progress.projectedPayoffDate)
    }

    @Test
    fun `multiple linked rules sum their monthly equivalents`() = runTest {
        val progress = single(
            accounts = listOf(AccountWithBalance(loanAccount(), BigDecimal("-8400.00"))),
            rules = listOf(
                installmentRule(BigDecimal("300"), startDate = LocalDate.of(2026, 1, 5)),
                // 600 quarterly reads as 200/month.
                installmentRule(
                    BigDecimal("600"),
                    frequency = RecurrenceFrequency.QUARTERLY,
                    startDate = LocalDate.of(2026, 1, 20),
                ),
            ),
        )

        assertEquals(BigDecimal("500.00"), progress.plannedMonthly)
        // ceil(8400 / 500) = 17.
        assertEquals(17L, progress.remainingInstallments)
        // The next charge is the earliest among the linked rules: the quarterly
        // one lands on July 20, before the monthly one's August 5.
        assertEquals(LocalDate.of(2026, 7, 20), progress.nextInstallmentDate)
        assertEquals(BigDecimal("600"), progress.nextInstallmentAmount)
    }

    @Test
    fun `rules in another currency or towards other accounts are excluded`() = runTest {
        val progress = single(
            accounts = listOf(AccountWithBalance(loanAccount(), BigDecimal("-8400.00"))),
            rules = listOf(
                installmentRule(BigDecimal("300"), currency = usd),
                installmentRule(BigDecimal("300"), destination = 42L),
                // Variable-amount rules have nothing to project either.
                installmentRule(amount = null),
            ),
        )

        assertFalse(progress.hasLinkedRule)
        assertNull(progress.plannedMonthly)
    }

    @Test
    fun `a loan at zero reads as paid off`() = runTest {
        val progress = single(
            accounts = listOf(AccountWithBalance(loanAccount(), BigDecimal.ZERO)),
            rules = listOf(installmentRule(BigDecimal("300"))),
        )

        assertTrue(progress.isPaidOff)
        assertEquals(1f, progress.fraction)
        assertEquals(BigDecimal("0.00"), progress.residual)
        assertNull(progress.remainingInstallments)
        assertNull(progress.projectedPayoffDate)
    }

    @Test
    fun `a repayment beyond the debt still reads as paid off, never as negative debt`() = runTest {
        val progress = single(
            accounts = listOf(AccountWithBalance(loanAccount(), BigDecimal("25.00"))),
        )

        assertTrue(progress.isPaidOff)
        assertEquals(BigDecimal("0.00"), progress.residual)
        assertEquals(1f, progress.fraction)
    }

    @Test
    fun `archived loans and other account types are not reported`() = runTest {
        val progressById = useCase(
            accounts = listOf(
                AccountWithBalance(loanAccount(id = 7L), BigDecimal("-100.00")),
                AccountWithBalance(loanAccount(id = 8L, archived = true), BigDecimal("-100.00")),
                AccountWithBalance(loanAccount(id = 9L, type = AccountType.CHECKING), BigDecimal("-100.00")),
            ),
        ).invoke().first()

        assertEquals(setOf(7L), progressById.keys)
    }
}
