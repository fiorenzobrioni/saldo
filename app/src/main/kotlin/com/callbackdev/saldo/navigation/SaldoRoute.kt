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
