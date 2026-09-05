@file:Suppress("TooManyFunctions") // One small composable per editor section/row/dialog.

package com.callbackdev.saldo.feature.recurring

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.AmountKeypadHost
import com.callbackdev.saldo.core.designsystem.component.AmountTarget
import com.callbackdev.saldo.core.designsystem.component.AnimatedSection
import com.callbackdev.saldo.core.designsystem.component.DiscardChangesDialog
import com.callbackdev.saldo.core.designsystem.component.EditorBottomBar
import com.callbackdev.saldo.core.designsystem.component.EditorSaveButton
import com.callbackdev.saldo.core.designsystem.component.HeroAmountField
import com.callbackdev.saldo.core.designsystem.component.LoadingState
import com.callbackdev.saldo.core.designsystem.component.SaldoDatePickerDialog
import com.callbackdev.saldo.core.designsystem.component.rememberUnsavedChangesGuard
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
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()
    val guard = rememberUnsavedChangesGuard(hasUnsavedChanges, onNavigateBack)
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

    DiscardChangesDialog(guard)

    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }
    // The rule's name comes first on this form: the keypad waits to be asked for.
    var keypadOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = keypadOpen) { keypadOpen = false }
    val amountTarget = AmountTarget(
        value = uiState.amountInput,
        fractionDigits = uiState.amountFractionDigits,
        allowNegative = false,
        onValueChange = viewModel::onAmountChanged,
    )

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(editorTitleRes(uiState.isNew, uiState.type))) },
                navigationIcon = {
                    IconButton(onClick = guard::requestNavigateBack) {
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
                    AmountKeypadHost(
                        target = amountTarget.takeIf { keypadOpen && !uiState.isVariableAmount },
                        onHide = { keypadOpen = false },
                    )
                    EditorSaveButton(
                        text = stringResource(editorSaveRes(uiState.type)),
                        onClick = viewModel::save,
                        // Always tappable: a failed tap surfaces every field error at
                        // once, which explains more than a disabled button ever could.
                        enabled = true,
                    )
                }
            }
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            LoadingState(modifier = Modifier.padding(innerPadding))
        } else {
            EditorForm(
                uiState = uiState,
                viewModel = viewModel,
                amountTarget = amountTarget,
                isKeypadOpen = keypadOpen,
                onActivateAmount = { keypadOpen = true },
                onCloseKeypad = { keypadOpen = false },
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
        SaldoDatePickerDialog(
            initialDate = uiState.startDate,
            onConfirm = {
                viewModel.onStartDateSelected(it)
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false },
        )
    }
    if (showEndPicker) {
        SaldoDatePickerDialog(
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

/** Amount field (or variable-amount note), the variable-amount switch, and the recording mode. */
@Suppress("LongParameterList")
@Composable
private fun AmountAndModeSection(
    uiState: RecurringRuleEditorUiState,
    viewModel: RecurringRuleEditorViewModel,
    isIncome: Boolean,
    amountTarget: AmountTarget,
    isKeypadOpen: Boolean,
    onActivateAmount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // The fixed amount and the variable-amount note swap with the toggle;
        // both animate so the form never snaps.
        AnimatedSection(visible = uiState.isVariableAmount) {
            VariableAmountNote(
                text = stringResource(
                    if (isIncome) {
                        R.string.income_editor_variable_amount_note
                    } else {
                        R.string.subscription_editor_variable_amount_note
                    },
                ),
            )
        }
        AnimatedSection(visible = !uiState.isVariableAmount) {
            HeroAmountField(
                target = amountTarget,
                currencySymbol = uiState.currency?.symbol,
                isError = uiState.showValidation && !uiState.isAmountValid,
                isActive = isKeypadOpen,
                onActivate = onActivateAmount,
                label = stringResource(R.string.subscription_editor_amount),
                errorText = stringResource(R.string.subscription_editor_amount_error),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        if (uiState.showVariableAmount) {
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
        }
        AnimatedSection(visible = uiState.showModeSelector) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionLabel(stringResource(R.string.subscription_editor_section_mode))
                ModeSelector(mode = uiState.mode, onModeChanged = viewModel::onModeChanged)
            }
        }
        AnimatedSection(visible = !uiState.showModeSelector && uiState.isCrossCurrency) {
            // The received amount cannot be fixed up front (the rate drifts), so the
            // rule confirms it at each occurrence instead of running automatically.
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(12.dp))
                VariableAmountNote(
                    text = stringResource(R.string.transfer_editor_cross_currency_note),
                )
            }
        }
    }
}

