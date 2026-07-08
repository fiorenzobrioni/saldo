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

/** Transaction editor: create mode when [transactionId] is null, edit mode otherwise. */
@Serializable
data class TransactionEditorRoute(val transactionId: Long? = null) : NavKey
