package com.callbackdev.saldo.feature.transactions

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.designsystem.visuals.contentColorOn
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
 * The hero amount input: a borderless, centered [BasicTextField] with the
 * currency symbol beside the digits, in a large tabular-figures style so the
 * amount stays the focal point of the screen. Input still goes through the
 * system decimal keyboard (ADR 16) and the raw text is sanitized by the
 * ViewModel, so both `.` and `,` are accepted while typing. On a failed save
 * attempt the amount turns error-colored and [errorText] appears below; for a
 * balance adjustment a sign-toggle sits next to the digits. [compact] renders
 * the smaller variant used for the second leg of a cross-currency transfer.
 */
@Suppress("LongParameterList")
@Composable
internal fun AmountField(
    input: String,
    currency: Currency?,
    isError: Boolean,
    showSignToggle: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    errorText: String? = null,
    compact: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val amountStyle = if (compact) {
        MaterialTheme.typography.headlineSmall.tabularNumbers()
    } else {
        MaterialTheme.typography.displayMedium.tabularNumbers()
    }
    val symbolStyle = if (compact) {
        MaterialTheme.typography.titleMedium
    } else {
        MaterialTheme.typography.headlineSmall
    }
    val amountColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val symbolColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
        }
        // A borderless field has no visible label in the standard case, so the
        // role is stated for TalkBack instead.
        val fieldDescription = label ?: stringResource(R.string.transaction_editor_amount)
        BasicTextField(
            value = input,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = amountStyle.copy(color = amountColor),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = (focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .fillMaxWidth()
                .semantics { contentDescription = fieldDescription },
            decorationBox = { innerTextField ->
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (compact) 4.dp else 8.dp),
                ) {
                    if (currency != null) {
                        Text(
                            text = currency.symbol,
                            style = symbolStyle,
                            color = symbolColor,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        // Wraps the digits but never pushes the symbol off-screen:
                        // past the cap the field scrolls horizontally instead.
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        if (input.isEmpty()) {
                            Text(
                                text = stringResource(R.string.editor_amount_placeholder),
                                style = amountStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = PLACEHOLDER_ALPHA),
                            )
                        }
                        innerTextField()
                    }
                    if (showSignToggle) {
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { onValueChange(toggleSign(input)) }) {
                            Icon(
                                imageVector = Icons.Outlined.Exposure,
                                contentDescription = stringResource(R.string.action_toggle_sign),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
        )
        if (isError && errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private const val PLACEHOLDER_ALPHA = 0.4f

private fun toggleSign(input: String): String =
    if (input.startsWith("-")) input.removePrefix("-") else "-$input"

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
                .clip(AvatarShape)
                .background(if (isSelected) color else color.copy(alpha = 0.16f)),
        ) {
            Icon(
                imageVector = CategoryVisuals.icon(category.icon),
                contentDescription = null,
                tint = if (isSelected) contentColorOn(color) else color,
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
