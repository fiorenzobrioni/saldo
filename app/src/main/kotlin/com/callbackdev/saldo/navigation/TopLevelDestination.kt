package com.callbackdev.saldo.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.callbackdev.saldo.R

/** Top-level destinations shown in the bottom navigation bar. */
enum class TopLevelDestination(
    val route: NavKey,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    DASHBOARD(DashboardRoute, R.string.nav_dashboard, Icons.Filled.SpaceDashboard),
    TRANSACTIONS(TransactionsRoute, R.string.nav_transactions, Icons.AutoMirrored.Filled.ReceiptLong),
    STATS(StatsRoute, R.string.nav_stats, Icons.Filled.Insights),
    SETTINGS(SettingsRoute, R.string.nav_settings, Icons.Filled.Settings),
}
