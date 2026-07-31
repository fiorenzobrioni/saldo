package com.callbackdev.saldo.feature.recurring

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.date.withLocaleDateCasing
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.SaldoCard
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.TransactionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The recurrence-scan surface of the hub (Fase 19, ADR 43): the explicit
 * action row, the "Suggestions" section it feeds and the partial-result note.
 * The row is the ONLY trigger of the scan; everything else here just renders
 * the persisted result.
 */
internal fun LazyListScope.recurrenceSuggestionItems(
    suggestions: List<RecurrenceSuggestionItem>,
    scan: RecurrenceScanUi,
    onScanClick: () -> Unit,
    onSuggestionClick: (RecurrenceSuggestionItem) -> Unit,
    onSuggestionDismiss: (RecurrenceSuggestionItem) -> Unit,
) {
    item(key = "recurrence-scan-row") {
        RecurrenceScanRow(scan = scan, onClick = onScanClick)
    }
    if (suggestions.isNotEmpty()) {
        item(key = "recurrence-suggestions-header") {
            Text(
                text = stringResource(R.string.recurrences_suggestions_header),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
        suggestions.forEach { entry ->
            item(key = entry.suggestion.key) {
                RecurrenceSuggestionCard(
                    item = entry,
                    onClick = { onSuggestionClick(entry) },
                    onDismiss = { onSuggestionDismiss(entry) },
                )
            }
        }
    }
    if (scan.truncated && scan.lastScan != null) {
        item(key = "recurrence-scan-truncated") {
            ScanNote(text = stringResource(R.string.recurrences_scan_truncated))
        }
    }
}

/**
 * The discreet, always-visible action row: "Search for unregistered
 * recurrences", with the date of the last search declared underneath and an
 * inline spinner while the pass runs. Honest when a search found nothing.
 */
@Composable
private fun RecurrenceScanRow(
    scan: RecurrenceScanUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaldoCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPadding,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.ManageSearch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                    text = stringResource(R.string.recurrences_scan_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = scanSubtitle(scan),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (scan.isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun scanSubtitle(scan: RecurrenceScanUi): String = when {
    scan.isScanning -> stringResource(R.string.recurrences_scan_running)
    scan.lastScan == null -> stringResource(R.string.recurrences_scan_hint)
    scan.foundNothing -> stringResource(
        R.string.recurrences_scan_last_empty,
        shortDate(scan.lastScan),
    )
    else -> stringResource(R.string.recurrences_scan_last, shortDate(scan.lastScan))
}

/**
 * One suggestion: "Looks like a subscription" with the series' own name,
 * amount and cadence. Tapping opens the rule editor prefilled; the trailing
 * cross dismisses for good. Mirrors the recap teaser card.
 */
@Composable
private fun RecurrenceSuggestionCard(
    item: RecurrenceSuggestionItem,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestion = item.suggestion
    val title = suggestion.name
        ?: item.category?.name
        ?: stringResource(
            if (suggestion.type == TransactionType.INCOME) {
                R.string.recurrences_suggestion_fallback_income
            } else {
                R.string.recurrences_suggestion_fallback_expense
            },
        )
    val amountLabel = if (suggestion.isVariableAmount) {
        MoneyFormatter.formatApprox(item.amount, suggestion.currency)
    } else {
        MoneyFormatter.format(item.amount, suggestion.currency)
    }
    val cadence = stringResource(suggestion.frequency.cadenceRes(), amountLabel)
    val next = stringResource(
        R.string.recurrences_suggestion_next,
        shortDate(suggestion.nextOccurrence),
    )
    SaldoCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = SaldoDimens.cardPaddingLarge,
                top = 6.dp,
                bottom = 6.dp,
                end = 4.dp,
            ),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$cadence · $next",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.recurrences_suggestion_count,
                        suggestion.occurrenceCount,
                        suggestion.occurrenceCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.recurrences_suggestion_dismiss),
                )
            }
        }
    }
}

/** "Amount every week/month/year" template of a suggested cadence. */
private fun RecurrenceFrequency.cadenceRes(): Int = when (this) {
    RecurrenceFrequency.WEEKLY -> R.string.recurrences_suggestion_cadence_weekly
    RecurrenceFrequency.ANNUAL -> R.string.recurrences_suggestion_cadence_annual
    else -> R.string.recurrences_suggestion_cadence_monthly
}

@Composable
private fun ScanNote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Localized compact date, e.g. "7 Jul", same recipe as the rule rows. */
@Composable
private fun shortDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "dMMM")
        date.format(DateTimeFormatter.ofPattern(pattern, locale))
            .withLocaleDateCasing(locale)
    }
}
