package com.callbackdev.saldo.feature.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.account.DefaultAccountResolver
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.quickentry.CategorySuggester
import com.callbackdev.saldo.core.domain.quickentry.CategorySuggestion
import com.callbackdev.saldo.core.domain.quickentry.QuickEntryParser
import com.callbackdev.saldo.core.domain.quickentry.SearchWord
import com.callbackdev.saldo.core.domain.quickentry.SuggestionOrigin
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.search.SearchText
import com.callbackdev.saldo.core.domain.transaction.QuickTransactionFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** What the quick entry sheet renders. */
data class QuickEntryUiState(
    val isLoading: Boolean = true,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val category: Category? = null,
    val categories: List<Category> = emptyList(),
    val account: AccountWithBalance? = null,
    val accounts: List<AccountWithBalance> = emptyList(),
    val isSaved: Boolean = false,
    /** The saved amount, formatted, shown by the confirmation state. */
    val savedAmount: String? = null,
    /** The quick text line, verbatim (ADR 42). */
    val quickText: String = "",
    /** Description proposed by the parser, saved with the movement. */
    val description: String = "",
    /** Date read from the text, or null for today. Shown when not null. */
    val parsedDate: LocalDate? = null,
    /** True when the current category came from the text, for the highlight. */
    val isCategorySuggested: Boolean = false,
) {
    val fractionDigits: Int
        get() = account?.let { MoneyMapper.fractionDigits(it.account.currency) } ?: DEFAULT_FRACTION_DIGITS

    val currencySymbol: String? get() = account?.account?.currency?.symbol

    val isAmountValid: Boolean
        get() = MoneyInput.parse(amountInput)?.let { it.signum() > 0 } == true

    /**
     * Nothing to save onto yet: no account, or no category of this type. The
     * widget never opens the sheet in this state (its NotReady face covers it),
     * but the Quick Settings tile has no such gate, so the sheet itself turns
     * into a door to the app instead of a form that cannot save.
     */
    val needsSetup: Boolean
        get() = !isLoading && (accounts.isEmpty() || categories.isEmpty())

    /**
     * [isSaved] closes the door for good: the sheet holds its confirmation for
     * a beat before it dismisses, and a second tap in that window must not
     * write the movement twice.
     */
    val canSave: Boolean
        get() = !isLoading && !isSaved && isAmountValid && account != null && category != null

    private companion object {
        const val DEFAULT_FRACTION_DIGITS = 2
    }
}

sealed interface QuickEntryEvent {
    /** Saved: the sheet plays its confirmation and closes itself. */
    data object Saved : QuickEntryEvent
    data object WriteFailed : QuickEntryEvent
}

/**
 * The quick entry step of widget and tile. Deliberately a separate, much
 * smaller view model than the full editor: it only ever writes an expense or
 * an income, and everything it does not cover (transfers, tags, notes) is one
 * tap away in the real editor. The one-line quick text (ADR 42) feeds amount,
 * description, a simple date and a category suggestion into the same form;
 * the parser proposes, only Save writes.
 *
 * The movement itself is built by [QuickTransactionFactory], the same shared
 * rules the full editor uses, so the sign convention and the zone offset cannot
 * drift between the two entry points.
 */
