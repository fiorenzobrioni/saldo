package com.callbackdev.saldo.feature.recurring

import androidx.annotation.StringRes
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.TransactionType

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

/** Editor title, by create/edit mode and rule type. */
@StringRes
internal fun editorTitleRes(isNew: Boolean, type: TransactionType): Int {
    val isIncome = type == TransactionType.INCOME
    return when {
        isNew && isIncome -> R.string.income_editor_title_new
        isNew -> R.string.subscription_editor_title_new
        isIncome -> R.string.income_editor_title_edit
        else -> R.string.subscription_editor_title_edit
    }
}

/** Save button label of the rule editor, by rule type. */
@StringRes
internal fun editorSaveRes(type: TransactionType): Int =
    if (type == TransactionType.INCOME) R.string.income_editor_save else R.string.subscription_editor_save

/** Delete action label of the rule editor, by rule type. */
@StringRes
internal fun editorDeleteRes(type: TransactionType): Int =
    if (type == TransactionType.INCOME) {
        R.string.income_editor_delete
    } else {
        R.string.subscription_editor_delete
    }
