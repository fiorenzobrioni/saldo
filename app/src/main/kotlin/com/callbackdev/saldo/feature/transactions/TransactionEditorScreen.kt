package com.callbackdev.saldo.feature.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.ArrowRightAlt
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.DiscardChangesDialog
import com.callbackdev.saldo.core.designsystem.component.EditorBottomBar
import com.callbackdev.saldo.core.designsystem.component.EditorSaveButton
import com.callbackdev.saldo.core.designsystem.component.InfoBanner
import com.callbackdev.saldo.core.designsystem.component.LoadingState
import com.callbackdev.saldo.core.designsystem.component.rememberMotionEnabled
import com.callbackdev.saldo.core.designsystem.component.rememberUnsavedChangesGuard
import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.navigation.TransactionEditorRoute
import java.time.LocalDate

/** Which modal surface of the editor is open. */
private enum class EditorSheet { NONE, ACCOUNT, TO_ACCOUNT, TAGS, CATEGORY }

/**
 * Create/edit form for a movement. The amount is the prominent focal point at
 * the top; the primary save action is a full-width button in the bottom bar,
 * kept above the keyboard by the window insets. Optimized for the typical
 * expense: the amount field takes focus on open (so the keyboard is up
 * immediately), the type defaults to expense, the account to the last used one
 * and the date to today.
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
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()
    val guard = rememberUnsavedChangesGuard(hasUnsavedChanges, onNavigateBack)

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

    DiscardChangesDialog(guard)

    var activeSheet by rememberSaveable { mutableStateOf(EditorSheet.NONE) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(editorTitleRes(uiState))) },
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
                    EditorSaveButton(
                        text = stringResource(saveLabelRes(uiState.type)),
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
                onAccountChipClick = { activeSheet = EditorSheet.ACCOUNT },
                onToAccountChipClick = { activeSheet = EditorSheet.TO_ACCOUNT },
                onDateChipClick = { showDatePicker = true },
                onTimeChipClick = { showTimePicker = true },
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

    if (showTimePicker) {
        TransactionTimePickerDialog(
            initialTime = uiState.time,
            onConfirm = { time ->
                viewModel.onTimeSelected(time)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
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
    onAccountChipClick: () -> Unit,
    onToAccountChipClick: () -> Unit,
    onDateChipClick: () -> Unit,
    onTimeChipClick: () -> Unit,
    onAddTagClick: () -> Unit,
    onShowAllCategories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val amountFocus = remember { FocusRequester() }
    // On a new movement the amount is what the user came to type: take focus so
    // the keyboard is up immediately, matching the old always-on keypad.
    LaunchedEffect(Unit) {
        if (uiState.isNew) amountFocus.requestFocus()
    }
    Column(modifier = modifier) {
        if (uiState.isRecurring) {
            Spacer(Modifier.height(16.dp))
            InfoBanner(
                text = uiState.recurringRuleName?.let {
                    stringResource(R.string.transaction_editor_recurring_banner, it)
                } ?: stringResource(R.string.transaction_editor_recurring_banner_generic),
            )
        }
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
        Spacer(Modifier.height(8.dp))
        AmountField(
            input = uiState.amountInput,
            currency = uiState.currency,
            isError = uiState.showValidation && !uiState.isAmountValid,
            showSignToggle = uiState.type == TransactionType.ADJUSTMENT,
            onValueChange = viewModel::onAmountChanged,
            focusRequester = amountFocus,
            errorText = stringResource(R.string.transaction_editor_amount_error),
            label = if (uiState.isCrossCurrency) {
                stringResource(
                    R.string.transaction_editor_sent_amount,
                    uiState.account?.currency?.currencyCode.orEmpty(),
                )
            } else {
                null
            },
        )
        AnimatedSection(visible = uiState.isCrossCurrency) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Spacer(Modifier.height(12.dp))
                AmountField(
                    input = uiState.toAmountInput,
                    currency = uiState.toAccount?.currency,
                    isError = uiState.showValidation && !uiState.isToAmountValid,
                    showSignToggle = false,
                    onValueChange = viewModel::onToAmountChanged,
                    errorText = stringResource(R.string.transaction_editor_amount_error),
                    compact = true,
                    label = stringResource(
                        R.string.transaction_editor_received_amount,
                        uiState.toAccount?.currency?.currencyCode.orEmpty(),
                    ),
                )
                ImpliedRateLabel(uiState)
            }
        }
        Spacer(Modifier.height(16.dp))
        ContextChips(
            uiState = uiState,
            onAccountChipClick = onAccountChipClick,
            onToAccountChipClick = onToAccountChipClick,
            onDateChipClick = onDateChipClick,
            onTimeChipClick = onTimeChipClick,
            onSwapAccounts = viewModel::onSwapAccounts,
        )
        AnimatedSection(visible = uiState.hasCategorySection) {
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
        )
        Spacer(Modifier.height(12.dp))
        TagsRow(uiState = uiState, onToggle = viewModel::onTagToggled, onAddClick = onAddTagClick)
        AnimatedSection(visible = uiState.hasCategorySection) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(8.dp))
                AnimatedSection(visible = uiState.type == TransactionType.INCOME) {
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
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Shared enter/exit for the form sections that appear and disappear when the
 * movement type changes; snaps to the final state when system animations are
 * off.
 */
