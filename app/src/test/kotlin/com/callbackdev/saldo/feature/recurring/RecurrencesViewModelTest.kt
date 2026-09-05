package com.callbackdev.saldo.feature.recurring

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.callbackdev.saldo.core.common.recurrencescan.RecurrenceScanSnapshot
import com.callbackdev.saldo.core.common.recurrencescan.RecurrenceScanStore
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceScanResult
import com.callbackdev.saldo.core.domain.recurrence.RecurrenceSuggestion
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.usecase.DetectRecurrenceSuggestionsUseCase
import com.callbackdev.saldo.core.domain.usecase.SetRecurringRulePausedUseCase
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency

@ExtendWith(MainDispatcherExtension::class)
class RecurrencesViewModelTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-09T09:00:00Z"), ZoneId.of("Europe/Rome"))

    private val recurringRuleRepository = mockk<RecurringRuleRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val userPreferences = mockk<UserPreferencesRepository>()
    private val detectRecurrences = mockk<DetectRecurrenceSuggestionsUseCase>()
    private val scanStore = mockk<RecurrenceScanStore>()
    private val setRecurringRulePaused = mockk<SetRecurringRulePausedUseCase>(relaxed = true)

    /** In-memory stand-ins for the scan store's persistence, like AppLockManagerTest does. */
    private val storedSnapshot = MutableStateFlow<RecurrenceScanSnapshot?>(null)
    private val storedDismissals = MutableStateFlow<Set<String>>(emptySet())

    private fun rule(
        id: Long,
        name: String,
        frequency: RecurrenceFrequency,
        amount: String,
        startDate: LocalDate,
        type: TransactionType = TransactionType.EXPENSE,
        endDate: LocalDate? = null,
        isPaused: Boolean = false,
    ) = RecurringRule(
        id = id,
        name = name,
        type = type,
        currency = eur,
        accountId = 1L,
        frequency = frequency,
        startDate = startDate,
        amount = BigDecimal(amount),
        dayOfReference = startDate.dayOfMonth,
        endDate = endDate,
        isPaused = isPaused,
    )

    private val netflix =
        rule(1L, "Netflix", RecurrenceFrequency.MONTHLY, "12.99", LocalDate.of(2026, 7, 7))
    private val spotify =
        rule(2L, "Spotify", RecurrenceFrequency.MONTHLY, "9.99", LocalDate.of(2026, 7, 12))
    private val insurance =
        rule(3L, "Assicurazione", RecurrenceFrequency.SEMIANNUAL, "96.00", LocalDate.of(2026, 9, 15))
    private val salary = rule(
        4L, "Stipendio", RecurrenceFrequency.MONTHLY, "2000.00",
        LocalDate.of(2026, 7, 27), TransactionType.INCOME,
    )
    private val rent = rule(
        5L, "Affitto attivo", RecurrenceFrequency.MONTHLY, "650.00",
        LocalDate.of(2026, 7, 3), TransactionType.INCOME,
    )
    private val ended = rule(
        6L, "Old", RecurrenceFrequency.MONTHLY, "5.00",
        LocalDate.of(2025, 1, 1), endDate = LocalDate.of(2026, 1, 1),
    )
    private val endedIncome = rule(
        7L, "Old bonus", RecurrenceFrequency.MONTHLY, "100.00",
        LocalDate.of(2025, 1, 1), TransactionType.INCOME, endDate = LocalDate.of(2026, 1, 1),
    )

    private fun account(id: Long, type: AccountType) = Account(
        id = id,
        name = "acc-$id",
        type = type,
        currency = eur,
        initialBalance = BigDecimal.ZERO,
    )

    private fun transfer(
        id: Long,
        amount: String,
        toAccountId: Long,
        startDate: LocalDate = LocalDate.of(2026, 7, 5),
    ) = RecurringRule(
        id = id,
        name = "transfer-$id",
        type = TransactionType.TRANSFER,
        currency = eur,
        accountId = 1L,
        frequency = RecurrenceFrequency.MONTHLY,
        startDate = startDate,
        amount = BigDecimal(amount),
        dayOfReference = startDate.dayOfMonth,
        transferAccountId = toAccountId,
        transferAmount = BigDecimal(amount),
        transferCurrency = eur,
    )

    private fun viewModel(
        rules: List<RecurringRule>,
        currencyOverride: Currency? = null,
        accounts: List<Account> = emptyList(),
    ): RecurrencesViewModel {
        every { recurringRuleRepository.observeRules() } returns flowOf(rules)
        every { accountRepository.observeAccountsWithBalance() } returns
            flowOf(accounts.map { AccountWithBalance(it, BigDecimal.ZERO) })
        every { categoryRepository.observeCategories() } returns flowOf(emptyList<Category>())
        every { userPreferences.primaryCurrencyOverride } returns flowOf(currencyOverride)
        every { scanStore.snapshot } returns storedSnapshot
        every { scanStore.dismissedKeys } returns storedDismissals
        coEvery { scanStore.saveResult(any(), any()) } coAnswers {
            storedSnapshot.value = RecurrenceScanSnapshot(secondArg(), firstArg())
        }
        coEvery { scanStore.dismiss(any()) } coAnswers {
            storedDismissals.value = storedDismissals.value + firstArg<String>()
        }
        return RecurrencesViewModel(
            recurringRuleRepository,
            accountRepository,
            categoryRepository,
            userPreferences,
            detectRecurrences,
            scanStore,
            setRecurringRulePaused,
            clock,
        )
    }

    private fun suggestion(
        key: String = "amount:EXPENSE:1:none:EUR:1299",
        amountMinor: Long = 1299L,
        accountId: Long = 1L,
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        name: String? = "Netflix",
    ) = RecurrenceSuggestion(
        key = key,
        type = TransactionType.EXPENSE,
        name = name,
        amountMinor = amountMinor,
        isVariableAmount = false,
        currency = eur,
        frequency = frequency,
        accountId = accountId,
        categoryId = null,
        occurrenceCount = 4,
        lastOccurrence = LocalDate.of(2026, 6, 15),
        nextOccurrence = LocalDate.of(2026, 7, 15),
        dayOfReference = 15,
    )

    private fun storedScan(vararg suggestions: RecurrenceSuggestion, truncated: Boolean = false) {
        storedSnapshot.value = RecurrenceScanSnapshot(
            scannedOn = LocalDate.of(2026, 7, 8),
            result = RecurrenceScanResult(suggestions.toList(), truncated),
        )
    }

    private suspend fun ReceiveTurbine<RecurrencesUiState>.awaitLoaded(): RecurrencesUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }

    @Test
    fun `monthly total sums equivalents, annual projects over twelve months, count is active`() = runTest {
        val viewModel = viewModel(listOf(netflix, spotify, insurance, salary, ended))

        viewModel.uiState.test {
            val state = awaitLoaded()
            // Income (salary) and the ended rule are excluded from the expenses
            // tab; the insurance starts on 15 Sep, so it is listed but not
            // priced (today is 9 Jul). Spotify starts on 12 Jul: later than
            // today but inside this month, so it is a real monthly cost.
            assertEquals(2, state.expenses.activeCount)
            // 12.99 + 9.99; the insurance starting next quarter adds nothing.
            assertEquals(BigDecimal("22.98"), state.expenses.monthlyTotal)
            assertEquals(BigDecimal("275.76"), state.expenses.annualProjection)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a paused rule is listed last, has no next charge and is priced at zero`() = runTest {
        // Netflix would sort first by next charge (7 Jul); paused it sinks below Spotify.
        val pausedNetflix = netflix.copy(isPaused = true)
        val viewModel = viewModel(listOf(pausedNetflix, spotify))

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(listOf("Spotify", "Netflix"), state.expenses.items.map { it.rule.name })
            assertEquals(null, state.expenses.items.last().nextCharge)
            assertEquals(1, state.expenses.activeCount)
            assertEquals(BigDecimal("9.99"), state.expenses.monthlyTotal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the pause quick action delegates to the use case with the opposite state`() = runTest {
        val viewModel = viewModel(listOf(netflix, spotify.copy(isPaused = true)))

        viewModel.uiState.test {
            val state = awaitLoaded()
            val netflixItem = state.expenses.items.first { it.rule.name == "Netflix" }
            val spotifyItem = state.expenses.items.first { it.rule.name == "Spotify" }

            viewModel.onPauseToggled(netflixItem)
            viewModel.onPauseToggled(spotifyItem)

            coVerify { setRecurringRulePaused(netflixItem.rule, paused = true, any()) }
            coVerify { setRecurringRulePaused(spotifyItem.rule, paused = false, any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a rule starting after this month is listed but priced at zero`() = runTest {
        val viewModel = viewModel(listOf(netflix, insurance))

        viewModel.uiState.test {
            val state = awaitLoaded()
            // Still on screen, with its first charge date: hiding it until
            // September would make a rule the user just created disappear.
            assertEquals(2, state.expenses.items.size)
            assertEquals(LocalDate.of(2026, 9, 15), state.expenses.items.first { it.rule.id == 3L }.nextCharge)
            // But it costs nothing this month.
            assertEquals(1, state.expenses.activeCount)
            assertEquals(BigDecimal("12.99"), state.expenses.monthlyTotal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a rule starting later this month already counts as a monthly cost`() = runTest {
        // Today is 9 Jul, Spotify's first charge is 12 Jul: pricing it at zero
        // until it first charges would understate the month just as badly.
        val viewModel = viewModel(listOf(spotify))

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(1, state.expenses.activeCount)
            assertEquals(BigDecimal("9.99"), state.expenses.monthlyTotal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `planned savings ignores a transfer starting after this month`() = runTest {
        val future = transfer(11L, "200.00", toAccountId = 2L, startDate = LocalDate.of(2026, 10, 1))
        val viewModel = viewModel(
            rules = listOf(transfer(10L, "150.00", toAccountId = 2L), future),
            accounts = listOf(account(1L, AccountType.CHECKING), account(2L, AccountType.SAVINGS)),
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            // Only the transfer already running feeds the planned-savings rate.
            assertEquals(BigDecimal("150.00"), state.plannedMonthlySavings)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `explicit currency override scopes the section totals over the item majority`() = runTest {
        val usd = Currency.getInstance("USD")
        val viewModel = viewModel(listOf(netflix, spotify), currencyOverride = usd)

        viewModel.uiState.test {
            val state = awaitLoaded()
            // All rules are EUR: with a USD override the section stays in USD
            // (consistent with dashboard and stats) and totals nothing.
            assertEquals(usd, state.expenses.currency)
            assertEquals(BigDecimal.ZERO, state.expenses.monthlyTotal)
            assertEquals(0, state.expenses.activeCount)
            // The rules themselves are still listed, each in its own currency.
            assertEquals(2, state.expenses.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `income section carries recurring incomes only, with totals and next credit`() = runTest {
        val viewModel = viewModel(listOf(netflix, salary, rent, endedIncome))

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(2, state.incomes.activeCount)
            // 2000.00 + 650.00; the ended income and the expense are excluded.
            assertEquals(BigDecimal("2650.00"), state.incomes.monthlyTotal)
            assertEquals(BigDecimal("31800.00"), state.incomes.annualProjection)
            // Default sort by next credit: salary 27 Jul before rent 3 Aug
            // (rent's July credit on the 3rd is already past today, 9 Jul).
            assertEquals(listOf("Stipendio", "Affitto attivo"), state.incomes.items.map { it.rule.name })
            assertEquals(LocalDate.of(2026, 7, 27), state.incomes.items[0].nextCharge)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expense and income sections do not leak into each other`() = runTest {
        val viewModel = viewModel(listOf(netflix, salary))

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(listOf("Netflix"), state.expenses.items.map { it.rule.name })
            assertEquals(listOf("Stipendio"), state.incomes.items.map { it.rule.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `transfers section carries transfers only and planned savings sums savings destinations`() = runTest {
        // Account 2 is a savings account; account 3 is checking.
        val toSavings = transfer(10L, "150.00", toAccountId = 2L)
        val toChecking = transfer(11L, "80.00", toAccountId = 3L)
        val viewModel = viewModel(
            rules = listOf(netflix, salary, toSavings, toChecking),
            accounts = listOf(
                account(1L, AccountType.CHECKING),
                account(2L, AccountType.SAVINGS),
                account(3L, AccountType.CHECKING),
            ),
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(
                listOf("transfer-10", "transfer-11"),
                state.transfers.items.map { it.rule.name }.sorted(),
            )
            // Only the transfer landing in the savings account counts.
            assertEquals(0, BigDecimal("150.00").compareTo(state.plannedMonthlySavings))
            // Transfers never surface in the expense or income tabs.
            assertEquals(listOf("Netflix"), state.expenses.items.map { it.rule.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `planned savings is zero when no transfer targets a savings account`() = runTest {
        val toChecking = transfer(11L, "80.00", toAccountId = 3L)
        val viewModel = viewModel(
            rules = listOf(toChecking),
            accounts = listOf(account(1L, AccountType.CHECKING), account(3L, AccountType.CHECKING)),
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(0, BigDecimal.ZERO.compareTo(state.plannedMonthlySavings))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `default sort is by next charge`() = runTest {
        val viewModel = viewModel(listOf(netflix, spotify, insurance))

        viewModel.uiState.test {
            val state = awaitLoaded()
            // Spotify 12 Jul, Netflix 7 Aug, Assicurazione 15 Sep.
            assertEquals(
                listOf("Spotify", "Netflix", "Assicurazione"),
                state.expenses.items.map { it.rule.name },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sort by cost orders by monthly equivalent descending`() = runTest {
        val viewModel = viewModel(listOf(netflix, spotify, insurance))

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.onSortSelected(SubscriptionSort.COST)
            var state = awaitItem()
            while (state.sort != SubscriptionSort.COST) state = awaitItem()
            // 16.00, 12.99, 9.99
            assertEquals(
                listOf("Assicurazione", "Netflix", "Spotify"),
                state.expenses.items.map { it.rule.name },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sort by name orders alphabetically`() = runTest {
        val viewModel = viewModel(listOf(netflix, spotify, insurance))

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.onSortSelected(SubscriptionSort.NAME)
            var state = awaitItem()
            while (state.sort != SubscriptionSort.NAME) state = awaitItem()
            assertEquals(
                listOf("Assicurazione", "Netflix", "Spotify"),
                state.expenses.items.map { it.rule.name },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the persisted scan result is re-presented without running a scan`() = runTest {
        storedScan(suggestion(), truncated = true)
        val viewModel = viewModel(listOf(spotify), accounts = listOf(account(1L, AccountType.CHECKING)))

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(listOf("Netflix"), state.suggestions.map { it.suggestion.name })
            assertEquals(LocalDate.of(2026, 7, 8), state.scan.lastScan)
            assertTrue(state.scan.truncated)
            cancelAndIgnoreRemainingEvents()
        }
        // The one promise of ADR 43: opening the hub never runs the detection.
        coVerify(exactly = 0) { detectRecurrences(any()) }
    }

    @Test
    fun `tapping the scan row runs the pass once and persists the outcome with its date`() = runTest {
        val found = RecurrenceScanResult(listOf(suggestion()), truncated = false)
        coEvery { detectRecurrences(any()) } returns found
        val viewModel = viewModel(listOf(spotify), accounts = listOf(account(1L, AccountType.CHECKING)))

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.onScanClick()
            var state = awaitItem()
            while (state.suggestions.isEmpty()) state = awaitItem()
            assertEquals(listOf("Netflix"), state.suggestions.map { it.suggestion.name })
            assertEquals(BigDecimal("12.99"), state.suggestions.single().amount)
            // Today from the fixed clock: the declared "last search" date.
            assertEquals(LocalDate.of(2026, 7, 9), state.scan.lastScan)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { detectRecurrences(LocalDate.of(2026, 7, 9)) }
        coVerify(exactly = 1) { scanStore.saveResult(found, LocalDate.of(2026, 7, 9)) }
    }

    @Test
    fun `a dismissed suggestion stays hidden`() = runTest {
        storedScan(suggestion())
        val viewModel = viewModel(listOf(spotify), accounts = listOf(account(1L, AccountType.CHECKING)))

        viewModel.uiState.test {
            val state = awaitLoaded()
            viewModel.onSuggestionDismissed(state.suggestions.single())
            var next = awaitItem()
            while (next.suggestions.isNotEmpty()) next = awaitItem()
            assertTrue(next.suggestions.isEmpty())
            // The result itself is still there, only the suggestion is gone.
            assertEquals(LocalDate.of(2026, 7, 8), next.scan.lastScan)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { scanStore.dismiss(suggestion().key) }
    }

    @Test
    fun `a suggestion covered by an existing rule disappears by construction`() = runTest {
        // Netflix the rule and Netflix the suggestion: same account, category,
        // frequency and amount. Creating the rule is what hides the suggestion.
        storedScan(suggestion())
        val viewModel = viewModel(listOf(netflix), accounts = listOf(account(1L, AccountType.CHECKING)))

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertTrue(state.suggestions.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a suggestion pointing at a missing account is not shown`() = runTest {
        storedScan(suggestion(accountId = 99L))
        val viewModel = viewModel(listOf(spotify), accounts = listOf(account(1L, AccountType.CHECKING)))

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertTrue(state.suggestions.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed scan reports the failure instead of silently doing nothing`() = runTest {
        coEvery { detectRecurrences(any()) } throws IllegalStateException("boom")
        val viewModel = viewModel(listOf(spotify), accounts = listOf(account(1L, AccountType.CHECKING)))

        viewModel.events.test {
            viewModel.onScanClick()
            assertEquals(RecurrencesEvent.ScanFailed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
