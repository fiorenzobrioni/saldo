package com.callbackdev.saldo.feature.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.feature.transactions.filter.DatePreset
import com.callbackdev.saldo.feature.transactions.filter.TransactionFilters
import java.time.LocalDate

/**
 * The always-visible date preset chips plus, when other filters are active, a
 * second row of dismissible chips (one per selected type/category/account/tag
 * and one for the amount range).
 */
@Composable
internal fun TransactionsFilterBar(
    filters: TransactionFilters,
    today: LocalDate,
    categories: List<Category>,
    accounts: List<Account>,
    tags: List<Tag>,
    onSetPreset: (DatePreset) -> Unit,
    onRequestCustomRange: () -> Unit,
    onFiltersChange: (TransactionFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        DatePresetRow(
            filters = filters,
            today = today,
            onSetPreset = onSetPreset,
            onRequestCustomRange = onRequestCustomRange,
        )
        if (filters.hasNonDateFilters) {
            ActiveFilterRow(
                filters = filters,
                categories = categories,
                accounts = accounts,
                tags = tags,
                onFiltersChange = onFiltersChange,
            )
        }
    }
}

private val TransactionFilters.hasNonDateFilters: Boolean
    get() = types.isNotEmpty() || categoryIds.isNotEmpty() || accountIds.isNotEmpty() ||
        tagIds.isNotEmpty() || amountMin != null || amountMax != null

@Composable
private fun DatePresetRow(
    filters: TransactionFilters,
    today: LocalDate,
    onSetPreset: (DatePreset) -> Unit,
    onRequestCustomRange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        DatePreset.entries.forEach { preset ->
            val selected = filters.datePreset == preset
            FilterChip(
                selected = selected,
                onClick = {
                    if (preset == DatePreset.CUSTOM) {
                        onRequestCustomRange()
                    } else {
                        // Reselecting the active preset goes back to "all".
                        onSetPreset(if (selected) DatePreset.ALL else preset)
                    }
                },
                label = {
                    Text(
                        if (preset == DatePreset.CUSTOM && selected) {
                            customRangeLabel(filters, today)
                        } else {
                            stringResource(preset.labelRes)
                        },
                    )
                },
            )
        }
    }
}

/** Chip label of the applied custom period, open bounds included. */
@Composable
private fun customRangeLabel(filters: TransactionFilters, today: LocalDate): String =
    periodLabel(filters.customStart, filters.customEnd, today)
        ?: stringResource(DatePreset.CUSTOM.labelRes)

private val DatePreset.labelRes: Int
    get() = when (this) {
        DatePreset.ALL -> R.string.filter_date_all
        DatePreset.THIS_WEEK -> R.string.filter_date_this_week
        DatePreset.THIS_MONTH -> R.string.filter_date_this_month
        DatePreset.LAST_MONTH -> R.string.filter_date_last_month
        DatePreset.LAST_90_DAYS -> R.string.filter_date_last_90_days
        DatePreset.THIS_YEAR -> R.string.filter_date_this_year
        DatePreset.CUSTOM -> R.string.filter_date_custom
    }

@Composable
private fun ActiveFilterRow(
    filters: TransactionFilters,
    categories: List<Category>,
    accounts: List<Account>,
    tags: List<Tag>,
    onFiltersChange: (TransactionFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryById = categories.associateBy { it.id }
    val accountById = accounts.associateBy { it.id }
    val tagById = tags.associateBy { it.id }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        filters.types.forEach { type ->
            RemovableChip(
                label = stringResource(type.labelRes),
                onRemove = { onFiltersChange(filters.copy(types = filters.types - type)) },
            )
        }
        filters.categoryIds.forEach { id ->
            RemovableChip(
                label = categoryById[id]?.name ?: return@forEach,
                onRemove = { onFiltersChange(filters.copy(categoryIds = filters.categoryIds - id)) },
            )
        }
        filters.accountIds.forEach { id ->
            RemovableChip(
                label = accountById[id]?.name ?: return@forEach,
                onRemove = { onFiltersChange(filters.copy(accountIds = filters.accountIds - id)) },
            )
        }
        filters.tagIds.forEach { id ->
            RemovableChip(
                label = tagById[id]?.name ?: return@forEach,
                onRemove = { onFiltersChange(filters.copy(tagIds = filters.tagIds - id)) },
            )
        }
        if (filters.amountMin != null || filters.amountMax != null) {
            RemovableChip(
                label = amountChipLabel(filters),
                onRemove = { onFiltersChange(filters.copy(amountMin = null, amountMax = null)) },
            )
        }
    }
}

@Composable
private fun amountChipLabel(filters: TransactionFilters): String {
    val min = filters.amountMin
    val max = filters.amountMax
    return when {
        min != null && max != null ->
            stringResource(R.string.filter_amount_between, min.toPlainString(), max.toPlainString())
        min != null -> stringResource(R.string.filter_amount_at_least, min.toPlainString())
        else -> stringResource(R.string.filter_amount_at_most, max!!.toPlainString())
    }
}

internal val TransactionType.labelRes: Int
    get() = when (this) {
        TransactionType.EXPENSE -> R.string.transaction_type_expense
        TransactionType.INCOME -> R.string.transaction_type_income
        TransactionType.TRANSFER -> R.string.transaction_type_transfer
        TransactionType.ADJUSTMENT -> R.string.transaction_type_adjustment
    }

@Composable
private fun RemovableChip(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.filter_remove, label),
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
        modifier = modifier,
    )
}

/**
 * The always-visible summary of the filtered view: movement count, net, and
 * the expense/income split, one line per currency.
 */
@Composable
internal fun FilteredTotalsBar(
    totals: List<FilteredTotal>,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pluralStringResource(R.plurals.filter_result_count, count, count),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                totals.firstOrNull()?.let { first ->
                    NetText(total = first)
                }
            }
            totals.forEachIndexed { index, total ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_stat_expenses) + " " +
                            MoneyFormatter.format(total.expenses, total.currency),
                        style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                        color = MaterialTheme.moneyColors.expense,
                    )
                    Text(
                        text = stringResource(R.string.dashboard_stat_incomes) + " " +
                            MoneyFormatter.formatSigned(total.incomes, total.currency),
                        style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                        color = MaterialTheme.moneyColors.income,
                    )
                    if (index > 0) {
                        NetText(total = total)
                    }
                }
            }
        }
    }
}

@Composable
private fun NetText(total: FilteredTotal, modifier: Modifier = Modifier) {
    val net = total.net
    Text(
        text = stringResource(
            R.string.filter_total_net,
            MoneyFormatter.formatSigned(net, total.currency),
        ),
        style = MaterialTheme.typography.titleSmall.tabularNumbers(),
        color = when {
            net.signum() > 0 -> MaterialTheme.moneyColors.income
            net.signum() < 0 -> MaterialTheme.moneyColors.expense
            else -> MaterialTheme.moneyColors.neutral
        },
        modifier = modifier,
    )
}
