package com.callbackdev.saldo.feature.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.common.recurrencescan.RecurrenceScanSnapshot
import com.callbackdev.saldo.core.common.recurrencescan.RecurrenceScanStore
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import com.callbackdev.saldo.core.domain.model.hasEndedBy
import com.callbackdev.saldo.core.domain.model.runsInMonthOf
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.common.time.midnightTicker
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceCalculator
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceDetector
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.usecase.DetectRecurrenceSuggestionsUseCase
import com.callbackdev.saldo.core.domain.usecase.SetRecurringRulePausedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

/** One-shot events consumed by the hub screen. */
sealed interface RecurrencesEvent {
    /** The explicit scan failed: say so instead of silently showing the old result. */
    data object ScanFailed : RecurrencesEvent

    /** Pausing or resuming a rule failed: the row keeps its previous state. */
    data object WriteFailed : RecurrencesEvent
}

/**
 * Drives the recurrences hub: active recurring expenses (subscriptions) and
 * recurring incomes, each with monthly-equivalent figures, the next
 * charge/credit, the monthly total and the annual projection. All figures
 * derive reactively from the database.
 *
 * The recurrence scan (Fase 19, ADR 43) has no observer here on purpose: the
 * hub re-presents the persisted result and the only trigger is [onScanClick].
 */
