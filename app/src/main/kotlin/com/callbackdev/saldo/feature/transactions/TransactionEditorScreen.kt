package com.callbackdev.saldo.feature.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapVert
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.AmountKeypadHost
import com.callbackdev.saldo.core.designsystem.component.AmountTarget
import com.callbackdev.saldo.core.designsystem.component.AnimatedSection
import com.callbackdev.saldo.core.designsystem.component.DiscardChangesDialog
import com.callbackdev.saldo.core.designsystem.component.EditorBottomBar
import com.callbackdev.saldo.core.designsystem.component.EditorSaveButton
import com.callbackdev.saldo.core.designsystem.component.HeroAmountField
import com.callbackdev.saldo.core.designsystem.component.InfoBanner
import com.callbackdev.saldo.core.designsystem.component.LoadingState
import com.callbackdev.saldo.core.designsystem.component.SaldoDatePickerDialog
import com.callbackdev.saldo.core.designsystem.component.rememberMotionEnabled
import com.callbackdev.saldo.core.designsystem.component.rememberUnsavedChangesGuard
import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.navigation.TransactionEditorRoute
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.LocalDate
import java.time.LocalTime

/** Which modal surface of the editor is open. */
private enum class EditorSheet { NONE, ACCOUNT, TO_ACCOUNT, TAGS, CATEGORY }

