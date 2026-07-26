package com.callbackdev.saldo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.date.toUtcLocalDate
import com.callbackdev.saldo.core.common.date.toUtcMillis
import java.time.LocalDate

/**
 * The app's single date picker dialog, locked to calendar mode (the
 * input/calendar toggle animates slowly and janky, and typing a date adds
 * little). [minDate] floors the selectable range (e.g. an end date after the
 * start). [showQuickDates] adds "Today"/"Yesterday" chips above the calendar
 * that confirm immediately, covering the most frequent date corrections;
 * a chip is hidden when its date falls below [minDate].
 *
 * [timeRow] is an optional row drawn under the quick dates: the movement
 * editor puts the time of day there, so it stays next to the date it belongs
 * to instead of spending a chip of its own on the form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaldoDatePickerDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    minDate: LocalDate? = null,
    showQuickDates: Boolean = false,
    timeRow: @Composable (() -> Unit)? = null,
) {
    val selectableDates = remember(minDate) {
        if (minDate == null) {
            DatePickerDefaults.AllDates
        } else {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !utcTimeMillis.toUtcLocalDate().isBefore(minDate)
            }
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toUtcMillis(),
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(millis.toUtcLocalDate())
                    }
                },
                enabled = state.selectedDateMillis != null,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    ) {
        if (showQuickDates) {
            val today = LocalDate.now()
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
            ) {
                if (minDate == null || !today.isBefore(minDate)) {
                    SuggestionChip(
                        onClick = { onConfirm(today) },
                        label = { Text(stringResource(R.string.date_today)) },
                    )
                }
                val yesterday = today.minusDays(1)
                if (minDate == null || !yesterday.isBefore(minDate)) {
                    SuggestionChip(
                        onClick = { onConfirm(yesterday) },
                        label = { Text(stringResource(R.string.date_yesterday)) },
                    )
                }
            }
        }
        timeRow?.invoke()
        DatePicker(state = state, showModeToggle = false)
    }
}
