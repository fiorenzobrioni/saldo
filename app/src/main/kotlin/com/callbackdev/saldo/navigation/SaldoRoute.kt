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

/** Account editor: create mode when [accountId] is null, edit mode otherwise. */
@Serializable
data class AccountEditorRoute(val accountId: Long? = null) : NavKey

/** Category list, reached from Settings. */
@Serializable
data object CategoriesRoute : NavKey

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
 */
@Serializable
data class TransactionEditorRoute(
    val transactionId: Long? = null,
    val initialTypeName: String? = null,
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

/** Confirmation of pending recurring movements (confirm mode / variable amount). */
@Serializable
data object PendingMovementsRoute : NavKey

/** Budget management (overall and per-category), reached from the dashboard and Settings. */
@Serializable
data object BudgetsRoute : NavKey

/** Budget editor: create mode when [budgetId] is null, edit mode otherwise. */
@Serializable
data class BudgetEditorRoute(val budgetId: Long? = null) : NavKey

/** Savings goals list, reached from the dashboard card and Settings. */
@Serializable
data object SavingsGoalsRoute : NavKey

/** Savings goal editor: create mode when [goalId] is null, edit mode otherwise. */
@Serializable
data class SavingsGoalEditorRoute(val goalId: Long? = null) : NavKey

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
 * [statsScope] restricts the list to what the statistics queries counted
 * (primary currency, excluded-from-stats skipped, spend-only rows for an
 * account drill-down), so the list always agrees with the tapped figure;
 * the dashboard's today/month drill-downs keep the cash view instead.
 * [uncategorizedOnly] narrows to movements without a category (the ring's
 * uncategorized slice).
 */
@Serializable
data class FilteredTransactionsRoute(
    val startEpochDay: Long,
    val endEpochDayExclusive: Long,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val statsScope: Boolean = false,
    val uncategorizedOnly: Boolean = false,
) : NavKey
