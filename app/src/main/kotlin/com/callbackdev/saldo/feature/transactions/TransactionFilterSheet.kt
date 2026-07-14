package com.callbackdev.saldo.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.feature.transactions.filter.TransactionFilters

/**
 * Full filter editor. Edits a local copy of [filters] and commits it on
 * "Apply", so dismissing the sheet never half-applies a selection. The search
 * query and the date preset are managed outside (app bar and chip row) and
 * pass through untouched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionFilterSheet(
    filters: TransactionFilters,
    categories: List<Category>,
    accounts: List<Account>,
    tags: List<Tag>,
    onApply: (TransactionFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(filters) }
    var minText by remember { mutableStateOf(filters.amountMin?.toPlainString().orEmpty()) }
    var maxText by remember { mutableStateOf(filters.amountMax?.toPlainString().orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.transactions_filters),
                style = MaterialTheme.typography.titleLarge,
            )

            FilterSection(title = stringResource(R.string.filter_section_type)) {
                TransactionType.entries.forEach { type ->
                    FilterChip(
                        selected = type in draft.types,
                        onClick = { draft = draft.copy(types = draft.types.toggled(type)) },
                        label = { Text(stringResource(type.labelRes)) },
                    )
                }
            }

            if (categories.isNotEmpty()) {
                FilterSection(title = stringResource(R.string.categories_title)) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = category.id in draft.categoryIds,
                            onClick = {
                                draft = draft.copy(categoryIds = draft.categoryIds.toggled(category.id))
                            },
                            label = { Text(category.name) },
                        )
                    }
                }
            }

            if (accounts.isNotEmpty()) {
                FilterSection(title = stringResource(R.string.accounts_title)) {
                    accounts.forEach { account ->
                        FilterChip(
                            selected = account.id in draft.accountIds,
                            onClick = {
                                draft = draft.copy(accountIds = draft.accountIds.toggled(account.id))
                            },
                            label = { Text(account.name) },
                        )
                    }
                }
            }

            if (tags.isNotEmpty()) {
                FilterSection(title = stringResource(R.string.transaction_editor_tags)) {
                    tags.forEach { tag ->
                        FilterChip(
                            selected = tag.id in draft.tagIds,
                            onClick = { draft = draft.copy(tagIds = draft.tagIds.toggled(tag.id)) },
                            label = { Text(tag.name) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.filter_section_amount),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = minText,
                    onValueChange = {
                        minText = MoneyInput.sanitize(it, AMOUNT_FRACTION_DIGITS, allowNegative = false)
                    },
                    label = { Text(stringResource(R.string.filter_amount_min)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = maxText,
                    onValueChange = {
                        maxText = MoneyInput.sanitize(it, AMOUNT_FRACTION_DIGITS, allowNegative = false)
                    },
                    label = { Text(stringResource(R.string.filter_amount_max)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        draft = TransactionFilters(
                            query = draft.query,
                            datePreset = draft.datePreset,
                            customStart = draft.customStart,
                            customEnd = draft.customEnd,
                        )
                        minText = ""
                        maxText = ""
                    },
                ) {
                    Text(stringResource(R.string.filter_reset))
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val min = MoneyInput.parse(minText)
                        val max = MoneyInput.parse(maxText)
                        // A reversed range would silently match nothing: normalize it.
                        val reversed = min != null && max != null && min > max
                        onApply(
                            draft.copy(
                                amountMin = if (reversed) max else min,
                                amountMax = if (reversed) min else max,
                            ),
                        )
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.filter_apply))
                }
            }
        }
    }
}

/** Digits accepted by the amount bounds; magnitudes span currencies, 2 is plenty. */
private const val AMOUNT_FRACTION_DIGITS = 2

private fun <T> Set<T>.toggled(value: T): Set<T> =
    if (value in this) this - value else this + value

@Composable
private fun FilterSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}