@HiltViewModel
class RecurrencesViewModel @Inject constructor(
    recurringRuleRepository: RecurringRuleRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    userPreferences: UserPreferencesRepository,
    private val detectRecurrenceSuggestions: DetectRecurrenceSuggestionsUseCase,
    private val recurrenceScanStore: RecurrenceScanStore,
    private val setRecurringRulePaused: SetRecurringRulePausedUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val sort = MutableStateFlow(SubscriptionSort.NEXT_CHARGE)

    /** True while a tapped scan is running; guards against a double tap. */
    private val isScanning = MutableStateFlow(false)

    /**
     * Sort choice and the current day, pre-combined to stay within combine's
     * arity. The midnight ticker re-anchors "today" so the next charge dates
     * and the active-rule filter stay correct while the hub is left open.
     */
    private val sortAndToday = combine(sort, midnightTicker(clock), ::Pair)

    /**
     * The persisted scan state (ADR 43): the last result with its date and the
     * dismissed keys. Reading a saved result is not a scan; nothing here ever
     * queries the ledger.
     */
    private val scanInputs = combine(
        recurrenceScanStore.snapshot,
        recurrenceScanStore.dismissedKeys,
        isScanning,
        ::Triple,
    )

    /** Currency preference and scan state, pre-combined to stay within combine's arity. */
    private val currencyAndScan = combine(userPreferences.primaryCurrencyOverride, scanInputs, ::Pair)

    val uiState: StateFlow<RecurrencesUiState> = combine(
        recurringRuleRepository.observeRules(),
        accountRepository.observeAccountsWithBalance(),
        categoryRepository.observeCategories(),
        sortAndToday,
        currencyAndScan,
    ) { rules, accounts, categories, (sortOrder, today), (currencyOverride, scanState) ->
        buildState(rules, accounts, categories, sortOrder, currencyOverride, today, scanState)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = RecurrencesUiState(today = LocalDate.now(clock)),
    )

    private val _events = Channel<RecurrencesEvent>(Channel.BUFFERED)
    val events: Flow<RecurrencesEvent> = _events.receiveAsFlow()

    fun onSortSelected(newSort: SubscriptionSort) {
        sort.update { newSort }
    }

    /**
     * The one and only trigger of the recurrence scan (ADR 43): runs the pass,
     * persists the outcome with today's date, and reports a failure instead of
     * pretending nothing happened.
     */
    fun onScanClick() {
        if (isScanning.value) return
        isScanning.value = true
        viewModelScope.launch {
            val today = LocalDate.now(clock)
            val result = suspendRunCatching {
                recurrenceScanStore.saveResult(detectRecurrenceSuggestions(today), today)
            }
            isScanning.value = false
            if (result.isFailure) _events.send(RecurrencesEvent.ScanFailed)
        }
    }

    /** Persists the dismissal: a dismissed suggestion never reappears (ADR 43). */
    fun onSuggestionDismissed(item: RecurrenceSuggestionItem) {
        viewModelScope.launch { recurrenceScanStore.dismiss(item.suggestion.key) }
    }

    /**
     * Pauses a running rule or resumes a paused one (Fase 39, F3). Resuming
     * never back-fills the skipped occurrences: see [SetRecurringRulePausedUseCase].
     */
    fun onPauseToggled(item: SubscriptionItem) {
        viewModelScope.launch {
            val result = suspendRunCatching {
                setRecurringRulePaused(item.rule, paused = !item.rule.isPaused, today = LocalDate.now(clock))
            }
            if (result.isFailure) _events.send(RecurrencesEvent.WriteFailed)
        }
    }

    @Suppress("LongParameterList") // One argument per combined source plus the resolved day.
    private fun buildState(
        rules: List<RecurringRule>,
        accounts: List<AccountWithBalance>,
        categories: List<Category>,
        sortOrder: SubscriptionSort,
        currencyOverride: Currency?,
        today: LocalDate,
        scanState: Triple<RecurrenceScanSnapshot?, Set<String>, Boolean>,
    ): RecurrencesUiState {
        val accountById = accounts.associate { it.account.id to it.account }
        val categoryById = categories.associateBy { it.id }
        val (snapshot, dismissedKeys, scanning) = scanState

        fun sectionFor(type: TransactionType): RecurrenceSection {
            // Listed: everything not yet over. A rule starting next quarter is
            // real and its first charge date is worth seeing, so it stays on
            // screen even though it is priced at zero below.
            val items = rules
                .filter { it.type == type && !it.hasEndedBy(today) }
                .map { rule ->
                    rule.toItem(
                        today = today,
                        account = accountById[rule.accountId],
                        category = categoryById[rule.categoryId],
                        transferAccount = accountById[rule.transferAccountId],
                    )
                }
                // Paused rules sink to the bottom of every sort: they are on file
                // but not running, and the running ones are what the list is for.
                .sortedWith(compareBy<SubscriptionItem> { it.rule.isPaused }.then(sortOrder.comparator()))

            // The explicit Settings choice keeps section totals consistent
            // with dashboard and stats; otherwise the section's own majority.
            val primary = currencyOverride
                ?: items
                    .groupingBy { it.rule.currency }
                    .eachCount()
                    .maxByOrNull { it.value }?.key
                ?: fallbackCurrency
            // Priced: only the rules that carry a cost into this month. A rule
            // starting later this month counts (it is a real monthly cost); one
            // starting next quarter does not, and counting it would inflate the
            // total and the annual projection from the moment it is created.
            val running = items.filter { it.rule.currency == primary && it.rule.runsInMonthOf(today) }
            val monthlyTotal = running
                .fold(BigDecimal.ZERO) { acc, item -> acc.add(item.monthlyEquivalent) }

            return RecurrenceSection(
                items = items,
                monthlyTotal = monthlyTotal,
                annualProjection = monthlyTotal.multiply(BigDecimal(MONTHS_PER_YEAR)),
                // Same scope as monthlyTotal, so "N subscriptions - X/month" is coherent.
                activeCount = running.size,
                currency = primary,
            )
        }

        val transfers = sectionFor(TransactionType.TRANSFER)
        // Planned savings: the monthly-equivalent of transfers landing in a
        // savings account, the honest seed of Savings Goals (v2.0).
        val savingsItems = transfers.items.filter {
            accountById[it.rule.transferAccountId]?.type == AccountType.SAVINGS &&
                it.rule.runsInMonthOf(today)
        }
        val savingsCurrency = currencyOverride
            ?: savingsItems.groupingBy { it.rule.currency }.eachCount().maxByOrNull { it.value }?.key
            ?: fallbackCurrency
        val plannedMonthlySavings = savingsItems
            .filter { it.rule.currency == savingsCurrency }
            .fold(BigDecimal.ZERO) { acc, item -> acc.add(item.monthlyEquivalent) }

        return RecurrencesUiState(
            isLoading = false,
            expenses = sectionFor(TransactionType.EXPENSE),
            incomes = sectionFor(TransactionType.INCOME),
            transfers = transfers,
            plannedMonthlySavings = plannedMonthlySavings,
            savingsCurrency = savingsCurrency,
            sort = sortOrder,
            today = today,
            suggestions = visibleSuggestions(snapshot, dismissedKeys, rules, accountById, categoryById),
            scan = RecurrenceScanUi(
                isScanning = scanning,
                lastScan = snapshot?.scannedOn,
                foundNothing = snapshot != null && snapshot.result.suggestions.isEmpty(),
                truncated = snapshot?.result?.truncated == true,
            ),
        )
    }

    /**
     * The suggestions worth showing (ADR 43): not dismissed, on a live
     * account, and not already covered by a rule - creating the rule is what
     * makes its suggestion disappear, since the manual history that produced
     * it stays in the ledger and a re-scan would keep finding it.
     */
    private fun visibleSuggestions(
        snapshot: RecurrenceScanSnapshot?,
        dismissedKeys: Set<String>,
        rules: List<RecurringRule>,
        accountById: Map<Long, Account>,
        categoryById: Map<Long, Category>,
    ): List<RecurrenceSuggestionItem> = snapshot?.result?.suggestions.orEmpty()
        .filter { it.key !in dismissedKeys }
        .filter { accountById[it.accountId]?.isArchived == false }
        .filterNot { RecurrenceDetector.isCoveredBy(it, rules) }
        .map { suggestion ->
            RecurrenceSuggestionItem(
                suggestion = suggestion,
                category = suggestion.categoryId?.let { categoryById[it] },
                amount = MoneyMapper.toAmount(suggestion.amountMinor, suggestion.currency),
            )
        }

    private fun RecurringRule.toItem(
        today: LocalDate,
        account: Account?,
        category: Category?,
        transferAccount: Account?,
    ) = SubscriptionItem(
        rule = this,
        account = account,
        category = category,
        monthlyEquivalent = RecurrenceCalculator.monthlyEquivalent(this) ?: BigDecimal.ZERO,
        // A paused rule has no next charge: the row says "Paused" in its place.
        nextCharge = if (isPaused) null else RecurrenceCalculator.nextOccurrence(this, nextChargeFloor(today)),
        transferAccount = transferAccount,
    )

    /**
     * Floor for the "next charge" lookup: today, or the day after the last
     * generated charge when today has already been charged, so a charge that just
     * fired is not shown again as upcoming.
     */
    private fun RecurringRule.nextChargeFloor(today: LocalDate): LocalDate {
        val afterGenerated = lastGeneratedDate?.plusDays(1)
        return if (afterGenerated != null && afterGenerated > today) afterGenerated else today
    }

    private fun SubscriptionSort.comparator(): Comparator<SubscriptionItem> = when (this) {
        SubscriptionSort.NEXT_CHARGE ->
            compareBy(nullsLast()) { it.nextCharge }
        SubscriptionSort.COST ->
            compareByDescending<SubscriptionItem> { it.monthlyEquivalent }
                .thenBy { it.rule.name.lowercase() }
        SubscriptionSort.NAME ->
            compareBy { it.rule.name.lowercase() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MONTHS_PER_YEAR = 12
    }
}