/** Which amount the in-app keypad is typing into, if any. */
private enum class AmountField { PRIMARY, SECONDARY }

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
    onNavigateToDuplicate: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionEditorViewModel =
        hiltViewModel<TransactionEditorViewModel, TransactionEditorViewModel.Factory>(
            creationCallback = { factory -> factory.create(route) },
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()
    val remindersEnabled by viewModel.remindersEnabled.collectAsStateWithLifecycle()
    val amountCountervalue by viewModel.amountCountervalue.collectAsStateWithLifecycle()
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
    var activeAmount by rememberSaveable { mutableStateOf<AmountField?>(null) }

    // What the user came here to type: the keypad is up from the first frame.
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && uiState.isNew) activeAmount = AmountField.PRIMARY
    }
    // Back closes the keypad first, before the unsaved-changes guard.
    BackHandler(enabled = activeAmount != null) { activeAmount = null }

    val primaryAmount = AmountTarget(
        value = uiState.amountInput,
        fractionDigits = uiState.amountFractionDigits,
        allowNegative = uiState.allowsNegativeAmount,
        onValueChange = viewModel::onAmountChanged,
    )
    val secondaryAmount = AmountTarget(
        value = uiState.toAmountInput,
        fractionDigits = uiState.toAmountFractionDigits,
        allowNegative = false,
        onValueChange = viewModel::onToAmountChanged,
    )

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
                        // A copy of this movement as a new one, dated now (Fase 39,
                        // F2). Not for adjustments: a balance restatement is not
                        // something to repeat.
                        val sourceId = route.transactionId
                        if (sourceId != null && uiState.type != TransactionType.ADJUSTMENT) {
                            IconButton(onClick = { onNavigateToDuplicate(sourceId) }) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription =
                                    stringResource(R.string.transaction_action_duplicate),
                                )
                            }
                        }
                        // Deletes right away: the app shell shows an undo snackbar
                        // on the screen the editor returns to, so no confirm dialog.
                        IconButton(onClick = viewModel::delete) {
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
                    AmountKeypadHost(
                        target = when (activeAmount) {
                            AmountField.PRIMARY -> primaryAmount
                            AmountField.SECONDARY -> secondaryAmount
                            null -> null
                        },
                        onHide = { activeAmount = null },
                    )
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
                remindersEnabled = remindersEnabled,
                amountCountervalue = amountCountervalue,
                primaryAmount = primaryAmount,
                secondaryAmount = secondaryAmount,
                activeAmount = activeAmount,
                onActivateAmount = { activeAmount = it },
                onCloseKeypad = { activeAmount = null },
                onAccountChipClick = { activeSheet = EditorSheet.ACCOUNT },
                onToAccountChipClick = { activeSheet = EditorSheet.TO_ACCOUNT },
                onDateChipClick = { showDatePicker = true },
                onTimeChipClick = { showTimePicker = true },
                onAddTagClick = { activeSheet = EditorSheet.TAGS },
                onShowAllCategories = { activeSheet = EditorSheet.CATEGORY },
                // No scroll here: the form pins the amount block and scrolls
                // everything below it on its own.
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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
        SaldoDatePickerDialog(
            initialDate = uiState.date,
            onConfirm = { date ->
                viewModel.onDateSelected(date)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
            showQuickDates = true,
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
    remindersEnabled: Boolean,
    amountCountervalue: TransactionEditorViewModel.AmountCountervalue?,
    primaryAmount: AmountTarget,
    secondaryAmount: AmountTarget,
    activeAmount: AmountField?,
    onActivateAmount: (AmountField) -> Unit,
    onCloseKeypad: () -> Unit,
    onAccountChipClick: () -> Unit,
    onToAccountChipClick: () -> Unit,
    onDateChipClick: () -> Unit,
    onTimeChipClick: () -> Unit,
    onAddTagClick: () -> Unit,
    onShowAllCategories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Two zones: the amount block (type selector and hero amounts, what the
    // keypad is typing into) is pinned above the keypad, and the whole rest of
    // the form scrolls in the room that is left. Account, date and categories
    // sit at the top of the scrolling zone, so on any normal screen they are
    // in view on open; on a short one they are a flick away, and that same
    // flick collapses the keypad, buying the space back.
    val scrollState = rememberScrollState()
    val keypadOpen by rememberUpdatedState(activeAmount != null)
    val closeKeypad by rememberUpdatedState(onCloseKeypad)
    // Scrolling toward the fields below means the amount is done: the keypad
    // gives its space back, the way the system IME hides on scroll.
    val collapseKeypadOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0f && keypadOpen) {
                    closeKeypad()
                }
                return Offset.Zero
            }
        }
    }
    Column(modifier = modifier) {
        if (uiState.duplicateAccountReplaced) {
            Spacer(Modifier.height(12.dp))
            InfoBanner(text = stringResource(R.string.transaction_editor_duplicate_account_replaced))
        }
        if (uiState.isRecurring) {
            Spacer(Modifier.height(12.dp))
            InfoBanner(
                text = uiState.recurringRuleName?.let {
                    stringResource(R.string.transaction_editor_recurring_banner, it)
                } ?: stringResource(R.string.transaction_editor_recurring_banner_generic),
            )
        }
        Spacer(Modifier.height(12.dp))
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
        }
        Spacer(Modifier.height(8.dp))
        HeroAmountField(
            target = primaryAmount,
            currencySymbol = uiState.currency?.symbol,
            isError = uiState.showValidation && !uiState.isAmountValid,
            isActive = activeAmount == AmountField.PRIMARY,
            onActivate = { onActivateAmount(AmountField.PRIMARY) },
            showSignToggle = uiState.type == TransactionType.ADJUSTMENT,
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
        // Countervalue of a foreign-currency amount in the primary one, at
        // the rate of the movement's own date (ADR 40): backdating the
        // movement moves the estimate with it.
        if (amountCountervalue != null) {
            CountervalueLabel(
                countervalue = amountCountervalue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AnimatedSection(visible = uiState.isCrossCurrency) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Spacer(Modifier.height(12.dp))
                HeroAmountField(
                    target = secondaryAmount,
                    currencySymbol = uiState.toAccount?.currency?.symbol,
                    isError = uiState.showValidation && !uiState.isToAmountValid,
                    isActive = activeAmount == AmountField.SECONDARY,
                    onActivate = { onActivateAmount(AmountField.SECONDARY) },
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
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .topEdgeFade(scrollState)
                .nestedScroll(collapseKeypadOnScroll)
                .verticalScroll(scrollState),
        ) {
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
            Spacer(Modifier.height(12.dp))
            // Any text field taking focus brings up the system IME, which has to
            // replace the keypad rather than stack with it.
            InlineDescriptionField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChanged,
                modifier = Modifier.onFocusChanged { if (it.isFocused) onCloseKeypad() },
            )
            NoteSection(
                note = uiState.note,
                onNoteChange = viewModel::onNoteChanged,
                onNoteFocused = onCloseKeypad,
            )
            Spacer(Modifier.height(12.dp))
            TagsRow(
                uiState = uiState,
                onToggle = viewModel::onTagToggled,
                onAddClick = onAddTagClick,
            )
            AnimatedSection(visible = uiState.hasReminderSection) {
                ReminderSection(
                    uiState = uiState,
                    remindersEnabled = remindersEnabled,
                    onToggled = viewModel::onReminderChanged,
                )
            }
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
                    CounterpartySection(
                        uiState = uiState,
                        onToggled = viewModel::onCounterpartyToggled,
                        onNameChange = viewModel::onCounterpartyChanged,
                        onFieldFocused = onCloseKeypad,
                    )
                    EditorSwitchRow(
                        title = stringResource(R.string.transaction_editor_exclude_stats),
                        subtitle = stringResource(
                            if (uiState.isCounterparty) {
                                R.string.transaction_editor_exclude_stats_locked
                            } else {
                                R.string.transaction_editor_exclude_stats_hint
                            },
                        ),
                        checked = uiState.isExcludedFromStats,
                        onCheckedChange = viewModel::onExcludedFromStatsChanged,
                        // A loan is out of the statistics by definition: the row
                        // stays visible, showing the value the loan implies.
                        enabled = !uiState.isCounterparty,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * The reminder for a movement dated ahead (ADR 36): one switch, and the lead
 * time is the one already configured for recurring renewals rather than a
 * second setting - "how early do you want to know" is one question. The
 * section only exists while the date is in the future, and it appears the
 * moment the date crosses it, so the option shows up exactly when it means
 * something.
 *
 * With notifications turned off in Settings the switch still works (the choice
 * is recorded on the movement) but says plainly that nothing will arrive: a
 * switch that promises a notification the app will not post is worse than no
 * switch at all.
 */
@Composable
private fun ReminderSection(
    uiState: TransactionEditorUiState,
    remindersEnabled: Boolean,
    onToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        EditorSwitchRow(
            title = stringResource(R.string.transaction_editor_reminder),
            subtitle = stringResource(R.string.transaction_editor_reminder_hint),
            checked = uiState.hasReminder,
            onCheckedChange = onToggled,
        )
        AnimatedSection(visible = uiState.hasReminder && !remindersEnabled) {
            Column(modifier = Modifier.fillMaxWidth()) {
                InfoBanner(text = stringResource(R.string.transaction_editor_reminder_disabled))
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/**
 * Loans between people (ADR 34): one switch, the person's name, and a line
 * saying what the movement means in the direction it already has. An expense
 * with a counterparty is money lent, an income with one is money coming back
 * (or a loan received): the same two verses the ledger already has, so nothing
 * new is invented here, and the reading is spelled out instead of being left to
 * be deduced.
 */
@Composable
private fun CounterpartySection(
    uiState: TransactionEditorUiState,
    onToggled: (Boolean) -> Unit,
    onNameChange: (String) -> Unit,
    onFieldFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        EditorSwitchRow(
            title = stringResource(R.string.transaction_editor_counterparty),
            subtitle = stringResource(R.string.transaction_editor_counterparty_hint_row),
            checked = uiState.isCounterparty,
            onCheckedChange = onToggled,
        )
        AnimatedSection(visible = uiState.isCounterparty) {
            Column(modifier = Modifier.fillMaxWidth()) {
                InlineCounterpartyField(
                    value = uiState.counterparty,
                    onValueChange = onNameChange,
                    isError = uiState.showValidation && !uiState.isCounterpartyValid,
                    modifier = Modifier.onFocusChanged { if (it.isFocused) onFieldFocused() },
                )
                CounterpartySuggestions(
                    suggestions = uiState.counterpartySuggestions,
                    onSelect = onNameChange,
                )
                Spacer(Modifier.height(8.dp))
                InfoBanner(
                    text = stringResource(
                        if (uiState.type == TransactionType.INCOME) {
                            R.string.transaction_editor_counterparty_banner_income
                        } else {
                            R.string.transaction_editor_counterparty_banner_expense
                        },
                    ),
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/**
 * The note, revealed on demand. A movement that already carries one always
 * shows it; on a new movement the field stays behind a quiet text action, so
 * the form the typical expense needs (amount, category, save) is not padded
 * with a large empty box nobody fills in. Once revealed it stays: clearing the
 * text leaves the field open, ready to be written in again.
 */
@Composable
private fun NoteSection(
    note: String,
    onNoteChange: (String) -> Unit,
    onNoteFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    val noteFocus = remember { FocusRequester() }
    val visible = revealed || note.isNotBlank()
    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedSection(visible = !visible) {
            AddNoteAction(
                onClick = { revealed = true },
                // Aligned with the description's text column, not its icon.
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        AnimatedSection(visible = visible) {
            InlineNoteField(
                value = note,
                onValueChange = onNoteChange,
                focusRequester = noteFocus,
                modifier = Modifier.onFocusChanged { if (it.isFocused) onNoteFocused() },
            )
        }
    }
    // Only when the user asked for it: an existing note must not steal focus
    // from the amount when an old movement is opened.
    LaunchedEffect(revealed) {
        if (revealed) noteFocus.requestFocus()
    }
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

/** "≈ 109,87 € (tasso BCE del 29/07)", centered under the hero amount (ADR 40). */
@Composable
private fun CountervalueLabel(
    countervalue: TransactionEditorViewModel.AmountCountervalue,
    modifier: Modifier = Modifier,
) {
    val approx = MoneyFormatter.formatApprox(countervalue.amount, countervalue.currency)
    val text = countervalue.rateDay?.let { day ->
        stringResource(
            R.string.transaction_editor_countervalue,
            approx,
            day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)),
        )
    } ?: approx
    Spacer(Modifier.height(4.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.tabularNumbers(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Suppress("LongParameterList")
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (motionEnabled) Modifier.animateContentSize() else Modifier),
    ) {
        if (uiState.isTransfer) {
            TransferAccounts(
                uiState = uiState,
                onAccountChipClick = onAccountChipClick,
                onToAccountChipClick = onToAccountChipClick,
                onSwapAccounts = onSwapAccounts,
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
        DateTimeChip(
            date = uiState.date,
            time = uiState.time,
            onDateClick = onDateChipClick,
            onTimeClick = onTimeChipClick,
        )
    }
}

/**
 * The two legs of a transfer, stacked and labelled. Two account names plus an
 * arrow never fit one line, and left to wrap they landed on ragged rows with
 * nothing saying which was the source: here they are two full-width rows
 * sharing a label column, so "from" and "to" read down the same edge. The
 * swap button sits beside them and still inverts the legs in one tap.
 */
@Composable
private fun TransferAccounts(
    uiState: TransactionEditorUiState,
    onAccountChipClick: () -> Unit,
    onToAccountChipClick: () -> Unit,
    onSwapAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            TransferLeg(
                label = stringResource(R.string.transaction_editor_leg_from),
                icon = AccountVisuals.icon(uiState.account?.icon),
                accountName = uiState.account?.name,
                placeholder = stringResource(R.string.transaction_editor_from_account),
                isError = uiState.showValidation && !uiState.isAccountValid,
                onClick = onAccountChipClick,
            )
            TransferLeg(
                label = stringResource(R.string.transaction_editor_leg_to),
                icon = AccountVisuals.icon(uiState.toAccount?.icon),
                accountName = uiState.toAccount?.name,
                placeholder = stringResource(R.string.transaction_editor_to_account),
                isError = uiState.showValidation && !uiState.isToAccountValid,
                onClick = onToAccountChipClick,
            )
        }
        IconButton(onClick = onSwapAccounts) {
            Icon(
                imageVector = Icons.Outlined.SwapVert,
                contentDescription = stringResource(R.string.transaction_editor_swap_accounts),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One leg of a transfer: its role on the left, its account chip filling the rest. */
@Suppress("LongParameterList")
@Composable
private fun TransferLeg(
    label: String,
    icon: ImageVector,
    accountName: String?,
    placeholder: String,
    isError: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(LegLabelWidth),
        )
        EditorChip(
            icon = icon,
            label = accountName ?: placeholder,
            isError = isError,
            onClick = onClick,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Enough for "Da"/"A" and "From"/"To", so both chips start on the same edge. */
private val LegLabelWidth = 36.dp

/**
 * Date and time in one chip, but two controls: the left half opens the
 * calendar, the right half the time picker. They share a pill so the form
 * spends one row on both, and the divider plus the clock glyph say that the
 * time is its own tap target - buried inside the date dialog, nobody found it.
 */
@Composable
private fun DateTimeChip(
    date: LocalDate,
    time: LocalTime,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChipHalf(
                icon = Icons.Outlined.CalendarToday,
                label = chipDayLabel(date, LocalDate.now()),
                clickLabel = stringResource(R.string.transaction_editor_change_date),
                onClick = onDateClick,
            )
            VerticalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.height(20.dp),
            )
            ChipHalf(
                icon = Icons.Outlined.Schedule,
                label = timeLabel(time),
                clickLabel = stringResource(R.string.transaction_editor_change_time),
                onClick = onTimeClick,
            )
        }
    }
}

/** One tappable half of the date/time chip: its glyph, its value, its action. */
@Composable
private fun ChipHalf(
    icon: ImageVector,
    label: String,
    clickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(onClickLabel = clickLabel, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Fills a chip that was given a width (a transfer leg), wraps
                // to its text in the free-standing case; a long account name
                // ellipsizes instead of pushing the arrow off the pill.
                modifier = Modifier.weight(1f, fill = false),
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
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
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
            // The grid holds them all now; "All" stays as the comfortable
            // full-screen way in when two rows are not the whole story.
            if (uiState.categories.size > VISIBLE_CATEGORIES) {
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
        Spacer(Modifier.height(8.dp))
        ScrollingCategoryGrid(
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

/** Categories the form's grid shows without scrolling: four per row, two rows. */
private const val VISIBLE_CATEGORIES = 8

/**
 * Fades out the first dps of the scrolling zone once it has scrolled, so the
 * content sliding under the pinned amount block reads as depth instead of a
 * clipped edge. At rest (scroll at the top) nothing is drawn over the content.
 */
private fun Modifier.topEdgeFade(scrollState: ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fadePx = ScrollFadeHeight.toPx()
        // Eases in over the first fadePx of scroll, so the edge never pops.
        val strength = (scrollState.value / fadePx).coerceIn(0f, 1f)
        if (strength > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 1f - strength),
                    1f to Color.Black,
                    endY = fadePx,
                ),
                size = Size(size.width, fadePx),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/** Depth of the fade under the pinned amount block. */
private val ScrollFadeHeight = 24.dp
