package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.SavingsGoal
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.SavingsGoalRepository
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

class ObserveSavingsGoalsProgressUseCaseTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val usd: Currency = Currency.getInstance("USD")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-15T09:00:00Z"), ZoneId.of("Europe/Rome"))

    private val savingsGoalRepository = mockk<SavingsGoalRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val recurringRuleRepository = mockk<RecurringRuleRepository>()

    private val savingsAccount = Account(
        id = 9L,
        name = "Risparmi",
        type = AccountType.SAVINGS,
        currency = eur,
        initialBalance = BigDecimal.ZERO,
    )

    private fun useCase(
        goals: List<SavingsGoal>,
        balance: BigDecimal,
        rules: List<RecurringRule> = emptyList(),
    ): ObserveSavingsGoalsProgressUseCase {
        every { savingsGoalRepository.observeGoals() } returns flowOf(goals)
        every { accountRepository.observeAccountsWithBalance() } returns
            flowOf(listOf(AccountWithBalance(savingsAccount, balance)))
        every { recurringRuleRepository.observeRules() } returns flowOf(rules)
        return ObserveSavingsGoalsProgressUseCase(
            savingsGoalRepository = savingsGoalRepository,
            accountRepository = accountRepository,
            recurringRuleRepository = recurringRuleRepository,
            clock = clock,
        )
    }

    private fun goal(target: String, date: LocalDate? = null) = SavingsGoal(
        id = 1L,
        name = "Holiday",
        targetAmount = BigDecimal(target),
        currency = eur,
        accountId = 9L,
        targetDate = date,
    )

    private fun transferRule(
        amount: BigDecimal?,
        currency: Currency = eur,
        destination: Long = 9L,
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    ) = RecurringRule(
        name = "To savings",
        type = TransactionType.TRANSFER,
        currency = currency,
        accountId = 1L,
        frequency = frequency,
        startDate = LocalDate.of(2026, 1, 1),
        amount = amount,
        transferAccountId = destination,
        transferAmount = amount,
        transferCurrency = currency,
    )

    @Test
    fun `progress and suggestion derive from the account balance and target date`() = runTest {
        val progress = useCase(
            goals = listOf(goal("1000", LocalDate.of(2026, 12, 31))),
            balance = BigDecimal("400.00"),
        ).invoke().first().single()

        assertEquals(BigDecimal("400.00"), progress.saved)
        assertEquals(BigDecimal("600.00"), progress.remaining)
        assertEquals(0.4f, progress.fraction)
        assertFalse(progress.isReached)
        // 600 spread over the 5 whole months July -> December, rounded up.
        assertEquals(BigDecimal("120.00"), progress.suggestedMonthly)
        assertEquals(BigDecimal.ZERO.setScale(2), progress.plannedMonthly)
        assertNull(progress.projectedDate)
        assertNull(progress.onTrack)
    }

    @Test
    fun `a reached goal has no suggestion even with a target date`() = runTest {
        val progress = useCase(
            goals = listOf(goal("1000", LocalDate.of(2026, 12, 31))),
            balance = BigDecimal("1000.00"),
        ).invoke().first().single()

        assertTrue(progress.isReached)
        assertEquals(BigDecimal("0.00"), progress.remaining)
        assertNull(progress.suggestedMonthly)
        assertNull(progress.projectedDate)
    }

    @Test
    fun `planned transfers project a completion date and an on-track verdict`() = runTest {
        val progress = useCase(
            goals = listOf(goal("1000", LocalDate.of(2026, 12, 31))),
            balance = BigDecimal("400.00"),
            rules = listOf(
                transferRule(BigDecimal("200")),
                // Cross-currency and variable-amount transfers are excluded from planned.
                transferRule(amount = null),
                transferRule(BigDecimal("50"), currency = usd),
                // A transfer to another account does not count.
                transferRule(BigDecimal("999"), destination = 42L),
            ),
        ).invoke().first().single()

        assertEquals(BigDecimal("200.00"), progress.plannedMonthly)
        // 600 remaining at 200/month -> 3 months from 2026-07-15.
        assertEquals(LocalDate.of(2026, 10, 15), progress.projectedDate)
        // 200 planned >= 120 suggested.
        assertEquals(true, progress.onTrack)
    }

    @Test
    fun `without a target date there is no suggestion but planned still projects`() = runTest {
        val progress = useCase(
            goals = listOf(goal("1000", date = null)),
            balance = BigDecimal("400.00"),
            rules = listOf(transferRule(BigDecimal("100"))),
        ).invoke().first().single()

        assertNull(progress.suggestedMonthly)
        assertEquals(BigDecimal("100.00"), progress.plannedMonthly)
        // 600 at 100/month -> 6 months.
        assertEquals(LocalDate.of(2027, 1, 15), progress.projectedDate)
        assertNull(progress.onTrack)
    }

    @Test
    fun `goals are returned alphabetically by name, case-insensitive`() = runTest {
        fun savingsAccount(id: Long) = Account(
            id = id,
            name = "Account $id",
            type = AccountType.SAVINGS,
            currency = eur,
            initialBalance = BigDecimal.ZERO,
        )

        fun namedGoal(id: Long, name: String) = SavingsGoal(
            id = id,
            name = name,
            targetAmount = BigDecimal("1000"),
            currency = eur,
            accountId = id,
        )

        every { savingsGoalRepository.observeGoals() } returns flowOf(
            listOf(
                namedGoal(1L, "vacanza"),
                namedGoal(2L, "Auto"),
                namedGoal(3L, "Casa"),
            ),
        )
        every { accountRepository.observeAccountsWithBalance() } returns flowOf(
            listOf(1L, 2L, 3L).map { AccountWithBalance(savingsAccount(it), BigDecimal.ZERO) },
        )
        every { recurringRuleRepository.observeRules() } returns flowOf(emptyList())

        val names = ObserveSavingsGoalsProgressUseCase(
            savingsGoalRepository = savingsGoalRepository,
            accountRepository = accountRepository,
            recurringRuleRepository = recurringRuleRepository,
            clock = clock,
        ).invoke().first().map { it.goal.name }

        assertEquals(listOf("Auto", "Casa", "vacanza"), names)
    }
}
