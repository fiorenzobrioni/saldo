package com.callbackdev.saldo.feature.recurring

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.date.toUtcLocalDate
import com.callbackdev.saldo.core.common.date.toUtcMillis
import com.callbackdev.saldo.core.common.date.withLocaleDateCasing
import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.designsystem.visuals.contentColorOn
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency

@Composable
internal fun NameField(
    name: String,
    @StringRes placeholderRes: Int,
    showError: Boolean,
    onNameChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChanged,
        label = { Text(stringResource(R.string.subscription_editor_name)) },
        placeholder = { Text(stringResource(placeholderRes)) },
        singleLine = true,
        isError = showError,
        supportingText = if (showError) {
            { Text(stringResource(R.string.subscription_editor_name_error)) }
        } else {
            null
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun AmountField(
    input: String,
    currency: Currency?,
    showError: Boolean,
    onChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = input,
        onValueChange = onChanged,
        label = { Text(stringResource(R.string.subscription_editor_amount)) },
        placeholder = { Text(stringResource(R.string.editor_amount_placeholder)) },
        prefix = currency?.let { { Text(it.symbol) } },
        singleLine = true,
        isError = showError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.headlineSmall,
        supportingText = if (showError) {
            { Text(stringResource(R.string.subscription_editor_amount_error)) }
        } else {
            null
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountField(
    accounts: List<Account>,
    selectedId: Long?,
    showError: Boolean,
    onSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes labelRes: Int = R.string.subscription_editor_account,
    @StringRes errorRes: Int = R.string.subscription_editor_account_error,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            isError = showError,
            label = { Text(stringResource(labelRes)) },
            leadingIcon = selected?.let { account ->
                {
                    Icon(
                        imageVector = AccountVisuals.icon(account.icon),
                        contentDescription = null,
                        tint = AccountVisuals.color(account.color),
                    )
                }
            },
            supportingText = if (showError) {
                { Text(stringResource(errorRes)) }
            } else {
                null
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name) },
                    leadingIcon = {
                        Icon(
                            imageVector = AccountVisuals.icon(account.icon),
                            contentDescription = null,
                            tint = AccountVisuals.color(account.color),
                        )
                    },
                    onClick = {
                        onSelected(account.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryField(
    categories: List<Category>,
    selectedId: Long?,
    onSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedId }
    val noneLabel = stringResource(R.string.subscription_editor_category_none)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.name ?: noneLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.subscription_editor_category)) },
            leadingIcon = {
                Icon(
                    imageVector = selected?.let { CategoryVisuals.icon(it.icon) } ?: Icons.Outlined.Category,
                    contentDescription = null,
                    tint = selected?.let { CategoryVisuals.color(it.color) }
                        ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(noneLabel) },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    leadingIcon = {
                        Icon(
                            imageVector = CategoryVisuals.icon(category.icon),
                            contentDescription = null,
                            tint = CategoryVisuals.color(category.color),
                        )
                    },
                    onClick = {
                        onSelected(category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FrequencyField(
    frequencies: List<RecurrenceFrequency>,
    selected: RecurrenceFrequency,
    onSelected: (RecurrenceFrequency) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = stringResource(selected.labelRes()),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.subscription_editor_frequency)) },
            leadingIcon = { Icon(Icons.Outlined.Repeat, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            frequencies.forEach { frequency ->
                DropdownMenuItem(
                    text = { Text(stringResource(frequency.labelRes())) },
                    onClick = {
                        onSelected(frequency)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Read-only date field with a leading calendar glyph; opens a picker on tap. */
@Composable
internal fun DateField(
    label: String,
    date: LocalDate?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = date?.let { formattedDate(it) } ?: placeholder,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Overlay so a tap opens the picker instead of focusing the read-only field.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        )
    }
}

@Composable
private fun formattedDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "dMMMy")
        date.format(DateTimeFormatter.ofPattern(pattern, locale))
            .withLocaleDateCasing(locale)
    }
}

/** Circular swatch grid for the subscription color palette (shared with categories). */
@Composable
internal fun SubscriptionColorPicker(
    selected: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        CategoryVisuals.colors.forEachIndexed { index, color ->
            val isSelected = color == selected
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CategoryVisuals.color(color))
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onColorSelected(color) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = stringResource(R.string.subscription_editor_color_option, index + 1),
                        tint = contentColorOn(CategoryVisuals.color(color)),
                    )
                }
            }
        }
    }
}

/** Grid of the shared icon set; the selected icon uses the chosen color. */
@Composable
internal fun SubscriptionIconPicker(
    selectedIcon: String,
    selectedColor: Int,
    onIconSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        CategoryVisuals.iconKeys.forEachIndexed { index, key ->
            val isSelected = key == selectedIcon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            CategoryVisuals.color(selectedColor)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    )
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onIconSelected(key) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CategoryVisuals.icon(key),
                    contentDescription = stringResource(R.string.subscription_editor_icon_option, index + 1),
                    tint = if (isSelected) {
                        contentColorOn(CategoryVisuals.color(selectedColor))
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * Material date picker locked to calendar mode ([showModeToggle] off): the
 * input/calendar toggle animation was slow and janky, and typing a date adds
 * little here. [minDate] floors the selectable range (for the end date).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecurringDatePickerDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    minDate: LocalDate? = null,
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}
