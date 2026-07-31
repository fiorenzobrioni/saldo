package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.recurrence.CandidateOccurrence
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceAmountGroup
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceDescriptionGroup
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency

class DetectRecurrenceSuggestionsUseCaseTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val zone: ZoneId = ZoneId.of("Europe/Rome")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), zone)
    private val today = LocalDate.of(2026, 7, 31)

    private val repository = mockk<TransactionRepository>()
    private val useCase = DetectRecurrenceSuggestionsUseCase(repository, clock)

    private fun amountGroup(
        amountMinor: Long,
        categoryId: Long? = 7L,
        count: Int = 3,
    ) = RecurrenceAmountGroup(
        type = TransactionType.EXPENSE,
        accountId = 1L,
        categoryId = categoryId,
        currency = eur,
        amountMinor = amountMinor,
        count = count,
    )

    private fun descriptionGroup(key: String, count: Int = 3) = RecurrenceDescriptionGroup(
        type = TransactionType.EXPENSE,
        accountId = 1L,
        categoryId = 7L,
        currency = eur,
        descriptionKey = key,
        count = count,
    )

    private fun monthly(vararg days: Pair<Int, Int>, amount: Long = -1299L, description: String? = "Netflix") =
        days.map { (month, day) ->
            CandidateOccurrence(LocalDate.of(2026, month, day), amount, description)
        }

    private fun noDescriptionGroups() {
        coEvery { repository.recurrenceDescriptionGroups(any(), any(), any()) } returns emptyList()
    }

    private fun noAmountGroups() {
        coEvery { repository.recurrenceAmountGroups(any(), any(), any()) } returns emptyList()
    }

    @Test
    fun `detects a subscription from the amount path within the declared window`() = runTest {
        noDescriptionGroups()
        val group = amountGroup(1299L)
        coEvery { repository.recurrenceAmountGroups(any(), any(), any()) } returns listOf(group)
        coEvery { repository.recurrenceAmountGroupOccurrences(group, any(), any()) } returns
            monthly(5 to 15, 6 to 15, 7 to 15)

        val result = useCase(today)

        assertEquals(1, result.suggestions.size)
        assertFalse(result.truncated)
        val suggestion = result.suggestions.single()
        assertEquals("Netflix", suggestion.name)
        assertEquals(1299L, suggestion.amountMinor)
        assertEquals(RecurrenceFrequency.MONTHLY, suggestion.frequency)
        // The window is the declared 12 months, anchored to the scan day.
        val expectedSince = today
            .minusMonths(DetectRecurrenceSuggestionsUseCase.WINDOW_MONTHS)
            .atStartOfDay(zone)
            .toInstant()
        coVerify { repository.recurrenceAmountGroups(expectedSince, any(), any()) }
    }

    @Test
    fun `description groups that normalize to the same series are merged before detection`() = runTest {
        noAmountGroups()
        // ASCII LOWER cannot fold the accent, so SQL returns two groups; the
        // real normalization merges them into one three-occurrence series.
        val accented = descriptionGroup("enel énergia", count = 2)
        val plain = descriptionGroup("enel energia", count = 1)
        coEvery { repository.recurrenceDescriptionGroups(any(), any(), any()) } returns
            listOf(accented, plain)
        coEvery { repository.recurrenceDescriptionGroupOccurrences(accented, any(), any()) } returns
            monthly(5 to 10, 7 to 10, amount = -4500L, description = "Enel Énergia")
        coEvery { repository.recurrenceDescriptionGroupOccurrences(plain, any(), any()) } returns
            monthly(6 to 10, amount = -4300L, description = "Enel Energia")

        val result = useCase(today)

        assertEquals(1, result.suggestions.size)
        assertEquals(3, result.suggestions.single().occurrenceCount)
        assertEquals("Enel Énergia", result.suggestions.single().name)
    }

    @Test
    fun `the amount-path duplicate of a description hit is dropped`() = runTest {
        val descGroup = descriptionGroup("netflix")
        coEvery { repository.recurrenceDescriptionGroups(any(), any(), any()) } returns listOf(descGroup)
        coEvery { repository.recurrenceDescriptionGroupOccurrences(descGroup, any(), any()) } returns
            monthly(5 to 15, 6 to 15, 7 to 15)
        val sameSeries = amountGroup(1299L)
        coEvery { repository.recurrenceAmountGroups(any(), any(), any()) } returns listOf(sameSeries)
        coEvery { repository.recurrenceAmountGroupOccurrences(sameSeries, any(), any()) } returns
            monthly(5 to 15, 6 to 15, 7 to 15)

        val result = useCase(today)

        assertEquals(1, result.suggestions.size)
    }

    @Test
    fun `hitting the candidate-group cap declares a truncated result`() = runTest {
        noDescriptionGroups()
        val groups = (1..DetectRecurrenceSuggestionsUseCase.MAX_CANDIDATE_GROUPS + 1)
            .map { amountGroup(amountMinor = it * 100L, categoryId = it.toLong()) }
        coEvery { repository.recurrenceAmountGroups(any(), any(), any()) } returns groups
        coEvery { repository.recurrenceAmountGroupOccurrences(any(), any(), any()) } returns emptyList()

        val result = useCase(today)

        assertTrue(result.truncated)
        assertTrue(result.suggestions.isEmpty())
        // Only the capped groups are examined: the probe row is never fetched.
        coVerify(exactly = DetectRecurrenceSuggestionsUseCase.MAX_CANDIDATE_GROUPS) {
            repository.recurrenceAmountGroupOccurrences(any(), any(), any())
        }
    }

    @Test
    fun `suggestions beyond the cap are cut, declared and ranked by occurrences`() = runTest {
        noDescriptionGroups()
        val groups = (1..DetectRecurrenceSuggestionsUseCase.MAX_SUGGESTIONS + 1)
            .map { amountGroup(amountMinor = it * 1000L, categoryId = it.toLong()) }
        coEvery { repository.recurrenceAmountGroups(any(), any(), any()) } returns groups
        groups.forEachIndexed { index, group ->
            // The first group gets one extra occurrence, so it must rank first.
            val days = if (index == 0) {
                monthly(4 to 15, 5 to 15, 6 to 15, 7 to 15, amount = -group.amountMinor)
            } else {
                monthly(5 to 15, 6 to 15, 7 to 15, amount = -group.amountMinor)
            }
            coEvery { repository.recurrenceAmountGroupOccurrences(group, any(), any()) } returns days
        }

        val result = useCase(today)

        assertTrue(result.truncated)
        assertEquals(DetectRecurrenceSuggestionsUseCase.MAX_SUGGESTIONS, result.suggestions.size)
        assertEquals(4, result.suggestions.first().occurrenceCount)
    }
}