@HiltViewModel(assistedFactory = QuickEntryViewModel.Factory::class)
class QuickEntryViewModel @AssistedInject constructor(
    @Assisted private val route: QuickEntryRoute,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val userPreferences: UserPreferencesRepository,
    private val vocabularyProvider: QuickEntryVocabularyProvider,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: QuickEntryRoute): QuickEntryViewModel
    }

    /** Who chose the current category: the parser only ever outranks a guess. */
    private enum class CategorySource { BASELINE, TEXT, USER }

    private data class Form(
        val amountInput: String = "",
        val categoryId: Long? = null,
        val accountId: Long? = null,
        val isSaved: Boolean = false,
        val savedAmount: String? = null,
        val quickText: String = "",
        val description: String = "",
        val date: LocalDate? = null,
        /** The category to fall back to when a text suggestion goes away. */
        val baselineCategoryId: Long? = null,
        val categorySource: CategorySource = CategorySource.BASELINE,
        /** Keypad edits win over the parser until the text is cleared. */
        val amountEdited: Boolean = false,
    )

    private val form = MutableStateFlow(
        Form(
            categoryId = route.categoryId,
            baselineCategoryId = route.categoryId,
            accountId = route.accountId,
        ),
    )

    private val _events = Channel<QuickEntryEvent>(Channel.BUFFERED)
    val events: Flow<QuickEntryEvent> = _events.receiveAsFlow()

    private var isSaving = false

    private var historyJob: Job? = null

    /** Per-word usage rows, fetched once per sheet lifetime. */
    private val usageCache = mutableMapOf<String, List<Long>>()

    private val categoryType = when (route.type) {
        TransactionType.INCOME -> CategoryType.INCOME
        else -> CategoryType.EXPENSE
    }

    private val categories = categoryRepository.observeCategories(categoryType)

    val uiState: StateFlow<QuickEntryUiState> = combine(
        form,
        accountRepository.observeAccountsWithBalance(),
        categories,
    ) { current, accounts, categories ->
        val pickable = accounts.filter { !it.account.isArchived || it.account.id == current.accountId }
        val account = pickable.firstOrNull { it.account.id == current.accountId }
        QuickEntryUiState(
            isLoading = false,
            type = route.type,
            amountInput = current.amountInput,
            category = categories.firstOrNull { it.id == current.categoryId },
            categories = categories,
            account = account,
            accounts = pickable,
            isSaved = current.isSaved,
            savedAmount = current.savedAmount,
            quickText = current.quickText,
            description = current.description,
            parsedDate = current.date,
            isCategorySuggested = current.categorySource == CategorySource.TEXT,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = QuickEntryUiState(type = route.type),
    )

    init {
        // The widget passes the account it rendered with; if it was archived or
        // deleted since, fall back to the app's own default chain rather than
        // opening a sheet that cannot save.
        if (route.accountId == null) preselectAccount()
        // The single-row widget has no grid, so it sends no category: the sheet
        // opens on the one this user actually reaches for. It is shown at the
        // top of the sheet and is one tap from being changed - a guess in plain
        // sight, not a decision made for them.
        if (route.categoryId == null) preselectCategory()
    }

    fun onAmountChanged(value: String) {
        // A keypad edit while quick text is present detaches the amount from
        // the parser: the figure the user just corrected must not be clobbered
        // by the next keystroke of text.
        form.update { it.copy(amountInput = value, amountEdited = it.quickText.isNotBlank()) }
    }

    fun onCategorySelected(categoryId: Long) {
        form.update { it.copy(categoryId = categoryId, categorySource = CategorySource.USER) }
    }

    /**
     * Live parse of the quick text line (ADR 42). Amount, date and description
     * are pure and instant; the category-name stage is pure too and applies on
     * the same keystroke, while the history stage costs a query and runs
     * debounced in [suggestFromHistory].
     */
    fun onQuickTextChanged(value: String) {
        val state = uiState.value
        val fractionDigits = state.fractionDigits
        val parsed = QuickEntryParser.parse(
            text = value,
            fractionDigits = fractionDigits,
            currencyMarkers = state.account?.account?.currency
                ?.let(vocabularyProvider::currencyMarkers)
                .orEmpty(),
            vocabulary = vocabularyProvider.vocabulary,
            today = LocalDate.now(clock),
        )
        val byName = CategorySuggester.byName(parsed.searchWords.map { it.folded }, state.categories)
        form.update { current ->
            val keepEdited = current.amountEdited && value.isNotBlank()
            current.copy(
                quickText = value,
                amountInput = if (keepEdited) {
                    current.amountInput
                } else {
                    parsed.amount
                        ?.let { MoneyInput.sanitize(it, fractionDigits, allowNegative = false) }
                        .orEmpty()
                },
                amountEdited = keepEdited,
                description = consumeNameWord(parsed.description, byName),
                date = parsed.date,
            )
        }
        if (byName != null) {
            historyJob?.cancel()
            applySuggestion(byName)
        } else {
            suggestFromHistory(parsed.searchWords)
        }
    }

    fun onAccountSelected(accountId: Long) {
        form.update { it.copy(accountId = accountId) }
    }

    /**
     * The debounced history stage of the category suggestion. Each word costs
     * at most one capped query per sheet lifetime (the per-word result is
     * cached); a weak or contested signal applies nothing (ADR 42).
     */
    private fun suggestFromHistory(words: List<SearchWord>) {
        historyJob?.cancel()
        if (words.isEmpty()) {
            applySuggestion(null)
            return
        }
        historyJob = viewModelScope.launch {
            delay(SUGGESTION_DEBOUNCE_MILLIS)
            val usage = words.associate { word -> word.folded to usageFor(word) }
            val suggestion = CategorySuggester.suggest(
                words = words.map { it.folded },
                categories = uiState.value.categories,
                usage = usage,
            )
            applySuggestion(suggestion)
        }
    }

    private suspend fun usageFor(word: SearchWord): List<Long> =
        usageCache.getOrPut(word.folded) {
            val since = LocalDate.now(clock)
                .minusMonths(USAGE_WINDOW_MONTHS)
                .atStartOfDay(clock.zone)
                .toInstant()
            runCatching {
                transactionRepository.descriptionUsage(
                    type = route.type,
                    since = since,
                    word = word.typed,
                    foldedWord = word.folded,
                    limit = USAGE_ROW_LIMIT,
                )
            }.getOrDefault(emptyList())
                .filter { CategorySuggester.matches(word.folded, it.description) }
                .map { it.categoryId }
        }

    /**
     * A suggestion never outranks the user's own pick; losing the suggestion
     * falls back to the baseline guess, never to an empty category out of the
     * blue (the baseline itself can be empty on a fresh install, and then
     * empty is the honest answer).
     */
    private fun applySuggestion(suggestion: CategorySuggestion?) {
        form.update { current ->
            when {
                current.categorySource == CategorySource.USER -> current
                suggestion != null -> current.copy(
                    categoryId = suggestion.categoryId,
                    categorySource = CategorySource.TEXT,
                )
                current.categorySource == CategorySource.TEXT -> current.copy(
                    categoryId = current.baselineCategoryId,
                    categorySource = CategorySource.BASELINE,
                )
                else -> current
            }
        }
    }

    /**
     * "12 benzina" with a "Benzina" category: the word IS the category, so a
     * description that would only repeat it is dropped. Anything more than
     * that single word stays untouched.
     */
    private fun consumeNameWord(description: String, byName: CategorySuggestion?): String {
        if (byName == null || byName.origin != SuggestionOrigin.CATEGORY_NAME) return description
        val words = description.split(WHITESPACE).filter { it.isNotEmpty() }
        val only = words.singleOrNull() ?: return description
        return if (SearchText.normalize(only.trim { !it.isLetterOrDigit() }) == byName.word) "" else description
    }

    fun save() {
        if (isSaving) return
        val state = uiState.value
        if (!state.canSave) return
        // canSave already proved both of these; the guards keep the types honest.
        val account = state.account?.account ?: return
        val amount = MoneyInput.parse(state.amountInput) ?: return
        isSaving = true
        val transaction = QuickTransactionFactory.create(
            type = state.type,
            amount = amount,
            account = account,
            categoryId = state.category?.id,
            // A date read from the text keeps the current time of day: the
            // day is the information, the hour is just "when it was written".
            dateTime = state.parsedDate?.atTime(LocalTime.now(clock)) ?: LocalDateTime.now(clock),
            zone = clock.zone,
            description = state.description.trim().ifEmpty { null },
        )
        viewModelScope.launch {
            val result = suspendRunCatching {
                transactionRepository.upsert(transaction)
                userPreferences.setLastUsedAccountId(account.id)
            }
            isSaving = false
            if (result.isSuccess) {
                form.update {
                    it.copy(
                        isSaved = true,
                        savedAmount = MoneyFormatter.format(transaction.amount.abs(), account.currency),
                    )
                }
                _events.send(QuickEntryEvent.Saved)
            } else {
                _events.send(QuickEntryEvent.WriteFailed)
            }
        }
    }

    private fun preselectCategory() {
        viewModelScope.launch {
            val available = categories.first()
            if (available.isEmpty()) return@launch
            val since = LocalDate.now(clock).minusDays(MOST_USED_WINDOW_DAYS).atStartOfDay(clock.zone).toInstant()
            val mostUsed = runCatching {
                transactionRepository.mostUsedCategoryIds(route.type, since, 1).firstOrNull()
            }.getOrNull()
            val chosen = available.firstOrNull { it.id == mostUsed } ?: available.first()
            form.update { current ->
                // The guess is also the baseline a lost text suggestion falls
                // back to, even when the parser got there first.
                val baseline = current.baselineCategoryId ?: chosen.id
                if (current.categoryId == null) {
                    current.copy(categoryId = chosen.id, baselineCategoryId = baseline)
                } else {
                    current.copy(baselineCategoryId = baseline)
                }
            }
        }
    }

    private fun preselectAccount() {
        viewModelScope.launch {
            val active = accountRepository.observeAccountsWithBalance().first()
                .map { it.account }
                .filter { !it.isArchived }
            val default = DefaultAccountResolver.resolve(
                accounts = active,
                defaultAccountId = userPreferences.defaultAccountId.first(),
                lastUsedAccountId = userPreferences.lastUsedAccountId.first(),
            )
            if (default != null) {
                form.update { if (it.accountId == null) it.copy(accountId = default.id) else it }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Fast enough to feel live, slow enough to skip mid-word queries. */
        const val SUGGESTION_DEBOUNCE_MILLIS = 250L

        /**
         * Declared caps of the history stage (ADR 42): how far back a word's
         * habit is read, and how many rows one word may ever cost.
         */
        const val USAGE_WINDOW_MONTHS = 24L
        const val USAGE_ROW_LIMIT = 200

        val WHITESPACE = Regex("\\s+")

        /**
         * Two months of history: long enough to be stable, short enough to
         * follow a change of habits. Used only here, at sheet-open time - the
         * widget itself never computes usage, so this costs one query per tap,
         * not one per refresh.
         */
        const val MOST_USED_WINDOW_DAYS = 60L
    }
}
