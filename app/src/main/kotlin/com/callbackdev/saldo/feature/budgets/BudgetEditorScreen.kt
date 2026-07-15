package com.callbackdev.saldo.feature.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.DiscardChangesDialog
import com.callbackdev.saldo.core.designsystem.component.EditorBottomBar
import com.callbackdev.saldo.core.designsystem.component.EditorSaveButton
import com.callbackdev.saldo.core.designsystem.component.rememberUnsavedChangesGuard
import com.callbackdev.saldo.navigation.BudgetEditorRoute

/**
 * Create/edit form for a budget: what it caps (the whole month or one expense
 * category; fixed in edit mode) and the monthly limit in the primary
 * currency. Edit mode also hosts the delete flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetEditorScreen(
    route: BudgetEditorRoute,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BudgetEditorViewModel =
        hiltViewModel<BudgetEditorViewModel, BudgetEditorViewModel.Factory>(
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
                BudgetEditorEvent.Saved,
                BudgetEditorEvent.Deleted,
                BudgetEditorEvent.BudgetMissing,
                -> onNavigateBack()

                BudgetEditorEvent.WriteFailed -> snackbarHostState.showSnackbar(writeFailedMessage)
            }
        }
    }

    DiscardChangesDialog(guard)

    if (uiState.showDeleteDialog) {
        DeleteBudgetDialog(
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDeleteDialog,
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (uiState.isNew) {
                                R.string.budgets_editor_title_new
                            } else {
                                R.string.budgets_editor_title_edit
                            },
                        ),
                    )
                },
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
                                contentDescription = stringResource(R.string.budgets_editor_delete),
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
                        text = stringResource(R.string.budgets_editor_save),
                        onClick = viewModel::save,
                        enabled = !uiState.isLoading && (uiState.isNew.not() || uiState.scopeOptions.isNotEmpty()),
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
                onScopeSelected = viewModel::onScopeSelected,
                onAmountChanged = viewModel::onAmountChanged,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun EditorForm(
    uiState: BudgetEditorUiState,
    onScopeSelected: (BudgetScope) -> Unit,
    onAmountChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionLabel(stringResource(R.string.budgets_editor_section_scope))
        if (uiState.isNew) {
            if (uiState.scopeOptions.isEmpty()) {
                Text(
                    text = stringResource(R.string.budgets_editor_no_scopes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ScopeChips(
                    options = uiState.scopeOptions,
                    selected = uiState.scope,
                    onScopeSelected = onScopeSelected,
                )
            }
        } else {
            Text(
                text = uiState.scope.label(),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.budgets_editor_scope_locked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
        AmountField(
            uiState = uiState,
            onAmountChanged = onAmountChanged,
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ScopeChips(
    options: List<BudgetScope>,
    selected: BudgetScope?,
    onScopeSelected: (BudgetScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        options.forEach { scope ->
            val isSelected = scope == selected
            FilterChip(
                selected = isSelected,
                onClick = { onScopeSelected(scope) },
                label = { Text(scope.label()) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun AmountField(
    uiState: BudgetEditorUiState,
    onAmountChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showError = uiState.showValidation && !uiState.isAmountValid
    OutlinedTextField(
        value = uiState.amountInput,
        onValueChange = onAmountChanged,
        label = { Text(stringResource(R.string.budgets_editor_amount)) },
        placeholder = { Text(stringResource(R.string.editor_amount_placeholder)) },
        suffix = { Text(uiState.currency.symbol) },
        singleLine = true,
        isError = showError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = {
            Text(
                stringResource(
                    if (showError) {
                        R.string.budgets_editor_amount_error
                    } else {
                        R.string.budgets_editor_amount_hint
                    },
                ),
            )
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun DeleteBudgetDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budgets_delete_dialog_title)) },
        text = { Text(stringResource(R.string.budgets_delete_dialog_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.budgets_editor_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
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
private fun BudgetScope?.label(): String = when (this) {
    BudgetScope.Overall, null -> stringResource(R.string.budgets_editor_scope_overall)
    is BudgetScope.ForCategory -> category.name
}
