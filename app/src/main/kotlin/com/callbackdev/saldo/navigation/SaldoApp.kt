package com.callbackdev.saldo.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.callbackdev.saldo.feature.dashboard.DashboardScreen
import com.callbackdev.saldo.feature.settings.SettingsScreen
import com.callbackdev.saldo.feature.stats.StatsScreen
import com.callbackdev.saldo.feature.transactions.TransactionsScreen

/** Root composable: scaffold with bottom navigation and Navigation 3 display. */
@Composable
fun SaldoApp() {
    val backStack = rememberNavBackStack(DashboardRoute)
    val currentRoute = backStack.lastOrNull()

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val label = stringResource(destination.labelRes)
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = { backStack.switchTopLevelTab(destination.route) },
                        icon = { Icon(imageVector = destination.icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding),
            entryProvider = entryProvider {
                entry<DashboardRoute> { DashboardScreen() }
                entry<TransactionsRoute> { TransactionsScreen() }
                entry<StatsRoute> { StatsScreen() }
                entry<SettingsRoute> { SettingsScreen() }
            },
        )
    }
}
