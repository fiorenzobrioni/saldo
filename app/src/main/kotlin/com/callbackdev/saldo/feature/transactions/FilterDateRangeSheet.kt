package com.callbackdev.saldo.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.date.toUtcLocalDate
import com.callbackdev.saldo.core.common.date.toUtcMillis
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How the custom period restricts the list: a closed range or a single open bound. */
private enum class DateFilterMode { RANGE, FROM, UNTIL }

/**
 * The custom-period editor of the movements filter: a full-height sheet with a
 * mode selector (closed range, "from" only, "until" only), a live summary of
 * the selection, and the matching Material calendar. Applies via [onApply]
 * with a null bound on the open side; [onClear] (shown only when a custom
 * period is already active) drops the period back to "all".
 *
 * The applied filter seeds all three modes on entry; switching modes carries
 * the selection over where the target state can represent it (a range state
 * cannot hold an end without a start). The pickers work in UTC-midnight
 * millis, converted straight to [LocalDate] so the picked calendar day is
 * preserved regardless of the device timezone (same convention as the
 * editor's picker).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterDateRangeSheet(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    today: LocalDate,
    showClear: Boolean,
    onApply: (LocalDate?, LocalDate?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember {
        mutableStateOf(
            when {
                initialStart != null && initialEnd == null -> DateFilterMode.FROM
                initialStart == null && initialEnd != null -> DateFilterMode.UNTIL
                else -> DateFilterMode.RANGE
            },
        )
    }
    val rangeState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart?.toUtcMillis(),
        // The range state rejects an end without a start ("until" only filter):
        // seed the end bound only when the start exists.
        initialSelectedEndDateMillis = if (initialStart != null) initialEnd?.toUtcMillis() else null,
    )
    val fromState = rememberDatePickerState(initialSelectedDateMillis = initialStart?.toUtcMillis())
    val untilState = rememberDatePickerState(initialSelectedDateMillis = initialEnd?.toUtcMillis())

    val (startMillis, endMillis) = selectedBounds(mode, rangeState, fromState, untilState)
    val start = startMillis?.toUtcLocalDate()
    val end = endMillis?.toUtcLocalDate()
    val applyEnabled = when (mode) {
        DateFilterMode.RANGE -> start != null && end != null
        DateFilterMode.FROM -> start != null
        DateFilterMode.UNTIL -> end != null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Fully expanded from the start: the calendar plus the action buttons
        // do not fit the default half-height state.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        // Scrollable as a whole so short screens (landscape) reach the buttons
        // by scrolling instead of squeezing the calendar. The range calendar
        // stays a bounded nested scrollable; the single-date calendar has a
        // fixed height, so neither fights the outer scroll for space.
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            // Horizontal padding wraps the text and button sections only: the
            // Material calendar specifies its own 360dp-wide container, which
            // padded constraints would clip on common 360dp-wide screens.
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = stringResource(R.string.filter_date_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.filter_date_sheet_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                ModeSelector(
                    mode = mode,
                    onModeChange = { target ->
                        if (target != mode) {
                            carrySelection(mode, target, rangeState, fromState, untilState)
                            mode = target
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                SelectionSummary(mode = mode, start = start, end = end, today = today)
            }
            Spacer(Modifier.height(4.dp))
            when (mode) {
                DateFilterMode.RANGE -> DateRangePicker(
                    state = rangeState,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                    modifier = Modifier.heightIn(max = RANGE_PICKER_MAX_HEIGHT),
                )

                DateFilterMode.FROM -> SingleDayPicker(fromState)
                DateFilterMode.UNTIL -> SingleDayPicker(untilState)
            }
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Button(
                    onClick = {
                        when (mode) {
                            DateFilterMode.RANGE -> onApply(start, end)
                            DateFilterMode.FROM -> onApply(start, null)
                            DateFilterMode.UNTIL -> onApply(null, end)
                        }
                    },
                    enabled = applyEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.filter_apply))
                }
                SecondaryActions(showClear = showClear, onClear = onClear, onDismiss = onDismiss)
            }
        }
    }
}

/** The two selected bounds in picker millis, as the active mode defines them. */
@OptIn(ExperimentalMaterial3Api::class)
private fun selectedBounds(
    mode: DateFilterMode,
    rangeState: DateRangePickerState,
    fromState: DatePickerState,
    untilState: DatePickerState,
): Pair<Long?, Long?> = when (mode) {
    DateFilterMode.RANGE -> rangeState.selectedStartDateMillis to rangeState.selectedEndDateMillis
    DateFilterMode.FROM -> fromState.selectedDateMillis to null
    DateFilterMode.UNTIL -> null to untilState.selectedDateMillis
}

