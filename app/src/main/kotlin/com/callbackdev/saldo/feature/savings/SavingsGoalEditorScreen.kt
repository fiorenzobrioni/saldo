@file:Suppress("TooManyFunctions") // One small composable per editor section/row/dialog.

package com.callbackdev.saldo.feature.savings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.DiscardChangesDialog
import com.callbackdev.saldo.core.designsystem.component.EditorBottomBar
import com.callbackdev.saldo.core.designsystem.component.EditorSaveButton
import com.callbackdev.saldo.core.designsystem.component.EmptyState
import com.callbackdev.saldo.core.designsystem.component.rememberUnsavedChangesGuard
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.designsystem.visuals.contentColorOn
import com.callbackdev.saldo.feature.recurring.DateField
import com.callbackdev.saldo.feature.recurring.RecurringDatePickerDialog
import com.callbackdev.saldo.feature.recurring.SubscriptionColorPicker
import com.callbackdev.saldo.feature.recurring.SubscriptionIconPicker
import com.callbackdev.saldo.navigation.SavingsGoalEditorRoute
import java.time.LocalDate

/**
 * Create/edit form for a savings goal: a live preview avatar plus name, target
 * amount, the linked savings account (with a shortcut to create one), an
 * optional target date, and the color and icon. In edit mode it hosts the
 * delete flow and the linked account is fixed. When creating a goal without any
 * free savings account, the form is replaced by a call to action to create one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalEditorScreen(
    route: SavingsGoalEditorRoute,
    onNavigateBack: () -> Unit,
    onNavigateToNewAccount: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavingsGoalEditorViewModel =
        hiltViewModel<SavingsGoalEditorViewModel, SavingsGoalEditorViewModel.Factory>(
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
                SavingsGoalEditorEvent.Saved,
                SavingsGoalEditorEvent.Deleted,
                SavingsGoalEditorEvent.GoalMissing,
                -> onNavigateBack()

                SavingsGoalEditorEvent.WriteFailed ->
                    snackbarHostState.showSnackbar(writeFailedMessage)
            }
        }
    }

    DiscardChangesDialog(guard)

    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val titleRes = if (uiState.isNew) {
        R.string.savings_editor_title_new
    } else {
        R.string.savings_editor_title_edit
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(titleRes)) },
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
                                contentDescription = stringResource(R.string.savings_delete_action),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!uiState.isLoading && !uiState.noAvailableAccounts) {
                EditorBottomBar {
                    EditorSaveButton(
                        text = stringResource(R.string.savings_editor_save),
                        onClick = viewModel::save,
                        enabled = true,
                    )
                }
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            uiState.noAvailableAccounts -> EmptyState(
                icon = Icons.Outlined.Savings,
                title = stringResource(
                    if (uiState.hasSavingsAccounts) {
                        R.string.savings_editor_all_taken_title
                    } else {
                        R.string.savings_editor_no_account_title
                    },
                ),
                body = stringResource(
                    if (uiState.hasSavingsAccounts) {
                        R.string.savings_editor_all_taken_body
                    } else {
                        R.string.savings_editor_no_account_body
                    },
                ),
                actionLabel = stringResource(R.string.savings_editor_no_account_cta),
                onAction = onNavigateToNewAccount,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            else -> EditorForm(
                uiState = uiState,
                viewModel = viewModel,
                onTargetDateClick = { showDatePicker = true },
                onCreateAccount = onNavigateToNewAccount,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            )
        }
    }

    if (showDatePicker) {
        RecurringDatePickerDialog(
            initialDate = uiState.targetDate ?: LocalDate.now().plusMonths(DEFAULT_TARGET_MONTHS),
            minDate = LocalDate.now(),
            onConfirm = {
                viewModel.onTargetDateSelected(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (uiState.showDeleteDialog) {
        DeleteGoalDialog(
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDeleteDialog,
        )
    }
}

@Composable
private fun EditorForm(
    uiState: SavingsGoalEditorUiState,
    viewModel: SavingsGoalEditorViewModel,
    onTargetDateClick: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Spacer(Modifier.height(16.dp))
        PreviewAvatar(uiState = uiState)
        Spacer(Modifier.height(24.dp))
        SavingsGoalNameField(
            name = uiState.name,
            showError = uiState.showValidation && !uiState.isNameValid,
            onNameChanged = viewModel::onNameChanged,
        )
        Spacer(Modifier.height(12.dp))
        SavingsTargetField(
            input = uiState.targetInput,
            currency = uiState.currency,
            showError = uiState.showValidation && !uiState.isTargetValid,
            onChanged = viewModel::onTargetChanged,
        )
        Spacer(Modifier.height(12.dp))
        SavingsAccountField(
            accounts = uiState.availableAccounts,
            selectedId = uiState.accountId,
            isEditable = uiState.isNew,
            showError = uiState.showValidation && !uiState.isAccountValid,
            onSelected = viewModel::onAccountSelected,
            onCreateAccount = onCreateAccount,
        )
        if (uiState.savedBalance.signum() > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.savings_editor_already_saved,
                    MoneyFormatter.format(uiState.savedBalance, uiState.currency),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        TargetDateControl(
            targetDate = uiState.targetDate,
            onToggle = { enabled ->
                if (enabled) {
                    viewModel.onTargetDateSelected(LocalDate.now().plusMonths(DEFAULT_TARGET_MONTHS))
                } else {
                    viewModel.onTargetDateCleared()
                }
            },
            onDateClick = onTargetDateClick,
        )
        SectionLabel(stringResource(R.string.savings_editor_section_color))
        SubscriptionColorPicker(selected = uiState.color, onColorSelected = viewModel::onColorSelected)
        SectionLabel(stringResource(R.string.savings_editor_section_icon))
        SubscriptionIconPicker(
            selectedIcon = uiState.icon,
            selectedColor = uiState.color,
            onIconSelected = viewModel::onIconSelected,
        )
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * "Has a target date" switch that reveals the date field when on. Enabling seeds
 * a date a few months out; disabling clears it (the suggestion needs a date).
 */
@Composable
private fun TargetDateControl(
    targetDate: LocalDate?,
    onToggle: (Boolean) -> Unit,
    onDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .toggleable(value = targetDate != null, role = Role.Switch, onValueChange = onToggle)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.savings_editor_has_target_date),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.savings_editor_has_target_date_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(16.dp))
            Switch(checked = targetDate != null, onCheckedChange = null)
        }
        if (targetDate != null) {
            Spacer(Modifier.height(8.dp))
            DateField(
                label = stringResource(R.string.savings_editor_target_date),
                date = targetDate,
                placeholder = "",
                onClick = onDateClick,
            )
        }
    }
}

@Composable
private fun PreviewAvatar(uiState: SavingsGoalEditorUiState, modifier: Modifier = Modifier) {
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
                fontWeight = FontWeight.SemiBold,
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
private fun DeleteGoalDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.savings_delete_title)) },
        text = { Text(stringResource(R.string.savings_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.savings_delete_action),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private const val DEFAULT_TARGET_MONTHS = 6L
