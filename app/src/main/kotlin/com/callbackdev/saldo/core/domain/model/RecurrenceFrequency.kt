package com.callbackdev.saldo.core.domain.model

/**
 * How often a [RecurringRule] fires. The generation engine (Phase 6) turns a
 * frequency plus a reference day into concrete dates.
 */
enum class RecurrenceFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    BIMONTHLY,
    QUARTERLY,
    SEMIANNUAL,
    ANNUAL,
}
