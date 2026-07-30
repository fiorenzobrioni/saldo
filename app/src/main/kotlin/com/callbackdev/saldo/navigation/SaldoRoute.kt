package com.callbackdev.saldo.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 route keys. Each key is [Serializable] and implements [NavKey]
 * so the back stack created with rememberNavBackStack survives configuration
 * changes and process death.
 */
@Serializable
data object DashboardRoute : NavKey

@Serializable
data object TransactionsRoute : NavKey

@Serializable
data object StatsRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

/** Account list, reached from Settings (and from the Dashboard in Phase 5). */
@Serializable
data object AccountsRoute : NavKey

/**
 * Account editor: create mode when [accountId] is null, edit mode otherwise.
 * [initialTypeName] preselects the account type on creation (used by the savings
 * goal "create account" shortcut); it is an
 * [com.callbackdev.saldo.core.domain.model.AccountType] name, ignored in edit mode.
 */
@Serializable
data class AccountEditorRoute(
    val accountId: Long? = null,
    val initialTypeName: String? = null,
) : NavKey

/** Category list, reached from Settings. */
@Serializable
data object CategoriesRoute : NavKey

/**
 * Exchange-rates board (ADR 40): every downloaded ECB currency against the
 * primary one. Reached from Settings, from the Dashboard's estimated-rates
 * line and from the accounts list's countervalues note.
 */
@Serializable
data object ExchangeRatesRoute : NavKey

/**
 * Tag management (rename, merge, delete), reached from Settings. Tags are
 * still created inline in the movement editor; this screen only curates them.
 */
@Serializable
data object TagsRoute : NavKey

/**
 * Category editor: create mode when [categoryId] is null, edit mode otherwise.
 * [initialTypeName] preselects the type (the tab the editor was opened from);
 * it is a [com.callbackdev.saldo.core.domain.model.CategoryType] name.
 */
@Serializable
data class CategoryEditorRoute(
    val categoryId: Long? = null,
    val initialTypeName: String? = null,
) : NavKey

/**
 * Transaction editor: create mode when [transactionId] is null, edit mode otherwise.
 * [initialTypeName] preselects the movement type on creation (used by the dashboard
 * quick actions); it is a [com.callbackdev.saldo.core.domain.model.TransactionType] name.
 *
 * [initialCounterparty] and [initialAmountInput] prefill a new movement from the
 * credits and debts screen ("mark as returned"). A non-null counterparty opens
 * the loan section already on, blank included: that is how the empty state
 * invites the first loan, with the section open and the name still to type.
 * The amount is a plain decimal string, sanitized to the chosen account's
 * currency by the editor; both are a starting point the user confirms, never a
 * silent write.
 */
@Serializable
data class TransactionEditorRoute(
    val transactionId: Long? = null,
    val initialTypeName: String? = null,
    val initialCounterparty: String? = null,
    val initialAmountInput: String? = null,
) : NavKey

/**
 * Recurrences hub (subscriptions and recurring incomes), reached from the
 * dashboard card and Settings.
 */
@Serializable
data object RecurrencesRoute : NavKey

/**
 * Recurring-rule editor: create mode when [ruleId] is null, edit mode otherwise.
 * [initialTypeName] preselects the rule type on creation (the tab the editor was
 * opened from); it is a [com.callbackdev.saldo.core.domain.model.TransactionType] name.
 */
@Serializable
data class RecurringRuleEditorRoute(
    val ruleId: Long? = null,
    val initialTypeName: String? = null,
) : NavKey

/**
 * What is coming (ADR 36): confirmed movements dated in the future and
 * occurrences still to confirm, in one list. [pendingOnly] opens it on the
 * confirmation queue, which is how the dashboard's "to confirm" card enters -
 * a starting filter, not a different screen.
 */
@Serializable
data class UpcomingRoute(val pendingOnly: Boolean = false) : NavKey

/** Budget management (overall and per-category), reached from the dashboard and Settings. */
@Serializable
data object BudgetsRoute : NavKey

/** Budget editor: create mode when [budgetId] is null, edit mode otherwise. */
@Serializable
data class BudgetEditorRoute(val budgetId: Long? = null) : NavKey

/**
 * Credits and debts toward people (ADR 34), reached from the dashboard card
 * and Settings. A view over the movements carrying a counterparty, not a
 * register of its own, so it has no editor route: the movements are edited
 * where every movement is.
 */
@Serializable
data object CounterpartiesRoute : NavKey

/** Savings goals list, reached from the dashboard card and Settings. */
@Serializable
data object SavingsGoalsRoute : NavKey

/** Savings goal editor: create mode when [goalId] is null, edit mode otherwise. */
@Serializable
data class SavingsGoalEditorRoute(val goalId: Long? = null) : NavKey

/**
 * Story-style recap of one completed month ("Saldo Wrapped"), pushed full
 * screen from the dashboard teaser or the statistics toolbar. [year]/[month]
 * identify the calendar month, always in the past.
 */
@Serializable
data class MonthlyRecapRoute(val year: Int, val month: Int) : NavKey

/** Security settings (app lock, biometrics, screen privacy), reached from Settings. */
@Serializable
data object SecurityRoute : NavKey

/** Manual file backup and guided restore, reached from Settings. */
@Serializable
data object BackupRoute : NavKey

/** About screen (version, license, credits), reached from Settings. */
@Serializable
data object AboutRoute : NavKey

/**
 * Statistics drill-down: the movements of a local-date window (`[start, end)`
 * as epoch days), optionally narrowed to one category or account. Pushed on
 * top of the Stats tab so back returns to the charts.
 *
 * Both bounds null means the whole ledger, with no date restriction at all:
 * what a credits-and-debts drill-down needs, where a loan from two years ago is
 * exactly as relevant as yesterday's.
 *
 * [statsScope] restricts the list to what the statistics queries counted
 * (primary currency, excluded-from-stats skipped, spend-only rows for an
 * account drill-down), so the list always agrees with the tapped figure;
 * the dashboard's today/month drill-downs keep the cash view instead.
 * [uncategorizedOnly] narrows to movements without a category (the ring's
 * uncategorized slice). [counterparty] narrows to one person (ADR 34).
 */
@Serializable
data class FilteredTransactionsRoute(
    val startEpochDay: Long? = null,
    val endEpochDayExclusive: Long? = null,
    val categoryId: Long? = null,
    val counterparty: String? = null,
    val accountId: Long? = null,
    val statsScope: Boolean = false,
    val uncategorizedOnly: Boolean = false,
    /**
     * Inverts the currency test of [statsScope]: the list shows exactly the
     * movements the statistics left out for being in another currency. Backs
     * the "not included" notice on the statistics screen.
     */
    val otherCurrenciesOnly: Boolean = false,
) : NavKey
