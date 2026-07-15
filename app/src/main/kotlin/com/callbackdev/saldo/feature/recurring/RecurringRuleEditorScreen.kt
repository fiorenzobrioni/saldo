package com.callbackdev.saldo.feature.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.EditorBottomBar
import com.callbackdev.saldo.core.designsystem.component.EditorSaveButton
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.designsystem.visuals.contentColorOn
import com.callbackdev.saldo.core.domain.model.RecurrenceMode
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.navigation.RecurringRuleEditorRoute
import java.time.LocalDate

/**
 * Create/edit form for a recurring rule (subscription or recurring income): a
 * live preview avatar plus name, amount, account, category, frequency,
 * first-charge and optional end date, color and icon. In edit mode it also
 * hosts the delete flow. Labels adapt to the rule type; the type itself is
 * fixed by the hub tab the editor was opened from (or by the edited rule).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringRuleEditorScreen(
    route: RecurringRuleEditorRoute,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecurringRuleEditorViewModel =
        hiltViewModel<RecurringRuleEditorViewModel, RecurringRuleEditorViewModel.Factory>(
            creationCallback = { factory -> factory.create(route) },
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val writeFailedMessage = stringResource(R.string.editor_write_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RecurringRuleEditorEvent.Saved,
                RecurringRuleEditorEvent.Deleted,
                RecurringRuleEditorEvent.RuleMissing,
                -> onNavigateBack()

                RecurringRuleEditorEvent.WriteFailed ->
                    snackbarHostState.showSnackbar(writeFailedMessage)
            }
        }
    }

    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(editorTitleRes(uiState.isNew, uiState.type))) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                },
                actions = {
                    if (!uiState.isNew) {
                        IconButton(onClick = viewModel::requestDelete) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(editorDeleteRes(uiState.type)),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!uiState.isLoading) {
                EditorBottomBar {
                    EditorSaveButton(
                        text = stringResource(editorSaveRes(uiState.type)),
                        onClick = viewModel::save,
                        enabled = !uiState.isLoading,
                    )
                }
            }
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            EditorForm(
                uiState = uiState,
                viewModel = viewModel,
                onStartDateClick = { showStartPicker = true },
                onEndDateClick = { showEndPicker = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            )
        }
    }

    if (showStartPicker) {
        RecurringDatePickerDialog(
            initialDate = uiState.startDate,
            onConfirm = {
                viewModel.onStartDateSelected(it)
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false },
        )
    }
    if (showEndPicker) {
        RecurringDatePickerDialog(
            initialDate = uiState.endDate ?: uiState.startDate,
            minDate = uiState.startDate,
            onConfirm = {
                viewModel.onEndDateSelected(it)
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false },
        )
    }
    if (uiState.showDeleteDialog) {
        DeleteRuleDialog(
            type = uiState.type,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDeleteDialog,
        )
    }
}

@Composable
private fun EditorForm(
    uiState: RecurringRuleEditorUiState,
    viewModel: RecurringRuleEditorViewModel,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isIncome = uiState.type == TransactionType.INCOME
    Column(modifier = modifier) {
        Spacer(Modifier.height(16.dp))
        PreviewAvatar(uiState = uiState)
        Spacer(Modifier.height(24.dp))
        NameField(
            name = uiState.name,
            placeholderRes = if (isIncome) {
                R.string.income_editor_name_hint
            } else {
                R.string.subscription_editor_name_hint
            },
            showError = uiState.showValidation && !uiState.isNameValid,
            onNameChanged = viewModel::onNameChanged,
        )
        Spacer(Modifier.height(12.dp))
        if (uiState.isVariableAmount) {
            VariableAmountNote(
                text = stringResource(
                    if (isIncome) {
                        R.string.income_editor_variable_amount_note
                    } else {
                        R.string.subscription_editor_variable_amount_note
                    },
                ),
            )
        } else {
            AmountField(
                input = uiState.amountInput,
                currency = uiState.currency,
                showError = uiState.showValidation && !uiState.isAmountValid,
                onChanged = viewModel::onAmountChanged,
            )
        }
        Spacer(Modifier.height(4.dp))
        SwitchRow(
            title = stringResource(R.string.subscription_editor_variable_amount),
            subtitle = stringResource(
                if (isIncome) {
                    R.string.income_editor_variable_amount_hint
                } else {
                    R.string.subscription_editor_variable_amount_hint
                },
            ),
            checked = uiState.isVariableAmount,
            onToggle = viewModel::onVariableAmountToggled,
        )
        if (!uiState.isVariableAmount) {
            SectionLabel(stringResource(R.string.subscription_editor_section_mode))
            ModeSelector(mode = uiState.mode, onModeChanged = viewModel::onModeChanged)
        }
        Spacer(Modifier.height(12.dp))
        AccountField(
            accounts = uiState.accounts,
            selectedId = uiState.accountId,
            showError = uiState.showValidation && !uiState.isAccountValid,
            onSelected = viewModel::onAccountSelected,
        )
        Spacer(Modifier.height(12.dp))
        CategoryField(
            categories = uiState.categories,
            selectedId = uiState.categoryId,
            onSelected = viewModel::onCategorySelected,
        )
        Spacer(Modifier.height(12.dp))
        // Frequency and first charge read as a pair: how often, starting when.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FrequencyField(
                frequencies = viewModel.frequencies,
                selected = uiState.frequency,
                onSelected = viewModel::onFrequencySelected,
                modifier = Modifier.weight(1f),
            )
            DateField(
                label = stringResource(
                    if (isIncome) {
                        R.string.income_editor_first_credit
                    } else {
                        R.string.subscription_editor_first_charge
                    },
                ),
                date = uiState.startDate,
                placeholder = "",
                onClick = onStartDateClick,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        EndDateControl(
            endDate = uiState.endDate,
            onToggle = viewModel::onEndDateToggled,
            onDateClick = onEndDateClick,
        )
        SectionLabel(stringResource(R.string.subscription_editor_section_color))
        SubscriptionColorPicker(selected = uiState.color, onColorSelected = viewModel::onColorSelected)
        SectionLabel(stringResource(R.string.subscription_editor_section_icon))
        SubscriptionIconPicker(
            selectedIcon = uiState.icon,
            selectedColor = uiState.color,
            onIconSelected = viewModel::onIconSelected,
        )
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * "Has an end date" switch that reveals the date field when on. Toggling off
 * clears the end date (the earlier inline clear was masked by the tap overlay).
 */
