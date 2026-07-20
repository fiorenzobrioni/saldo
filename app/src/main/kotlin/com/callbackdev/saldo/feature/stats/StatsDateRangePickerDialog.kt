package com.callbackdev.saldo.feature.stats

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.date.toUtcLocalDate
import com.callbackdev.saldo.core.common.date.toUtcMillis
import java.time.LocalDate

/**
 * Material date range picker for the custom stats period. Both bounds are
 * required: stats queries always run over a closed interval (unlike the
 * movements filter, whose custom period may be open-ended).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsDateRangePickerDialog(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart?.toUtcMillis(),
        initialSelectedEndDateMillis = initialEnd?.toUtcMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = state.selectedStartDateMillis ?: return@TextButton
                    val end = state.selectedEndDateMillis ?: return@TextButton
                    onConfirm(start.toUtcLocalDate(), end.toUtcLocalDate())
                },
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
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
        DateRangePicker(
            state = state,
            showModeToggle = false,
            modifier = Modifier.heightIn(max = RANGE_PICKER_MAX_HEIGHT),
        )
    }
}

private val RANGE_PICKER_MAX_HEIGHT = 460.dp
