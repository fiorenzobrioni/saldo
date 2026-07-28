package com.callbackdev.saldo.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.account.DefaultAccountResolver
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.money.TransactionSign
import com.callbackdev.saldo.core.domain.transaction.CounterpartyNames
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TagRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.undo.UndoDeleteCoordinator
import com.callbackdev.saldo.core.domain.undo.UndoableDelete
import com.callbackdev.saldo.navigation.TransactionEditorRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Form state holder for the transaction editor. One callback per field is the
 * natural shape of a form screen (hence the function count) and the constructor
 * takes the DI graph of the four aggregates the form touches.
 */
@Suppress("TooManyFunctions", "LongParameterList")
@HiltViewModel(assistedFactory = TransactionEditorViewModel.Factory::class)
class TransactionEditorViewModel @AssistedInject constructor(
    @Assisted private val route: TransactionEditorRoute,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val userPreferences: UserPreferencesRepository,
    private val undoCoordinator: UndoDeleteCoordinator,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: TransactionEditorRoute): TransactionEditorViewModel
    }

    /** Raw form fields; resolved against repository flows in [uiState]. */
    private data class Form(
        val isLoading: Boolean,
        val isNew: Boolean,
        val type: TransactionType,
        val date: LocalDate,
        val time: LocalTime,
        val isTypeLocked: Boolean = false,
        /** True when the type was chosen upfront (a quick action), so it is not offered as a choice. */
        val isTypePreset: Boolean = false,
        val amountInput: String = "",
        val toAmountInput: String = "",
        val accountId: Long? = null,
        val toAccountId: Long? = null,
        val categoryId: Long? = null,
        val description: String = "",
        /** The long free-text note; blank means "no note" and persists as null. */
        val note: String = "",
        val selectedTagIds: Set<Long> = emptySet(),
        val isExcludedFromStats: Boolean = false,
        /**
         * The exclusion flag the user had chosen before marking the movement as
         * a loan, restored when the mark is removed: forcing the flag on must
         * not silently rewrite a choice the user made by hand.
         */
        val excludedBeforeCounterparty: Boolean = false,
        val isCounterparty: Boolean = false,
        val counterparty: String = "",
        val isRefund: Boolean = false,
        /** Read-only metadata: kept out of [snapshot] so it never marks the form dirty. */
        val isRecurring: Boolean = false,
        val recurringRuleName: String? = null,
        val showValidation: Boolean = false,
    )

    private val form: MutableStateFlow<Form> = run {
        val now = LocalDateTime.now(clock)
        MutableStateFlow(
            Form(
                isLoading = route.transactionId != null,
                isNew = route.transactionId == null,
                type = route.initialType(),
                isTypePreset = route.initialTypeName != null,
                date = now.toLocalDate(),
                time = now.toLocalTime(),
                // Prefilled by "mark as returned": the section opens already on,
                // with the exclusion it implies (the amount is sanitized once
                // the account, hence the currency, is known).
                isCounterparty = route.transactionId == null && route.initialCounterparty != null,
                counterparty = route.initialCounterparty.orEmpty(),
                isExcludedFromStats =
                route.transactionId == null && route.initialCounterparty != null,
            ),
        )
    }

    val uiState: StateFlow<TransactionEditorUiState> = combine(
        form,
        accountRepository.observeAccountsWithBalance(),
        categoryRepository.observeCategories(),
        tagRepository.observeTags(),
        transactionRepository.observeCounterpartyNames(),
        ::buildUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TransactionEditorUiState(),
    )

    private val _events = Channel<TransactionEditorEvent>(Channel.BUFFERED)
    val events: Flow<TransactionEditorEvent> = _events.receiveAsFlow()

    /** Snapshot of the editable fields captured when the form became ready. */
    private val baseline = MutableStateFlow<FormSnapshot?>(null)

    /** True once the user changed a field away from its initial value. */
    val hasUnsavedChanges: StateFlow<Boolean> = combine(form, baseline) { current, base ->
        base != null && base != current.snapshot()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = false,
    )

    /** The persisted transaction being edited; null in create mode. */
    private var existing: Transaction? = null

    /** Guards against a double-tap on save creating two movements; reset on failure. */
    private var isSaving = false

    init {
        val transactionId = route.transactionId
        if (transactionId == null) preselectDefaultAccount() else load(transactionId)
    }

    fun onTypeChanged(type: TransactionType) {
        val current = form.value
        val blocked = current.isTypeLocked ||
            type == current.type ||
            type == TransactionType.ADJUSTMENT ||
            (!current.isNew && type == TransactionType.TRANSFER)
        if (blocked) return
        form.update {
            it.copy(
                type = type,
                categoryId = null,
                isRefund = if (type == TransactionType.INCOME) it.isRefund else false,
                amountInput = it.amountInput.removePrefix("-"),
            )
        }
    }

    fun onAccountSelected(account: Account) {
        form.update { current ->
            current.copy(
                accountId = account.id,
                toAccountId = current.toAccountId.takeIf { it != account.id },
                amountInput = rescale(
                    current.amountInput,
                    MoneyMapper.fractionDigits(account.currency),
                ),
            )
        }
    }

    fun onToAccountSelected(account: Account) {
        form.update { current ->
            current.copy(
                toAccountId = account.id,
                toAmountInput = rescale(
                    current.toAmountInput,
                    MoneyMapper.fractionDigits(account.currency),
                ),
            )
        }
    }

    /**
     * Swaps the two legs of a transfer. On a cross-currency transfer the typed
     * amounts travel with their currency, so each account keeps its own figure.
     */
    fun onSwapAccounts() {
        val wasCrossCurrency = uiState.value.isCrossCurrency
        form.update { current ->
            if (current.type != TransactionType.TRANSFER) return@update current
            current.copy(
                accountId = current.toAccountId,
                toAccountId = current.accountId,
                amountInput = if (wasCrossCurrency) current.toAmountInput else current.amountInput,
                toAmountInput = if (wasCrossCurrency) current.amountInput else current.toAmountInput,
            )
        }
    }

    fun onCategorySelected(categoryId: Long) {
        form.update { it.copy(categoryId = categoryId) }
    }

    fun onDateSelected(date: LocalDate) {
        form.update { it.copy(date = date) }
    }

    fun onTimeSelected(time: LocalTime) {
        form.update { it.copy(time = time) }
    }

    fun onDescriptionChanged(description: String) {
        form.update { it.copy(description = description) }
    }

    fun onNoteChanged(note: String) {
        form.update { it.copy(note = note) }
    }

    fun onAmountChanged(raw: String) {
        val digits = uiState.value.amountFractionDigits
        form.update {
            it.copy(
                amountInput = MoneyInput.sanitize(
                    raw,
                    digits,
                    allowNegative = it.type == TransactionType.ADJUSTMENT,
                ),
            )
        }
    }

    fun onToAmountChanged(raw: String) {
        val digits = uiState.value.toAmountFractionDigits
        form.update { it.copy(toAmountInput = MoneyInput.sanitize(raw, digits, allowNegative = false)) }
    }

    fun onTagToggled(tag: Tag) {
        form.update {
            val ids = it.selectedTagIds
            it.copy(selectedTagIds = if (tag.id in ids) ids - tag.id else ids + tag.id)
        }
    }

    /** Creates (or reuses, case-insensitively) a tag and selects it. */
    fun onCreateTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val existingTag = tagRepository.observeTags().first()
                .firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            val tagId = existingTag?.id ?: tagRepository.upsert(Tag(name = trimmed))
            form.update { it.copy(selectedTagIds = it.selectedTagIds + tagId) }
        }
    }

    fun onExcludedFromStatsChanged(excluded: Boolean) {
        // While the movement is marked as a loan the flag is not the user's to
        // set: lending is never spending, and the switch is disabled to say so.
        if (form.value.isCounterparty) return
        form.update { it.copy(isExcludedFromStats = excluded) }
    }

    /**
     * Marks the movement as a loan to or from a person (ADR 34). Turning it on
     * forces the exclusion from statistics; turning it off hands the flag back
     * to the user with the value they had chosen before. The typed name stays
     * either way, so a mistaken tap costs nothing to undo.
     */
    fun onCounterpartyToggled(enabled: Boolean) {
        form.update { current ->
            if (current.isCounterparty == enabled) {
                current
            } else if (enabled) {
                current.copy(
                    isCounterparty = true,
                    excludedBeforeCounterparty = current.isExcludedFromStats,
                    isExcludedFromStats = true,
                )
            } else {
                current.copy(
                    isCounterparty = false,
                    isExcludedFromStats = current.excludedBeforeCounterparty,
                )
            }
        }
    }

    fun onCounterpartyChanged(name: String) {
        form.update { it.copy(counterparty = name) }
    }

    /** A refund offsets an expense: toggling it switches the category set. */
    fun onRefundChanged(refund: Boolean) {
        form.update { it.copy(isRefund = refund, categoryId = null) }
    }

    fun save() {
        val state = uiState.value
        if (state.isLoading || isSaving) return
        val transaction = if (state.isValid) buildTransaction(state, form.value) else null
        if (transaction == null) {
            form.update { it.copy(showValidation = true) }
            return
        }
        isSaving = true
        viewModelScope.launch {
            val result = suspendRunCatching {
                val id = transactionRepository.upsert(transaction)
                tagRepository.setTagsForTransaction(id, form.value.selectedTagIds.toList())
                userPreferences.setLastUsedAccountId(transaction.accountId)
            }
            isSaving = false
            _events.send(
                if (result.isSuccess) TransactionEditorEvent.Saved else TransactionEditorEvent.WriteFailed,
            )
        }
    }

    /**
     * Deletes immediately (no confirmation dialog: undo is offered instead,
     * matching the ledger's swipe-delete), capturing the tags first so the
     * app-level snackbar can restore the movement.
     */
    fun delete() {
        val transaction = existing ?: return
        viewModelScope.launch {
            suspendRunCatching {
                val tagIds = tagRepository.observeTagsForTransaction(transaction.id).first()
                    .map { it.id }
                transactionRepository.delete(transaction)
                tagIds
            }
                .onSuccess { tagIds ->
                    undoCoordinator.publish(UndoableDelete.Movement(transaction, tagIds))
                    _events.send(TransactionEditorEvent.Deleted)
                }
                .onFailure { _events.send(TransactionEditorEvent.WriteFailed) }
        }
    }

    private fun buildUiState(
        current: Form,
        accounts: List<AccountWithBalance>,
        categories: List<Category>,
        tags: List<Tag>,
        counterpartyNames: List<String>,
    ): TransactionEditorUiState {
        val byId = accounts.associateBy { it.account.id }
        val pickable = accounts.filter {
            !it.account.isArchived ||
                it.account.id == current.accountId ||
                it.account.id == current.toAccountId
        }
        val categoryType = when {
            current.type == TransactionType.EXPENSE -> CategoryType.EXPENSE
            current.type == TransactionType.INCOME && current.isRefund -> CategoryType.EXPENSE
            current.type == TransactionType.INCOME -> CategoryType.INCOME
            else -> null
        }
        return TransactionEditorUiState(
            isLoading = current.isLoading,
            isNew = current.isNew,
            type = current.type,
            isTypeLocked = current.isTypeLocked,
            isTypePreset = current.isTypePreset,
            amountInput = current.amountInput,
            toAmountInput = current.toAmountInput,
            accounts = pickable,
            account = current.accountId?.let { byId[it]?.account },
            toAccount = current.toAccountId?.let { byId[it]?.account },
            categories = categoryType?.let { wanted ->
                categories.filter { it.type == wanted || it.type == CategoryType.BOTH }
            }.orEmpty(),
            categoryId = current.categoryId,
            date = current.date,
            time = current.time,
            description = current.description,
            note = current.note,
            allTags = tags,
            selectedTags = tags.filter { it.id in current.selectedTagIds },
            isExcludedFromStats = current.isExcludedFromStats,
            isRefund = current.isRefund,
            isCounterparty = current.isCounterparty,
            counterparty = current.counterparty,
            counterpartySuggestions = counterpartySuggestions(counterpartyNames, current),
            isRecurring = current.isRecurring,
            recurringRuleName = current.recurringRuleName,
            showValidation = current.showValidation,
        )
    }

    /**
     * Preselects the account for a new movement: the explicit Settings
     * default when it points to an active account, otherwise the last used
     * one, otherwise the first active. An archived or deleted default is
     * silently skipped.
     */
    private fun preselectDefaultAccount() {
        viewModelScope.launch {
            val defaultId = userPreferences.defaultAccountId.first()
            val lastUsedId = userPreferences.lastUsedAccountId.first()
            val active = accountRepository.observeAccountsWithBalance().first()
                .map { it.account }
                .filter { !it.isArchived }
            val default = DefaultAccountResolver.resolve(active, defaultId, lastUsedId)
            if (default != null) {
                form.update { if (it.accountId == null) it.copy(accountId = default.id) else it }
            }
            // The prefilled amount can only be sanitized once a currency is
            // known, so it lands here rather than in the initial form; it is
            // part of the baseline, so leaving an untouched prefill asks nothing.
            val digits = default?.currency?.let(MoneyMapper::fractionDigits) ?: DEFAULT_FRACTION_DIGITS
            route.initialAmountInput?.let { prefill ->
                form.update {
                    if (it.amountInput.isEmpty()) {
                        it.copy(amountInput = MoneyInput.sanitize(prefill, digits, allowNegative = false))
                    } else {
                        it
                    }
                }
            }
            captureBaseline()
        }
    }

    private fun load(transactionId: Long) {
        viewModelScope.launch {
            val transaction = transactionRepository.getTransaction(transactionId)
            if (transaction == null) {
                _events.send(TransactionEditorEvent.TransactionMissing)
                return@launch
            }
            existing = transaction
            val tagIds = tagRepository.observeTagsForTransaction(transactionId).first()
                .map { it.id }
                .toSet()
            // The FK is SET_NULL on rule deletion, so a non-null id still resolves;
            // fall back to a nameless banner if the lookup ever comes back empty.
            val ruleName = transaction.recurringRuleId
                ?.let { recurringRuleRepository.getRule(it)?.name }
            val local = transaction.timestamp.atOffset(transaction.zoneOffset)
            form.update {
                it.copy(
                    isLoading = false,
                    isNew = false,
                    isRecurring = transaction.recurringRuleId != null,
                    recurringRuleName = ruleName,
                    type = transaction.type,
                    isTypeLocked = transaction.type == TransactionType.TRANSFER ||
                        transaction.type == TransactionType.ADJUSTMENT,
                    amountInput = when (transaction.type) {
                        TransactionType.ADJUSTMENT -> plainInput(transaction.amount)
                        else -> plainInput(transaction.amount.abs())
                    },
                    toAmountInput = transaction.transferAmount?.let(::plainInput).orEmpty(),
                    accountId = transaction.accountId,
                    toAccountId = transaction.transferAccountId,
                    categoryId = transaction.categoryId,
                    date = local.toLocalDate(),
                    time = local.toLocalTime(),
                    description = transaction.description.orEmpty(),
                    note = transaction.note.orEmpty(),
                    selectedTagIds = tagIds,
                    isExcludedFromStats = transaction.isExcludedFromStats,
                    // Nothing says what the user would have chosen for a movement
                    // that has always been a loan, so removing the mark hands back
                    // an unchecked flag rather than inventing one.
                    excludedBeforeCounterparty = transaction.counterparty == null &&
                        transaction.isExcludedFromStats,
                    isCounterparty = transaction.counterparty != null,
                    counterparty = transaction.counterparty.orEmpty(),
                    isRefund = transaction.isRefund,
                )
            }
            captureBaseline()
        }
    }

    /**
     * The already-used names worth offering under the field: those matching what
     * has been typed so far, one per person (the same name written two ways is
     * one suggestion, in its most recent spelling), minus the exact match, which
     * would only repeat what is already in the field.
     */
    private fun counterpartySuggestions(names: List<String>, current: Form): List<String> {
        if (!current.isCounterparty) return emptyList()
        val typed = CounterpartyNames.key(current.counterparty)
        val seen = mutableSetOf<String>()
        return names
            .filter { seen.add(CounterpartyNames.key(it)) }
            .filter { name ->
                val key = CounterpartyNames.key(name)
                key != typed && (typed.isEmpty() || key.contains(typed))
            }
            .take(MAX_COUNTERPARTY_SUGGESTIONS)
    }

    /** Builds the domain movement with the sign conventions of the model. */
    private fun buildTransaction(
        state: TransactionEditorUiState,
        current: Form,
    ): Transaction? {
        val account = state.account
        val parsed = account?.let {
            MoneyInput.parse(current.amountInput)
                ?.setScale(MoneyMapper.fractionDigits(it.currency), RoundingMode.HALF_UP)
        }
        val isTransfer = current.type == TransactionType.TRANSFER
        val toAccount = state.toAccount.takeIf { isTransfer }
        val transferAmount = if (toAccount != null && parsed != null) {
            transferAmountFor(state, current, parsed.abs(), toAccount)
        } else {
            null
        }
        val transferLegMissing = isTransfer && (toAccount == null || transferAmount == null)
        if (account == null || parsed == null || transferLegMissing) return null
        val dateTime = LocalDateTime.of(current.date, current.time)
        val offset = clock.zone.rules.getOffset(dateTime)
        val hasCategory = current.type == TransactionType.EXPENSE ||
            current.type == TransactionType.INCOME
        val base = existing
        return Transaction(
            id = base?.id ?: 0L,
            type = current.type,
            amount = TransactionSign.signed(current.type, parsed),
            currency = account.currency,
            accountId = account.id,
            timestamp = dateTime.toInstant(offset),
            zoneOffset = offset,
            transferAccountId = toAccount?.id,
            transferAmount = transferAmount,
            transferCurrency = toAccount?.currency,
            categoryId = if (hasCategory) current.categoryId else null,
            description = current.description.trim().ifEmpty { null },
            note = current.note.trim().ifEmpty { null },
            // A loan is out of the statistics by construction, whatever the
            // switch says: the two can only disagree if the flag was forced.
            isExcludedFromStats = hasCategory &&
                (current.isExcludedFromStats || current.isCounterparty),
            isRefund = current.type == TransactionType.INCOME && current.isRefund,
            counterparty = if (hasCategory && current.isCounterparty) {
                current.counterparty.trim().ifEmpty { null }
            } else {
                null
            },
            recurringRuleId = base?.recurringRuleId,
            // Carried through rather than defaulted: a pending movement is not
            // reachable from this editor today, but letting the flag fall back
            // to false would silently confirm one the day it becomes editable.
            isPending = base?.isPending ?: false,
            recurringOccurrenceDate = base?.recurringOccurrenceDate,
        )
    }

    /** The positive effect on the destination account of a transfer. */
    private fun transferAmountFor(
        state: TransactionEditorUiState,
        current: Form,
        magnitude: BigDecimal,
        toAccount: Account,
    ): BigDecimal? {
        val toDigits = MoneyMapper.fractionDigits(toAccount.currency)
        return if (state.isCrossCurrency) {
            MoneyInput.parse(current.toAmountInput)?.abs()?.setScale(toDigits, RoundingMode.HALF_UP)
        } else {
            magnitude
        }
    }

    private fun rescale(input: String, digits: Int): String {
        val parsed = MoneyInput.parse(input) ?: return MoneyInput.sanitize(input, digits)
        return if (parsed.scale() > digits) {
            parsed.setScale(digits, RoundingMode.HALF_UP).toPlainString()
        } else {
            input
        }
    }

    private fun plainInput(amount: BigDecimal): String =
        amount.stripTrailingZeros().toPlainString()

    /** Records the current form as the baseline to detect later edits against. */
    private fun captureBaseline() {
        baseline.value = form.value.snapshot()
    }

    /** The user-editable fields whose change counts as an unsaved edit. */
    private data class FormSnapshot(
        val type: TransactionType,
        val date: LocalDate,
        val time: LocalTime,
        val amountInput: String,
        val toAmountInput: String,
        val accountId: Long?,
        val toAccountId: Long?,
        val categoryId: Long?,
        val description: String,
        val note: String,
        val selectedTagIds: Set<Long>,
        val isExcludedFromStats: Boolean,
        val isRefund: Boolean,
        val isCounterparty: Boolean,
        val counterparty: String,
    )

    private fun Form.snapshot() = FormSnapshot(
        type = type,
        date = date,
        time = time,
        amountInput = amountInput,
        toAmountInput = toAmountInput,
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        description = description,
        note = note,
        selectedTagIds = selectedTagIds,
        isExcludedFromStats = isExcludedFromStats,
        isRefund = isRefund,
        isCounterparty = isCounterparty,
        counterparty = counterparty,
    )

    /**
     * The type a freshly created movement starts with. Only EXPENSE, INCOME and
     * TRANSFER can be requested (the dashboard quick actions); anything else,
     * including ADJUSTMENT, falls back to EXPENSE.
     */
    private fun TransactionEditorRoute.initialType(): TransactionType {
        val requested = initialTypeName
            ?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
        return when (requested) {
            TransactionType.INCOME, TransactionType.TRANSFER -> requested
            else -> TransactionType.EXPENSE
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Suggestions under the counterparty field: enough to pick from, never a list. */
        const val MAX_COUNTERPARTY_SUGGESTIONS = 4
    }
}
