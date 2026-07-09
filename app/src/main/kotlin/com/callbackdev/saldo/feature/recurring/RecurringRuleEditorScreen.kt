package com.callbackdev.saldo.feature.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.EditorBottomBar
import com.callbackdev.saldo.core.designsystem.component.EditorSaveButton
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.navigation.RecurringRuleEditorRoute

/**
 * Create/edit form for a subscription (recurring expense): a live preview avatar
 * plus name, amount, account, category, frequency, first-charge and optional end
 * date, color and icon. In edit mode it also hosts the delete flow.
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

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RecurringRuleEditorEvent.Saved,
                RecurringRuleEditorEvent.Deleted,
                RecurringRuleEditorEvent.RuleMissing,
                -> onNavigateBack()
            }
        }
    }

    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (uiState.isNew) {
                                R.string.subscription_editor_title_new
                            } else {
                                R.string.subscription_editor_title_edit
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (!uiState.isLoading) {
                EditorBottomBar {
                    EditorSaveButton(
                        text = stringResource(R.string.subscription_editor_save),
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
        DeleteSubscriptionDialog(
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
    Column(modifier = modifier) {
        Spacer(Modifier.height(16.dp))
        PreviewAvatar(uiState = uiState)
        Spacer(Modifier.height(24.dp))
        NameField(
            name = uiState.name,
            showError = uiState.showValidation && !uiState.isNameValid,
            onNameChanged = viewModel::onNameChanged,
        )
        Spacer(Modifier.height(16.dp))
        AmountField(
            input = uiState.amountInput,
            currency = uiState.currency,
            showError = uiState.showValidation && !uiState.isAmountValid,
            onChanged = viewModel::onAmountChanged,
        )
        Spacer(Modifier.height(16.dp))
        AccountField(
            accounts = uiState.accounts,
            selectedId = uiState.accountId,
            showError = uiState.showValidation && !uiState.isAccountValid,
            onSelected = viewModel::onAccountSelected,
        )
        Spacer(Modifier.height(16.dp))
        CategoryField(
            categories = uiState.categories,
            selectedId = uiState.categoryId,
            onSelected = viewModel::onCategorySelected,
        )
        Spacer(Modifier.height(16.dp))
        FrequencyField(
            frequencies = viewModel.frequencies,
            selected = uiState.frequency,
            onSelected = viewModel::onFrequencySelected,
        )
        Spacer(Modifier.height(16.dp))
        DateField(
            label = stringResource(R.string.subscription_editor_first_charge),
            date = uiState.startDate,
            placeholder = "",
            onClick = onStartDateClick,
        )
        Spacer(Modifier.height(16.dp))
        DateField(
            label = stringResource(R.string.subscription_editor_end_date),
            date = uiState.endDate,
            placeholder = stringResource(R.string.subscription_editor_end_date_none),
            onClick = onEndDateClick,
            onClear = { viewModel.onEndDateSelected(null) },
        )
        SectionLabel(stringResource(R.string.subscription_editor_section_color))
        SubscriptionColorPicker(selected = uiState.color, onColorSelected = viewModel::onColorSelected)
        SectionLabel(stringResource(R.string.subscription_editor_section_icon))
        SubscriptionIconPicker(
            selectedIcon = uiState.icon,
            selectedColor = uiState.color,
            onIconSelected = viewModel::onIconSelected,
        )
        if (!uiState.isNew) {
            Spacer(Modifier.height(32.dp))
            DeleteButton(onDelete = viewModel::requestDelete)
        }
        Spacer(Modifier.height(32.dp))
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
                tint = androidx.compose.ui.graphics.Color.White,
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
private fun DeleteButton(onDelete: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onDelete, modifier = modifier.fillMaxWidth()) {
        Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.subscription_editor_delete),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DeleteSubscriptionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subscription_delete_title)) },
        text = { Text(stringResource(R.string.subscription_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.subscription_editor_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
