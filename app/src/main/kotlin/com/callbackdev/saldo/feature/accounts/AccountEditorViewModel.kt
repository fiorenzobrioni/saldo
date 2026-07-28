package com.callbackdev.saldo.feature.accounts

import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.CreditCardConfig
import com.callbackdev.saldo.core.domain.creditcard.BillingCycleCalculator
import com.callbackdev.saldo.core.domain.model.CurrencyCatalog
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import com.callbackdev.saldo.core.domain.model.primaryCurrency
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.navigation.AccountEditorRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.util.Currency

/** Immutable UI state of the account editor form. */
data class AccountEditorUiState(
    val isLoading: Boolean,
    val isNew: Boolean = true,
    val name: String = "",
    val type: AccountType = AccountType.CHECKING,
    val currency: Currency,
    /** True when the account already has movements: its currency cannot change. */
    val isCurrencyLocked: Boolean = false,
    val initialBalanceInput: String = "",
    val color: Int,
    val icon: String,
    val isIncludedInTotal: Boolean = true,
    val isIncludedInBudget: Boolean = true,
    // --- Credit card configuration (shown only when [type] is CREDIT_CARD) ---
    val statementClosingDay: Int = DEFAULT_CLOSING_DAY,
    val paymentDueDay: Int = DEFAULT_DUE_DAY,
    /** Account charged for the statement; null until the user picks one. */
    val linkedAccountId: Long? = null,
    val creditLimitInput: String = "",
    /** True auto-posts the statement; false waits for confirmation (default). */
    val statementAutoPost: Boolean = false,
    /** Set on a failed save attempt to surface field errors. */
    val showValidation: Boolean = false,
) {
    val isNameValid: Boolean get() = name.isNotBlank()
    val isCreditCard: Boolean get() = type == AccountType.CREDIT_CARD
    val isLoan: Boolean get() = type == AccountType.LOAN

    /**
     * A loan's initial balance is today's remaining debt, so it is mandatory
     * and strictly negative (the opposite of the credit card rule, where the
     * initial balance is forbidden: there the debt is built by the cycle's
     * movements, here it pre-exists the app). Always true for other types.
     */
    val isLoanBalanceValid: Boolean
        get() = !isLoan || MoneyInput.parse(initialBalanceInput)?.let { it.signum() < 0 } == true
}

/** Default billing cycle days for a freshly configured credit card. */
const val DEFAULT_CLOSING_DAY = 31
const val DEFAULT_DUE_DAY = 15

/** One-shot events consumed by the editor screen. */
sealed interface AccountEditorEvent {
    data object Saved : AccountEditorEvent

    /** The account to edit no longer exists: leave the screen. */
    data object AccountMissing : AccountEditorEvent

    /** A write failed: stay on the screen and let the user retry. */
    data object WriteFailed : AccountEditorEvent
}

