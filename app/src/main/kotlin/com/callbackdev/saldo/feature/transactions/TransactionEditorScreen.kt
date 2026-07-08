package com.callbackdev.saldo.feature.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.navigation.TransactionEditorRoute
import java.time.LocalDate

/** Which modal surface of the editor is open. */
private enum class EditorSheet { NONE, ACCOUNT, TO_ACCOUNT, TAGS }

/**
 * Create/edit form for a movement. Optimized for the typical expense: the
 * in-app keypad is active on open, the type defaults to expense, the account
 * to the last used one and the date to today, so recording an expense is
 * FAB, amount, category, save (3 taps plus the amount).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorScreen(
    route: TransactionEditorRoute,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionEditorViewModel =
        hiltViewModel<TransactionEditorViewModel, TransactionEditorViewModel.Factory>(
            creationCallback = { factory -> factory.create(route) },
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                TransactionEditorEvent.Saved,
                TransactionEditorEvent.Deleted,
                TransactionEditorEvent.TransactionMissing,
                -> onNavigateBack()
            }
        }
    }

    var activeSheet by rememberSaveable { mutableStateOf(EditorSheet.NONE) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val decimalSeparator = rememberDecimalSeparator()

    val focusAmount: (AmountTarget) -> Unit = { target ->
        focusManager.clearFocus()
        keyboardController?.hide()
        viewModel.onAmountTargetChanged(target)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(editorTitleRes(uiState))) },
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
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription =
                                stringResource(R.string.transaction_editor_delete),
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = viewModel::save,
                        enabled = !uiState.isLoading,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.amountTarget != AmountTarget.NONE && !uiState.isLoading,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                AmountKeypad(
                    onKey = viewModel::onKeypadKey,
                    onSave = viewModel::save,
                    decimalSeparator = decimalSeparator,
                    showSignToggle = uiState.type == TransactionType.ADJUSTMENT,
                )
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
                decimalSeparator = decimalSeparator,
                onAmountClick = focusAmount,
                onAccountChipClick = { activeSheet = EditorSheet.ACCOUNT },
                onToAccountChipClick = { activeSheet = EditorSheet.TO_ACCOUNT },
                onDateChipClick = { showDatePicker = true },
                onAddTagClick = { activeSheet = EditorSheet.TAGS },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp),
            )
        }
    }

    when (activeSheet) {
        EditorSheet.ACCOUNT -> AccountPickerSheet(
            title = stringResource(
                if (uiState.isTransfer) {
                    R.string.transaction_editor_from_account
                } else {
                    R.string.transaction_editor_account
                },
            ),
            accounts = uiState.accounts,
            selectedAccountId = uiState.account?.id,
            disabledAccountId = uiState.toAccount?.id.takeIf { uiState.isTransfer },
            onSelect = {
                viewModel.onAccountSelected(it.account)
                activeSheet = EditorSheet.NONE
            },
            onDismiss = { activeSheet = EditorSheet.NONE },
        )

        EditorSheet.TO_ACCOUNT -> AccountPickerSheet(
            title = stringResource(R.string.transaction_editor_to_account),
            accounts = uiState.accounts,
            selectedAccountId = uiState.toAccount?.id,
            disabledAccountId = uiState.account?.id,
            onSelect = {
                viewModel.onToAccountSelected(it.account)
                activeSheet = EditorSheet.NONE
            },
            onDismiss = { activeSheet = EditorSheet.NONE },
        )

        EditorSheet.TAGS -> TagPickerSheet(
            allTags = uiState.allTags,
            selectedTagIds = uiState.selectedTags.map { it.id }.toSet(),
            onToggle = viewModel::onTagToggled,
            onCreate = viewModel::onCreateTag,
            onDismiss = { activeSheet = EditorSheet.NONE },
        )

        EditorSheet.NONE -> Unit
    }

    if (showDatePicker) {
        TransactionDatePickerDialog(
            initialDate = uiState.date,
            onConfirm = { date ->
                viewModel.onDateSelected(date)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showDeleteDialog) {
        DeleteTransactionDialog(
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

private fun editorTitleRes(uiState: TransactionEditorUiState): Int = when {
    uiState.isNew -> R.string.transaction_editor_title_new
    uiState.type == TransactionType.TRANSFER -> R.string.transaction_editor_title_edit_transfer
    uiState.type == TransactionType.ADJUSTMENT ->
        R.string.transaction_editor_title_edit_adjustment

    else -> R.string.transaction_editor_title_edit
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun EditorForm(
    uiState: TransactionEditorUiState,
    viewModel: TransactionEditorViewModel,
    decimalSeparator: Char,
    onAmountClick: (AmountTarget) -> Unit,
    onAccountChipClick: () -> Unit,
    onToAccountChipClick: () -> Unit,
    onDateChipClick: () -> Unit,
    onAddTagClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Spacer(Modifier.height(12.dp))
        if (!uiState.isTypeLocked) {
            TypeSelector(
                selected = uiState.type,
                options = if (uiState.isNew) {
                    listOf(
                        TransactionType.EXPENSE,
                        TransactionType.INCOME,
                        TransactionType.TRANSFER,
                    )
                } else {
                    listOf(TransactionType.EXPENSE, TransactionType.INCOME)
                },
                onTypeChanged = viewModel::onTypeChanged,
            )
            Spacer(Modifier.height(16.dp))
        }
        AmountDisplay(
            input = uiState.amountInput,
            currency = uiState.currency,
            isActive = uiState.amountTarget == AmountTarget.AMOUNT,
            isError = uiState.showValidation && !uiState.isAmountValid,
            decimalSeparator = decimalSeparator,
            onClick = { onAmountClick(AmountTarget.AMOUNT) },
            label = if (uiState.isCrossCurrency) {
                stringResource(
                    R.string.transaction_editor_sent_amount,
                    uiState.account?.currency?.currencyCode.orEmpty(),
                )
            } else {
                null
            },
        )
        if (uiState.isCrossCurrency) {
            Spacer(Modifier.height(8.dp))
            AmountDisplay(
                input = uiState.toAmountInput,
                currency = uiState.toAccount?.currency,
                isActive = uiState.amountTarget == AmountTarget.TO_AMOUNT,
                isError = uiState.showValidation && !uiState.isToAmountValid,
                decimalSeparator = decimalSeparator,
                onClick = { onAmountClick(AmountTarget.TO_AMOUNT) },
                label = stringResource(
                    R.string.transaction_editor_received_amount,
                    uiState.toAccount?.currency?.currencyCode.orEmpty(),
                ),
            )
        }
        Spacer(Modifier.height(12.dp))
        ContextChips(
            uiState = uiState,
            onAccountChipClick = onAccountChipClick,
            onToAccountChipClick = onToAccountChipClick,
            onDateChipClick = onDateChipClick,
        )
        if (uiState.hasCategorySection) {
            CategorySection(
                uiState = uiState,
                onSelect = viewModel::onCategorySelected,
            )
        }
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = uiState.description,
            onValueChange = viewModel::onDescriptionChanged,
            label = { Text(stringResource(R.string.transaction_editor_description)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        viewModel.onAmountTargetChanged(AmountTarget.NONE)
                    }
                },
        )
        Spacer(Modifier.height(16.dp))
        TagsRow(uiState = uiState, onToggle = viewModel::onTagToggled, onAddClick = onAddTagClick)
        if (uiState.hasCategorySection) {
            Spacer(Modifier.height(8.dp))
            if (uiState.type == TransactionType.INCOME) {
                EditorSwitchRow(
                    title = stringResource(R.string.transaction_editor_refund),
                    subtitle = stringResource(R.string.transaction_editor_refund_hint),
                    checked = uiState.isRefund,
                    onCheckedChange = viewModel::onRefundChanged,
                )
            }
            EditorSwitchRow(
                title = stringResource(R.string.transaction_editor_exclude_stats),
                subtitle = stringResource(R.string.transaction_editor_exclude_stats_hint),
                checked = uiState.isExcludedFromStats,
                onCheckedChange = viewModel::onExcludedFromStatsChanged,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ContextChips(
    uiState: TransactionEditorUiState,
    onAccountChipClick: () -> Unit,
    onToAccountChipClick: () -> Unit,
    onDateChipClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accountError = uiState.showValidation && !uiState.isAccountValid
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (uiState.isTransfer) {
            EditorChip(
                icon = accountChipIcon(uiState.account),
                label = uiState.account?.name
                    ?: stringResource(R.string.transaction_editor_from_account),
                isError = accountError,
                onClick = onAccountChipClick,
            )
            EditorChip(
                icon = accountChipIcon(uiState.toAccount),
                label = uiState.toAccount?.name
                    ?: stringResource(R.string.transaction_editor_to_account),
                isError = uiState.showValidation && !uiState.isToAccountValid,
                onClick = onToAccountChipClick,
            )
        } else {
            EditorChip(
                icon = accountChipIcon(uiState.account),
                label = uiState.account?.name
                    ?: stringResource(R.string.transaction_editor_account),
                isError = accountError,
                onClick = onAccountChipClick,
            )
        }
        EditorChip(
            icon = Icons.Outlined.CalendarToday,
            label = dayLabel(uiState.date, LocalDate.now()),
            isError = false,
            onClick = onDateChipClick,
        )
    }
}

private fun accountChipIcon(account: Account?): ImageVector = AccountVisuals.icon(account?.icon)

@Composable
private fun EditorChip(
    icon: ImageVector,
    label: String,
    isError: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = 1.dp,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun CategorySection(
    uiState: TransactionEditorUiState,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasError = uiState.showValidation && !uiState.isCategoryValid
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.transaction_editor_category),
            style = MaterialTheme.typography.titleSmall,
            color = if (hasError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
        )
        if (hasError) {
            Text(
                text = stringResource(R.string.transaction_editor_category_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        CategoryGrid(
            categories = uiState.categories,
            selectedId = uiState.categoryId,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun TagsRow(
    uiState: TransactionEditorUiState,
    onToggle: (Tag) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        uiState.selectedTags.forEach { tag ->
            InputChip(
                selected = true,
                onClick = { onToggle(tag) },
                label = { Text(tag.name) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.transaction_editor_remove_tag),
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
        InputChip(
            selected = false,
            onClick = onAddClick,
            label = { Text(stringResource(R.string.transaction_editor_add_tag)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
    }
}
