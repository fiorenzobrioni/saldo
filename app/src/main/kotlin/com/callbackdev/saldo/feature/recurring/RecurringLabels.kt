package com.callbackdev.saldo.feature.recurring

import androidx.annotation.StringRes
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency

/** User-facing label for a [RecurrenceFrequency]. */
@StringRes
fun RecurrenceFrequency.labelRes(): Int = when (this) {
    RecurrenceFrequency.DAILY -> R.string.recurrence_daily
    RecurrenceFrequency.WEEKLY -> R.string.recurrence_weekly
    RecurrenceFrequency.MONTHLY -> R.string.recurrence_monthly
    RecurrenceFrequency.BIMONTHLY -> R.string.recurrence_bimonthly
    RecurrenceFrequency.QUARTERLY -> R.string.recurrence_quarterly
    RecurrenceFrequency.SEMIANNUAL -> R.string.recurrence_semiannual
    RecurrenceFrequency.ANNUAL -> R.string.recurrence_annual
}
