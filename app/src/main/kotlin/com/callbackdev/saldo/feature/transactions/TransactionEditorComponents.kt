package com.callbackdev.saldo.feature.transactions

import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.money.MoneyMapper
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
 * The big, centered amount readout driven by the in-app keypad. Borderless: the
 * amount is the focal point of the screen. Tapping it makes it the keypad target
 * (and brings the keypad back when a text field had the focus); a caret blinks
 * while it is active. An empty amount shows a muted, currency-scaled zero.
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
    val fractionDigits = currency?.let { MoneyMapper.fractionDigits(it) } ?: DEFAULT_FRACTION_DIGITS
    val isPlaceholder = input.isEmpty()
    val display = if (isPlaceholder) {
        zeroText(fractionDigits, decimalSeparator)
    } else {
        input.replace('.', decimalSeparator)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
    ) {
        label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(4.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = display,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                color = when {
                    isError -> MaterialTheme.colorScheme.error
                    isPlaceholder -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            if (isActive) {
                AmountCaret()
            }
            currency?.let {
                Text(
                    text = it.symbol,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AmountCaret(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "amount-caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = CARET_BLINK_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "amount-caret-alpha",
    )
    Box(
        modifier = modifier
            .padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
            .size(width = 2.dp, height = 36.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall),
    )
}

/** Borderless inline description field with a leading icon, matching the amount's flat look. */
@Composable
internal fun InlineDescriptionField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(stringResource(R.string.transaction_editor_description_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Notes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

private fun zeroText(fractionDigits: Int, separator: Char): String =
    if (fractionDigits <= 0) "0" else "0" + separator + "0".repeat(fractionDigits)

private const val DEFAULT_FRACTION_DIGITS = 2
private const val CARET_BLINK_MILLIS = 600

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
                modifier = Modifier.size(20.dp),
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
