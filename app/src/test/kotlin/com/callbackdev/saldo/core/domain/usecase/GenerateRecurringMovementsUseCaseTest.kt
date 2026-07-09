package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurrenceMode
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency

class GenerateRecurringMovementsUseCaseTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val zone: ZoneId = ZoneId.of("Europe/Rome")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-09T09:00:00Z"), zone)
    private val today: LocalDate = LocalDate.of(2026, 7, 9)

    private val recurringRuleRepository = mockk<RecurringRuleRepository>()
    private val transactionRepository = mockk<TransactionRepository>()

    private val generatedMovements = mutableListOf<Transaction>()
    private val updatedRules = mutableListOf<RecurringRule>()

    private fun useCase(rules: List<RecurringRule>): GenerateRecurringMovementsUseCase {
        generatedMovements.clear()
        updatedRules.clear()
        coEvery { recurringRuleRepository.getRules() } returns rules
        coEvery { transactionRepository.upsert(any()) } answers {
            generatedMovements.add(firstArg())
            generatedMovements.size.toLong()
        }
        coEvery { recurringRuleRepository.upsert(any()) } answers {
            updatedRules.add(firstArg())
            firstArg<RecurringRule>().id
        }
        return GenerateRecurringMovementsUseCase(recurringRuleRepository, transactionRepository, clock)
    }

    private fun rule(
        id: Long = 1L,
        type: TransactionType = TransactionType.EXPENSE,
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        startDate: LocalDate,
        amount: BigDecimal? = BigDecimal("12.99"),
        lastGenerated: LocalDate? = null,
        endDate: LocalDate? = null,
        mode: RecurrenceMode = RecurrenceMode.AUTOMATIC,
        isVariable: Boolean = false,
        categoryId: Long? = 5L,
    ) = RecurringRule(
        id = id,
        name = "Netflix",
        type = type,
        currency = eur,
        accountId = 3L,
        frequency = frequency,
        startDate = startDate,
        amount = amount,
        categoryId = categoryId,
        dayOfReference = startDate.dayOfMonth,
        endDate = endDate,
        mode = mode,
        isVariableAmount = isVariable,
        lastGeneratedDate = lastGenerated,
    )

    private fun Transaction.localDate(): LocalDate = timestamp.atOffset(zoneOffset).toLocalDate()

    @Test
    fun `catches up every missed charge from the start date`() = runTest {
        val useCase = useCase(listOf(rule(startDate = LocalDate.of(2026, 5, 7))))

        val result = useCase(today)

        assertEquals(3, result.size)
        assertEquals(
            listOf(LocalDate.of(2026, 5, 7), LocalDate.of(2026, 6, 7), LocalDate.of(2026, 7, 7)),
            generatedMovements.map { it.localDate() },
        )
        val movement = generatedMovements.first()
        assertEquals(BigDecimal("-12.99"), movement.amount)
        assertEquals(eur, movement.currency)
        assertEquals(3L, movement.accountId)
        assertEquals(5L, movement.categoryId)
        assertEquals(1L, movement.recurringRuleId)
        assertEquals("Netflix", movement.description)
        assertTrue(generatedMovements.none { it.isPending })
        assertTrue(result.none { it.isPending })
        assertEquals(LocalDate.of(2026, 7, 7), updatedRules.single().lastGeneratedDate)
    }

    @Test
    fun `resumes from the day after the last generated date`() = runTest {
        val useCase = useCase(
            listOf(rule(startDate = LocalDate.of(2026, 1, 1), lastGenerated = LocalDate.of(2026, 5, 1))),
        )

        val result = useCase(today)

        assertEquals(
            listOf(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1)),
            generatedMovements.map { it.localDate() },
        )
        assertEquals(2, result.size)
    }

    @Test
    fun `is idempotent once caught up`() = runTest {
        val useCase = useCase(
            listOf(rule(startDate = LocalDate.of(2026, 1, 7), lastGenerated = LocalDate.of(2026, 7, 7))),
        )

        val result = useCase(today)

        assertEquals(0, result.size)
        assertEquals(emptyList<Transaction>(), generatedMovements)
        coVerify(exactly = 0) { recurringRuleRepository.upsert(any()) }
    }

    @Test
    fun `income rules generate a positive movement`() = runTest {
        val useCase = useCase(
            listOf(
                rule(
                    type = TransactionType.INCOME,
                    startDate = LocalDate.of(2026, 7, 1),
                    amount = BigDecimal("2000.00"),
                ),
            ),
        )

        useCase(today)

        assertEquals(BigDecimal("2000.00"), generatedMovements.single().amount)
    }

    @Test
    fun `confirm-mode rules generate pending movements with the fixed amount`() = runTest {
        val useCase = useCase(
            listOf(rule(startDate = LocalDate.of(2026, 7, 7), mode = RecurrenceMode.CONFIRM)),
        )

        val result = useCase(today)

        assertEquals(1, result.size)
        val movement = generatedMovements.single()
        assertTrue(movement.isPending)
        assertEquals(BigDecimal("-12.99"), movement.amount)
        assertTrue(result.single().isPending)
        assertEquals(BigDecimal("12.99"), result.single().amount)
    }

    @Test
    fun `variable-amount rules generate a pending movement with zero and no known amount`() = runTest {
        val useCase = useCase(
            listOf(rule(startDate = LocalDate.of(2026, 7, 7), amount = null, isVariable = true)),
        )

        val result = useCase(today)

        assertEquals(1, result.size)
        val movement = generatedMovements.single()
        assertTrue(movement.isPending)
        assertEquals(BigDecimal.ZERO.negate(), movement.amount)
        assertNull(result.single().amount)
    }

    @Test
    fun `skips automatic rules without an amount`() = runTest {
        val useCase = useCase(
            listOf(rule(startDate = LocalDate.of(2026, 6, 1), amount = null)),
        )

        val result = useCase(today)

        assertEquals(0, result.size)
        assertEquals(emptyList<Transaction>(), generatedMovements)
    }

    @Test
    fun `does not generate before the start date`() = runTest {
        val useCase = useCase(listOf(rule(startDate = LocalDate.of(2026, 8, 7))))

        val result = useCase(today)

        assertEquals(0, result.size)
    }

    @Test
    fun `stops at the end date`() = runTest {
        val useCase = useCase(
            listOf(
                rule(
                    startDate = LocalDate.of(2026, 1, 7),
                    endDate = LocalDate.of(2026, 3, 31),
                ),
            ),
        )

        useCase(today)

        assertEquals(
            listOf(LocalDate.of(2026, 1, 7), LocalDate.of(2026, 2, 7), LocalDate.of(2026, 3, 7)),
            generatedMovements.map { it.localDate() },
        )
    }
}