@HiltViewModel(assistedFactory = AccountEditorViewModel.Factory::class)
@Suppress("TooManyFunctions") // One handler per form field plus the unsaved-changes baseline helpers.
class AccountEditorViewModel @AssistedInject constructor(
    @Assisted private val route: AccountEditorRoute,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val userPreferences: UserPreferencesRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: AccountEditorRoute): AccountEditorViewModel
    }

    /** Currencies offered in the picker: locale currency first, then common ones. */
    val currencies: List<Currency> = CurrencyCatalog.supportedCurrencies

    /**
     * The type a new account starts on, from the route (the savings goal
     * "create account" shortcut passes SAVINGS). Falls back to [AccountType.CHECKING];
     * ignored in edit mode, where [loadAccount] sets the persisted type.
     */
    private val initialType: AccountType = route.initialTypeName
        ?.let { name -> AccountType.entries.firstOrNull { it.name == name } }
        ?: AccountType.CHECKING

    private val _uiState = MutableStateFlow(
        AccountEditorUiState(
            isLoading = route.accountId != null,
            isNew = route.accountId == null,
            type = initialType,
            currency = fallbackCurrency,
            color = AccountVisuals.defaultColorFor(initialType),
            icon = AccountVisuals.defaultIconFor(initialType),
            // Apply the ADR 22 savings preset (and the ADR 33 loan one) to the
            // seeded type too, so a preselected account opens with the right
            // inclusion defaults (an explicit toggle still wins from here on).
            isIncludedInBudget = initialType != AccountType.SAVINGS && initialType != AccountType.LOAN,
            isIncludedInTotal = initialType != AccountType.LOAN,
        ),
    )
    val uiState: StateFlow<AccountEditorUiState> = _uiState.asStateFlow()

    /**
     * Accounts eligible as the statement's linked account: same currency as the
     * card, not archived, not the card itself, and not another credit card
     * (a card cannot pay a card). Updates as the chosen currency changes. The
     * account currently referenced stays selectable even if archived, so an
     * archival elsewhere never blanks the field (same rule as the recurring
     * rule editor, Fase 9.7).
     */
    val linkedAccountCandidates: StateFlow<List<Account>> = combine(
        accountRepository.observeAccountsWithBalance(),
        _uiState,
    ) { accounts, state ->
        accounts.map { it.account }.filter { candidate ->
            (!candidate.isArchived || candidate.id == state.linkedAccountId) &&
                candidate.id != route.accountId &&
                candidate.type != AccountType.CREDIT_CARD &&
                candidate.currency == state.currency
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _events = Channel<AccountEditorEvent>(Channel.BUFFERED)
    val events: Flow<AccountEditorEvent> = _events.receiveAsFlow()

    /** Snapshot of the editable fields captured when the form became ready. */
    private val baseline = MutableStateFlow<FormSnapshot?>(null)

    /** True once the user changed a field away from its initial value. */
    val hasUnsavedChanges: StateFlow<Boolean> = combine(_uiState, baseline) { state, base ->
        base != null && base != state.snapshot()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    /** The persisted account being edited; null in create mode. */
    private var existing: Account? = null

    /** Guards against a double-tap on save creating two accounts; reset on failure. */
    private var isSaving = false

    /** True once the user picks an icon: type changes stop updating it. */
    private var userPickedIcon = false

    /** True once the user picks a color: type changes stop updating it. */
    private var userPickedColor = false

    /** True once the user touches the budget toggle: type changes stop presetting it. */
    private var userToggledBudget = false

    /** True once the user touches the total toggle: type changes stop presetting it. */
    private var userToggledTotal = false

    /** True once the user picks a currency: the async preselection stops overriding it. */
    private var userPickedCurrency = false

    init {
        val accountId = route.accountId
        if (accountId == null) preselectPrimaryCurrency() else loadAccount(accountId)
    }

    /**
     * A new account opens in the app's primary currency (the explicit Settings
     * choice, otherwise the plurality of the existing accounts), not the device
     * locale's. Every aggregate - total balance, today/month cards, statistics,
     * budgets - is scoped to the primary currency, so an account created in the
     * wrong one silently disappears from all of them.
     *
     * Resolved asynchronously, so the baseline is captured only afterwards and
     * the preselection never registers as an unsaved edit (same pattern as the
     * movement editor's default account).
     */
    private fun preselectPrimaryCurrency() {
        viewModelScope.launch {
            val accounts = accountRepository.observeAccountsWithBalance().first()
            val override = userPreferences.primaryCurrencyOverride.first()
            val primary = primaryCurrency(accounts, override)
            if (!userPickedCurrency) {
                _uiState.update { it.copy(currency = primary) }
            }
            captureBaseline()
        }
    }

    private fun loadAccount(accountId: Long) {
        viewModelScope.launch {
            val account = accountRepository.getAccount(accountId)
            if (account == null) {
                _events.send(AccountEditorEvent.AccountMissing)
                return@launch
            }
            existing = account
            // A persisted icon, color and currency are the user's: never preset over them.
            userPickedIcon = true
            userPickedColor = true
            userPickedCurrency = true
            // A persisted inclusion choice is the user's: never preset over it.
            userToggledBudget = true
            userToggledTotal = true
            val movementCount = transactionRepository.countForAccount(accountId)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isNew = false,
                    name = account.name,
                    type = account.type,
                    currency = account.currency,
                    isCurrencyLocked = movementCount > 0,
                    initialBalanceInput = account.initialBalance
                        .stripTrailingZeros()
                        .toPlainString(),
                    color = account.color ?: AccountVisuals.defaultColorFor(account.type),
                    icon = account.icon ?: AccountVisuals.defaultIconFor(account.type),
                    isIncludedInTotal = account.isIncludedInTotal,
                    isIncludedInBudget = account.isIncludedInBudget,
                    statementClosingDay = account.creditCard?.statementClosingDay ?: DEFAULT_CLOSING_DAY,
                    paymentDueDay = account.creditCard?.paymentDueDay ?: DEFAULT_DUE_DAY,
                    linkedAccountId = account.creditCard?.linkedAccountId,
                    creditLimitInput = account.creditCard?.creditLimit
                        ?.stripTrailingZeros()?.toPlainString().orEmpty(),
                    statementAutoPost = account.creditCard?.autoPost ?: false,
                )
            }
            captureBaseline()
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onTypeChanged(type: AccountType) {
        _uiState.update {
            it.copy(
                type = type,
                icon = if (userPickedIcon) it.icon else AccountVisuals.defaultIconFor(type),
                color = if (userPickedColor) it.color else AccountVisuals.defaultColorFor(type),
                // Savings and loans default to excluded from the budget
                // (dipping into savings should not consume the month's budget;
                // a loan installment is a transfer, invisible to it anyway);
                // an explicit user choice always wins over the preset.
                isIncludedInBudget = if (userToggledBudget) {
                    it.isIncludedInBudget
                } else {
                    type != AccountType.SAVINGS && type != AccountType.LOAN
                },
                // Loans also default out of the total balance (ADR 33): the
                // Dashboard answers "how much do I have", and a six-figure
                // mortgage would bury that answer. Including it is the user's
                // explicit patrimonial reading, never the preset's.
                isIncludedInTotal = if (userToggledTotal) {
                    it.isIncludedInTotal
                } else {
                    type != AccountType.LOAN
                },
            )
        }
    }

    fun onCurrencyChanged(currency: Currency) {
        if (_uiState.value.isCurrencyLocked) return
        userPickedCurrency = true
        val digits = MoneyMapper.fractionDigits(currency)
        _uiState.update { state ->
            // The new currency may allow fewer decimals (e.g. JPY): rescale the
            // typed amount instead of stripping the separator, which would
            // silently multiply the value.
            val parsed = MoneyInput.parse(state.initialBalanceInput)
            val input = if (parsed != null && parsed.scale() > digits) {
                parsed.setScale(digits, RoundingMode.HALF_UP).toPlainString()
            } else {
                state.initialBalanceInput
            }
            // The linked account must share the card currency; a currency change
            // can invalidate the current choice, so clear it and let the user re-pick.
            state.copy(currency = currency, initialBalanceInput = input, linkedAccountId = null)
        }
    }

    fun onInitialBalanceChanged(raw: String) {
        _uiState.update {
            it.copy(
                initialBalanceInput = MoneyInput.sanitize(
                    raw,
                    MoneyMapper.fractionDigits(it.currency),
                ),
            )
        }
    }

    fun onColorSelected(color: Int) {
        userPickedColor = true
        _uiState.update { it.copy(color = color) }
    }

    fun onIconSelected(icon: String) {
        userPickedIcon = true
        _uiState.update { it.copy(icon = icon) }
    }

    fun onIncludedInTotalChanged(included: Boolean) {
        userToggledTotal = true
        _uiState.update { it.copy(isIncludedInTotal = included) }
    }

    fun onIncludedInBudgetChanged(included: Boolean) {
        userToggledBudget = true
        _uiState.update { it.copy(isIncludedInBudget = included) }
    }

    fun onStatementClosingDayChanged(day: Int) {
        _uiState.update { it.copy(statementClosingDay = day.coerceIn(MIN_DAY, MAX_DAY)) }
    }

    fun onPaymentDueDayChanged(day: Int) {
        _uiState.update { it.copy(paymentDueDay = day.coerceIn(MIN_DAY, MAX_DAY)) }
    }

    fun onLinkedAccountChanged(accountId: Long?) {
        _uiState.update { it.copy(linkedAccountId = accountId) }
    }

    fun onCreditLimitChanged(raw: String) {
        _uiState.update {
            it.copy(creditLimitInput = MoneyInput.sanitize(raw, MoneyMapper.fractionDigits(it.currency)))
        }
    }

    fun onStatementAutoPostChanged(autoPost: Boolean) {
        _uiState.update { it.copy(statementAutoPost = autoPost) }
    }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || isSaving) return
        if (!state.isNameValid || !state.isLoanBalanceValid) {
            _uiState.update { it.copy(showValidation = true) }
            return
        }
        // A credit card's balance always starts from zero: an "initial debt"
        // would be phantom debt no statement could ever settle (the statement
        // amount sums the cycle's movements, and the initial balance is not a
        // movement). Pre-existing debt is recorded via a balance adjustment,
        // which is a movement and gets charged with the next statement.
        val initialBalance = if (state.isCreditCard) {
            BigDecimal.ZERO
        } else {
            MoneyInput.parse(state.initialBalanceInput) ?: BigDecimal.ZERO
        }
        val base = existing
        val account = Account(
            id = base?.id ?: 0L,
            name = state.name.trim(),
            type = state.type,
            currency = state.currency,
            initialBalance = initialBalance,
            color = state.color,
            icon = state.icon,
            isIncludedInTotal = state.isIncludedInTotal,
            isIncludedInBudget = state.isIncludedInBudget,
            isArchived = base?.isArchived ?: false,
            sortOrder = base?.sortOrder ?: 0,
            createdAt = base?.createdAt ?: clock.instant(),
            creditCard = state.toCreditCardConfig(base),
        )
        isSaving = true
        viewModelScope.launch {
            // A new account appends to the end of its type group; an edit keeps
            // the account's manually arranged position untouched.
            val toSave = if (base == null) {
                account.copy(sortOrder = accountRepository.nextSortOrder(account.type))
            } else {
                account
            }
            val result = suspendRunCatching { accountRepository.upsert(toSave) }
            isSaving = false
            _events.send(
                if (result.isSuccess) AccountEditorEvent.Saved else AccountEditorEvent.WriteFailed,
            )
        }
    }

    /**
     * Builds the [CreditCardConfig] from the form when the type is a credit
     * card, preserving the settlement watermark across edits so an edit never
     * re-charges an already-settled cycle; null for any other type.
     */
    private fun AccountEditorUiState.toCreditCardConfig(base: Account?): CreditCardConfig? {
        if (type != AccountType.CREDIT_CARD) return null
        // A freshly configured card seeds its watermark at the last closing before
        // today, so pre-existing history is never back-charged: only cycles that
        // close from now on produce a statement. An edit keeps the real watermark.
        val watermark = base?.creditCard?.lastSettledClosing
            ?: BillingCycleCalculator.closingOnOrBefore(LocalDate.now(clock), statementClosingDay)
        return CreditCardConfig(
            statementClosingDay = statementClosingDay,
            paymentDueDay = paymentDueDay,
            linkedAccountId = linkedAccountId,
            creditLimit = MoneyInput.parse(creditLimitInput)?.takeIf { it.signum() > 0 },
            autoPost = statementAutoPost,
            lastSettledClosing = watermark,
        )
    }

    /** Records the current form as the baseline to detect later edits against. */
    private fun captureBaseline() {
        baseline.value = _uiState.value.snapshot()
    }

    /** The user-editable fields whose change counts as an unsaved edit. */
    private data class FormSnapshot(
        val name: String,
        val type: AccountType,
        val currency: Currency,
        val initialBalanceInput: String,
        val color: Int,
        val icon: String,
        val isIncludedInTotal: Boolean,
        val isIncludedInBudget: Boolean,
        val statementClosingDay: Int,
        val paymentDueDay: Int,
        val linkedAccountId: Long?,
        val creditLimitInput: String,
        val statementAutoPost: Boolean,
    )

    private fun AccountEditorUiState.snapshot() = FormSnapshot(
        name = name,
        type = type,
        currency = currency,
        initialBalanceInput = initialBalanceInput,
        color = color,
        icon = icon,
        isIncludedInTotal = isIncludedInTotal,
        isIncludedInBudget = isIncludedInBudget,
        statementClosingDay = statementClosingDay,
        paymentDueDay = paymentDueDay,
        linkedAccountId = linkedAccountId,
        creditLimitInput = creditLimitInput,
        statementAutoPost = statementAutoPost,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MIN_DAY = 1
        const val MAX_DAY = 31
    }
}
