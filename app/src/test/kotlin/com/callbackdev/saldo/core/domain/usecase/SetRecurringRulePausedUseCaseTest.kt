package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency

class SetRecurringRulePausedUseCaseTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-09T09:00:00Z"), ZoneId.of("Europe/Rome"))
    private val today: LocalDate = LocalDate.of(2026, 7, 9)

    private val repository = mockk<RecurringRuleRepository>()
    private val saved = slot<RecurringRule>()
    private val useCase = SetRecurringRulePausedUseCase(repository, clock)

    private fun rule(isPaused: Boolean, lastGenerated: LocalDate?) = RecurringRule(
        id = 1L,
        name = "Netflix",
        type = TransactionType.EXPENSE,
        currency = Currency.getInstance("EUR"),
        accountId = 1L,
        frequency = RecurrenceFrequency.MONTHLY,
        startDate = LocalDate.of(2026, 1, 12),
        amount = BigDecimal("12.99"),
        dayOfReference = 12,
        lastGeneratedDate = lastGenerated,
        isPaused = isPaused,
    )

    @Test
    fun `pausing flips the flag and leaves the watermark alone`() = runTest {
        coEvery { repository.upsert(capture(saved)) } returns 1L

        useCase(rule(isPaused = false, lastGenerated = LocalDate.of(2026, 6, 12)), paused = true)

        assertTrue(saved.captured.isPaused)
        assertEquals(LocalDate.of(2026, 6, 12), saved.captured.lastGeneratedDate)
    }

    @Test
    fun `resuming moves a stale watermark to yesterday so the pause is not back-filled`() = runTest {
        coEvery { repository.upsert(capture(saved)) } returns 1L

        useCase(rule(isPaused = true, lastGenerated = LocalDate.of(2026, 3, 12)), paused = false)

        assertFalse(saved.captured.isPaused)
        // April, May and June were skipped on purpose; the next run starts from today.
        assertEquals(today.minusDays(1), saved.captured.lastGeneratedDate)
    }

    @Test
    fun `resuming keeps a watermark that already covers today`() = runTest {
        coEvery { repository.upsert(capture(saved)) } returns 1L

        useCase(rule(isPaused = true, lastGenerated = today), paused = false)

        assertEquals(today, saved.captured.lastGeneratedDate)
    }

    @Test
    fun `a no-op request writes nothing`() = runTest {
        useCase(rule(isPaused = false, lastGenerated = null), paused = false)

        coVerify(exactly = 0) { repository.upsert(any()) }
    }
}
