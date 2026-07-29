package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.common.prefs.RenewalReminderPreferences
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
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
import java.time.ZoneOffset
import java.util.Currency

class CheckDueMovementRemindersUseCaseTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val zone: ZoneId = ZoneId.of("Europe/Rome")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-09T09:00:00Z"), zone)
    private val today: LocalDate = LocalDate.of(2026, 7, 9)

    private val transactionRepository = mockk<TransactionRepository>()
    private val userPreferences = mockk<UserPreferencesRepository>()

    private val watermarkWrites = mutableListOf<Pair<Long, LocalDate>>()

    private fun movement(
        id: Long = 1L,
        date: LocalDate,
        description: String? = "Bollo auto",
        amount: String = "-212.00",
    ) = Transaction(
        id = id,
        type = TransactionType.EXPENSE,
        amount = BigDecimal(amount),
        currency = eur,
        accountId = 1L,
        timestamp = date.atTime(12, 0).atZone(zone).toInstant(),
        zoneOffset = ZoneOffset.ofHours(2),
        description = description,
        hasReminder = true,
    )

    private fun useCase(
        due: List<Transaction>,
        prefs: RenewalReminderPreferences = RenewalReminderPreferences(enabled = true, leadDays = 3),
    ): CheckDueMovementRemindersUseCase {
        watermarkWrites.clear()
        coEvery { transactionRepository.getDueReminders(any(), any()) } returns due
        coEvery { transactionRepository.updateReminderWatermark(any(), any()) } answers {
            watermarkWrites.add(firstArg<Long>() to secondArg())
        }
        every { userPreferences.renewalReminderPreferences } returns flowOf(prefs)
        return CheckDueMovementRemindersUseCase(transactionRepository, userPreferences, clock)
    }

    @Test
    fun `a movement inside the window is reported with the days left`() = runTest {
        val useCase = useCase(listOf(movement(date = LocalDate.of(2026, 7, 11))))

        val reminders = useCase(today)

        assertEquals(1, reminders.size)
        assertEquals("Bollo auto", reminders.single().title)
        assertEquals(2, reminders.single().daysUntil)
        assertEquals(LocalDate.of(2026, 7, 11), reminders.single().dueDate)
    }

    @Test
    fun `a movement due today reports zero days, not a negative count`() = runTest {
        val useCase = useCase(listOf(movement(date = today)))

        assertEquals(0, useCase(today).single().daysUntil)
    }

    @Test
    fun `the window asked of the repository spans today through the lead time`() = runTest {
        val useCase = useCase(emptyList(), RenewalReminderPreferences(enabled = true, leadDays = 5))

        useCase(today)

        coVerify { transactionRepository.getDueReminders(today, LocalDate.of(2026, 7, 14)) }
    }

    @Test
    fun `reporting a reminder advances its watermark to the movement's date`() = runTest {
        // The watermark is what keeps a daily run inside the window from
        // notifying about the same date again.
        val useCase = useCase(listOf(movement(id = 7L, date = LocalDate.of(2026, 7, 10))))

        useCase(today)

        assertEquals(listOf(7L to LocalDate.of(2026, 7, 10)), watermarkWrites)
    }

    @Test
    fun `nothing is reported and nothing is queried while reminders are off`() = runTest {
        val useCase = useCase(
            listOf(movement(date = LocalDate.of(2026, 7, 10))),
            RenewalReminderPreferences(enabled = false, leadDays = 3),
        )

        assertTrue(useCase(today).isEmpty())
        coVerify(exactly = 0) { transactionRepository.getDueReminders(any(), any()) }
        assertTrue(watermarkWrites.isEmpty())
    }

    @Test
    fun `a movement without a description still yields a reminder for the notifier to word`() = runTest {
        val useCase = useCase(listOf(movement(date = LocalDate.of(2026, 7, 10), description = null)))

        assertEquals("", useCase(today).single().title)
    }
}
