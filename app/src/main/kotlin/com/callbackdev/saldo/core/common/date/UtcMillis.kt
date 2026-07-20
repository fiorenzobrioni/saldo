package com.callbackdev.saldo.core.common.date

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The Material date pickers represent a picked day as its UTC-midnight epoch
 * millis. These two conversions keep that convention in one place so every
 * picker in the app (movements filter, stats period, movement editor,
 * recurrence editor) interprets a calendar day the same way, regardless of
 * the device timezone.
 */
fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Inverse of [toUtcMillis]: the calendar day a picker's UTC millis stand for. */
fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
