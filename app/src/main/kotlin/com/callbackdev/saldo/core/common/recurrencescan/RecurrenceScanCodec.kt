package com.callbackdev.saldo.core.common.recurrencescan

import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceScanResult
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceSuggestion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.Currency

/** The persisted outcome of the last explicit scan, with the day it ran (ADR 43). */
data class RecurrenceScanSnapshot(
    val scannedOn: LocalDate,
    val result: RecurrenceScanResult,
)

/**
 * JSON codec for the persisted scan snapshot, kept apart from the domain
 * model so the stored shape can only change on purpose (same discipline as
 * the backup schema). Decoding never throws: any unreadable value degrades
 * to null, which the UI reads as "never scanned".
 */
object RecurrenceScanCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(snapshot: RecurrenceScanSnapshot): String =
        json.encodeToString(StoredScan.serializer(), snapshot.toStored())

    fun decode(raw: String): RecurrenceScanSnapshot? = runCatching {
        json.decodeFromString(StoredScan.serializer(), raw).toSnapshot()
    }.getOrNull()

    private fun RecurrenceScanSnapshot.toStored(): StoredScan = StoredScan(
        scannedOnEpochDay = scannedOn.toEpochDay(),
        truncated = result.truncated,
        suggestions = result.suggestions.map { suggestion ->
            StoredSuggestion(
                key = suggestion.key,
                type = suggestion.type.name,
                name = suggestion.name,
                amountMinor = suggestion.amountMinor,
                isVariableAmount = suggestion.isVariableAmount,
                currencyCode = suggestion.currency.currencyCode,
                frequency = suggestion.frequency.name,
                accountId = suggestion.accountId,
                categoryId = suggestion.categoryId,
                occurrenceCount = suggestion.occurrenceCount,
                lastOccurrenceEpochDay = suggestion.lastOccurrence.toEpochDay(),
                nextOccurrenceEpochDay = suggestion.nextOccurrence.toEpochDay(),
                dayOfReference = suggestion.dayOfReference,
            )
        },
    )

    private fun StoredScan.toSnapshot(): RecurrenceScanSnapshot = RecurrenceScanSnapshot(
        scannedOn = LocalDate.ofEpochDay(scannedOnEpochDay),
        result = RecurrenceScanResult(
            suggestions = suggestions.map { stored ->
                RecurrenceSuggestion(
                    key = stored.key,
                    type = TransactionType.valueOf(stored.type),
                    name = stored.name,
                    amountMinor = stored.amountMinor,
                    isVariableAmount = stored.isVariableAmount,
                    currency = Currency.getInstance(stored.currencyCode),
                    frequency = RecurrenceFrequency.valueOf(stored.frequency),
                    accountId = stored.accountId,
                    categoryId = stored.categoryId,
                    occurrenceCount = stored.occurrenceCount,
                    lastOccurrence = LocalDate.ofEpochDay(stored.lastOccurrenceEpochDay),
                    nextOccurrence = LocalDate.ofEpochDay(stored.nextOccurrenceEpochDay),
                    dayOfReference = stored.dayOfReference,
                )
            },
            truncated = truncated,
        ),
    )
}

@Serializable
private data class StoredScan(
    val scannedOnEpochDay: Long,
    val truncated: Boolean,
    val suggestions: List<StoredSuggestion>,
)

@Serializable
private data class StoredSuggestion(
    val key: String,
    val type: String,
    val name: String?,
    val amountMinor: Long,
    val isVariableAmount: Boolean,
    val currencyCode: String,
    val frequency: String,
    val accountId: Long,
    val categoryId: Long?,
    val occurrenceCount: Int,
    val lastOccurrenceEpochDay: Long,
    val nextOccurrenceEpochDay: Long,
    val dayOfReference: Int,
)