@Composable
private fun EndDateControl(
    endDate: LocalDate?,
    onToggle: (Boolean) -> Unit,
    onDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SwitchRow(
            title = stringResource(R.string.subscription_editor_has_end_date),
            subtitle = stringResource(R.string.subscription_editor_has_end_date_hint),
            checked = endDate != null,
            onToggle = onToggle,
        )
        if (endDate != null) {
            Spacer(Modifier.height(8.dp))
            DateField(
                label = stringResource(R.string.subscription_editor_end_date),
                date = endDate,
                placeholder = "",
                onClick = onDateClick,
            )
        }
    }
}

/** A labelled switch row (title + hint), used for the variable-amount and end-date toggles. */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
            .padding(vertical = 8.dp, horizontal = 4.dp),
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

/** Segmented selector for how a generated movement is registered. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(
    mode: RecurrenceMode,
    onModeChanged: (RecurrenceMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(RecurrenceMode.AUTOMATIC, RecurrenceMode.CONFIRM)
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == mode,
                onClick = { onModeChanged(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = {
                    Text(
                        stringResource(
                            if (option == RecurrenceMode.AUTOMATIC) {
                                R.string.subscription_editor_mode_automatic
                            } else {
                                R.string.subscription_editor_mode_confirm
                            },
                        ),
                    )
                },
            )
        }
    }
}

/** Placeholder shown in place of the amount field for a variable-amount rule. */
@Composable
private fun VariableAmountNote(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviewAvatar(uiState: RecurringRuleEditorUiState, modifier: Modifier = Modifier) {
    val color = CategoryVisuals.color(uiState.color)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(AvatarShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CategoryVisuals.icon(uiState.icon),
                contentDescription = null,
                tint = contentColorOn(color),
                modifier = Modifier.size(34.dp),
            )
        }
        if (uiState.name.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = uiState.name.trim(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 24.dp, bottom = 12.dp),
    )
}

@Composable
private fun DeleteRuleDialog(
    type: TransactionType,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (type == TransactionType.INCOME) {
                        R.string.income_delete_title
                    } else {
                        R.string.subscription_delete_title
                    },
                ),
            )
        },
        // The body is type-agnostic: it explains rule vs already-recorded movements.
        text = { Text(stringResource(R.string.subscription_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(editorDeleteRes(type)),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
