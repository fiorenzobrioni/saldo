package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.common.prefs.RenewalReminderPreferences
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency

class CheckUpcomingRenewalsUseCaseTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-09T09:00:00Z"), ZoneId.of("Europe/Rome"))
    private val today: LocalDate = LocalDate.of(2026, 7, 9)

    private val recurringRuleRepository = mockk<RecurringRuleRepository>()
    private val userPreferences = mockk<UserPreferencesRepository>()

    private val reminderWrites = mutableListOf<Pair<Long, LocalDate>>()

    private fun useCase(
        rules: List<RecurringRule>,
        prefs: RenewalReminderPreferences = RenewalReminderPreferences(enabled = true, leadDays = 3),
    ): CheckUpcomingRenewalsUseCase {
        reminderWrites.clear()
        coEvery { recurringRuleRepository.getRules() } returns rules
        coEvery { recurringRuleRepository.updateLastReminderDate(any(), any()) } answers {
            reminderWrites.add(firstArg<Long>() to secondArg())
        }
        every { userPreferences.renewalReminderPreferences } returns flowOf(prefs)
        return CheckUpcomingRenewalsUseCase(recurringRuleRepository, userPreferences, clock)
    }

    private fun rule(
        id: Long = 1L,
        name: String = "Netflix",
        type: TransactionType = TransactionType.EXPENSE,
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        startDate: LocalDate,
        amount: BigDecimal? = BigDecimal("12.99"),
        lastGenerated: LocalDate? = null,
        lastReminder: LocalDate? = null,
        endDate: LocalDate? = null,
        isVariable: Boolean = false,
    ) = RecurringRule(
        id = id,
        name = name,
        type = type,
        currency = eur,
        accountId = 1L,
        frequency = frequency,
        startDate = startDate,
        amount = amount,
        dayOfReference = startDate.dayOfMonth,
        endDate = endDate,
        isVariableAmount = isVariable,
        lastGeneratedDate = lastGenerated,
        lastReminderDate = lastReminder,
    )

    @Test
    fun `a charge exactly at the lead distance is reported and watermarked`() = runTest {
        // Next charge 12 Jul, today 9 Jul, lead 3 days.
        val netflix = rule(startDate = LocalDate.of(2026, 6, 12), lastGenerated = LocalDate.of(2026, 6, 12))

        val renewals = useCase(listOf(netflix)).invoke()

        assertEquals(1, renewals.size)
        with(renewals.single()) {
            assertEquals("Netflix", ruleName)
            assertEquals(LocalDate.of(2026, 7, 12), dueDate)
            assertEquals(3, daysUntil)
            assertEquals(BigDecimal("12.99"), amount)
        }
        assertEquals(listOf(1L to LocalDate.of(2026, 7, 12)), reminderWrites)
    }

    @Test
    fun `a charge beyond the lead window is not reported`() = runTest {
        // Next charge 13 Jul: 4 days away, lead 3.
        val rule = rule(startDate = LocalDate.of(2026, 6, 13), lastGenerated = LocalDate.of(2026, 6, 13))

        assertTrue(useCase(listOf(rule)).invoke().isEmpty())
        assertTrue(reminderWrites.isEmpty())
    }

    @Test
    fun `an occurrence already reminded is not reported again`() = runTest {
        val rule = rule(
            startDate = LocalDate.of(2026, 6, 12),
            lastGenerated = LocalDate.of(2026, 6, 12),
            lastReminder = LocalDate.of(2026, 7, 12),
        )

        assertTrue(useCase(listOf(rule)).invoke().isEmpty())
        assertTrue(reminderWrites.isEmpty())
    }

    @Test
    fun `skipped worker days still remind once, closer to the due date`() = runTest {
        // Charge on 10 Jul, 1 day away: the 3-days-before run never happened
        // (device off), the reminder still fires at the first chance.
        val rule = rule(startDate = LocalDate.of(2026, 6, 10), lastGenerated = LocalDate.of(2026, 6, 10))

        val first = useCase(listOf(rule)).invoke()
        assertEquals(1, first.single().daysUntil)

        // The same day's second run (or the next catch-up) is silent.
        val reminded = rule(
            startDate = LocalDate.of(2026, 6, 10),
            lastGenerated = LocalDate.of(2026, 6, 10),
            lastReminder = LocalDate.of(2026, 7, 10),
        )
        assertTrue(useCase(listOf(reminded)).invoke().isEmpty())
    }

    @Test
    fun `disabled setting reports nothing and writes nothing`() = runTest {
        val rule = rule(startDate = LocalDate.of(2026, 6, 12), lastGenerated = LocalDate.of(2026, 6, 12))
        val useCase = useCase(listOf(rule), prefs = RenewalReminderPreferences(enabled = false))

        assertTrue(useCase().isEmpty())
        coVerify(exactly = 0) { recurringRuleRepository.updateLastReminderDate(any(), any()) }
    }

    @Test
    fun `each allowed lead time widens the window accordingly`() = runTest {
        // Next charge 16 Jul: 7 days away.
        val weekAway = rule(startDate = LocalDate.of(2026, 6, 16), lastGenerated = LocalDate.of(2026, 6, 16))

        for (lead in listOf(1, 2, 3)) {
            val prefs = RenewalReminderPreferences(enabled = true, leadDays = lead)
            assertTrue(useCase(listOf(weekAway), prefs).invoke().isEmpty(), "lead $lead should not fire")
        }
        val prefs = RenewalReminderPreferences(enabled = true, leadDays = 7)
        assertEquals(7, useCase(listOf(weekAway), prefs).invoke().single().daysUntil)
    }

    @Test
    fun `income rules are reported with their type`() = runTest {
        val salary = rule(
            id = 2L,
            name = "Stipendio",
            type = TransactionType.INCOME,
            startDate = LocalDate.of(2026, 6, 10),
            amount = BigDecimal("2000.00"),
            lastGenerated = LocalDate.of(2026, 6, 10),
        )

        val renewals = useCase(listOf(salary)).invoke()

        assertEquals(TransactionType.INCOME, renewals.single().type)
        assertEquals("Stipendio", renewals.single().ruleName)
    }

    @Test
    fun `ended rules are excluded`() = runTest {
        val ended = rule(
            startDate = LocalDate.of(2026, 6, 12),
            lastGenerated = LocalDate.of(2026, 6, 12),
            endDate = LocalDate.of(2026, 7, 1),
        )

        assertTrue(useCase(listOf(ended)).invoke().isEmpty())
    }

    @Test
    fun `an occurrence generated today is not announced as upcoming`() = runTest {
        // Charge generated today (9 Jul): the floor moves past it, and the next
        // one (9 Aug) is outside the window.
        val chargedToday = rule(startDate = LocalDate.of(2026, 7, 9), lastGenerated = LocalDate.of(2026, 7, 9))

        assertTrue(useCase(listOf(chargedToday)).invoke().isEmpty())
    }

    @Test
    fun `a due-today occurrence not yet generated is reported as zero days`() = runTest {
        // No generation has happened for today's charge (e.g. the reminder runs
        // standalone): report it as due today rather than staying silent.
        val dueToday = rule(startDate = LocalDate.of(2026, 6, 9), lastGenerated = LocalDate.of(2026, 6, 9))

        val renewals = useCase(listOf(dueToday)).invoke()

        assertEquals(0, renewals.single().daysUntil)
        assertEquals(today, renewals.single().dueDate)
    }

    @Test
    fun `variable-amount rules are reported without an amount`() = runTest {
        val variable = rule(
            startDate = LocalDate.of(2026, 6, 12),
            amount = null,
            isVariable = true,
            lastGenerated = LocalDate.of(2026, 6, 12),
        )

        val renewals = useCase(listOf(variable)).invoke()

        assertEquals(null, renewals.single().amount)
    }
}
