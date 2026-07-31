package com.callbackdev.saldo.core.common.recurrencescan

import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceScanResult
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceSuggestion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Currency

class RecurrenceScanCodecTest {

    private val snapshot = RecurrenceScanSnapshot(
        scannedOn = LocalDate.of(2026, 7, 31),
        result = RecurrenceScanResult(
            suggestions = listOf(
                RecurrenceSuggestion(
                    key = "amount:EXPENSE:1:7:EUR:1299",
                    type = TransactionType.EXPENSE,
                    name = "Netflix",
                    amountMinor = 1299L,
                    isVariableAmount = false,
                    currency = Currency.getInstance("EUR"),
                    frequency = RecurrenceFrequency.MONTHLY,
                    accountId = 1L,
                    categoryId = 7L,
                    occurrenceCount = 4,
                    lastOccurrence = LocalDate.of(2026, 7, 15),
                    nextOccurrence = LocalDate.of(2026, 8, 15),
                    dayOfReference = 15,
                ),
                RecurrenceSuggestion(
                    key = "desc:INCOME:2:none:EUR:stipendio",
                    type = TransactionType.INCOME,
                    name = null,
                    amountMinor = 180000L,
                    isVariableAmount = true,
                    currency = Currency.getInstance("EUR"),
                    frequency = RecurrenceFrequency.MONTHLY,
                    accountId = 2L,
                    categoryId = null,
                    occurrenceCount = 6,
                    lastOccurrence = LocalDate.of(2026, 7, 27),
                    nextOccurrence = LocalDate.of(2026, 8, 27),
                    dayOfReference = 27,
                ),
            ),
            truncated = true,
        ),
    )

    @Test
    fun `snapshot round-trips through the codec`() {
        val decoded = RecurrenceScanCodec.decode(RecurrenceScanCodec.encode(snapshot))
        assertEquals(snapshot, decoded)
    }

    @Test
    fun `unreadable content degrades to null, never throws`() {
        assertNull(RecurrenceScanCodec.decode("not json at all"))
        assertNull(RecurrenceScanCodec.decode("{}"))
        assertNull(RecurrenceScanCodec.decode(""))
        // A stored enum name that no longer exists degrades the whole snapshot.
        val broken = RecurrenceScanCodec.encode(snapshot).replace("MONTHLY", "FORTNIGHTLY")
        assertNull(RecurrenceScanCodec.decode(broken))
    }
}
