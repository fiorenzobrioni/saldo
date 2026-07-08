package com.callbackdev.saldo.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TagRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
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
    private val userPreferences: UserPreferencesRepository,
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
        val amountInput: String = "",
        val toAmountInput: String = "",
        val amountTarget: AmountTarget = AmountTarget.AMOUNT,
        val accountId: Long? = null,
        val toAccountId: Long? = null,
        val categoryId: Long? = null,
        val description: String = "",
        val selectedTagIds: Set<Long> = emptySet(),
        val isExcludedFromStats: Boolean = false,
        val isRefund: Boolean = false,
        val showValidation: Boolean = false,
    )

    private val form: MutableStateFlow<Form> = run {
        val now = LocalDateTime.now(clock)
        MutableStateFlow(
            Form(
                isLoading = route.transactionId != null,
                isNew = route.transactionId == null,
                type = TransactionType.EXPENSE,
                date = now.toLocalDate(),
                time = now.toLocalTime(),
            ),
        )
    }

    val uiState: StateFlow<TransactionEditorUiState> = combine(
        form,
        accountRepository.observeAccountsWithBalance(),
        categoryRepository.observeCategories(),
        tagRepository.observeTags(),
        ::buildUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TransactionEditorUiState(),
    )

    private val _events = Channel<TransactionEditorEvent>(Channel.BUFFERED)
    val events: Flow<TransactionEditorEvent> = _events.receiveAsFlow()

    /** The persisted transaction being edited; null in create mode. */
    private var existing: Transaction? = null

    /** Guards against a double-tap on save creating two movements. */
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

    fun onCategorySelected(categoryId: Long) {
        form.update { it.copy(categoryId = categoryId) }
    }

    fun onDateSelected(date: LocalDate) {
        form.update { it.copy(date = date) }
    }

    fun onDescriptionChanged(description: String) {
        form.update { it.copy(description = description) }
    }

    fun onAmountTargetChanged(target: AmountTarget) {
        form.update { it.copy(amountTarget = target) }
    }

    fun onKeypadKey(key: KeypadKey) {
        val state = uiState.value
        when (form.value.amountTarget) {
            AmountTarget.AMOUNT -> {
                val digits = state.currency?.let(MoneyMapper::fractionDigits)
                    ?: DEFAULT_FRACTION_DIGITS
                form.update {
                    it.copy(
                        amountInput = AmountInputEditor.apply(
                            current = it.amountInput,
                            key = key,
                            fractionDigits = digits,
                            allowNegative = it.type == TransactionType.ADJUSTMENT,
                        ),
                    )
                }
            }

            AmountTarget.TO_AMOUNT -> {
                val digits = state.toAccount?.currency?.let(MoneyMapper::fractionDigits)
                    ?: DEFAULT_FRACTION_DIGITS
                form.update {
                    it.copy(
                        toAmountInput = AmountInputEditor.apply(
                            current = it.toAmountInput,
                            key = key,
                            fractionDigits = digits,
                        ),
                    )
                }
            }

            AmountTarget.NONE -> Unit
        }
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
        form.update { it.copy(isExcludedFromStats = excluded) }
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
            val id = transactionRepository.upsert(transaction)
            tagRepository.setTagsForTransaction(id, form.value.selectedTagIds.toList())
            userPreferences.setLastUsedAccountId(transaction.accountId)
            _events.send(TransactionEditorEvent.Saved)
        }
    }

    fun delete() {
        val transaction = existing ?: return
        viewModelScope.launch {
            transactionRepository.delete(transaction)
            _events.send(TransactionEditorEvent.Deleted)
        }
    }

    private fun buildUiState(
        current: Form,
        accounts: List<AccountWithBalance>,
        categories: List<Category>,
        tags: List<Tag>,
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
            amountInput = current.amountInput,
            toAmountInput = current.toAmountInput,
            amountTarget = current.amountTarget,
            accounts = pickable,
            account = current.accountId?.let { byId[it]?.account },
            toAccount = current.toAccountId?.let { byId[it]?.account },
            categories = categoryType?.let { wanted ->
                categories.filter { it.type == wanted || it.type == CategoryType.BOTH }
            }.orEmpty(),
            categoryId = current.categoryId,
            date = current.date,
            description = current.description,
            allTags = tags,
            selectedTags = tags.filter { it.id in current.selectedTagIds },
            isExcludedFromStats = current.isExcludedFromStats,
            isRefund = current.isRefund,
            showValidation = current.showValidation,
        )
    }

    /** Preselects the last used account (Phase 9 adds an explicit default setting). */
    private fun preselectDefaultAccount() {
        viewModelScope.launch {
            val lastUsedId = userPreferences.lastUsedAccountId.first()
            val active = accountRepository.observeAccountsWithBalance().first()
                .map { it.account }
                .filter { !it.isArchived }
            val default = active.firstOrNull { it.id == lastUsedId }
                ?: active.firstOrNull()
                ?: return@launch
            form.update { if (it.accountId == null) it.copy(accountId = default.id) else it }
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
            val local = transaction.timestamp.atOffset(transaction.zoneOffset)
            form.update {
                it.copy(
                    isLoading = false,
                    isNew = false,
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
                    selectedTagIds = tagIds,
                    isExcludedFromStats = transaction.isExcludedFromStats,
                    isRefund = transaction.isRefund,
                )
            }
        }
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
            amount = signedAmount(current.type, parsed),
            currency = account.currency,
            accountId = account.id,
            timestamp = dateTime.toInstant(offset),
            zoneOffset = offset,
            transferAccountId = toAccount?.id,
            transferAmount = transferAmount,
            transferCurrency = toAccount?.currency,
            categoryId = if (hasCategory) current.categoryId else null,
            description = current.description.trim().ifEmpty { null },
            note = base?.note,
            isExcludedFromStats = if (hasCategory) current.isExcludedFromStats else false,
            isRefund = current.type == TransactionType.INCOME && current.isRefund,
            recurringRuleId = base?.recurringRuleId,
        )
    }

    /** Sign convention of [Transaction.amount]: the effect on the source account. */
    private fun signedAmount(type: TransactionType, parsed: BigDecimal): BigDecimal =
        when (type) {
            TransactionType.EXPENSE, TransactionType.TRANSFER -> parsed.abs().negate()
            TransactionType.INCOME -> parsed.abs()
            TransactionType.ADJUSTMENT -> parsed
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

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val DEFAULT_FRACTION_DIGITS = 2
    }
}