/** Source account, plus a destination account (transfer) or a category (expense/income). */
@Composable
private fun AccountsSection(
    uiState: RecurringRuleEditorUiState,
    viewModel: RecurringRuleEditorViewModel,
    isTransfer: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        AccountField(
            accounts = uiState.accounts,
            selectedId = uiState.accountId,
            showError = uiState.showValidation && !uiState.isAccountValid,
            onSelected = viewModel::onAccountSelected,
            labelRes = if (isTransfer) {
                R.string.transfer_editor_from_account
            } else {
                R.string.subscription_editor_account
            },
        )
        Spacer(Modifier.height(12.dp))
        if (isTransfer) {
            AccountField(
                accounts = uiState.accounts,
                selectedId = uiState.transferAccountId,
                showError = uiState.showValidation && !uiState.isTransferAccountValid,
                onSelected = viewModel::onTransferAccountSelected,
                labelRes = R.string.transfer_editor_to_account,
                errorRes = R.string.transfer_editor_to_account_error,
            )
        } else {
            CategoryField(
                categories = uiState.categories,
                selectedId = uiState.categoryId,
                onSelected = viewModel::onCategorySelected,
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun EditorForm(
    uiState: RecurringRuleEditorUiState,
    viewModel: RecurringRuleEditorViewModel,
    amountTarget: AmountTarget,
    isKeypadOpen: Boolean,
    onActivateAmount: () -> Unit,
    onCloseKeypad: () -> Unit,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isIncome = uiState.type == TransactionType.INCOME
    val isTransfer = uiState.isTransfer
    Column(modifier = modifier) {
        Spacer(Modifier.height(16.dp))
        PreviewAvatar(uiState = uiState)
        Spacer(Modifier.height(24.dp))
        NameField(
            name = uiState.name,
            placeholderRes = when {
                isTransfer -> R.string.transfer_editor_name_hint
                isIncome -> R.string.income_editor_name_hint
                else -> R.string.subscription_editor_name_hint
            },
            showError = uiState.showValidation && !uiState.isNameValid,
            onNameChanged = viewModel::onNameChanged,
            // The name field brings the system IME up: the keypad steps aside.
            modifier = Modifier.onFocusChanged { if (it.isFocused) onCloseKeypad() },
        )
        Spacer(Modifier.height(12.dp))
        AmountAndModeSection(
            uiState = uiState,
            viewModel = viewModel,
            isIncome = isIncome,
            amountTarget = amountTarget,
            isKeypadOpen = isKeypadOpen,
            onActivateAmount = onActivateAmount,
        )
        Spacer(Modifier.height(12.dp))
        AccountsSection(uiState = uiState, viewModel = viewModel, isTransfer = isTransfer)
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
                    when {
                        isTransfer -> R.string.transfer_editor_first_transfer
                        isIncome -> R.string.income_editor_first_credit
                        else -> R.string.subscription_editor_first_charge
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
        // A new rule starts running; pausing is a state of an existing one.
        if (!uiState.isNew) {
            Spacer(Modifier.height(4.dp))
            SwitchRow(
                title = stringResource(R.string.subscription_editor_paused),
                subtitle = stringResource(R.string.subscription_editor_paused_hint),
                checked = uiState.isPaused,
                onToggle = viewModel::onPausedToggled,
            )
        }
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
        AnimatedSection(visible = endDate != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                    when (type) {
                        TransactionType.INCOME -> R.string.income_delete_title
                        TransactionType.TRANSFER -> R.string.transfer_delete_title
                        else -> R.string.subscription_delete_title
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
