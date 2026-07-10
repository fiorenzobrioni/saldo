package com.callbackdev.saldo.feature.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.EditorBottomBar
import com.callbackdev.saldo.core.designsystem.component.EditorSaveButton
import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.navigation.TransactionEditorRoute
import java.time.LocalDate

/** Which modal surface of the editor is open. */
private enum class EditorSheet { NONE, ACCOUNT, TO_ACCOUNT, TAGS, CATEGORY }

/**
 * Create/edit form for a movement. The amount is the borderless focal point of
 * the screen with the in-app keypad below it; the primary save action is a
 * full-width button under the keypad. Optimized for the typical expense: the
 * keypad is active on open, the type defaults to expense, the account to the
 * last used one and the date to today.
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

    val snackbarHostState = remember { SnackbarHostState() }
    val writeFailedMessage = stringResource(R.string.editor_write_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                TransactionEditorEvent.Saved,
                TransactionEditorEvent.Deleted,
                TransactionEditorEvent.TransactionMissing,
                -> onNavigateBack()

                TransactionEditorEvent.WriteFailed ->
                    snackbarHostState.showSnackbar(writeFailedMessage)
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                },
            )
        },
        bottomBar = {
            if (!uiState.isLoading) {
                EditorBottomBar {
                    AnimatedVisibility(
                        visible = uiState.amountTarget != AmountTarget.NONE,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        AmountKeypad(
                            onKey = viewModel::onKeypadKey,
                            decimalSeparator = decimalSeparator,
                            showSignToggle = uiState.type == TransactionType.ADJUSTMENT,
                        )
                    }
                    EditorSaveButton(
                        text = stringResource(saveLabelRes(uiState.type)),
                        onClick = viewModel::save,
                        enabled = uiState.isAmountValid,
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
                decimalSeparator = decimalSeparator,
                onAmountClick = focusAmount,
                onAccountChipClick = { activeSheet = EditorSheet.ACCOUNT },
                onToAccountChipClick = { activeSheet = EditorSheet.TO_ACCOUNT },
                onDateChipClick = { showDatePicker = true },
                onAddTagClick = { activeSheet = EditorSheet.TAGS },
                onShowAllCategories = { activeSheet = EditorSheet.CATEGORY },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
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

        EditorSheet.CATEGORY -> CategoryPickerSheet(
            categories = uiState.categories,
            selectedId = uiState.categoryId,
            onSelect = {
                viewModel.onCategorySelected(it)
                activeSheet = EditorSheet.NONE
            },
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
    // Generic "new movement" (from the ledger FAB): the type selector is visible,
    // so a specific title would be misleading. Quick actions preset the type and
    // hide the selector, so they keep the specific, confirming title.
    uiState.isNew && !uiState.isTypePreset -> R.string.transaction_editor_title_new
    uiState.isNew && uiState.type == TransactionType.INCOME ->
        R.string.transaction_editor_title_new_income

    uiState.isNew && uiState.type == TransactionType.TRANSFER ->
        R.string.transaction_editor_title_new_transfer

    uiState.isNew -> R.string.transaction_editor_title_new_expense
    uiState.type == TransactionType.TRANSFER -> R.string.transaction_editor_title_edit_transfer
    uiState.type == TransactionType.ADJUSTMENT ->
        R.string.transaction_editor_title_edit_adjustment

    else -> R.string.transaction_editor_title_edit
}

private fun saveLabelRes(type: TransactionType): Int = when (type) {
    TransactionType.EXPENSE -> R.string.transaction_editor_save_expense
    TransactionType.INCOME -> R.string.transaction_editor_save_income
    TransactionType.TRANSFER -> R.string.transaction_editor_save_transfer
    TransactionType.ADJUSTMENT -> R.string.transaction_editor_save_adjustment
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
    onShowAllCategories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Spacer(Modifier.height(16.dp))
        val showTypeSelector = !uiState.isTypeLocked && !(uiState.isNew && uiState.isTypePreset)
        if (showTypeSelector) {
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
        Spacer(Modifier.height(16.dp))
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
                onShowAll = onShowAllCategories,
            )
        }
        Spacer(Modifier.height(20.dp))
        InlineDescriptionField(
            value = uiState.description,
            onValueChange = viewModel::onDescriptionChanged,
            modifier = Modifier.onFocusChanged { state ->
                if (state.isFocused) {
                    viewModel.onAmountTargetChanged(AmountTarget.NONE)
                }
            },
        )
        Spacer(Modifier.height(12.dp))
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
        Spacer(Modifier.height(24.dp))
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
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (uiState.isTransfer) {
            EditorChip(
                icon = AccountVisuals.icon(uiState.account?.icon),
                label = uiState.account?.name
                    ?: stringResource(R.string.transaction_editor_from_account),
                isError = accountError,
                onClick = onAccountChipClick,
            )
            EditorChip(
                icon = AccountVisuals.icon(uiState.toAccount?.icon),
                label = uiState.toAccount?.name
                    ?: stringResource(R.string.transaction_editor_to_account),
                isError = uiState.showValidation && !uiState.isToAccountValid,
                onClick = onToAccountChipClick,
            )
        } else {
            EditorChip(
                icon = AccountVisuals.icon(uiState.account?.icon),
                label = uiState.account?.name
                    ?: stringResource(R.string.transaction_editor_account),
                isError = accountError,
                onClick = onAccountChipClick,
            )
        }
        EditorChip(
            icon = Icons.Outlined.CalendarToday,
            label = chipDayLabel(uiState.date, LocalDate.now()),
            isError = false,
            onClick = onDateChipClick,
        )
    }
}

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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (isError) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        } else {
            null
        },
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
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
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CategorySection(
    uiState: TransactionEditorUiState,
    onSelect: (Long) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasError = uiState.showValidation && !uiState.isCategoryValid
    val visible = remember(uiState.categories, uiState.categoryId) {
        visibleCategories(uiState.categories, uiState.categoryId)
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.transaction_editor_category),
                style = MaterialTheme.typography.titleSmall,
                color = if (hasError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.weight(1f),
            )
            if (uiState.categories.size > CATEGORY_GRID_CAP) {
                TextButton(
                    onClick = onShowAll,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.transaction_editor_categories_all))
                }
            }
        }
        if (hasError) {
            Text(
                text = stringResource(R.string.transaction_editor_category_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }
        CategoryGrid(
            categories = visible,
            selectedId = uiState.categoryId,
            onSelect = onSelect,
        )
    }
}

/**
 * The categories shown inline: the first [CATEGORY_GRID_CAP] by order, always
 * keeping the selected one visible; "All" opens the full list in a sheet.
 */
private fun visibleCategories(categories: List<Category>, selectedId: Long?): List<Category> {
    if (categories.size <= CATEGORY_GRID_CAP) return categories
    val head = categories.take(CATEGORY_GRID_CAP)
    val selected = categories.firstOrNull { it.id == selectedId }
    return if (selected == null || head.any { it.id == selected.id }) {
        head
    } else {
        head.dropLast(1) + selected
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

private const val CATEGORY_GRID_CAP = 8
