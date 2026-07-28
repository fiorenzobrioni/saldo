package com.callbackdev.saldo.feature.accounts

import com.callbackdev.saldo.core.designsystem.visuals.infoRes
import com.callbackdev.saldo.core.designsystem.visuals.labelRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.AmountKeypadHost
import com.callbackdev.saldo.core.designsystem.component.AmountTarget
import com.callbackdev.saldo.core.designsystem.component.AmountTextField
import com.callbackdev.saldo.core.designsystem.component.AnimatedSection
import com.callbackdev.saldo.core.designsystem.component.DiscardChangesDialog
import com.callbackdev.saldo.core.designsystem.component.EditorBottomBar
import com.callbackdev.saldo.core.designsystem.component.InfoBanner
import com.callbackdev.saldo.core.designsystem.component.EditorSaveButton
import com.callbackdev.saldo.core.designsystem.component.LoadingState
import com.callbackdev.saldo.core.designsystem.component.rememberUnsavedChangesGuard
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.navigation.AccountEditorRoute
import java.util.Currency

/**
 * Create/edit form for an account: name, type, currency, initial balance,
 * color, icon and inclusion in the total balance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditorScreen(
    route: AccountEditorRoute,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountEditorViewModel =
        hiltViewModel<AccountEditorViewModel, AccountEditorViewModel.Factory>(
            creationCallback = { factory -> factory.create(route) },
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val linkedCandidates by viewModel.linkedAccountCandidates.collectAsStateWithLifecycle()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()
    val guard = rememberUnsavedChangesGuard(hasUnsavedChanges, onNavigateBack)
    val snackbarHostState = remember { SnackbarHostState() }
    val writeFailedMessage = stringResource(R.string.editor_write_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AccountEditorEvent.Saved,
                AccountEditorEvent.AccountMissing,
                -> onNavigateBack()

                AccountEditorEvent.WriteFailed -> snackbarHostState.showSnackbar(writeFailedMessage)
            }
        }
    }

    DiscardChangesDialog(guard)

    // The name comes first on this form, so the keypad waits to be asked for.
    // One flag is enough for both amounts: the initial balance and the credit
    // limit never coexist, exactly as their two sections cross-fade.
    var keypadOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = keypadOpen) { keypadOpen = false }
    val fractionDigits = MoneyMapper.fractionDigits(uiState.currency)
    val initialBalanceTarget = AmountTarget(
        value = uiState.initialBalanceInput,
        fractionDigits = fractionDigits,
        // An account can legitimately start in the red (an overdraft).
        allowNegative = true,
        onValueChange = viewModel::onInitialBalanceChanged,
    )
    val creditLimitTarget = AmountTarget(
        value = uiState.creditLimitInput,
        fractionDigits = fractionDigits,
        allowNegative = false,
        onValueChange = viewModel::onCreditLimitChanged,
    )
    val amountTarget = if (uiState.isCreditCard) creditLimitTarget else initialBalanceTarget

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        stringResource(
                            if (uiState.isNew) {
                                R.string.account_editor_title_new
                            } else {
                                R.string.account_editor_title_edit
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
            )
        },
        bottomBar = {
            if (!uiState.isLoading) {
                EditorBottomBar {
                    AmountKeypadHost(
                        target = amountTarget.takeIf { keypadOpen },
                        onHide = { keypadOpen = false },
                    )
                    EditorSaveButton(
                        text = stringResource(R.string.account_editor_save),
                        onClick = viewModel::save,
                        // Always tappable: a failed tap surfaces the field errors,
                        // which explains more than a disabled button ever could.
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
                currencies = viewModel.currencies,
                linkedCandidates = linkedCandidates,
                onNameChanged = viewModel::onNameChanged,
                onTypeChanged = viewModel::onTypeChanged,
                onCurrencyChanged = viewModel::onCurrencyChanged,
                initialBalanceTarget = initialBalanceTarget,
                creditLimitTarget = creditLimitTarget,
                onActivateAmount = { keypadOpen = true },
                onCloseKeypad = { keypadOpen = false },
                onColorSelected = viewModel::onColorSelected,
                onIconSelected = viewModel::onIconSelected,
                onIncludedInTotalChanged = viewModel::onIncludedInTotalChanged,
                onIncludedInBudgetChanged = viewModel::onIncludedInBudgetChanged,
                onStatementClosingDayChanged = viewModel::onStatementClosingDayChanged,
                onPaymentDueDayChanged = viewModel::onPaymentDueDayChanged,
                onLinkedAccountChanged = viewModel::onLinkedAccountChanged,
                onStatementAutoPostChanged = viewModel::onStatementAutoPostChanged,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun EditorForm(
    uiState: AccountEditorUiState,
    currencies: List<Currency>,
    linkedCandidates: List<Account>,
    onNameChanged: (String) -> Unit,
    onTypeChanged: (AccountType) -> Unit,
    onCurrencyChanged: (Currency) -> Unit,
    initialBalanceTarget: AmountTarget,
    creditLimitTarget: AmountTarget,
    onActivateAmount: () -> Unit,
    onCloseKeypad: () -> Unit,
    onColorSelected: (Int) -> Unit,
    onIconSelected: (String) -> Unit,
    onIncludedInTotalChanged: (Boolean) -> Unit,
    onIncludedInBudgetChanged: (Boolean) -> Unit,
    onStatementClosingDayChanged: (Int) -> Unit,
    onPaymentDueDayChanged: (Int) -> Unit,
    onLinkedAccountChanged: (Long?) -> Unit,
    onStatementAutoPostChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Spacer(Modifier.height(8.dp))
        NameField(
            name = uiState.name,
            showError = uiState.showValidation && !uiState.isNameValid,
            onNameChanged = onNameChanged,
            // The name field brings the system IME up: the keypad steps aside.
            modifier = Modifier.onFocusChanged { if (it.isFocused) onCloseKeypad() },
        )
        SectionLabel(stringResource(R.string.account_editor_section_type))
        TypeChips(selected = uiState.type, onTypeChanged = onTypeChanged)
        Spacer(Modifier.height(12.dp))
        // What the selected type is for and how to use it, right under the
        // selector so the choice and its explanation read as one unit.
        InfoBanner(stringResource(uiState.type.infoRes()))
        Spacer(Modifier.height(16.dp))
        CurrencyField(
            selected = uiState.currency,
            currencies = currencies,
            locked = uiState.isCurrencyLocked,
            onCurrencyChanged = onCurrencyChanged,
        )
        Spacer(Modifier.height(16.dp))
        // A credit card has no initial balance: it always starts at zero and
        // pre-existing debt is entered via a balance adjustment (see the
        // guidance in the credit card section). The two sections cross-fade
        // when the type changes instead of snapping.
        AnimatedSection(visible = !uiState.isCreditCard) {
            // A loan's initial balance is today's remaining debt: mandatory and
            // negative, with its own hint and an explicit error on save.
            val showLoanError = uiState.showValidation && !uiState.isLoanBalanceValid
            AmountTextField(
                target = initialBalanceTarget,
                label = stringResource(R.string.account_editor_initial_balance),
                onActivate = onActivateAmount,
                suffix = uiState.currency.symbol,
                supportingText = when {
                    showLoanError -> stringResource(R.string.account_editor_loan_balance_error)
                    uiState.isLoan -> stringResource(R.string.account_editor_initial_balance_hint_loan)
                    else -> stringResource(R.string.account_editor_initial_balance_hint)
                },
                showSignToggle = true,
                isError = showLoanError,
            )
        }
        AnimatedSection(visible = uiState.isCreditCard) {
            CreditCardSection(
                uiState = uiState,
                linkedCandidates = linkedCandidates,
                onStatementClosingDayChanged = onStatementClosingDayChanged,
                onPaymentDueDayChanged = onPaymentDueDayChanged,
                onLinkedAccountChanged = onLinkedAccountChanged,
                creditLimitTarget = creditLimitTarget,
                onActivateCreditLimit = onActivateAmount,
                onStatementAutoPostChanged = onStatementAutoPostChanged,
            )
        }
        SectionLabel(stringResource(R.string.account_editor_section_color))
        ColorPicker(selected = uiState.color, onColorSelected = onColorSelected)
        SectionLabel(stringResource(R.string.account_editor_section_icon))
        IconPicker(
            selectedIcon = uiState.icon,
            selectedColor = uiState.color,
            onIconSelected = onIconSelected,
        )
        Spacer(Modifier.height(8.dp))
        InclusionToggleRow(
            titleRes = R.string.account_editor_include_in_total,
            hintRes = R.string.account_editor_include_in_total_hint,
            included = uiState.isIncludedInTotal,
            onChanged = onIncludedInTotalChanged,
        )
        InclusionToggleRow(
            titleRes = R.string.account_editor_include_in_budget,
            hintRes = R.string.account_editor_include_in_budget_hint,
            included = uiState.isIncludedInBudget,
            onChanged = onIncludedInBudgetChanged,
        )
        Spacer(Modifier.height(32.dp))
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
private fun NameField(
    name: String,
    showError: Boolean,
    onNameChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChanged,
        label = { Text(stringResource(R.string.account_editor_name)) },
        singleLine = true,
        isError = showError,
        supportingText = if (showError) {
            { Text(stringResource(R.string.account_editor_name_error)) }
        } else {
            null
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun TypeChips(
    selected: AccountType,
    onTypeChanged: (AccountType) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        AccountType.entries.forEach { type ->
            val isSelected = type == selected
            FilterChip(
                selected = isSelected,
                onClick = { onTypeChanged(type) },
                label = { Text(stringResource(type.labelRes())) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyField(
    selected: Currency,
    currencies: List<Currency>,
    locked: Boolean,
    onCurrencyChanged: (Currency) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && !locked,
        onExpandedChange = { if (!locked) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = currencyLabel(selected),
            onValueChange = {},
            readOnly = true,
            enabled = !locked,
            label = { Text(stringResource(R.string.account_editor_currency)) },
            supportingText = if (locked) {
                { Text(stringResource(R.string.account_editor_currency_locked)) }
            } else {
                null
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && !locked)
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !locked)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded && !locked,
            onDismissRequest = { expanded = false },
        ) {
            currencies.forEach { currency ->
                DropdownMenuItem(
                    text = { Text(currencyLabel(currency)) },
                    onClick = {
                        onCurrencyChanged(currency)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun currencyLabel(currency: Currency): String =
    "${currency.currencyCode} - ${currency.displayName}"

/** A full-width toggle row (title + hint + trailing switch), the whole row tappable. */
@Composable
private fun InclusionToggleRow(
    titleRes: Int,
    hintRes: Int,
    included: Boolean,
    onChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .toggleable(
                value = included,
                role = Role.Switch,
                onValueChange = onChanged,
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(hintRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(16.dp))
        Switch(checked = included, onCheckedChange = null)
    }
}
