package com.callbackdev.saldo.feature.transactions

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.TransactionType
import java.util.Currency

/** User-facing label for a [TransactionType]. */
@StringRes
fun TransactionType.labelRes(): Int = when (this) {
    TransactionType.EXPENSE -> R.string.transaction_type_expense
    TransactionType.INCOME -> R.string.transaction_type_income
    TransactionType.TRANSFER -> R.string.transaction_type_transfer
    TransactionType.ADJUSTMENT -> R.string.transaction_type_adjustment
}

/** Segmented selector between the types available for the current mode. */
@Composable
internal fun TypeSelector(
    selected: TransactionType,
    options: List<TransactionType>,
    onTypeChanged: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, type ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onTypeChanged(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(stringResource(type.labelRes())) },
            )
        }
    }
}

/**
 * The big amount readout driven by the in-app keypad. Tapping it makes it the
 * keypad target (and brings the keypad back when a text field had the focus).
 */
@Composable
internal fun AmountDisplay(
    input: String,
    currency: Currency?,
    isActive: Boolean,
    isError: Boolean,
    decimalSeparator: Char,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val border = when {
        isError -> BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        isActive -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> null
    }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = border,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp),
        ) {
            label?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = input.ifEmpty { "0" }.replace('.', decimalSeparator),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (input.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                currency?.let {
                    Text(
                        text = it.symbol,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}

/** Grid of selectable categories (4 per row), colored per category. */
@Composable
internal fun CategoryGrid(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        maxItemsInEachRow = 4,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        categories.forEach { category ->
            CategoryCell(
                category = category,
                isSelected = category.id == selectedId,
                onSelect = { onSelect(category.id) },
                modifier = Modifier.weight(1f),
            )
        }
        // Pad the last row so the cells keep the same width as full rows.
        repeat((4 - categories.size % 4) % 4) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun CategoryCell(
    category: Category,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = CategoryVisuals.color(category.color)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(vertical = 6.dp, horizontal = 2.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (isSelected) color else color.copy(alpha = 0.16f)),
        ) {
            Icon(
                imageVector = CategoryVisuals.icon(category.icon),
                contentDescription = null,
                tint = if (isSelected) Color.White else color,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.size(4.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** A labelled switch row with an optional supporting line. */
@Composable
internal fun EditorSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
