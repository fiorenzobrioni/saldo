package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurrenceMode
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRunner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.async
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

class GenerateRecurringMovementsUseCaseTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val zone: ZoneId = ZoneId.of("Europe/Rome")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-09T09:00:00Z"), zone)
    private val today: LocalDate = LocalDate.of(2026, 7, 9)

    private val recurringRuleRepository = mockk<RecurringRuleRepository>()
    private val transactionRepository = mockk<TransactionRepository>()

    private val generatedMovements = mutableListOf<Transaction>()
    private val advancedWatermarks = mutableListOf<LocalDate>()

    /** Pass-through runner that records whether writes happen inside a transaction. */
    private class RecordingTransactionRunner : TransactionRunner {
        var inTransaction = false
            private set

        override suspend fun <T> inTransaction(block: suspend () -> T): T {
            inTransaction = true
            return try {
                block()
            } finally {
                inTransaction = false
            }
        }
    }

    private val transactionRunner = RecordingTransactionRunner()

    private fun useCase(
        rules: List<RecurringRule>,
        alreadyGenerated: Set<LocalDate> = emptySet(),
    ): GenerateRecurringMovementsUseCase {
        generatedMovements.clear()
        advancedWatermarks.clear()
        coEvery { recurringRuleRepository.getRules() } returns rules
        coEvery { transactionRepository.insertIfAbsent(any()) } answers {
            check(transactionRunner.inTransaction) { "movement inserted outside a transaction" }
            val movement = firstArg<Transaction>()
            if (movement.recurringOccurrenceDate in alreadyGenerated) {
                -1L
            } else {
                generatedMovements.add(movement)
                generatedMovements.size.toLong()
            }
        }
        coEvery { recurringRuleRepository.updateLastGeneratedDate(any(), any()) } answers {
            check(transactionRunner.inTransaction) { "watermark advanced outside a transaction" }
            advancedWatermarks.add(secondArg())
        }
        return GenerateRecurringMovementsUseCase(
            recurringRuleRepository,
            transactionRepository,
            transactionRunner,
            clock,
        )
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
        transferAccountId: Long? = null,
        transferAmount: BigDecimal? = null,
        transferCurrency: Currency? = null,
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
        transferAccountId = transferAccountId,
        transferAmount = transferAmount,
        transferCurrency = transferCurrency,
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
        assertEquals(LocalDate.of(2026, 7, 7), advancedWatermarks.single())
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
        coVerify(exactly = 0) { recurringRuleRepository.updateLastGeneratedDate(any(), any()) }
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
    fun `same-currency transfer materializes a confirmed single-record transfer`() = runTest {
        val useCase = useCase(
            listOf(
                rule(
                    type = TransactionType.TRANSFER,
                    startDate = LocalDate.of(2026, 7, 7),
                    amount = BigDecimal("100.00"),
                    transferAccountId = 9L,
                    transferAmount = BigDecimal("100.00"),
                    transferCurrency = eur,
                    categoryId = null,
                ),
            ),
        )

        val result = useCase(today)

        val movement = generatedMovements.single()
        assertFalse(movement.isPending)
        // Source leg leaves the account (negative); destination mirrors it exactly.
        assertEquals(BigDecimal("-100.00"), movement.amount)
        assertEquals(3L, movement.accountId)
        assertEquals(9L, movement.transferAccountId)
        assertEquals(BigDecimal("100.00"), movement.transferAmount)
        assertEquals(eur, movement.transferCurrency)
        assertNull(movement.categoryId)
        assertFalse(result.single().isPending)
    }

    @Test
    fun `cross-currency transfer generates a pending movement awaiting the received amount`() = runTest {
        val usd = Currency.getInstance("USD")
        val useCase = useCase(
            listOf(
                rule(
                    type = TransactionType.TRANSFER,
                    startDate = LocalDate.of(2026, 7, 7),
                    amount = BigDecimal("100.00"),
                    mode = RecurrenceMode.CONFIRM,
                    transferAccountId = 9L,
                    // Cross-currency rules leave the destination amount null.
                    transferAmount = null,
                    transferCurrency = usd,
                    categoryId = null,
                ),
            ),
        )

        val result = useCase(today)

        val movement = generatedMovements.single()
        assertTrue(movement.isPending)
        // Source leg is fixed and known; the received amount is entered at confirmation.
        assertEquals(BigDecimal("-100.00"), movement.amount)
        assertEquals(9L, movement.transferAccountId)
        assertNull(movement.transferAmount)
        assertEquals(usd, movement.transferCurrency)
        assertTrue(result.single().isPending)
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
    fun `stamps each generated movement with its occurrence date`() = runTest {
        val useCase = useCase(listOf(rule(startDate = LocalDate.of(2026, 5, 7))))

        useCase(today)

        assertEquals(
            listOf(LocalDate.of(2026, 5, 7), LocalDate.of(2026, 6, 7), LocalDate.of(2026, 7, 7)),
            generatedMovements.map { it.recurringOccurrenceDate },
        )
    }

    @Test
    fun `skips occurrences already persisted and still advances the watermark`() = runTest {
        // Simulates a stale watermark (interrupted previous run): the first two
        // occurrences already exist in the database, so the unique-index backstop
        // rejects them and only the third is created and notified.
        val useCase = useCase(
            rules = listOf(rule(startDate = LocalDate.of(2026, 5, 7))),
            alreadyGenerated = setOf(LocalDate.of(2026, 5, 7), LocalDate.of(2026, 6, 7)),
        )

        val result = useCase(today)

        assertEquals(listOf(LocalDate.of(2026, 7, 7)), result.map { it.date })
        assertEquals(listOf(LocalDate.of(2026, 7, 7)), generatedMovements.map { it.localDate() })
        assertEquals(LocalDate.of(2026, 7, 7), advancedWatermarks.single())
    }

    @Test
    fun `advances the watermark even when every occurrence was already persisted`() = runTest {
        val useCase = useCase(
            rules = listOf(rule(startDate = LocalDate.of(2026, 6, 7))),
            alreadyGenerated = setOf(LocalDate.of(2026, 6, 7), LocalDate.of(2026, 7, 7)),
        )

        val result = useCase(today)

        assertTrue(result.isEmpty())
        assertTrue(generatedMovements.isEmpty())
        assertEquals(LocalDate.of(2026, 7, 7), advancedWatermarks.single())
    }

    @Test
    fun `concurrent runs are serialized and generate once`() = runTest {
        val startDate = LocalDate.of(2026, 5, 7)
        var watermark: LocalDate? = null
        coEvery { recurringRuleRepository.getRules() } answers {
            listOf(rule(startDate = startDate, lastGenerated = watermark))
        }
        coEvery { recurringRuleRepository.updateLastGeneratedDate(any(), any()) } answers {
            watermark = secondArg()
        }
        generatedMovements.clear()
        coEvery { transactionRepository.insertIfAbsent(any()) } answers {
            generatedMovements.add(firstArg())
            generatedMovements.size.toLong()
        }
        val useCase = GenerateRecurringMovementsUseCase(
            recurringRuleRepository,
            transactionRepository,
            transactionRunner,
            clock,
        )

        val first = async { useCase(today) }
        val second = async { useCase(today) }
        val results = listOf(first.await(), second.await())

        // One run does all the work, the other reads the advanced watermark and is a no-op.
        assertEquals(3, generatedMovements.size)
        assertEquals(3, results.sumOf { it.size })
        assertFalse(results.all { it.isNotEmpty() })
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