/**
 * Carries the selection of the mode being left into the mode being entered,
 * where the target state can represent it: the range's start seeds "from",
 * the range's end seeds "until", and a "from" date seeds the range's start
 * (keeping a compatible end). An "until" date cannot seed the range state,
 * which rejects an end without a start; that mode keeps its own selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
private fun carrySelection(
    from: DateFilterMode,
    to: DateFilterMode,
    rangeState: DateRangePickerState,
    fromState: DatePickerState,
    untilState: DatePickerState,
) {
    when (to) {
        DateFilterMode.RANGE -> {
            val start = if (from == DateFilterMode.FROM) fromState.selectedDateMillis else null
            if (start != null) {
                val end = rangeState.selectedEndDateMillis?.takeIf { it >= start }
                rangeState.setSelection(start, end)
                rangeState.displayedMonthMillis = start
            }
        }

        DateFilterMode.FROM -> {
            val start = if (from == DateFilterMode.RANGE) rangeState.selectedStartDateMillis else null
            if (start != null) {
                fromState.selectedDateMillis = start
                fromState.displayedMonthMillis = start
            }
        }

        DateFilterMode.UNTIL -> {
            val end = if (from == DateFilterMode.RANGE) rangeState.selectedEndDateMillis else null
            if (end != null) {
                untilState.selectedDateMillis = end
                untilState.displayedMonthMillis = end
            }
        }
    }
}

@Composable
private fun ModeSelector(
    mode: DateFilterMode,
    onModeChange: (DateFilterMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        DateFilterMode.RANGE to R.string.filter_date_mode_range,
        DateFilterMode.FROM to R.string.filter_date_mode_from,
        DateFilterMode.UNTIL to R.string.filter_date_mode_until,
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, labelRes) ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onModeChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
}

/**
 * Live restatement of the current selection: what the filter will do in words,
 * plus a support line (day count, the missing bound, or the open side).
 */
@Composable
private fun SelectionSummary(
    mode: DateFilterMode,
    start: LocalDate?,
    end: LocalDate?,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = summaryTitle(mode, start, end, today),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                summarySupport(mode, start, end)?.let { support ->
                    Text(
                        text = support,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun summaryTitle(
    mode: DateFilterMode,
    start: LocalDate?,
    end: LocalDate?,
    today: LocalDate,
): String = when (mode) {
    // A half-picked range reads as incomplete ("5 Jul – …"), not as an applied
    // open period: RANGE can only apply with both bounds.
    DateFilterMode.RANGE ->
        if (start != null && end == null) {
            stringResource(R.string.filter_date_range_partial, shortDayLabel(start, today))
        } else {
            periodLabel(start, end, today) ?: stringResource(R.string.filter_date_pick_start)
        }

    DateFilterMode.FROM ->
        periodLabel(start, null, today) ?: stringResource(R.string.filter_date_pick_start)

    DateFilterMode.UNTIL ->
        periodLabel(null, end, today) ?: stringResource(R.string.filter_date_pick_end)
}

@Composable
private fun summarySupport(
    mode: DateFilterMode,
    start: LocalDate?,
    end: LocalDate?,
): String? = when (mode) {
    DateFilterMode.RANGE -> when {
        start != null && end != null -> {
            val days = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
            pluralStringResource(R.plurals.filter_date_day_count, days, days)
        }

        start != null -> stringResource(R.string.filter_date_pick_end)
        else -> null
    }

    DateFilterMode.FROM -> if (start != null) stringResource(R.string.filter_date_no_end) else null
    DateFilterMode.UNTIL -> if (end != null) stringResource(R.string.filter_date_no_start) else null
}

/**
 * Single-date calendar for the open-ended modes, header hidden (the summary
 * above already restates the selection).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleDayPicker(state: DatePickerState, modifier: Modifier = Modifier) {
    DatePicker(
        state = state,
        title = null,
        headline = null,
        showModeToggle = false,
        modifier = modifier,
    )
}

@Composable
private fun SecondaryActions(
    showClear: Boolean,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (showClear) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.filter_date_clear))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    } else {
        Box(modifier = modifier.fillMaxWidth()) {
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.Center)) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

private val RANGE_PICKER_MAX_HEIGHT = 420.dp
