package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Budget
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.BudgetRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Currency

class ObserveSafeToSpendUseCaseTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val zone: ZoneId = ZoneId.of("Europe/Rome")

    // Fixed "now" = 12 July 2026: 20 days left in the month, today included.
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-12T09:00:00Z"), zone)

    private val budgetRepository = mockk<BudgetRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val recurringRuleRepository = mockk<RecurringRuleRepository>()
    private val accountRepository = mockk<AccountRepository>()

    private fun useCase(
        budgets: List<Budget> = listOf(overallBudget("500.00")),
        totalSpend: BigDecimal = BigDecimal.ZERO,
        pending: List<Transaction> = emptyList(),
        rules: List<RecurringRule> = emptyList(),
        accounts: List<AccountWithBalance> = emptyList(),
        clock: Clock = this.clock,
    ): ObserveSafeToSpendUseCase {
        every { budgetRepository.observeBudgets() } returns flowOf(budgets)
        every {
            transactionRepository.observeStatsSpendTotal(any(), any(), any())
        } returns flowOf(totalSpend)
        every { transactionRepository.observePendingTransactions() } returns flowOf(pending)
        every { recurringRuleRepository.observeRules() } returns flowOf(rules)
        every { accountRepository.observeAccountsWithBalance() } returns flowOf(accounts)
        return ObserveSafeToSpendUseCase(
            budgetRepository = budgetRepository,
            transactionRepository = transactionRepository,
            recurringRuleRepository = recurringRuleRepository,
            accountRepository = accountRepository,
            clock = clock,
        )
    }

    private fun account(id: Long, includedInBudget: Boolean) = AccountWithBalance(
        account = Account(
            id = id,
            name = "Account $id",
            type = AccountType.CHECKING,
            currency = eur,
            initialBalance = BigDecimal.ZERO,
            isIncludedInBudget = includedInBudget,
        ),
        balance = BigDecimal.ZERO,
    )

    private fun overallBudget(amount: String) =
        Budget(id = 1L, categoryId = null, amount = BigDecimal(amount), currency = eur)

    private fun pendingExpense(
        amount: String,
        day: LocalDate = LocalDate.of(2026, 7, 10),
        currency: Currency = eur,
        type: TransactionType = TransactionType.EXPENSE,
    ) = Transaction(
        type = type,
        amount = BigDecimal(amount),
        currency = currency,
        accountId = 1L,
        timestamp = LocalDateTime.of(day, java.time.LocalTime.NOON).toInstant(ZoneOffset.UTC),
        zoneOffset = ZoneOffset.UTC,
        isPending = true,
        recurringRuleId = 9L,
    )

    private fun monthlyExpenseRule(amount: String, dayOfMonth: Int) = RecurringRule(
        id = 5L,
        name = "Netflix",
        type = TransactionType.EXPENSE,
        currency = eur,
        accountId = 1L,
        frequency = RecurrenceFrequency.MONTHLY,
        startDate = LocalDate.of(2026, 1, dayOfMonth),
        amount = BigDecimal(amount),
        dayOfReference = dayOfMonth,
    )

    @Test
    fun `null without an overall budget in the currency`() = runTest {
        assertNull(useCase(budgets = emptyList()).invoke(eur).first())
        assertNull(
            useCase(
                budgets = listOf(
                    Budget(id = 2L, categoryId = 7L, amount = BigDecimal.TEN, currency = eur),
                ),
            ).invoke(eur).first(),
        )
    }

    @Test
    fun `remaining subtracts spend, pending and upcoming exactly once`() = runTest {
        val safeToSpend = useCase(
            totalSpend = BigDecimal("-200.00"),
            // The generated-but-pending charge: excluded from spend, behind the
            // generation floor, so only pendingCommitted may carry it.
            pending = listOf(pendingExpense("-30.00")),
            rules = listOf(
                monthlyExpenseRule("30.00", dayOfMonth = 10)
                    .copy(lastGeneratedDate = LocalDate.of(2026, 7, 10)),
                monthlyExpenseRule("15.00", dayOfMonth = 25).copy(id = 6L),
            ),
        ).invoke(eur).first()!!

        assertEquals(BigDecimal("200.00"), safeToSpend.spent)
        assertEquals(BigDecimal("30.00"), safeToSpend.pendingCommitted)
        // Only the charge still ahead (the 25th); the generated one is not re-counted.
        assertEquals(BigDecimal("15.00"), safeToSpend.upcomingRecurring)
        // 500 - 200 - 30 - 15
        assertEquals(BigDecimal("255.00"), safeToSpend.remaining)
        assertEquals(20, safeToSpend.daysLeft)
        // 255 / 20 = 12.75, floored at currency scale.
        assertEquals(BigDecimal("12.75"), safeToSpend.perDay)
    }

    @Test
    fun `per day floors instead of rounding up`() = runTest {
        val safeToSpend = useCase(totalSpend = BigDecimal("-400.01")).invoke(eur).first()!!

        // 99.99 / 20 = 4.9995 -> 4.99, never 5.00.
        assertEquals(BigDecimal("4.99"), safeToSpend.perDay)
    }

    @Test
    fun `negative remaining keeps the figure and drops the per day`() = runTest {
        val safeToSpend = useCase(totalSpend = BigDecimal("-620.00")).invoke(eur).first()!!

        assertEquals(BigDecimal("-120.00"), safeToSpend.remaining)
        assertNull(safeToSpend.perDay)
    }

    @Test
    fun `pending in other currencies, months or types does not count`() = runTest {
        val usd = Currency.getInstance("USD")
        val safeToSpend = useCase(
            pending = listOf(
                pendingExpense("-10.00", currency = usd),
                pendingExpense("-10.00", day = LocalDate.of(2026, 6, 10)),
                pendingExpense("10.00", type = TransactionType.INCOME),
            ),
        ).invoke(eur).first()!!

        assertEquals(BigDecimal.ZERO, safeToSpend.pendingCommitted)
        assertEquals(BigDecimal("500.00"), safeToSpend.remaining)
    }

    @Test
    fun `pending and upcoming on a budget-excluded account do not count`() = runTest {
        val safeToSpend = useCase(
            // pendingExpense and monthlyExpenseRule both use accountId = 1L.
            pending = listOf(pendingExpense("-30.00")),
            rules = listOf(monthlyExpenseRule("15.00", dayOfMonth = 25).copy(id = 6L)),
            accounts = listOf(account(id = 1L, includedInBudget = false)),
        ).invoke(eur).first()!!

        assertEquals(BigDecimal.ZERO, safeToSpend.pendingCommitted)
        assertEquals(BigDecimal.ZERO, safeToSpend.upcomingRecurring)
        assertEquals(BigDecimal("500.00"), safeToSpend.remaining)
    }

    @Test
    fun `last day of the month divides by one`() = runTest {
        val endOfMonthClock = Clock.fixed(Instant.parse("2026-07-31T09:00:00Z"), zone)
        val safeToSpend = useCase(
            totalSpend = BigDecimal("-400.00"),
            clock = endOfMonthClock,
        ).invoke(eur).first()!!

        assertEquals(1, safeToSpend.daysLeft)
        assertEquals(BigDecimal("100.00"), safeToSpend.perDay)
    }
}