@Composable
private fun AnimatedSection(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val motionEnabled = rememberMotionEnabled()
    AnimatedVisibility(
        visible = visible,
        enter = if (motionEnabled) fadeIn() + expandVertically() else EnterTransition.None,
        exit = if (motionEnabled) fadeOut() + shrinkVertically() else ExitTransition.None,
        modifier = modifier,
        content = content,
    )
}

/**
 * Rate implied by the two legs of a cross-currency transfer ("1 EUR ≈ 1,08
 * CHF"): a plausibility check for the typed amounts, computed locally.
 */
@Composable
private fun ImpliedRateLabel(uiState: TransactionEditorUiState, modifier: Modifier = Modifier) {
    val rate = uiState.impliedRate ?: return
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(
            R.string.transaction_editor_implied_rate,
            uiState.account?.currency?.currencyCode.orEmpty(),
            rateLabel(rate),
            uiState.toAccount?.currency?.currencyCode.orEmpty(),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun ContextChips(
    uiState: TransactionEditorUiState,
    onAccountChipClick: () -> Unit,
    onToAccountChipClick: () -> Unit,
    onDateChipClick: () -> Unit,
    onTimeChipClick: () -> Unit,
    onSwapAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accountError = uiState.showValidation && !uiState.isAccountValid
    val motionEnabled = rememberMotionEnabled()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .fillMaxWidth()
            .then(if (motionEnabled) Modifier.animateContentSize() else Modifier),
    ) {
        if (uiState.isTransfer) {
            EditorChip(
                icon = AccountVisuals.icon(uiState.account?.icon),
                label = uiState.account?.name
                    ?: stringResource(R.string.transaction_editor_from_account),
                isError = accountError,
                onClick = onAccountChipClick,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            // The arrow states the direction of the transfer; tapping it swaps
            // the two accounts, the quickest fix for a reversed entry.
            IconButton(
                onClick = onSwapAccounts,
                modifier = Modifier.align(Alignment.CenterVertically),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowRightAlt,
                    contentDescription =
                    stringResource(R.string.transaction_editor_swap_accounts),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            EditorChip(
                icon = AccountVisuals.icon(uiState.toAccount?.icon),
                label = uiState.toAccount?.name
                    ?: stringResource(R.string.transaction_editor_to_account),
                isError = uiState.showValidation && !uiState.isToAccountValid,
                onClick = onToAccountChipClick,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        } else {
            EditorChip(
                icon = AccountVisuals.icon(uiState.account?.icon),
                label = uiState.account?.name
                    ?: stringResource(R.string.transaction_editor_account),
                isError = accountError,
                onClick = onAccountChipClick,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        EditorChip(
            icon = Icons.Outlined.CalendarToday,
            label = chipDayLabel(uiState.date, LocalDate.now()),
            isError = false,
            onClick = onDateChipClick,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        EditorChip(
            icon = Icons.Outlined.Schedule,
            label = timeLabel(uiState.time),
            isError = false,
            onClick = onTimeChipClick,
            modifier = Modifier.align(Alignment.CenterVertically),
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
        AnimatedSection(visible = hasError) {
            Text(
                text = stringResource(R.string.transaction_editor_category_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(12.dp))
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
